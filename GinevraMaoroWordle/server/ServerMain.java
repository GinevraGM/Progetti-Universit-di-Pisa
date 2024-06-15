import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ServerMain {

	// metodo main
	public static void main(String[] args) {
        
		try (FileInputStream fileInput = new FileInputStream("Configurazione_Server.properties")){
            Properties properties = new Properties();
            properties.load(fileInput);
			Server Wordle_server = new Server(Integer.parseInt(properties.getProperty("porta_registro")),
                                            Integer.parseInt(properties.getProperty("porta_notifica")),
                                            Integer.parseInt(properties.getProperty("porta_registrazioni")),
                                             Integer.parseInt(properties.getProperty("frequenza_serializzazione")),
                                            Integer.parseInt(properties.getProperty("frequenza_aggiornamento_parola")),
                                            Integer.parseInt(properties.getProperty("porta_socket_TCP")),
                                            Integer.parseInt(properties.getProperty("porta_multicast")),
                                            properties.getProperty("indirizzo_multicast"));
            //avvio il server
			Wordle_server.avvio();

		} catch (IOException e) {
            e.printStackTrace();
        }
	}
}
