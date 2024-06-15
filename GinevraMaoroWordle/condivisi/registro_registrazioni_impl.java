import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.rmi.RemoteException;


public class registro_registrazioni_impl implements registro_registrazioni_interface{
    
    private ConcurrentHashMap <String, Utente> Registro;
    private Classifica classifica;
    Server_NotifyEvent_impl notify_server;

    //costruttore
    public registro_registrazioni_impl(Classifica classifica,Server_NotifyEvent_impl notify_server) throws RemoteException{
        // eseguo tutti i passaggi per inserire nel registro gli utenti dal file json (deserializzazione)
        this.classifica=classifica;
        this.notify_server=notify_server;

        
        try {
            // Creazione di un oggetto Gson
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            // Percorso del file JSON da deserializzare
            String filePath = "Registro_registrazioni.json";

            // Lettura del file JSON
            Reader reader = new FileReader(filePath);

            // Tipo di dati per la deserializzazione (ConcurrentHashMap<String, Utente>)
            Type type = new TypeToken<ConcurrentHashMap<String, Utente>>() {}.getType();

            // Deserializzazione del file JSON in un oggetto ConcurrentHashMap<String, Utente>
            Registro = gson.fromJson(reader, type);

            // Chiusura del reader
            reader.close();

            //devo settare a false il flag logged di tutti gli utenti quando avvio il servere e scarico il registro
            for (String chiave : Registro.keySet()) {
                Utente utente = Registro.get(chiave);
                utente.setlogout();
                // Operazioni con gli oggetti Utente deserializzati
            }

            // Utilizzo dell'oggetto deserializzato
            System.out.println("stampo gli utenti registrati");
            this.StampaRegistro();
            
        } catch (FileNotFoundException e) {
            // Eccezione sollevata se il file non è stato trovato
                e.printStackTrace();
        } catch (IOException e) {
            // Eccezione sollevata durante l'operazione di input/output
                 e.printStackTrace();
        } catch (JsonIOException e) {
            // Eccezione sollevata in caso di errori di input/output durante la lettura del JSON
                 e.printStackTrace();
        } catch (Exception e) {
            // Gestione generale di altre eccezioni non previste
                e.printStackTrace();
        }

    }

    public boolean add_registrazione(String nomeutente,String password) { 
        //essendo un meodo che puo essere chiamato concorrentemente non ho messo syncronized ma uso una op atomica per aggiungere el alla concurrenthasmap
        
        if (password.equals("")){
            System.err.println("non hai inserito la password");
            return false;
        }
        // devo controllare che non ci sia gia un utente con quell username
        //put if absent è una op atomica, ritorna null se ha trovato assente quellusername
        // e quindi lo ha inserito lei
        if(Registro.putIfAbsent(nomeutente,new Utente(nomeutente, password))!=null){
			return false;
        } 
        //aggiungo l'utente anche in classifica con punteggio 0, add_entry è syncronized
            classifica.add_entry(nomeutente,notify_server);
            
        return true;

    }

    public synchronized void StampaRegistro(){

        // Utilizzo dell'oggetto deserializzato
            for (String chiave : Registro.keySet()) {
                Utente utente = Registro.get(chiave);
                System.out.println("\n");
                utente.stampa_dati_utente();
                // Operazioni con gli oggetti Utente deserializzati
            }

    }

    public Utente getUtente(String nomeutente)
    {
        return Registro.get(nomeutente); //!restituisce null se non è nel database.
    }

    public ConcurrentHashMap <String, Utente> getRegistro()
    {
        return this.Registro;
    }


    public boolean containsUtente(String nomeutente)
    {
        
        if(Registro.containsKey(nomeutente)){
            System.out.println("nome utente "+nomeutente+" trovato nel registro");
            return true;
        }
        System.out.println("nome utente "+nomeutente+" non trovato nel registro");
        return false;
    }

    public String getpassword (String nomeutente){

        return this.getUtente(nomeutente).getpassword();
    }

    public boolean setlogged(String nomeutente){ 
        Utente u=Registro.get(nomeutente);

        synchronized(u)// cosi non sincronizzo tt il registro ma solo laccesso all'utente
        {
            
            if(this.getUtente(nomeutente).getlogged()==false)
            {
                this.getUtente(nomeutente).setlogged(); 
                return true;
            }
            else{
                System.out.println(nomeutente+" questo utente è gia loggato");
                return false; //questo nome utente è gia loggato

            }

        }

    }

    public void setlogout(String nomeutente){ //non importa sincronizzarlo perche il metodo setlogout in utente lo è

        this.getUtente(nomeutente).setlogout();
    
    }

}
