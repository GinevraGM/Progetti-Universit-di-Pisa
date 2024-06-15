import java.rmi.RemoteException;
import java.util.ArrayList;


public class Classifica {
    private ArrayList<entry_classifica> classifica;

    public Classifica(){
        this.classifica= new ArrayList<entry_classifica>();
    }

    //le operazioni con la classifica sono synchronized perchè è una risorsa condivisa da piu thread:
    //quello che aggiunge le registrazioni e i thread del server che aggiornano la classifica quando i giocatori vincono.

    //si chiama quando quando avvio il server devo caricare in classifica tutti gli utenti del registro
    public synchronized void add_entry_ordinata(String nomeutente,float punteggio){ 

        entry_classifica ec=new entry_classifica(nomeutente, punteggio);
        int i=0;
        //quando vado a inserire il primo elemento trovo la classifica vuota quindi non devo fare nessun compear to, ma semplicemente aggiungere la entry
        if(classifica.isEmpty())
        {
            classifica.add(ec);
        }
        else{//la classifica non è vuota quindi devo aggiungere in modo ordinato
            while(i<classifica.size() && ec.compareTo(classifica.get(i))<0 )// ec<classifica(i)
            {
                i++;
            }

            classifica.add(i, ec);
        }
    }

    //si chiama quando registro un nuovo utente,quindi lo aggiungo in fondo alla classifica con punteggio 0.
    public synchronized void add_entry(String nomeutente,Server_NotifyEvent_impl notify_server){ 
        
        classifica.add(new entry_classifica(nomeutente)); 
        //aggiunge in fondo con punteggio a 0, non ce bisogno di fare l'ordinamento della classifica

        try {
            //se l'utente registarto è uno dei primi 3 a registrarsi
            //devo notificare l'aggiornamento della classifica nelle prime 3 pos

            //in caso la classifica ha solo 1 posizione non ce bisogno di callback intanto ce solo un utente in classifica
            
            if(classifica.size()==2){
                String primo=classifica.get(0).get_nomeutente()+" "+classifica.get(0).get_punteggio();
                String secondo=classifica.get(1).get_nomeutente()+" "+classifica.get(1).get_punteggio();

                notify_server.update(primo, secondo, "");
            }
            if(classifica.size()==3){
                String primo=classifica.get(0).get_nomeutente()+" "+classifica.get(0).get_punteggio();
                String secondo=classifica.get(1).get_nomeutente()+" "+classifica.get(1).get_punteggio();
                String terzo=classifica.get(2).get_nomeutente()+" "+classifica.get(2).get_punteggio();

                notify_server.update(primo, secondo, terzo);

            }
            
        } catch (RemoteException re) {
            re.printStackTrace();
        }catch (Exception e) {
            e.printStackTrace();
        }        
    }

    // upgrade classifica prende il nome utente e il nuovo punteggio, rimuove la vecchia entry di quell'utente 
    //aggiunge la nuova entry con il nuovo punteggio per quell'utente
    //viene chiamato quando un utente vince una partita

    public synchronized void upgrade_classifica(String nomeutente, float punteggio,Server_NotifyEvent_impl notify_server)
    {
        entry_classifica ec=new entry_classifica(nomeutente, punteggio);
        //rimuovo dalla classifica la vecchia entry che ha nome utente uguale alla nuova entry che devo inserire
        classifica.remove(ec);
        int i=0;
        while(i<classifica.size() && ec.compareTo(classifica.get(i))<0)// ec<classifica(i)
        {
            i++;
        }

        classifica.add(i, ec);

        //se il nuovo punteggio fa si che l'utente venga inserito in una delle prime 3 posizioni 
        //devo notificare coloro iscritti al servizio di notifica di aggiornamenro della classifica
        if(i==0||i==1||i==2) 
        {
            //fare la callback
            try {
                //quando ho solo 1 utente in classifica non faccio call back
                if(classifica.size()==2){
                    String primo=classifica.get(0).get_nomeutente()+" "+classifica.get(0).get_punteggio();
                    String secondo=classifica.get(1).get_nomeutente()+" "+classifica.get(1).get_punteggio();

                    notify_server.update(primo, secondo, "");
                }
                if(classifica.size()>=3){
                    String primo=classifica.get(0).get_nomeutente()+" "+classifica.get(0).get_punteggio();
                    String secondo=classifica.get(1).get_nomeutente()+" "+classifica.get(1).get_punteggio();
                    String terzo=classifica.get(2).get_nomeutente()+" "+classifica.get(2).get_punteggio();

                    notify_server.update(primo, secondo, terzo);

                }
                

            } catch (RemoteException re) {
                re.printStackTrace();
            }catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    
    public synchronized void stampa_classifica(){
        int j;
        for(int i=0;i<classifica.size();i++)
        {
            j=i+1;
            System.out.println(j+"° "+classifica.get(i).get_nomeutente()+" "+classifica.get(i).get_punteggio());
        }

    }

    public synchronized String get_classifica_as_string(){
        int j;
        String c="CLASSIFICA:"+"\n";
        for(int i=0;i<classifica.size();i++)
        {
            j=i+1;
            c=c+j+"° "+classifica.get(i).get_nomeutente()+" "+classifica.get(i).get_punteggio()+"\n";
            
        }
        return c;                
    }
}
