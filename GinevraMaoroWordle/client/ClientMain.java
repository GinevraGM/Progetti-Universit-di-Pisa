import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ClientMain {

    public static void main(String[] args) {

		try (FileInputStream fileInput = new FileInputStream("Configurazione_Client.properties")){
            
			Properties properties = new Properties();
            properties.load(fileInput);
			
			Client Wordle_client = new Client(Integer.parseInt(properties.getProperty("registry_port")),
											Integer.parseInt(properties.getProperty("porta_socket_TCP")),
											Integer.parseInt(properties.getProperty("porta_callback")),
											properties.getProperty("indirizzo_multicast"),
											Integer.parseInt(properties.getProperty("porta_multicast")),
											Integer.parseInt(properties.getProperty("socket_tcp_timeout")));

			Wordle_client.avvio();

		} catch (IOException e) {
            e.printStackTrace();
        }
	}
}