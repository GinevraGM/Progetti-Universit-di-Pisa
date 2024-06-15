import java.rmi.*;
import java.rmi.server.*;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("serial")

public class NotifyEvent_impl extends RemoteObject implements NotifyEvent_interface {

    List<String> top3;

    /* crea un nuovo callback client */
    public NotifyEvent_impl( ) throws RemoteException {
        super( ); 
        top3=new ArrayList<>();
    }

    /* metodo che può essere richiamato dal servente per notificare una nuova quotazione del titolo */
    public void notifyEvent(String primo, String secondo,String terzo) throws RemoteException { 
        top3.clear();
        System.out.println("Ricevuta notifica di aggiornamento della top 3");

        top3.add(0, primo);
        top3.add(1, secondo);
        top3.add(2, terzo);

        System.out.println("1° "+primo);
        System.out.println("2° "+secondo);
        System.out.println("3° "+terzo);
        
    }
}




