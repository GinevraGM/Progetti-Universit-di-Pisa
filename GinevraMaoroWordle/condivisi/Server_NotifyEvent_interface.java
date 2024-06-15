import java.rmi.*;

public interface Server_NotifyEvent_interface extends Remote
{
    /* registrazione per la callback */
    public void registerForCallback (NotifyEvent_interface ClientInterface) throws RemoteException;
    /* cancella registrazione per la callback */
    public void unregisterForCallback (NotifyEvent_interface ClientInterface) throws RemoteException; 
}

