package org.example.Client;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

// Clasa NewsList gestioneaza lista locala de stiri
// Ofera deduplicare si persista stirile intr-un fisier
public class NewsList {
    private final List<News> newsList;
    private final ConcurrentHashMap<String, Boolean> existingIds;  // pentru deduplicare
    public static final String PERSIST_FILE = "./src/main/resources/persisted_news.txt";

    // Constructor
    public NewsList() {
        this.newsList = new ArrayList<>();
        this.existingIds = new ConcurrentHashMap<>();
    }

    // verifica daca un ID exista deja in lista
    public boolean existsId(String id) {
        return existingIds.containsKey(id);
    }

    // adauga o stire in lista si in setul de ID-uri
    public void addNews(News myNews) {
        newsList.add(myNews);
        existingIds.put(myNews.getId(), true);
    }

    // sterge o stire din lista pe baza ID-ului si returneaza succesul operatiei
    public int deleteNewsById(String id) {
        for (int i = 0; i < newsList.size(); i++) {
            if (newsList.get(i).getId().equals(id)) {
                existingIds.remove(id);
                newsList.remove(i);
                return 1;
            }
        }
        return 0;
    }

    // returneaza o stire de la un index specific, filtrata dupa topicuri
    public News getNewsWithIndex(int index, List<String> filterTopics) {
        List<News> filtered = new ArrayList<>();
        synchronized (newsList) {
            for (News n : newsList) {
                if (filterTopics.contains(n.getTopic())) {
                    filtered.add(n);
                }
            }
        }
        if (index - 1 >= 0 && index - 1 < filtered.size()) {
            return filtered.get(index - 1);
        }
        return null;
    }

    // afiseaza toate stirile filtrate dupa topicuri
    public void printAllNews(List<String> filterTopics) {
        List<News> filtered = new ArrayList<>();
        synchronized (newsList) {
            for (News n : newsList) {
                if (filterTopics.contains(n.getTopic())) {
                    filtered.add(n);
                }
            }
        }
        int i = 1;
        for (News n : filtered) {
            System.out.println(i + ". [" + n.getId() + "]. Titlu: " + n.getTitle() + ", Topic: " + n.getTopic());
            i++;
        }
    }

    // afiseaza detaliile unei stiri de la un index specific, filtrata dupa topicuri
    public void printNewsWithIndex(int index, List<String> filterTopics) {
        News n = getNewsWithIndex(index, filterTopics);
        if (n != null) {
            System.out.println("############################################");
            System.out.println("Index: " + index);
            System.out.println("ID: [" + n.getId() + "]");
            System.out.println("Titlu: " + n.getTitle());
            System.out.println("Topic: " + n.getTopic());
            System.out.println("Continut: " + n.getContent());
            System.out.println("############################################");
        } else {
            System.out.println("Nu exista stire la indexul dat (filtrata dupa topicurile abonate).");
        }
    }

    // persista o stire in fisierul de persistenta
    public void persistNews(News news) {
        synchronized (this) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(PERSIST_FILE, true))) {
                writer.write(news.toJson());
                writer.newLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // incarca stirile existente din fisier la pornirea aplicației
    public void loadPersistedNews() {
        File f = new File(PERSIST_FILE);
        if (!f.exists()) {
            // daca fisierul nu exista, nu incarca nimic
            return;
        }
        synchronized (this) {
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) {
                    News n = News.fromJson(line);
                    // adauga stirea doar daca nu exista deja
                    if (!existsId(n.getId())) {
                        addNews(n);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
