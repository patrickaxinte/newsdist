import java.io.*;
import java.net.Socket;
import java.util.*;

public class ServerCommunicationHandler {
    // Map pentru a stoca abonații pe fiecare topic
    private static final Map<String, List<Socket>> subscribers = new HashMap<>();

    // Map pentru a stoca știrile pe fiecare topic
    private static final Map<String, List<String>> newsByTopic = new HashMap<>();

    /**
     * Gestionează conexiunea cu un client și procesează comenzile primite.
     * @param clientSocket Socket-ul clientului conectat.
     */
    public void handleClient(Socket clientSocket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("Received: " + message);

                // Procesare comandă
                String response = processCommand(message, clientSocket);
                if (response != null) {
                    out.println(response); // Trimite răspunsul către client
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Procesează comenzile primite de la client.
     * @param command Comanda primită de la client.
     * @param clientSocket Socket-ul clientului.
     * @return Răspunsul pentru client (sau null dacă nu e nevoie de răspuns).
     */
    private String processCommand(String command, Socket clientSocket) {
        String[] parts = command.split(":");
        String action = parts[0];

        switch (action) {
            case "SUBSCRIBE":
                return handleSubscribe(parts[1], clientSocket);
            case "PUBLISH":
                return handlePublish(parts[1], parts[2], parts[3]);
            case "GET":
                return handleGet(parts[1]);
            default:
                return "Unknown command!";
        }
    }

    /**
     * Abonează un client la un topic specific.
     * @param topic Topicul la care se abonează clientul.
     * @param clientSocket Socket-ul clientului.
     * @return Confirmarea abonării.
     */
    private String handleSubscribe(String topic, Socket clientSocket) {
        subscribers.putIfAbsent(topic, new ArrayList<>());
        subscribers.get(topic).add(clientSocket);
        return "Subscribed to topic: " + topic;
    }

    /**
     * Publică o știre pe un topic și notifică abonații.
     * @param topic Topicul unde se publică știrea.
     * @param title Titlul știrii.
     * @param content Conținutul știrii.
     * @return Confirmarea publicării.
     */
    private String handlePublish(String topic, String title, String content) {
        newsByTopic.putIfAbsent(topic, new ArrayList<>());
        String news = title + ": " + content;
        newsByTopic.get(topic).add(news);

        // Notifică abonații
        notifySubscribers(topic, news);
        return "News published to topic: " + topic;
    }

    /**
     * Notifică toți abonații unui topic despre o nouă știre.
     * @param topic Topicul unde s-a publicat știrea.
     * @param news Știrea publicată.
     */
    private void notifySubscribers(String topic, String news) {
        List<Socket> clients = subscribers.getOrDefault(topic, new ArrayList<>());
        for (Socket client : clients) {
            try {
                PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                out.println("New news on topic " + topic + ": " + news);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Returnează lista de știri pentru un anumit topic.
     * @param topic Topicul cerut.
     * @return Lista de știri sub formă de string.
     */
    private String handleGet(String topic) {
        List<String> newsList = newsByTopic.getOrDefault(topic, new ArrayList<>());
        return String.join("\n", newsList);
    }
}
