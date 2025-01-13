package org.example.Client;

import java.util.ArrayList;
import java.util.List;

public class Topics {
    private final List<String> topicsList;

    public Topics() {
        this.topicsList = new ArrayList<>();
    }

    public boolean existsTopic(String myTopic) {
        return topicsList.contains(myTopic);
    }

    public void printAllTopics() {
        int i = 1;
        System.out.println("Topic-urile disponibile sunt urmatoarele:");
        for (String topic : topicsList) {
            System.out.println(i + ". " + topic);
            i++;
        }
        System.out.println("\n");
    }

    public List<String> getAllTopics() {
        return topicsList;
    }

    public void addNewTopic(String newTopic) {
        topicsList.add(newTopic);
    }
}

