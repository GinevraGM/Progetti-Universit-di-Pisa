
import java.rmi.*; 
import java.rmi.server.*; 
import java.util.*;

@SuppressWarnings("serial")

public class Server_NotifyEvent_impl extends RemoteObject implements Server_NotifyEvent_interface{ 
    
    // lista dei client registrati 
    private List <NotifyEvent_interface> clients; //transient

    // costruttore che crea il nuovo servente che notifica
    public Server_NotifyEvent_impl()throws RemoteException {
        super( );
        clients = new ArrayList<NotifyEvent_interface>( ); 
    }

    // ho tutti metodi synchronized perche List <NotifyEvent_interface> clients è condivisa, + client si possono registrare/unregistrere contemporaneamente
    
    // registrazione per la callback 
    public synchronized void registerForCallback(NotifyEvent_interface Client) throws RemoteException {
        if (!clients.contains(Client)){
            clients.add(Client); 
            System.out.println("New client registered for callback" );
        }
    }

    // annulla registrazione per il callback 
    public synchronized void unregisterForCallback (NotifyEvent_interface Client) throws RemoteException {
        if (clients.remove(Client)) {
            System.out.println("Client unregistered");
        }
        else { 
            System.out.println("Unable to unregister client."); 
        } 
    }


    // notifica di una variazione di valore nella top3
    // quando viene richiamato, fa il callback a tutti i client registrati */
    public void update(String primo, String secondo,String terzo) throws RemoteException {
        //System.out.println("avvio updateee");
        doCallbacks(primo,secondo,terzo);
    }

    //esegue la callback, notifica tutti coloro registrati al servizio di notifica 
    private synchronized void doCallbacks(String primo, String secondo,String terzo) throws RemoteException{
        System.out.println("Starting callbacks."); 
        //Iterator <NotifyEvent_interface> i = clients.iterator(); non posso usare l'iteratore perche modifico la sd mentre la itero
        int size=clients.size();
        int i=0;

        for(i=0;i<size;i++)
        {
            NotifyEvent_interface client=clients.get(i);
            try {
                client.notifyEvent(primo,secondo,terzo); //si impalla qui
            } catch (ConnectException e) {
                System.err.println("Si è tentato di mandare una notifica a un client non piu connesso" + e.getMessage());
                System.err.println("Errore: " + e.getMessage());
                clients.remove(client);
                i=i-1;//perche rimuovendo mi shiftano tt gli indici a sx
                size=clients.size();//ricalcolo la dim
                System.err.println("ho rimosso da clients il client che non è piu raggiungibile");
            }
            
            //System.out.println("fine for"); 
        }
        System.out.println("Callbacks complete.");

    }

        
        //int numeroClienti = clients.size( ); 
       /*  while (i.hasNext()) {
            System.out.println("entro while"); 
            NotifyEvent_interface client =i.next();//(NotifyEvent_interface) 
            System.out.println("dopo i.next()");
            try {
                client.notifyEvent(primo,secondo,terzo); //si impalla qui
            } catch (ConnectException e) {
                System.err.println("Si è tentato di mandare una notifica a un client non piu connesso" + e.getMessage());
                System.err.println("Errore: " + e.getMessage());
                //clients.remove(client); non lo posso fare qui senno mi solleva ConcurrentModificationException
                //System.err.println("ho rimosso da clients il client che non è piu raggiungibile");
                

            }
            
            System.out.println("fine while"); 
        }
        System.out.println("Callbacks complete.");
    } */


  

}





