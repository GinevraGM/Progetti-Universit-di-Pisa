
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.rmi.server.*;

@SuppressWarnings("deprecation")

public class Client {

    private int registry_port;
    private int porta_socket_TCP;
    private int porta_callback;
    private String indirizzo_multicast;//=226.226.226.226
    private int porta_multicast;//=4000
    private int socket_tcp_timeout;
    private List<String> notifiche_di_share;
    private MulticastSocket ms;
    

    public Client(int registry_port,int porta_socket_TCP,int porta_callback,String indirizzo_multicast,int porta_multicast,int socket_tcp_timeout) {
        this.registry_port = registry_port;
        this.porta_socket_TCP=porta_socket_TCP;
        this.porta_callback=porta_callback;
        this.indirizzo_multicast=indirizzo_multicast;
        this.porta_multicast=porta_multicast;
        this.socket_tcp_timeout=socket_tcp_timeout;
    }

    public void Scrittura_nel_canale(SocketChannel client,String msg) throws IOException{

        // la prima parte del messaggio contiene la lunghezza del messaggio
        ByteBuffer length = ByteBuffer.allocate(Integer.BYTES); //buffer che contiene la lunghezza del messaggio
        length.putInt(msg.length()); //scrivo la lunghezza del messaggio nel buffer 
        length.flip(); //preparo il buffer per essere letto
        while (length.hasRemaining()) { 
            client.write(length);//scrivo nel canale la lunghezza del messaggio,leggendola dal buffer
        }
        length.clear();//pulisco il buffer che contiene la lunghezza

        // la seconda parte del messaggio contiene il messaggio da inviare
        ByteBuffer readBuffer = ByteBuffer.wrap(msg.getBytes());

        while (readBuffer.hasRemaining()) {
            client.write(readBuffer);//scrivo nel canale il messaggio,leggendolo dal buffer
        }
 
        readBuffer.clear();

    }




    public int playWordleCLIENT (SocketChannel client, String nomeutente, AtomicReference<String> secretword,AtomicReference<String> traduzione) throws IOException
    {

        //messaggio da inviare al server
        String msg = nomeutente + " " + "gioca";
        this.Scrittura_nel_canale(client,msg);
        
        //il server controlla se questo utente puo giocare + alloca partita per quel client +altre op e poi restituisce ok;
        //leggo la risposta del server
        
        ByteBuffer reply = ByteBuffer.allocate(2048);
        
        if(client.read(reply)==-1)//raggiunto l'end of stream, chiuso il sockt remoto(lato server)
        {
            System.out.println("il server ha chiuso la sua connessione,probabilmente ti sei loggato da un altro dispositivo");
            return -1;
        }

        //preparo il buffer alla lettura
        reply.flip();

        String riscontro=new String(reply.array()).trim();

        //splittare il messaggio 
        String[] m = riscontro.split(":"); // Dividi la stringa in base agli :

        String ack = m[0];  // ok/errore
        System.out.printf("%s ", ack); 
        String ack_message = m[1];  //puoi giocare...
        System.out.printf("%s\n", ack_message);
        
        reply.clear();
        if(ack.equals("ok"))
        {
            secretword.set(m[2]); //= new String(m[2]); //m[2]
            traduzione.set(m[3]); //= new String(m[3]);
        
            return 1;
        }
        else{// ho ricevuto errore: hai gia giocato la parola del giorno

            return 0;
        }
        

    

    }


    public int sendCLIENT (SocketChannel client, String nomeutente, String guessWord) throws IOException 
    {   //restituisce 1 quando ho finito i tentativi o ho vinto
        
        // Creo il messaggio da inviare al server
        String msg = nomeutente + " " + "tentativo"+ " " +guessWord;
        this.Scrittura_nel_canale(client,msg);
    
        //leggo la risposta del server
        
        ByteBuffer reply = ByteBuffer.allocate(2048);

        if(client.read(reply)==-1)//raggiunto lend of strem, chiuso il sockt remoto(lato server)
        {
            System.out.println("il server ha chiuso la sua connessione,probabilmente ti sei loggato da un altro dispositivo");
            return -1;
        }

        
        reply.flip();

        String riscontro=new String(reply.array()).trim();

        //splittare il messaggio 
        String[] m = riscontro.split(":"); // Dividi la stringa in base agli :

        String ack = m[0];  // tentativi rimasti, da trasformare in intero
        int tentativi_rimasti=Integer.parseInt(ack);
        
        String ack_message = m[1];  // vinto/suggerimento/errore2
        
        reply.clear();

        
        if(ack_message.equals("vinto"))
        {
            System.out.printf("HAI VINTO!\n");
            return 1;//mi fa uscire dallo switch perchè ho vinto
        }
        if(tentativi_rimasti==0)
        {
            System.out.println("Hai finito i tentativi");//quindi ha perso
            //scrivo questo messaggio al server cosi il server aggiorna le statistiche dell'utente con questa partita persa
            this.Scrittura_nel_canale(client,msg);
            return 1;//mi fa uscire dallo switch perche non ho piu tentativi
        }
        if(ack_message.equals("suggerimento")){
            String suggerimento=m[2]; //suggerimento ricevuto
            System.out.printf("\nNon hai indovinato, questo è il suggerimento\n%s\n", suggerimento);
            System.out.printf("ti sono rimasti %d tentativi\n",tentativi_rimasti);
            return 0;//non esco dallo switch ho ancora tentativi posso ancora provare a indovinare
        }
        if(ack_message.equals("errore2")){
            System.out.printf("\nLa parola che hai inviato non è presente nel vocabolario\n");
            System.out.printf("prova con un'altra parola\n");
            System.out.printf("ti sono rimasti %d tentativi\n",tentativi_rimasti);
            return 0;//non esco dallo switch ho ancora tentativi posso ancora provare a indovinare
        }
        else{
            System.out.printf("Client: Qualcosa è andato storto\n");
            return 0;
        }

    }

    public int statistics(SocketChannel client, String nomeutente) throws IOException{

        // Creo il messaggio da inviare al server
        String msg = nomeutente + " " + "statistics";
        this.Scrittura_nel_canale(client,msg);
        
        ByteBuffer reply = ByteBuffer.allocate(2048);
        
        if(client.read(reply)==-1){
            System.out.println("il server ha chiuso la sua connessione,probabilmente ti sei loggato da un altro dispositivo");
            return -1;
        }


        reply.flip();
        //prendo le statistiche dal buffer e le trasformo in stringa
        String statistiche =new String(reply.array()).trim();

        System.out.printf("Le tue statistiche \n%s\n", statistiche);

        reply.clear();
        return 1;
        

    }


    public int ShowMeRanking(SocketChannel client, String nomeutente) throws IOException{

         // Creo il messaggio da inviare al server
        String msg = nomeutente + " " + "classifica";
        this.Scrittura_nel_canale(client,msg);
        
        
        ByteBuffer reply = ByteBuffer.allocate(2048);

        if(client.read(reply)==-1){
            System.out.println("il server ha chiuso la sua connessione,probabilmente ti sei loggato da un altro dispositivo");
            return -1;
        }
       

        reply.flip();

        String classificaS =new String(reply.array()).trim();

        System.out.printf("%s\n", classificaS);

        reply.clear();
        return 1;

    }

    public void ShareClient(SocketChannel client, String nomeutente) throws IOException{

         // Creo il messaggio da inviare al server
        String msg = nomeutente + " " + "share";
        this.Scrittura_nel_canale(client,msg);
        
        
        ByteBuffer reply = ByteBuffer.allocate(2048);

        if(client.read(reply)==-1){
            System.out.println("il server ha chiuso la sua connessione,probabilmente ti sei loggato da un altro dispositivo");
            //return -1;
        }
        

        //client.read(reply); 
        reply.flip();

        String s =new String(reply.array()).trim();

        System.out.printf("Client: il server ha inviato %s\n",s);

        reply.clear();

    }




    public class Multicast_client implements Runnable {
        
        
        public Multicast_client(){
            notifiche_di_share=Collections.synchronizedList(new ArrayList<String>());
            //sincronizzo ArrayList perchè posso aggiungere e rimuovere contemporanemanete le notifiche da questa sd
            //mi devo unire al gruppo di multicast
            try {
                // Ottengo l'indirizzo del gruppo e ne controllo la validità.
                InetAddress ia_group = InetAddress.getByName(indirizzo_multicast);
                if (!ia_group.isMulticastAddress()) {
                    throw new IllegalArgumentException(
                    "Indirizzo multicast non valido: " + ia_group.getHostAddress());
                }
                //apro il multicast socket sulla porta
                ms = new MulticastSocket(porta_multicast);
                //mi unisco al gruppo multicast.
                ms.joinGroup(ia_group);
            
            }catch (IOException ioe) {
                ioe.printStackTrace();
            }catch (Exception e) {
                e.printStackTrace();
            }
        }

        //run con cui il client legge le notifiche di share che gli arrivano dal gruppo multicast, nel quale invia notifiche di share il server 
        public void run(){
            while(true){
                try {

					byte[] buf = new byte[2048];
					DatagramPacket dp = new DatagramPacket(buf, buf.length);
					// Ricevo il pacchetto.
					ms.receive(dp);

                    String s = new String(dp.getData());
                    
					notifiche_di_share.add(s);

                }catch (SocketException se) { 
					System.out.println("Multicast_client:Trovata socket remota chiusa");
                    return;
				}catch (IOException e) {
					e.printStackTrace();
				}
            }

        }
    
        
    }

    private void showMesharing() {

		if(notifiche_di_share.isEmpty()) { //non ho ricevuto nessuna notifica
			System.out.println("non ci sono nuove notifiche di condivisione da parte degli altri utenti");
			return;
		}
        int dim=notifiche_di_share.size();
        //dopo che ho preso questa dimensione, sicuramente le notifiche non diminuicono, al max aumentano
        for(int i=0;i<dim;i++)
        { 
            //leggo e rimuovo le notifiche dalla testa dell ArrayList
            System.out.println(notifiche_di_share.get(0));
			notifiche_di_share.remove(0);
        }
        
	}


    public void avvio() {

		System.out.println("****************************************************************");
		System.out.printf("WORDLE: un gioco di parole 3.0\n");
        System.out.println("****************************************************************");
		System.out.printf("Vuoi registrarti o effettuare il login?\n"
				+ "!Ricorda!: non puoi effettuare il login se non sei registrato\n");
		System.out.printf("Digita:\n");
		System.out.printf("login -> per effettuare il login\n");
		System.out.printf("reg -> per effettuare la registrazione\n");

		// all' avvio del client un utente non è nè loggato nè registrato
		boolean logged = false;	
        String nomeutente=null;
        String password=null;
        registro_registrazioni_interface stub=null;
        // apro uno scanner sullo standard input
		Scanner sc = new Scanner(System.in);
        Scanner scl = new Scanner(System.in);
		// comando inserito da tastiera, all'inizio sarà reg o login
		String cmd = null;
        SocketChannel client=null;
        Registry r=null;

        try {
            //ottengo un riferimento al registry
            r =LocateRegistry.getRegistry(registry_port);
            //ottengo un riferimento al registro delle registrazioni remoto
            stub =(registro_registrazioni_interface) r.lookup("SERVIZIO-REGISTRAZIONE");
            
        } catch (RemoteException re) {
			re.printStackTrace();
		} catch (NotBoundException e) {
			e.printStackTrace();
		}


	    // finchè non sono loggato non posso giocare o effettuare gli altri comandi
		while (!logged) {
			// leggo il comando inserito da tastiera
			cmd = sc.next();
			// guardo che comando è stato inserito dall' utente
			switch (cmd) {
			
			case "reg":
                System.out.print("Inserisci l'username\n");
                nomeutente = sc.next();
                System.out.print("Inserisci la password\n");
                password = sc.next();
                // la password non può essere la stringa vuota
				while (password.equals("")) {
					System.err.println("non hai inserito la password\n");
					password = sc.next();
                }
                //adesso posso registrarmi, add_registrazione restituisce false quando quell'username è gia presente
                try {
                    while((stub.add_registrazione(nomeutente,password))==false)
                    {
                        System.err.println("questo nomeutente non è disponibile,sceglierne un altro");
                        nomeutente = sc.next();
                    }
                    System.out.println("registrazione andata a buon fine");
                    System.out.println("adesso per effettuare il login digita login");
                
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case "login": 
                System.out.print("Inserisci il tuo username\n");
                nomeutente = sc.next();
                System.out.print("Inserisci la tua password\n");
                password = sc.next();
                try {
                    if(stub.containsUtente(nomeutente)){ //restituise true quando questo nome utente è registrato, quindi contenuto nel registro                      
                        if(password.equals(stub.getpassword(nomeutente))){ //controlla che la password inserita corrispona a quella associata a quel nome utente nel registro
                            //apro la connessione 
                            client = SocketChannel.open(new InetSocketAddress(InetAddress.getLocalHost(), porta_socket_TCP));
                            Socket socket = client.socket();

                            socket.setSoTimeout(socket_tcp_timeout);
                               
                            if(!stub.setlogged(nomeutente)){//controllo se lutente è gia loggato,se non lo è si logga e ritorna true
                                //entro qui se lutente è gia loggato
                                System.out.println("questo utente è già loggato");	
                                System.out.println("se vuoi effettuare la disconnessione dall'altro dispositivo digita OK");
                                System.out.println("DISCONNETTENDOTI perderai la partita in corso sull'altro dispositivo");
                                System.out.println("comandi disponibili:");	
                                System.out.println("OK -> PER DISCONNETTERTI");
                                System.out.println("NO -> PER NON disconnetterti");//in realta può scrivere qualsiasi cosa

                                if((sc.next()).equals("OK"))
                                {
                                    stub.setlogout(nomeutente);//syncronized la setlogout di utente, chiamata dalla setlogout dello stub
                                    //qui nel mentre da un altro dispositivo potrei essermi connesso con quel nome utente 
                                    //quindi ricontrollo in set logged se il flag logged è ancora a false 
                                    if(stub.setlogged(nomeutente))// sono riuscito a loggarmi, restituisce true se ha trovato quellutente non loggato e quindi si logga 
                                    {
                                        //disconnesso e riloggato con sucesso
                                        String msg = nomeutente + " " + "DISCONNETTI"; 
                                        //per disconnettere il vecchio client, il server chiudera la sockert col vecchio client sul quale era loggato l'utente
                                        Scrittura_nel_canale(client,msg);

                                        ByteBuffer reply = ByteBuffer.allocate(2048);
        
                                        client.read(reply); 

                                     

                                        reply.flip();

                                        String s =new String(reply.array()).trim();
                                        System.out.printf("%s\n",s); //ok:disconnessione e riconnessione andata a buon fine

                                        reply.clear();
                                        logged=true; //per uscire dal while che mi obblica a fare il login
                                        break;
                                    }
                                    else
                                    {
                                    
                                        System.out.println("errore nella disconnessione,riprova");//qualcun altro si è riconnesso nel mentre
                                        try {
                                            client.close();//chiudo perche nel caso digiti login nello switch apro un nuovo canale
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }

                                    }
                                    
                                }
                                else 
                                {
                                    try {
                                        client.close();//chiudo perche nel caso digiti login nello switch apro un nuovo canale
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    } 

                                }

                                System.out.printf("Digita:\n");
                                System.out.printf("login -> per effettuare il login\n");
                                System.out.printf("reg -> per effettuare la registrazione\n");
                                	
                            }
                            else
                            {
                                String msg = nomeutente + " " + "connesso"; //per avvertire il client che questo nome utente si è loggato e connesso
                                Scrittura_nel_canale(client,msg);
                                ByteBuffer reply = ByteBuffer.allocate(2048);
        
                                client.read(reply); 
                                
                                reply.flip();

                                String s =new String(reply.array()).trim();
                                System.out.printf("%s\n",s);

                                reply.clear();

                                logged=true;
                            }      
                            
                    
                        } 
                        else{
                            System.out.println("non è stato possibile eseguire il login: PASSWORD ERRATA");
                            System.out.printf("Digita:\n");
                            System.out.printf("login -> per effettuare il login\n");
                            System.out.printf("reg -> per effettuare la registrazione\n");

                        }
                    }
                    else{
                        System.out.println("questo nomeutente non è registrato");
                        System.out.println("prima di fare il login devi registrarti, digita reg per farlo o login per riprovare il login");
                        break;
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }

            break;
			default:
				System.out.println("Comando inserito non valido\n");
				System.out.printf("Per effettuare il login digita: login\n");
				System.out.printf("Per effettuare la registrazione digita: reg\n");

		    }

		}

        //dopo essersi loggato mi devo registrare a un servizio di notifica implementato con RMI e callback
        Server_NotifyEvent_interface server=null;
        NotifyEvent_interface stub_notify=null;
        try {
            //riferimento,stub del servizio remoto del server di notifica di aggiornamento della classifica 
            server = (Server_NotifyEvent_interface) r.lookup("SERVIZIO-NOTIFICA");
            //alloco il mio oggeto remoto che servira poi al server per avvertirmi
            NotifyEvent_interface callbackObj = new NotifyEvent_impl();
            //esporto loggetto remoto lato client per ottenere un riferimento, stub
            stub_notify = (NotifyEvent_interface) UnicastRemoteObject.exportObject(callbackObj,porta_callback);
            //registro lo stub sul server remote object, per ricevere le callback 
            server.registerForCallback(stub_notify);


        } catch (RemoteException re) {
			re.printStackTrace();
        }catch (NotBoundException e) {
			e.printStackTrace();
        }

        //unirsi al gruppo multicast per la condivisione dell'esito della partita 
        Thread gruppo_multicast = new Thread(new Multicast_client());
        //avvio il thread che si unisce al gruppo multicast e legge le notifiche ricevute
		gruppo_multicast.start();

        // Arrivato a questo punto l'utente è loggato e connesso col server
        try
        {
            System.out.printf("\nBenvenuto su Wordle %s!\n", nomeutente);
            System.out.println("puoi digitare uno dei seguenti comandi:");
            System.out.println("gioca-> per iniziare una nuova partita");
            System.out.println("stat-> per visualizzare le tue statistiche di gioco");
            System.out.println("mc-> (mostra condivisioni) per visualizzare le condivisioni dei risultati degli altri giocatori");
            System.out.println("class-> per visualizzare la classifica");
            System.out.println("logout-> per uscire dal gioco");
            
            boolean continua=true;
            while (continua) {
                cmd=sc.next();

                switch (cmd) {
                    case ("logout"):
                        //lo faccio scrivendo nel canale, lato server devo chiudere il canale, cancellare la chiave e rimuovere questo nome utente da connessi 
                        String msg = nomeutente + " "+ "logout"; 
                        try {
                            this.Scrittura_nel_canale(client,msg);
                        } catch (IOException e) {
                            System.out.println("trovata connessione già chiusa; probabilmente ti sei loggato da un altro dispositivo");
                        }
                        ByteBuffer reply = ByteBuffer.allocate(2048);
        
                        client.read(reply); 
                        
                        reply.flip();

                        String s =new String(reply.array()).trim();
                        System.out.printf("%s\n",s);

                        reply.clear();

                        continua=false; 
                        
                        break;
                    case ("gioca"):
                        AtomicReference<String> traduzione = new AtomicReference<>();
                        AtomicReference<String> secretword = new AtomicReference<>();
                        int p;
                        //metodo per giocare
                        if((p=playWordleCLIENT(client, nomeutente,secretword,traduzione))==1){
                            
                            //inizio la partita, posso digitare solo send ^guess word^ o exit
                            System.out.println("\nHai avviato una partita");
                            System.out.println("puoi digitare uno dei seguenti comandi:");
                            System.out.println("send ^guess word^-> per provare a indovinare la parola");
                            System.out.println("exit-> per abbandonare la partita");

                            
                            boolean ok=true;// per continuare a poter digitare send ^guess word^ o exit 
                            boolean avanti=true;// per condividere la partita terminata 

                            while(ok){
                                
                                cmd=scl.nextLine();
                                String[] m = cmd.split(" "); // Dividi la stringa in base agli spazi
                                
                                String comando = m[0];  // send o exit
                            
                                switch (comando) {
                                    case ("send"):
                                        if(m.length<2)// non è stata inserita la ^guess word^
                                        {
                                            System.out.println("insieme al comando send devi inviare la parola");
                                            break;
                                        }
                                        String guessWord = m[1]; //guesseword
                                        if(guessWord.length()!=10)
                                        {
                                            System.out.println("\n^guess word^ troppo breve/lunga, deve essere di 10 caratteri");
                                            System.out.println("puoi digitare uno dei seguenti comandi:");
                                            System.out.println("send ^guess word^-> per provare a indovinare la parola");
                                            System.out.println("exit-> per abbandonare la partita");
                                            break;
                                        }
                                        else {
                                            int sendc=sendCLIENT(client,nomeutente,guessWord);

                                            if(sendc==-1){ //quando trova la socket remota del server chiusa, quindi il client si chiude 
                                                continua=false;
                                                ok=false;
                                                avanti=false;
                                                break;
                                            }

                                            if(sendc==0) //0 sta per non interrompere lo switch
                                            {//entro in questo if se non ho indovinato la parola e ho ancora tentativi disponibili
                                                System.out.println("\nsend ^guess word^-> per provare a indovinare la parola");
                                                System.out.println("exit-> per abbandonare la partita");
                                                break;

                                            }//sendc=1
                                            else{// non ho piu tentativi disponibili o ho indovinato la parola
                                                //quindi in entrambi i casi esco dallo while mettendo ok a false 
                                                System.out.println("\nla parola da indovinare era "+secretword);
                                                System.out.println("la sua traduzione è "+traduzione);
                                                
                                                ok=false; 
                                                break;

                                            }

                                        }
                                    
                                    case ("exit"):
                                        System.out.println("\nla parola da indovinare era "+secretword);
                                        System.out.println("la sua traduzione è "+traduzione);
                                        String msg2 = nomeutente + " " + "exit";
                                        this.Scrittura_nel_canale(client,msg2);
                                        ok=false;
                                        avanti=true;
                                        
                                        break;
                                
                                    default:
                                        System.out.println("\nComando inserito non valido\n");
                                        System.out.println("Stai giocando una partita");
                                        System.out.println("puoi digitare uno dei seguenti comandi:");
                                        System.out.println("send ^guess word^-> per provare a indovinare la parola");
                                        System.out.println("exit-> per abbandonare la partita");

                                        break;
                                }
                                

                            }
                            
                            
                            while(avanti){ // while allinterno del quale posso decidere se condividere la partita
                                System.out.println("Vuoi condividere i risultati della partita appena giocata?\n");
                                System.out.println("Digita SI per condividere, NO altrimenti\n");
                                cmd=scl.nextLine();
                                if(cmd.equals("SI")){
                                    String msg3 = nomeutente + " " + "share";
                                    this.Scrittura_nel_canale(client,msg3);
                                    ByteBuffer reply2 = ByteBuffer.allocate(2048);
         

                                    if(client.read(reply2)==-1)//raggiunto lend of strem, chiuso il sockt remoto(lato server)
                                    {
                                        System.out.println("il server ha chiuso la sua connessione,probabilmente ti sei loggato da un altro dispositivo");
                                        continua=false;
                                        break;
                                    }

                                    

                                    reply2.flip();
                                    String riscontro=new String(reply2.array()).trim();
                                    if(riscontro.equals("ok"))
                                    {
                                        System.out.printf("%s condivisione andata a buon fine\n", riscontro);
                                        break;
                                    }
                                    else{
                                        System.out.printf("condivisione non andata a buon fine\n");
                                        System.out.println("Digita SI per condividere, NO altrimenti\n");
                            
                                    }
                                    
                                    

                                }
                                if (cmd.equals("NO")) {
                                    break;
                                }
                                System.out.println("Comando inserito non valido\n");

                            }
                            
                                
                        }
                        if(p==-1){
                            //è stata trovata la socket remota chiusa
                            continua=false;
                            break;
                        }
                        //caso in cui p=0 quindi ho gia giocato la partita, posso pero eseguire altri comandi
                        if(continua==true){ //potrebbe essere a false se l'ho settata quando vado a fare la share trovo la socket remota chiusa
                            System.out.println("\npuoi digitare uno dei seguenti comandi:");
                            System.out.println("gioca-> per iniziare una nuova partita");
                            System.out.println("stat-> per visualizzare le tue statistiche di gioco");
                            System.out.println("mc-> (mostra condivisioni) per visualizzare le condivisioni dei risultati degli altri giocatori");
                            System.out.println("class-> per visualizzare la classifica");
                            System.out.println("logout-> per uscire dal gioco");
                        }
                        

                        break;
                
                    case ("stat"):
                        if(statistics(client,nomeutente)==-1) //-1 quando trovo la socket remota chiusa 
                        {
                            continua=false;
                            break;
                        }
                        System.out.println("\npuoi digitare uno dei seguenti comandi:");
                        System.out.println("gioca-> per iniziare una nuova partita");
                        System.out.println("stat-> per visualizzare le tue statistiche di gioco");
                        System.out.println("mc-> (mostra condivisioni) per visualizzare le condivisioni dei risultati degli altri giocatori");
                        System.out.println("class-> per visualizzare la classifica");
                        System.out.println("logout-> per uscire dal gioco");

                        break;
                    case ("class"):
                        if(ShowMeRanking(client,nomeutente)==-1) //-1 quando trovo la socket remota chiusa 
                        {
                            continua=false;
                            break;
                        }
                        System.out.println("\npuoi digitare uno dei seguenti comandi:");
                        System.out.println("gioca-> per iniziare una nuova partita");
                        System.out.println("stat-> per visualizzare le tue statistiche di gioco");
                        System.out.println("mc-> (mostra condivisioni) per visualizzare le condivisioni dei risultati degli altri giocatori");
                        System.out.println("class-> per visualizzare la classifica");
                        System.out.println("logout-> per uscire dal gioco");
                        break;

                    case ("mc"):
                        showMesharing();
                        System.out.println("\npuoi digitare uno dei seguenti comandi:");
                        System.out.println("gioca-> per iniziare una nuova partita");
                        System.out.println("stat-> per visualizzare le tue statistiche di gioco");
                        System.out.println("mc-> (mostra condivisioni) per visualizzare le condivisioni dei risultati degli altri giocatori");
                        System.out.println("class-> per visualizzare la classifica");
                        System.out.println("logout-> per uscire dal gioco");

                        break;
                    default:
				    System.out.println("Comando inserito non valido\n");
                    System.out.println("reiserisci");
                }

            }
            
        }
        catch (IOException e) {

            e.printStackTrace();
            
        }
        catch (Exception e) {

            e.printStackTrace();
            
        }
        finally {
            try {
                System.out.println("Client: chiudo il socket channel");
                client.close();
                System.out.println("Client: chiudo lo scanner");
                sc.close();  
                scl.close();     
                System.out.println("Client: mi deregistro dal servizio di notifica");
                server.unregisterForCallback(stub_notify);
                System.out.println("Client: chiudo il multicast socket");
                ms.close();
                System.out.println("Client: chiusura in finally");
                System.exit(0);//interrompo anche il thread per le notifiche di share
                
            } catch (SocketException se) {
                System.out.println("Socket exception");
                se.printStackTrace();
            } catch (IOException e){
                
                e.printStackTrace();
            }

        }
    
    }

}

