

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.concurrent.ConcurrentHashMap;

public interface registro_registrazioni_interface extends Remote {

    public boolean add_registrazione(String nomeutente,String password) throws RemoteException;

    public void StampaRegistro() throws RemoteException;

    public ConcurrentHashMap <String, Utente> getRegistro() throws RemoteException;

    public boolean containsUtente(String nomeutente) throws RemoteException;

    public Utente getUtente(String nomeutente) throws RemoteException;

    public String getpassword (String nomeutente) throws RemoteException;

    public boolean setlogged(String nomeutente) throws RemoteException;

    public void setlogout(String nomeutente) throws RemoteException;
}
