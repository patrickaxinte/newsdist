import java.io.*;
import java.net.*;
import java.util.*;

public class NewsClient {
    private final String serverAddress;
    private final int serverPort;
    private List<String> subscribedTopics;

    public NewsClient(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.subscribedTopics = new ArrayList<>();
    }

    // Method to connect to server and handle user interactions
    public void start() {
        try (Socket socket = new Socket(serverAddress, serverPort);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connected to the news system.");

            while (true) {
                System.out.println("\nOptions:");
                System.out.println("1. Subscribe to a topic");
                System.out.println("2. Post a news");
                System.out.println("3. Fetch latest news");
                System.out.println("4. Exit");
                System.out.print("Choose an option: ");
                int choice = scanner.nextInt();
                scanner.nextLine(); // Consume newline

                switch (choice) {
                    case 1 -> {
                        System.out.print("Enter topic to subscribe: ");
                        String topic = scanner.nextLine();
                        subscribedTopics.add(topic);
                        out.println("SUBSCRIBE:" + topic);
                        System.out.println("Subscribed to topic: " + topic);
                    }
                    case 2 -> {
                        System.out.print("Enter topic: ");
                        String topic = scanner.nextLine();
                        System.out.print("Enter title: ");
                        String title = scanner.nextLine();
                        System.out.print("Enter content: ");
                        String content = scanner.nextLine();
                        out.println("POST:" + topic + ":" + title + ":" + content);
                        System.out.println("News posted.");
                    }
                    case 3 -> {
                        out.println("FETCH");
                        String news = in.readLine();
                        System.out.println("Latest news:\n" + news);
                    }
                    case 4 -> {
                        System.out.println("Exiting...");
                        return;
                    }
                    default -> System.out.println("Invalid choice. Try again.");
                }
            }
        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        String serverAddress = "127.0.0.1"; // Localhost
        int serverPort = 12345; // Default port

        NewsClient client = new NewsClient(serverAddress, serverPort);
        client.start();
    }
}
