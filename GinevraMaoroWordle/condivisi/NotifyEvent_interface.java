import java.rmi.*;

public interface NotifyEvent_interface extends Remote {
/* Metodo invocato dal server per notificare un evento ad un client remoto. */
    public void notifyEvent(String primo, String secondo,String terzo) throws RemoteException; //invece di int value passerei le 3 posizioni
}
    