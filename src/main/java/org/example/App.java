package org.example;

import org.example.Client.Client;
import org.example.Client.Topics;

public class App {
    public static void main(String[] args) {
        // verifica daca exista suficiente argumente la rulare
        if (args.length < 1) {
            System.out.println("Usage: java -jar MqttClient.jar <nodeIndex>");
            System.exit(1);
        }

        int nodeIndex;
        try {
            // parseaza indexul nodului din argumente
            nodeIndex = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("NodeIndex trebuie sa fie un numar intreg (1 => N1, 2 => N2, 3 => N3)");
            System.exit(1);
            return;
        }

        // creeaza o instanta a clientului
        Client c = new Client();
        // gestioneaza fișierul pentru a asigura id-ul nodului
        c.manageIdFile(nodeIndex);

        // creeaza un set de topicuri si adauga cateva implicite
        Topics topics = new Topics();
        topics.addNewTopic("technology");
        topics.addNewTopic("crypto");
        topics.addNewTopic("ai");
        topics.addNewTopic("blockchain");
        topics.addNewTopic("health");
        topics.addNewTopic("vremea");
        topics.addNewTopic("sport");
        topics.addNewTopic("filme");
        topics.addNewTopic("stergere");

        // conecteaza clientul la broker
        c.connectToBroker();

        // porneste thread-ul pentru input utilizator
        c.startUserInputThread(c, topics);

        // incarca cheia NewsAPI si porneste thread-ul de preluare a stirilor
        c.loadNewsApiKeyFromProperties();
        c.startNewsApiFetchingThread(topics);
    }
}
