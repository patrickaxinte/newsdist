package org.example.Client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.UUID;

public class News implements Comparable<News> {
    private String id;
    private String title;
    private String content;
    private String topic;

    // Constructor
    public News(String authorID, String title, String content, String topic) {
        this.id = authorID + ":" + UUID.randomUUID().toString();
        this.title = title;
        this.content = content;
        this.topic = topic;
    }

    // constructor gol necesar pentru deserializare din json
    public News() {}

    public String getId() { return id; }

    public String getTitle() { return title; }
    public void setTitle(String t) { this.title = t; }

    public String getContent() { return content; }
    public void setContent(String c) { this.content = c; }

    public String getTopic() { return topic; }
    public void setTopic(String t) { this.topic = t; }

    // transforma obiectul news in format json
    public String toJson() {
        Gson gson = new GsonBuilder().create();
        return gson.toJson(this);
    }

    // creeaza un obiect news dintr-un string json
    public static News fromJson(String json) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(json, News.class);
    }

    // compara doua obiecte news in functie de id-ul string
    @Override
    public int compareTo(News other) {
        return this.id.compareTo(other.id);
    }
}
