import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.URL;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@SuppressWarnings("deprecation")


public class Server {

    private ExecutorService service;
    private int porta_registro;//3333 associata al registry
    private int porta_notifica;//39000 porta a cui associo l'oggetto remoto del servizio notifica
    private int porta_registrazioni;//39001 porta a cui associo oggetto remoto del servizio registrazioni
    private int frequenza; // serializzazione 90000
    private int frequenza_aggiornamento_parola;//60000
    private int porta_socket_TCP;//9999
    private registro_registrazioni_impl registro;
    private Map <String, Partita> partite; //tiene le partite in corso, una per giocatore 
    private Set<String> parole;// = new HashSet<>(); per contenere le parole del vocabolario
    private SecretWordObject SWO;
    private Classifica classifica;
    private HashMap <String, SocketChannel> connessi; //per tenere traccia con quale connessione sono connessi gli utenti;
    private Server_NotifyEvent_impl notify_server;
    private String indirizzo_multicast;//=226.226.226.226
    private int porta_multicast;//=4000

	public Server(int porta_registro,int porta_notifica,int porta_registrazioni,int frequenza,int frequenza_aggiornamento_parola,int porta_socket_TCP,int porta_multicast,String indirizzo_multicast) { 
        this.porta_registro = porta_registro;
        this.porta_notifica=porta_notifica;
        this.porta_registrazioni=porta_registrazioni;
        this.frequenza=frequenza;
        this.frequenza_aggiornamento_parola=frequenza_aggiornamento_parola;
        this.partite=Collections.synchronizedMap(new HashMap <String, Partita>()); //
        this.porta_socket_TCP=porta_socket_TCP;
        this.connessi=new HashMap <String, SocketChannel> ();
        this.classifica=new Classifica();
        this.indirizzo_multicast=indirizzo_multicast;
        this.porta_multicast=porta_multicast;
        
    }


    //task che serializza periodicamente il registro delle registrazioni
    public class serializzatore_Registro implements Runnable {
        
        ConcurrentHashMap <String, Utente> Registro;
        int frequenza;
        public serializzatore_Registro(ConcurrentHashMap <String, Utente> Registro, int frequenza) throws NullPointerException
        {
            if(Registro==null) throw new NullPointerException();
            this.Registro=Registro;
            this.frequenza=frequenza;
            
        }
    
        public void run(){
            while(true)
            {
                try {
                
					Thread.sleep(frequenza);
                   
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
                File fileregistrazioni = new File("Registro_registrazioni.json");
                try ( FileOutputStream fos = new FileOutputStream(fileregistrazioni);
						// apro un OutputStreamWriter
						OutputStreamWriter ow = new OutputStreamWriter(fos);) {
					// serializzo
					Gson gson = new GsonBuilder().setPrettyPrinting().create();
					String jsonString = gson.toJson(Registro);
					// scrivo sull' OutputStream
					ow.write(jsonString);
					ow.flush();

					System.out.println("Serializzazione avvenuta con successo");

				}catch (RemoteException e) {

					e.printStackTrace();
				}

				catch (FileNotFoundException e) {

					e.printStackTrace();
				} catch (IOException ioe) {

					ioe.printStackTrace();
				}

            }

        }

    }


    public class estrazione_nuova_parola implements Runnable{
        //l'aggiornamento della secret word deve essere in mutua esclusione con gli altri thread che vanno a leggerla per questo modifico SWO in blocchi synchronized
        private int frequenza_aggiornamento_parola;
        private SecretWordObject SWO;
        private int n_secretword=0; //quando avvio il server inizio a numerare le secretword che estraggo
        public estrazione_nuova_parola(int frequenza_aggiornamento_parola,SecretWordObject SWO)throws NullPointerException{ 
            if(SWO==null) throw new NullPointerException();
            this.frequenza_aggiornamento_parola=frequenza_aggiornamento_parola;
            this.SWO=SWO;
        }

        public void run(){

            while(true){

                try {
                    // Creazione di un oggetto Random
                    Random random=new Random();
                    String traduzione=null;
        
                    // Ottenere una parola casuale dall'HashSet
                    int size=parole.size();
                    int item=random.nextInt(size); // genera un numero casuale compreso tra 0 e (size - 1)
                    int i=0;
        
                    for (String parola : parole) { //op un po "costosa" ma che intanto avviene una volta al "giorno"
                        if (i == item) {
                            try{
                                //traduco la parola inviando una richiesta al servizio di traduzione
                                URL url=new URL("https://api.mymemory.translated.net/get?q="+parola+"!&langpair=en|it");
                                
                                try (BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()))){
                                    String inputLine;
                                    // leggo la traduzione ottenuta come risposta
                                    inputLine = in.readLine(); //legge il file json dato in una sola riga come risposta e lo mette in una stringa inputline
                                    String[] m = inputLine.split(":");
                                    //{"responseData": m0
                                    //{"translatedText": m1
                                    //"Ciao Mondo!","match":m2
                                    //1} m3
                                    String[] n = m[2].split(",");
                                    traduzione=n[0];
                                    
                                    //System.out.println(inputLine); 
                                    
                                } catch (IOException ioe) {
                                    ioe.printStackTrace(System.err); 
                                }

                            }catch (MalformedURLException mue) { 
                                mue.printStackTrace(System.err); 
                            }
                
                            synchronized(SWO) //aggiorno la parola in modo sincronizzato
                            {
                                SWO.setSW(parola); // setto la nuova secretword
                                SWO.set_n_SW(n_secretword);//numero le parole estratte dal server
                                SWO.set_traduzione(traduzione);//setto la traduzione
                                SWO.resetgiocatori();// resetto la struttura dati che contiene i giocatori che stanno giocando/hanno gia giocato questa sw
                            }
                            n_secretword=n_secretword+1;//aumento n_secrteword per la prossima parola
                            System.out.println("PAROLA DEL GIORNO "+ parola);
                            System.out.println("Traduzione "+ traduzione);
                            break;
                        }
                        i++;
                    }
                    
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    
					Thread.sleep(frequenza_aggiornamento_parola);
                   
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
            }
        }
    }

    public void crea_set_parole(){
        try (BufferedReader reader = new BufferedReader(new FileReader("words.txt"))) {
            String parola;
            while ((parola = reader.readLine()) != null) { // Leggi ogni riga del file
                parole.add(parola.trim()); // Aggiungi la parola all'HashSet (rimuovi spazi vuoti eventuali)
            }
        }catch (IOException e) {
            System.err.println("Errore nella lettura del file: " + e.getMessage());
        }catch (Exception e) {
            e.printStackTrace();
        }

    }





    public class logout implements Runnable {
        private registro_registrazioni_impl registro;
        private String nomeutente;
        private SelectionKey key;
        private SocketChannel client;

        public logout(registro_registrazioni_impl registro,String nomeutente,SelectionKey key){
            this.nomeutente=nomeutente;
            this.registro=registro;
            this.key=key;
            client=(SocketChannel) key.channel();

        }
    
        public void run(){
            try {
                registro.setlogout(nomeutente); //op logout sincronizzata in utente, chiamata da registro_impl
                System.out.println("Server: in chiusura la connessione con il client " + client.getRemoteAddress());
                
                synchronized(connessi)
                {
                    connessi.remove(nomeutente);
                }

                try {
                    ByteBuffer buffer = ByteBuffer.allocate(1024);
                    String replyStr = "ok";
                    buffer.put(replyStr.getBytes());
                    buffer.flip();
                
                    //while per assicurarmi di scrivere tt il buffer nel canale 
                    while (buffer.hasRemaining()) {
                        client.write(buffer);
                    }
                
                }catch (IOException ioe) {
                    ioe.printStackTrace();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }

                client.close();
                key.cancel();

            } catch (IOException e) {
                e.printStackTrace();
            }

        }

    }

    public class connesso implements Runnable {
       
        private String nomeutente;
        private SocketChannel c_channel;
        private HashMap <String, SocketChannel> connessi;

        public connesso(String nomeutente,SocketChannel c_channel, HashMap <String, SocketChannel> connessi){
            this.nomeutente=nomeutente;
            this.c_channel=c_channel;
            this.connessi=connessi;
        }
        
    
        public void run(){
            synchronized(connessi){ //accedo a connessi anche quando mi voglio disconnetere quindi va synch.
            
                connessi.put(nomeutente, c_channel);
                
            
            }

            try {
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                String replyStr = "ok:connessione andata a buon fine ";
                //int bytes_to_send = replyStr.getBytes().length;
                buffer.put(replyStr.getBytes());
                buffer.flip();
            
                //while per assicurarmi di scrivere tt il buffer nel canale 
                while (buffer.hasRemaining()) {
                    c_channel.write(buffer);
                }
                
            }catch (IOException ioe) {
                ioe.printStackTrace();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            

            
        }

    }



    public class disconnetti implements Runnable {
       
        private String nomeutente;
        private SocketChannel c_channel;
        private HashMap <String, SocketChannel> connessi;
        

        public disconnetti(String nomeutente,SocketChannel c_channel, HashMap <String, SocketChannel> connessi){
            this.nomeutente=nomeutente;
            this.c_channel=c_channel;
            this.connessi=connessi;
        }
        
    
        public void run(){
            synchronized(connessi){
        
                SocketChannel old = connessi.get(nomeutente);//(SocketChannel)la get mi restituise il socket di quel nome uetente
                
                if(old!=null){ //potrebbe essere a null perche nel metre laltro client ha fatto il logout
                    try {
                        
                        old.close();// chiudo il vecchio canale relativo alla vecchia connessione
                    } catch (Exception e) {
                        
                        e.printStackTrace();
                    }

                }
                connessi.put(nomeutente,c_channel);//rimuovo la vecchia remote addres, se è ancora presente, e aggiungo il nuovo remote address
                
            }

            try {
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                String replyStr = "ok:disconnessione e riconnessione andata a buon fine ";
                buffer.put(replyStr.getBytes());
                buffer.flip();
            
                //while per assicurarmi di scrivere tt il buffer nel canale 
                while (buffer.hasRemaining()) {
                    c_channel.write(buffer);
                }
            
            }catch (IOException ioe) {
                ioe.printStackTrace();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
             

        }

    }


    public class playWordleSERVER implements Runnable {
        private registro_registrazioni_impl registro;
        private String nomeutente;
        private SocketChannel client;
        private Map <String, Partita> partite;

        public playWordleSERVER(String nomeutente,SocketChannel client,Map <String, Partita> partite,registro_registrazioni_impl registro){
            this.nomeutente=nomeutente;
            this.registro=registro;
            this.client=client;
            this.partite=partite;
        }
        
    
        public void run(){
            
            //controllo che lultima partita di questo utente sia stata usata per calcolare le statistiche dell'utene
            // prima di andare a sostituirla con la nuova partita. Questo puo succedere quando per esempio il client 
            //si disconnette in modo anomalo mentre sta giocando una partita(es ctrl_c)e quindi il server non riceve
            //quei comandi che lo portano ad aggiornare le statistiche 
            if(partite.get(nomeutente)!=null && partite.get(nomeutente).get_flag()==1)
            {
                Utente u=registro.getUtente(nomeutente);
                u.reset_streak();//considero la partita persa quindi azzero il suo streak
                u.aggiorna_percentuale_vinte();
                //setto il flag a 0 per segnalare che il risultato di quella aprtita 
                //è stato usato per aggiornare le statistiche dell'utente
                partite.get(nomeutente).set_flag();
                
            }
            
            //adesso posso sostituire la vecchia partita con la nuova
            String secretword=null;
            String traduzione=null;
            int n_secretword=-1;
                                            
            //controllo se ha giocato alla parola del giorno
            synchronized(SWO) //cosi evito di entrare in concorrenza col thread che mi aggiorna la SWO
            {
                if(!SWO.containsusername(nomeutente)){
                    //il giocatore NON ha gia giocato la parola del giorno
                    SWO.addgiocatore(nomeutente);//aggiungo al set di giocatori che stanno giocando quella parola questo utente
                    System.out.println("aggiunto al gioco della secret word "+SWO.getSW()+" "+nomeutente);
                    secretword=SWO.getSW();
                    traduzione=SWO.get_traduzione();
                    n_secretword=SWO.get_n_secretword();
                            
                }    
                                                    
            }
                
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            try{
                if(secretword!=null) //significa che NON ha gia giocato la parola del giorno
                {
                    registro.getUtente(nomeutente).incrementa_partite_giocate(); //incremento le partite giocate da questo Utente/username
                    //non cè bisogno di sincronizzarla perche lutente puo giocare solo in un client alla volta 
                    //e registro è una struttura sincronizzata perche è una ConcurrentHashMap
                    
                    //creo la partita di questo utente per questa parola 
                    
                    partite.put(nomeutente, new Partita(secretword,traduzione,n_secretword));   
                
                    String replyStr = "ok:puoi giocare:"+secretword+":"+traduzione;
                    buffer.put(replyStr.getBytes());
                    buffer.flip();
                
                    //while per assicurarmi di scrivere tutto il buffer nel canale 
                    while (buffer.hasRemaining()) {
                        client.write(buffer);
                    }

                }
                else{ 
                    //lutente ha gia giocato questa secretword
                    //devo scrivere sul canale errore in risposta al giocatore
                    
                    String replyStr = "errore:hai gia giocato la parola del giorno";
                    buffer.put(replyStr.getBytes());
                    buffer.flip();
                
                    //while per assicurarmi di scrivere tutto il buffer nel canale 
                    while (buffer.hasRemaining()) {
                        client.write(buffer);
                    }  

                }

            }catch (IOException ioe) {
                ioe.printStackTrace();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        
        }

    }


    public class tentativoSERVER implements Runnable {
        private registro_registrazioni_impl registro;
        private Server_NotifyEvent_impl notify_server;
        private String nomeutente;
        private String guessWord;
        private SocketChannel client;
        private Map <String, Partita> partite;
        private Partita partita;
        private Set<String> parole;
        private String secretword;
        private int tentativi_rimasti;

        public tentativoSERVER(String nomeutente,registro_registrazioni_impl registro,SocketChannel client,Map <String, Partita> partite,String guessWord,Set<String> parole,Classifica classifica,Server_NotifyEvent_impl notify_server){
            this.nomeutente=nomeutente;
            this.registro=registro; //per aggiornare le statistiche del giocatore quando vince
            this.notify_server=notify_server;
            this.client=client;
            this.partite=partite;
            this.guessWord=guessWord;
            this.parole=parole;
            //estraggo la partita di questo utente 
            this.partita=partite.get(nomeutente);
            //estraggo la secret word relativa a questa partita
            this.secretword=partita.getsecretword();
        }
        
    
        public void run(){

            //System.out.println("entro in tentativo");
            System.out.println("la guessword ricevuta è "+guessWord);
            System.out.println("la secretword di questa partita è "+secretword);

            ByteBuffer buffer = ByteBuffer.allocate(1024);
            tentativi_rimasti=12-partite.get(nomeutente).gettentativi();

            try {
                //controllare che abbia tentativi rimasti viene fatto nel client
            
                //controllo che la guessword appartenga alla lista di parole
                if(parole.contains(guessWord))
                {
                    //aumentare i tentativi della partita
                    partita.add_tentativo();
                    //diminuire i tentativi rimasti
                    tentativi_rimasti=tentativi_rimasti-1;
                    //controllare se ha indovinato
                    if(guessWord.equals(secretword))
                    {
                        String replyStr = tentativi_rimasti+":vinto";
                        Utente u=registro.getUtente(nomeutente);
                        float punteggio;
                        //siccome lutente ha vinto devo aggiornare :
                        //streak di vincite 
                        u.aggiungi_streak();//aggiunge +1 alle vincite
                        //guess distributio
                        u.aggiorna_guess_distribution(partite.get(nomeutente).gettentativi());
                        //%vinte
                        u.aggiorna_percentuale_vinte();
                        //aggiorno il punteggio
                        punteggio=u.aggiorna_punteggio();
                        //aggiungo alla partita il fatto che lultima parola è stata indovinata
                        partita.addSuggerimento("++++++++++");
                        //setto il flag a 0 per segnalare che il risultato di quella aprtita è stato usato per aggiornare le statistiche dell'utenet
                        partite.get(nomeutente).set_flag();

                        //devo aggiornare la classifica, visto che questo utente ha vinto, sara cambiato il suo punteggio
                        //quindi devo aggiornare il punteggio nella classe utente(fatto sopra) e creare una nuova entry in classifica(rimuovendo quella vecchia col vecchio punteggio)
                        //in upgrade_classifica rimuove la vecchia entry col vecchio punteggio e aggiunge la nuova entry col nuovo punteggio
                        classifica.upgrade_classifica(nomeutente, punteggio,notify_server);

                        buffer.put(replyStr.getBytes());
                        buffer.flip();
                
                        //while per assicurarmi di scrivere tt il buffer nel canale 
                        while (buffer.hasRemaining()) {
                            client.write(buffer);
                        }

                    }
                    else{
                        //se non ha indovinato mandare il suggerimento, memorizzare tutti i suggerimenti in partita
                        
                        char[] SW = secretword.toCharArray(); //array di caratteri per la secretword
                        char[] GW = guessWord.toCharArray(); //array di caratteri per la guessedword
                        char[] SuggW = new char[12]; //array di caratteri per la suggestionword

                        for(int i=0;i<10;i++)
                        {
                            SuggW[i]='x';
                            for(int j=0;j<10;j++)
                            {
                                if(GW[i]==SW[j])
                                {
                                    if(i==j)
                                    {
                                        SuggW[i]='+'; 
                                        break;
                                    }
                                    else{
                                        SuggW[i]='?';
                                    }
                                }
                            }
                            
                        }
                        //se non ha indovinato mandare il suggerimento, memorizzare tutti i suggerimenti in partita

                        String suggerimento= new String(SuggW);
                        //aggiungo il suggerimento nelloggetto partita
                        partita.addSuggerimento(suggerimento);


                        String replyStr = tentativi_rimasti+":"+"suggerimento:"+suggerimento;
                        buffer.put(replyStr.getBytes());
                        buffer.flip();
                
                        //while per assicurarmi di scrivere tt il buffer nel canale 
                        while (buffer.hasRemaining()) {
                            client.write(buffer);
                        }

                    }
                    
                }
                else{//la parola non è presente nel vocabolario
                    String replyStr = tentativi_rimasti+":"+"errore2"; //errore2:la parola che hai inviato non è presente nel vocabolario
                    buffer.put(replyStr.getBytes());
                    buffer.flip();
                
                    //while per assicurarmi di scrivere tt il buffer nel canale 
                    while (buffer.hasRemaining()) {
                        client.write(buffer);
                    }

                }

            }catch (IOException ioe) {
                ioe.printStackTrace();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        
        }

    }

 public class statisticsSERVER implements Runnable {
        
        private String nomeutente;
        private SocketChannel client;
        private Map <String, Partita> partite;

        public statisticsSERVER(String nomeutente,SocketChannel client, Map <String, Partita> partite){
            this.nomeutente=nomeutente;
            this.client=client;
            this.partite=partite;
        }
        
    
        public void run(){

            ByteBuffer buffer = ByteBuffer.allocate(1024);
            
            try{
                //controllo che lultima partita di questo utente sia stata usata per calcolare le statistiche dellutene 
                //prima di mandargli le statistiche
                Utente u=registro.getUtente(nomeutente);

                if(partite.get(nomeutente)!=null && partite.get(nomeutente).get_flag()==1)
                {
                    u.reset_streak();//lutente ha perso/abbandonato la partita quindi azzero il suo streak
                    u.aggiorna_percentuale_vinte();
                    partite.get(nomeutente).set_flag();//setto il flag a 0 per segnalare che il risultato di quella aprtita è stato usato per aggiornare le statistiche dell'utenet
                    System.out.println("stampo i dati dellutente dopo che ho trovato una vecchia partita non scaricata=ctrlc in mezzo a una partita");
                    u.stampa_dati_utente();
                }

                String replyStr =u.get_stat_as_string();
                buffer.put(replyStr.getBytes());
                buffer.flip();
            
                //while per assicurarmi di scrivere tt il buffer nel canale 
                while (buffer.hasRemaining()) {
                    client.write(buffer);
                }
                
            }catch (IOException ioe) {
                ioe.printStackTrace();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        
        }

    }

    public class ShowMeRankingSERVER implements Runnable {
        
        
        private SocketChannel client;
        private Classifica classifica;

        public ShowMeRankingSERVER(SocketChannel client, Classifica classifica){
            this.client=client;
            this.classifica=classifica;
        }
        
    
        public void run(){

            System.out.println("entro in classifica");
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            
            try{
                
                String replyStr =classifica.get_classifica_as_string();

                buffer.put(replyStr.getBytes());
                buffer.flip();
            
                //while per assicurarmi di scrivere tt il buffer nel canale 
                while (buffer.hasRemaining()) {
                    client.write(buffer);
                }
                
            }catch (IOException ioe) {
                ioe.printStackTrace();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        
        }

    }

    public class ShareServer implements Runnable {
        
        private String nomeutente;
        private Partita p;
        private SocketChannel client;

        public ShareServer(String nomeutente,Map <String, Partita> partite, SocketChannel client){
            this.nomeutente=nomeutente;
            this.p=partite.get(nomeutente);
            this.client=client;
            
        }
        
        public void run(){

            ByteBuffer buffer = ByteBuffer.allocate(1024);
            
            try (DatagramSocket dsocket = new DatagramSocket()) {
                // Ottengo l'indirizzo del gruppo e ne controllo la validità.
                InetAddress ia_group = InetAddress.getByName(indirizzo_multicast);
                if (!ia_group.isMulticastAddress()) {
                    throw new IllegalArgumentException(
                    "Indirizzo multicast non valido: " + ia_group);
                }
                String share_partita=nomeutente+"\n"+p.get_partita_as_string();
                //partita non importa sinceonizzarla perche il client è sequenziale, finche non riceve risposta dal server
                //non andraà a fare altre operazioni sulla partita, si puo avviare una partita alla volta dopo lo share 

                byte[] content = share_partita.getBytes();

                DatagramPacket dpacket = new DatagramPacket(content, content.length,ia_group, porta_multicast);
                
                // Invio il pacchetto.
                dsocket.send(dpacket);
                System.out.printf("Server: messaggio inviato su multicast \n%s",share_partita);

                String replyStr ="ok";
                buffer.put(replyStr.getBytes());
                buffer.flip();
                
                //while per assicurarmi di scrivere tt il buffer nel canale 
                while (buffer.hasRemaining()) {
                    client.write(buffer);
                }
               
            
            }catch (IOException ioe) {
                ioe.printStackTrace();
            }catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    // metodo che viene eseguito per avviare il server
	public void avvio() {

        //creo un FixedThreadpool con tanti thread quanti sono i core
        try{
			int coreCount = Runtime.getRuntime().availableProcessors();
			System.out.println("Cores disponibili sulla macchina : " + coreCount);
			service = Executors.newFixedThreadPool(coreCount);
			System.out.println("ThreadPool creato");
        }
		catch (Exception e) {
            e.printStackTrace();
			System.out.println("Communication error " + e.toString());
        }

        //creo il servizio di registrazione con rmi
        //all'interno del quale:
        //1)creo il registro con i dati salvati nel file json.
        //2)metto ogni utente del file json nella classifica.

        //creo il servizio di notifica di aggiornamento dei primi 3 posti in classifica con rmi callbacks.
        try {
            
            //creo un istanza dell'oggetto remoto x le notifiche 
            notify_server=new Server_NotifyEvent_impl();
            //Esporto l'oggetto remoto ottenenedo lo stub corrispondente
            Server_NotifyEvent_interface stub_notify=(Server_NotifyEvent_interface) UnicastRemoteObject.exportObject(notify_server,porta_notifica); //39000
            //creazione di un registry sulla porta specificata
            LocateRegistry.createRegistry(porta_registro);//3333
            Registry r =LocateRegistry.getRegistry(porta_registro);
            //pubblicazione dello stub nel registry
            r.rebind("SERVIZIO-NOTIFICA",stub_notify);


            //creo un istanza dell' remoto x le registrazioni
            registro =new registro_registrazioni_impl(classifica,notify_server);
            //Esporto loggetto remoto ottenenedo lo stub corrispondente
            registro_registrazioni_interface stub = (registro_registrazioni_interface) UnicastRemoteObject.exportObject(registro,porta_registrazioni);//39001
            
            //pubblicazione dello stub nel registry
            r.rebind("SERVIZIO-REGISTRAZIONE",stub);


            System.out.printf("\nHo creato il registry per i servizi: SERVIZIO-REGISTRAZIONE e SERVIZIO-NOTIFICA sulle porte %d %d\n", porta_registro,porta_notifica);
            
        } catch (RemoteException e) {
            System.err.println("Errore: " + e.getMessage());
        }
        catch (Exception e) {
            e.printStackTrace();
        }


        //ora che ho il registro creo la classifica con ogni entry che rappresenta un utente
        ConcurrentHashMap <String, Utente> R=registro.getRegistro();
        for(Utente u: R.values())
        {
            //add_entry_ordinata si chiama quando quando avvio il server devo caricare in classifica tutti gli utenti del registro
            classifica.add_entry_ordinata(u.getNomeutente(), u.get_punteggio());
        }
        System.err.println("\nStampo la Classifica");
        classifica.stampa_classifica();

        //avvio il thread per la serializzazione periodica del registro delle registrazioni nel file json
        try {
            Thread serializ_registro = new Thread(new serializzatore_Registro(registro.getRegistro(),frequenza));
			serializ_registro.start();
            
        } catch (RejectedExecutionException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        parole=new HashSet<>();
        //creo la lista di parole dal file
        this.crea_set_parole();
        //creo l'oggetto che contiene la secret word e la lista di coloro che la stanno/lhanno giocata
        SWO=new SecretWordObject();

        //avvio il thread che estrae la nuova secretword periodicamente
        try {
            Thread estrattore_sw = new Thread(new estrazione_nuova_parola(frequenza_aggiornamento_parola, SWO));
			estrattore_sw.start();
        } catch (RejectedExecutionException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
       
        //mi preparo per ricevere connessioni dai client
        //dichiaro un ServerSocketChannel che sarà il mio listening socket per le richieste di connessione
        Selector selector;
        ServerSocketChannel serverSocketChannel;
		try {
            //apro un canale associato a un serversocket
            serverSocketChannel=ServerSocketChannel.open();
            ServerSocket socket = serverSocketChannel.socket(); //per reperire il socket associato al serversocletchannel
            //lego la socket a questa particolare inetsocketaddres
            socket.bind(new InetSocketAddress(porta_socket_TCP)); //ind ip sottointeso quello del localhost
            //setto il canale non  bloccante
            serverSocketChannel.configureBlocking(false);
            //apro il selettore
            selector = Selector.open();
            //registro sul selettore il serversocketchannel con l'interesse di monitorare le operazioni di accept
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.printf("Server pronto, in attesa di connessioni sulla porta %d\n", this.porta_socket_TCP);
                
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        while (true) { 

            try {
                selector.select(); //bloccante
            } catch (IOException ex) {
                System.out.println("errore sulla selct"); 
                ex.printStackTrace();
                break;
            }

            Set <SelectionKey> readyKeys = selector.selectedKeys();
            //prendo un iteratore sulle chiavi del selettore pronte per un qualche tipo di operazione 
            Iterator <SelectionKey> iterator = readyKeys.iterator();
            
            while (iterator.hasNext()) {
                //seleziono la prossima chiave
                SelectionKey key = iterator.next();
                iterator.remove();
                // rimuove la chiave dal Selected Key Set, ma non dal Registered Set 
                try {
                    if (key.isAcceptable()) { //vero se il canale associato a questa chiave è pronto per accettare una nuova connesione 
                        
                        //estraggo il canale associato alla chiave pronta ad accettare una nuova connessione, 
                        //questo canale sarà sempre quello che funge da welcome socket,
                        // e che ho registrato sul selector con la volonta di monitorare su di lui le operazioni di accept 
                        ServerSocketChannel server = (ServerSocketChannel) key.channel(); 

                        SocketChannel client = server.accept(); //stabilita peculiare connessione col client
                        System.out.println("Server: accettata nuova connessione dal client: " + client.getRemoteAddress()); 
                        client.configureBlocking(false);

                        // crea il buffer
                        ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);//crea un ByteBuffer con capacità sufficiente per memorizzare un intero
                        ByteBuffer message = ByteBuffer.allocate(2048);
                        ByteBuffer[] bfs = {length, message};
                        // aggiunge il canale del client al selector con l'operazione OP_READ e aggiunge l'array di bytebuffer [length, message] come attachment
                        client.register(selector, SelectionKey.OP_READ, bfs);

                    }
    
                    if (key.isReadable()){
                        SocketChannel c_channel = (SocketChannel) key.channel(); //estraggo la chiave pronta a leggere
                        // recupera l'array di bytebuffer (attachment)
                        ByteBuffer[] bfs = (ByteBuffer[]) key.attachment();
                        c_channel.read(bfs);//legge i dati dal SocketChannel nei buffer length e message.
                        if (!bfs[0].hasRemaining()){ //Verifica se il buffer length ha terminato la lettura dell'intero intero
                            bfs[0].flip(); //prepara il buffer lenght per la lettura
                            int l = bfs[0].getInt(); //l'intero lo estraggo, mi indica quanto è lungo il messaggio che dovro leggere dal chennel

                            if (bfs[1].position() == l) { //quindi ho letto lintero messaggio dal canale e lho scritto nel buffer message
                                bfs[1].flip();
                                String msg = new String(bfs[1].array()).trim();//creare una nuova stringa a partire dall'array di byte ottenuto dal buffer che contiene il messaggio + Il metodo trim() rimuove eventuali spazi vuoti sia all'inizio che alla fine della stringa
                                System.out.printf("Server: ricevuto %s\n", msg);
                                //splittare il messaggio in nomeutente password e comando 
                                String[] m = msg.split(" "); // Dividi la stringa in base agli spazi
                                
                                String nomeutente = m[0];  // "Ginevra"t
                                String cmd = m[1];  // "gioca" 
                                
                                
                                // rinizializzo il ByteBufferArray per successive comunicazioni
								ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
								ByteBuffer message = ByteBuffer.allocate(2048);
								ByteBuffer[] bfs2 = { length, message };
								key.attach(bfs2);
                                
                                //con uno switch capisco che comando mi ha mandato il client
                                //in modo da fare eseguire le giuste operazioni/task ad un thread del threadpool

                                switch (cmd) {
                                    
                                    case ("logout"):
                                        service.execute(new logout(registro,nomeutente,key));
                                        break;
                                    case ("gioca"):
                                        service.execute(new playWordleSERVER(nomeutente, c_channel,partite,registro));
                                        break;
                                    case ("tentativo"):
                                        String guessWord = m[2];
                                        service.execute(new tentativoSERVER(nomeutente, registro,c_channel, partite, guessWord, parole,classifica,notify_server));
                                        break;
                                    case ("exit"):
                                        //entro in exit sia che l'utente abbia perso la partita finendo i tentativi 
                                        //sia che l'utente abbandoni la partita quindi la consideriamo come persa
                                        
                                        Utente u=registro.getUtente(nomeutente);
                                        u.reset_streak();//lutente ha perso/abbandonato la partita quindi azzero il suo streak
                                        u.aggiorna_percentuale_vinte();
                                        partite.get(nomeutente).set_flag();//setto il flag a 0 per segnalare che il risultato di quella aprtita è stato usato per aggiornare le statistiche dell'utenet
                                        
                                        break;
                                    case ("statistics"):
                                        service.execute(new statisticsSERVER(nomeutente,c_channel,partite));
                                        break;
                                    case ("classifica"):
                                        service.execute(new ShowMeRankingSERVER(c_channel,  classifica));
                                        break;
                                    case ("share"):
                                        service.execute(new ShareServer( nomeutente, partite,c_channel));
                                        break;
                                    case ("DISCONNETTI"):
                                        service.execute(new disconnetti( nomeutente, c_channel, connessi));
                                        break;
                                    
                                    case ("connesso"):
                                        service.execute(new connesso(nomeutente, c_channel, connessi));
                                        break;

                                    default:
                                        System.out.println("Comando inserito non valido\n");
                                        System.out.println("reiserisci");
                                


                                }

							    
                            }    
                        }
                    }

                }catch(SocketException se){ //terminazione improvvisa del client es con ctrl-c
                    
                    se.printStackTrace();
                    System.out.println("socket exception");
                    key.cancel();
                    try {
                        key.channel().close();
                    } catch (Exception cex) {
                        
                        cex.printStackTrace();
                    }
                    
        
                }
                catch(IOException e){
                    
                    e.printStackTrace();
                    System.out.println("*********IO exception********");
                    key.cancel();
                    try {
                        key.channel().close();
                    } catch (Exception cex) {
                        
                        e.printStackTrace();
                    }
                    
        
                }
                catch(CancelledKeyException cke)
                {
                    cke.printStackTrace();
                    System.out.println("cancelled key EXCEPTIO");
                    key.cancel();
                    try {
                        key.channel().close();
                    } catch (Exception cex) {
                        cke.printStackTrace();
                    }
                }
                

            }

        }   

    }
}



    




  


    
