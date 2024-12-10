import java.io.*;
import java.net.Socket;

public class TestClient {
    public static void main(String[] args) {
        try (Socket socket = new Socket("localhost", 12345);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Trimite comanda de abonare
            out.println("SUBSCRIBE:blockchain");
            System.out.println("Server response: " + in.readLine());

            // Trimite comanda de publicare
            out.println("PUBLISH:blockchain:Blockchain News: Adoption is growing!");
            System.out.println("Server response: " + in.readLine());

            // Trimite comanda GET
            out.println("GET:blockchain");
            System.out.println("Server response: " + in.readLine());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
