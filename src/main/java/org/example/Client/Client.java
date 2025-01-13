package org.example.Client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.eclipse.paho.client.mqttv3.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

// clasa client gestioneaza conexiunea mqtt, publicarea si abonarea la stiri,
// precum si integrarea cu RingManager pentru topologia inelara (ring topology)
public class Client implements MqttCallback {

    private final RingManager ringManager;
    private String broker;
    private List<Broker> brokerList;
    // id-ul clientului (N1, N2, N3, etc.)
    private String id;
    private final int qos;
    private MqttClient mqttClient;
    private boolean connected;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    // lista locala de stiri (cu deduplicare)
    private NewsList newsList;
    private String newsApiKey;
    private final List<String> subscribedTopics = new ArrayList<>();

    private int localPublishCounter = 0;

    // flag care indica daca incarcam stiri din fisier
    private boolean isLoadingPersistedNews = false;

    // pentru heartbeat
    private final AtomicLong lastHeartbeatReceived = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastPredecessorHeartbeatReceived = new AtomicLong(System.currentTimeMillis()); // Adăugat
    private static final long HEARTBEAT_TIMEOUT_MS = 20000; // 20 sec

    // lista actualizata de noduri active
    private final Set<String> activeNodes = ConcurrentHashMap.newKeySet();

    // threadpool pentru messageArrived
    private final ExecutorService messageExecutor = Executors.newFixedThreadPool(10);

    // constructor client
    public Client() {
        this.brokerList = new ArrayList<>();
        Broker broker1 = new Broker("tcp://localhost:1883", false);
        Broker broker2 = new Broker("tcp://localhost:1884", false);
        brokerList.add(broker1);
        brokerList.add(broker2);

        this.qos = 2;
        this.connected = false;
        this.newsList = new NewsList();
        this.ringManager = new RingManager(this);
    }

    // metoda getter pentru activenodes
    public Set<String> getActiveNodes() {
        return Collections.unmodifiableSet(activeNodes);
    }

    // metoda getter pentru id
    public String getId() {
        return id;
    }

    // metoda getter pentru nodeindex din id
    public int getNodeIndex(String nodeId) {
        return extractNodeIndex(nodeId);
    }

    // extrage indexul nodului din id (ex: N1 -> 1)
    private int extractNodeIndex(String nodeId) {
        try {
            return Integer.parseInt(nodeId.substring(1));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE; // noduri cu id-uri nevalide vor fi puse la sfarsit
        }
    }

    // seteaza id-ul clientului
    public void setId(String newId) {
        this.id = newId;
    }

    // conectare la broker si initializarea descoperirii nodurilor
    public void connectToBroker() {
        Future<?> connectFuture = executorService.submit(() -> {
            while (true) {
                for (Broker myBroker : brokerList) {
                    try {
                        mqttClient = new MqttClient(myBroker.getIpBroker(), id, null);
                        mqttClient.setCallback(this);
                        MqttConnectOptions connOpts = new MqttConnectOptions();
                        connOpts.setCleanSession(true);

                        System.out.println("[" + id + "] Conectare la broker : " + myBroker.getIpBroker());
                        mqttClient.connect(connOpts);
                        System.out.println("[" + id + "] Conectat cu succes. ID-ul clientului: " + id);

                        myBroker.setRunning(true);
                        this.broker = myBroker.getIpBroker();
                        this.connected = true;
                        writeToLogFile("Conectare cu SUCCES la broker-ul " + myBroker.getIpBroker() + " #############################");

                        // abonari implicite
                        subscribe("stergere");
                        subscribe("ring_heartbeat");
                        subscribe("ring_discovery");

                        // anunta prezenta sa in inel
                        announcePresence();

                        // porneste thread-ul de heartbeat
                        startHeartbeatThread();

                        // incarca stirile persistate la prima conexiune
                        isLoadingPersistedNews = true;
                        newsList.loadPersistedNews();
                        isLoadingPersistedNews = false;

                        return; // ne oprim dupa primul broker valid
                    } catch (MqttException e) {
                        this.connected = false;
                        myBroker.setRunning(false);
                        writeToLogFile("Conectare cu ESUAT la broker-ul " + myBroker.getIpBroker());
                        System.out.println("[" + id + "] Conectarea la broker-ul " + myBroker.getIpBroker() + " a esuat, se incearca alt broker");
                    }
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        });

        try {
            connectFuture.get(); // asteapta finalizarea
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    public void disconnectFromBroker() throws MqttException {
        mqttClient.disconnect();
        System.out.println("[" + id + "] Deconectat de la broker");
    }

    //abonare
    public void subscribe(String topic) throws MqttException {
        mqttClient.subscribe(topic);
        subscribedTopics.add(topic);
        writeToLogFile("Abonare la topicul [" + topic + "]");
    }

    //dezabonare
    public void unsubscribe(String topic) throws MqttException {
        mqttClient.unsubscribe(topic);
        subscribedTopics.remove(topic);
        writeToLogFile("Dezabonare de la topicul [" + topic + "]");
    }

    // reinnoieste abonamentele
    private void renewSubscriptions() throws MqttException {
        for (String topic : subscribedTopics) {
            mqttClient.subscribe(topic);
        }
    }


    // Suprascrierea metodei publishOnTopic pentru compatibilitate cu codul existent
    public void publishOnTopic(String anyTopic, String payload) throws MqttException {
        publishOnTopic(anyTopic, payload, false); // Setează retained la false implicit
    }

    // metoda simpla pentru a publica pe un topic (folosita in ringmanager)
    public void publishOnTopic(String anyTopic, String payload, boolean retained) throws MqttException {
        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(this.qos);
        message.setRetained(retained);
        mqttClient.publish(anyTopic, message);
    }

    @Override
    public void connectionLost(Throwable cause) {
        this.connected = false;
        System.out.println("[" + id + "] Conexiune pierduta cu broker-ul");
        writeToLogFile("Conexiune pierduta cu broker-ul [" + this.broker + "]");
        startReconnectThread();
    }

    // porneste firul de reconectare
    private void startReconnectThread() {
        Thread connectThread = new Thread(() -> {
            while (!connected) {
                for (Broker myBroker : brokerList) {
                    try {
                        mqttClient = new MqttClient(myBroker.getIpBroker(), id, null);
                        mqttClient.setCallback(this);
                        MqttConnectOptions connOpts = new MqttConnectOptions();
                        connOpts.setCleanSession(true);

                        writeToLogFile("Se incearca reconectarea la broker-ul: " + myBroker.getIpBroker());
                        mqttClient.connect(connOpts);

                        System.out.println("[" + id + "] Reconectare cu SUCCES la broker-ul: " + myBroker.getIpBroker());
                        writeToLogFile("Conectare cu SUCCES la broker-ul: " + myBroker.getIpBroker());

                        myBroker.setRunning(true);
                        this.broker = myBroker.getIpBroker();
                        this.connected = true;
                        renewSubscriptions();

                        // anunta din nou prezenta sa in inel
                        announcePresence();

                        // porneste din nou thread-ul de heartbeat
                        startHeartbeatThread();

                        return;
                    } catch (MqttException e) {
                        myBroker.setRunning(false);
                        connected = false;
                        System.out.println("[" + id + "] Eroare reconectare la: " + myBroker.getIpBroker());
                        writeToLogFile("Eroare reconectare la broker-ul " + myBroker.getIpBroker() + ": " + e.getMessage());
                    }
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        });
        connectThread.start();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        messageExecutor.submit(() -> {
            String payload = new String(message.getPayload());
            if (topic.equals("ring_heartbeat")) {
                handleHeartbeat(payload);
            } else if (topic.equals("ring_discovery")) {
                handleRingDiscovery(payload);
            } else if (topic.startsWith("replicate/")) {
                // stiri replicate de la alt nod
                News replicatedNews = deserializeNews(payload);
                writeToLogFile("[" + id + "] REPLICATED NEWS on topic " + topic + ": " + replicatedNews.getId());
                processNewsInternal(replicatedNews, true);
            } else {
                // stiri obisnuite
                News news = deserializeNews(payload);
                processNewsInternal(news, false);
            }
        });
    }
    private void handleHeartbeat(String payload) {
        if (payload.startsWith("heartbeat_request:")) {
            String fromNode = payload.split(":")[1];
            String responseMsg = "heartbeat_response:" + this.id;
            try {
                publishOnTopic("ring_heartbeat", responseMsg);
                writeToLogFile("Heartbeat response trimis catre " + fromNode);
            } catch (MqttException e) {
                e.printStackTrace();
                writeToLogFile("Eroare la trimiterea heartbeat_response catre " + fromNode);
            }
        } else if (payload.startsWith("heartbeat_response:")) {
            String fromNode = payload.split(":")[1];


            if (fromNode.equals(this.id)) {
                writeToLogFile("[Heartbeat] Am primit propriul heartbeat response: " + fromNode + ". Ignorat.");
                return;
            }

            if (ringManager.getSuccessor() != null && ringManager.getSuccessor().equals(fromNode)) {
                ringManager.resetFailureCount(fromNode);
                lastHeartbeatReceived.set(System.currentTimeMillis());
                writeToLogFile("Heartbeat response primit de la succesor: " + fromNode);
            }

            if (ringManager.getPredecessor() != null && ringManager.getPredecessor().equals(fromNode)) {
                ringManager.resetFailureCount(fromNode);
                lastPredecessorHeartbeatReceived.set(System.currentTimeMillis());
                writeToLogFile("Heartbeat response primit de la predecesor: " + fromNode);
            }

            if (getActiveNodes().size() == 2
                    && ringManager.getSuccessor() != null
                    && ringManager.getPredecessor() != null
                    && ringManager.getSuccessor().equals(ringManager.getPredecessor())
                    && ringManager.getSuccessor().equals(fromNode)) {

                // În ring-ul cu 2 noduri, succesorul și predecesorul sunt același nod
                ringManager.resetFailureCount(fromNode);
                lastHeartbeatReceived.set(System.currentTimeMillis());
                lastPredecessorHeartbeatReceived.set(System.currentTimeMillis());

                writeToLogFile("[Heartbeat 2-nodes patch] Reset complet al contorului pentru " + fromNode);
            }
        }
    }


    // gestioneaza mesajele de descoperire a nodurilor
    private void handleRingDiscovery(String payload) {
        // format payload: "join:Nx" sau "leave:Nx"
        if (payload.startsWith("join:")) {
            String newNode = payload.split(":")[1];
            if (!newNode.equals(this.id)) {
                // Validare a ID-ului nodului
                if (!newNode.matches("N\\d+")) {
                    writeToLogFile("ID-ul nodului " + newNode + " este invalid.");
                    System.out.println("[" + id + "] ID-ul nodului " + newNode + " este invalid.");
                    return;
                }
                activeNodes.add(newNode);
                // reset heartbeatFailures in ringManager
                ringManager.resetFailureCount(newNode);
                writeToLogFile("Nodul " + newNode + " s-a alaturat inelului.");
                System.out.println("[" + id + "] Nodul " + newNode + " s-a alaturat inelului.");
                updateRing();
            }
        } else if (payload.startsWith("leave:")) {
            String leavingNode = payload.split(":")[1];
            activeNodes.remove(leavingNode);
            writeToLogFile("Nodul " + leavingNode + " a parasit inelul.");
            System.out.println("[" + id + "] Nodul " + leavingNode + " a parasit inelul.");
            updateRing();
        }
    }

    // anunta prezenta sa in inel
    private void announcePresence() {
        String msg = "join:" + this.id;
        try {
            publishOnTopic("ring_discovery", msg, true); // Mesaj reținut
            writeToLogFile("Anuntare prezenta in inel: " + this.id);
            // Adaugă singurul nod activ (dacă este primul nod)
            activeNodes.add(this.id);
            updateRing();
        } catch (MqttException e) {
            e.printStackTrace();
            writeToLogFile("Eroare la anuntarea prezentei: " + e.getMessage());
        }
    }

    private synchronized void updateRing() {
        ringManager.updateRing();
    }

    // prelucreaza stirile, cu deduplicare si persistare
    public synchronized void processNewsInternal(News news, boolean replicated) {
        // deduplicare
        if (newsList.existsId(news.getId())) {
            writeToLogFile("[" + id + "] Stirea " + news.getId() + " e deja in local. Se ignora.");
            return;
        }

        // adaugam in memorie
        newsList.addNews(news);

        // persistam doar daca nu incarcam din fisier
        if (!isLoadingPersistedNews) {
            newsList.persistNews(news);
        }

        // verificam topic stergere
        if (news.getTopic().equals("stergere")) {
            stergeStire(news);
        } else {
            System.out.println("\n[" + id + "] Received News:");
            System.out.println("ID: " + news.getId());
            System.out.println("Title: " + news.getTitle());
            System.out.println("Content: " + news.getContent());
            System.out.println("Topic: " + news.getTopic());
            System.out.println();

            writeToLogFile("S-a primit o stire cu topicul [" + news.getTopic() + "]");

            // replicam si publicam doar daca nu e replicat si nu incarcam din fisier
            if (!isLoadingPersistedNews && !replicated) {
                publishNews(news);
            }
        }
    }

    // sterge local o stire
    public void stergeStire(News news) {
        String idForDelete = news.getContent();
        int rezultat = newsList.deleteNewsById(idForDelete);
        if (rezultat == 1) {
            System.out.println("[" + id + "] Stirea cu id-ul " + idForDelete + " a fost stearsa local");
            writeToLogFile("[" + id + "] Stirea cu id-ul " + idForDelete + " a fost stearsa local");
        } else {
            System.out.println("[" + id + "] Stirea cu id-ul " + idForDelete + " nu exista local");
            writeToLogFile("[" + id + "] Stirea cu id-ul " + idForDelete + " nu exista local");
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // nu folosim in mod curent
    }

    private News deserializeNews(String json) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(json, News.class);
    }

    // scrie mesaje in fisierul de log
    public void writeToLogFile(String mesaj) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("./src/main/java/org/example/logs.txt", true))) {
            String timestamp = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date());
            String mesajFinal = "[" + id + "][" + timestamp + "] " + mesaj;
            writer.write(mesajFinal);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // adauga stirie in baza unui meniu
    public void addNewsMenu(Topics topicsList) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Introdu titlul stirii: ");
        String myTitle = scanner.nextLine();

        System.out.print("Introdu continutul stirii: ");
        String myContent = scanner.nextLine();

        String myTopic;
        do {
            System.out.print("Introdu topicul stirii: ");
            myTopic = scanner.nextLine();
        } while (!topicsList.existsTopic(myTopic));

        News myNews = new News(this.id, myTitle, myContent, myTopic);
        // deduplicare
        if (!newsList.existsId(myNews.getId())) {
            publishNews(myNews);
        } else {
            System.out.println("[" + id + "] Deja exista ID-ul: " + myNews.getId() + " local. Se ignora.");
        }
    }

    // trimite cerere de stergere pentru o stirie
    public void sendNewsToDeleteNews(News newsForDelete) {
        String idStireDeSters = newsForDelete.getId();
        String[] idParts = idStireDeSters.split(":");
        if (!idParts[0].equals(this.id)) {
            System.out.println("[" + id + "] Aceasta stire nu poate fi stearsa, nu apartine local!");
        } else {
            News newsForAll = new News(this.id, "Stergere stire", idStireDeSters, "stergere");
            publishNews(newsForAll);
        }
    }

    // publica o stirie pe un topic si replica la succesor
    public Future<Void> publishNews(News news) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // deduplicare locala inainte de publicare
        if (newsList.existsId(news.getId())) {
            writeToLogFile("[" + id + "] Deja exista stirea " + news.getId() + " local. Nu o republicam.");
            future.complete(null);
            return future;
        }

        String topic = news.getTopic();
        String payload = news.toJson();
        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(this.qos);

        try {
            mqttClient.publish(topic, message);
            System.out.println("[" + id + "] Stirea publicata cu succes " + localPublishCounter);
            localPublishCounter++;

            writeToLogFile("Publicare stire cu topicul [" + topic + "]");

            // adaugam local (+ persistam)
            newsList.addNews(news);
            if (!isLoadingPersistedNews) {
                newsList.persistNews(news);
            }

            // replicare la succesor
            ringManager.replicateNews(news);

            future.complete(null);
        } catch (MqttException e) {
            System.out.println("[" + id + "] Stirea nu a putut fi publicata");
            writeToLogFile("Stirea cu topicul [" + topic + "] nu a putut fi publicata. " + e.getMessage());
            future.completeExceptionally(e);
        }
        return future;
    }

    // porneste thread-ul de input utilizator
    public void startUserInputThread(Client c, Topics topics) {
        executorService.submit(() -> {
            Scanner scanner = new Scanner(System.in);
            while (true) {

                System.out.println("╔════════════════════════════════╗");
                System.out.println("║      ***** NewsDist *****      ║");
                System.out.println("╠════════════════════════════════╣");
                System.out.println("║ 1. Afiseaza toate topic-urile  ║");
                System.out.println("║ 2. Abonare la un topic         ║");
                System.out.println("║ 3. Dezabonare de la un topic   ║");
                System.out.println("║ 4. Vizualizare lista stiri     ║");
                System.out.println("║ 5. Vizualizare detalii stire   ║");
                System.out.println("║ 6. Adauga stire                ║");
                System.out.println("║ 7. Topicuri-uri abonate        ║");
                System.out.println("║ 8. Stergere stire              ║");
                System.out.println("║ 99. Exit                       ║");
                System.out.println("╚════════════════════════════════╝");
                System.out.print("Optiunea mea este: ");
                int choice;
                try {
                    choice = Integer.parseInt(scanner.nextLine());
                } catch (NumberFormatException e) {
                    System.out.println("Optiune invalida!");
                    continue;
                }

                switch (choice) {
                    case 1:
                        topics.printAllTopics();
                        break;
                    case 2:
                        System.out.print("Introdu numele topicului la care doresti sa te abonezi: ");
                        String newTopic = scanner.nextLine();
                        if (topics.existsTopic(newTopic)) {
                            try {
                                c.subscribe(newTopic);
                                System.out.println("[" + id + "] Abonare cu succes la topicul: " + newTopic);
                            } catch (MqttException e) {
                                e.printStackTrace();
                            }
                        } else {
                            System.out.println("Nu exista acest topic");
                        }
                        break;
                    case 3:
                        System.out.print("Introdu numele topicului la care doresti sa te dezabonezi: ");
                        String unsubT = scanner.nextLine();
                        if (topics.existsTopic(unsubT)) {
                            try {
                                c.unsubscribe(unsubT);
                                System.out.println("[" + id + "] Dezabonare cu succes");
                            } catch (MqttException e) {
                                e.printStackTrace();
                            }
                        } else {
                            System.out.println("Nu exista acest topic");
                        }
                        break;
                    case 4:
                        System.out.println("Lista de stiri (pe topic-urile la care esti abonat):");
                        newsList.printAllNews(c.getSubscribedTopics());
                        break;
                    case 5:
                        System.out.print("Introdu indexul stirii: ");
                        String idxStr = scanner.nextLine();
                        try {
                            int idx = Integer.parseInt(idxStr);
                            newsList.printNewsWithIndex(idx, c.getSubscribedTopics());
                        } catch (NumberFormatException e) {
                            System.out.println("Index invalid!");
                        }
                        break;
                    case 6:
                        addNewsMenu(topics);
                        break;
                    case 7:
                        System.out.println("Te-ai abonat la urmatoarele topic-uri:");
                        c.getSubscribedTopics().forEach(System.out::println);
                        break;
                    case 8:
                        System.out.print("Introdu indexul stirii de sters: ");
                        String idx2Str = scanner.nextLine();
                        try {
                            int idx2 = Integer.parseInt(idx2Str);
                            News newsForDel = newsList.getNewsWithIndex(idx2, c.getSubscribedTopics());
                            if (newsForDel != null) {
                                sendNewsToDeleteNews(newsForDel);
                            } else {
                                System.out.println("Nu exista stire la indexul dat (filtrata dupa topicurile abonate).");
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("Index invalid!");
                        }
                        break;
                    case 99:
                        try {
                            c.disconnectFromBroker();
                        } catch (MqttException e) {
                            e.printStackTrace();
                        }
                        System.out.println("[" + id + "] Programul s-a inchis cu succes!");
                        System.exit(0);
                        break;
                    default:
                        System.out.println("Alegere invalida!");
                }
            }
        });
    }

    // incarca cheia NewsAPI din fisier
    public void loadNewsApiKeyFromProperties() {
        Properties properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/application.properties")) {
            if (input == null) {
                System.out.println("[" + id + "] Nu s-a gasit application.properties!");
                return;
            }
            properties.load(input);
            this.newsApiKey = properties.getProperty("NEWS_API_KEY");
            if (this.newsApiKey == null || this.newsApiKey.isEmpty()) {
                System.out.println("NEWS_API_KEY lipseste sau e gol!");
            } else {
                System.out.println("NEWS_API_KEY a fost incarcat cu succes.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // thread ce preia stiri de la newsapi din ora in ora
    public void startNewsApiFetchingThread(Topics topics) {
        final long SLEEP_INTERVAL_MS = 3600000L; // 1 ora
        Thread newsApiThread = new Thread(() -> {
            while (true) {
                try {
                    List<String> allTopics = topics.getAllTopics();
                    for (String topic : allTopics) {
                        if (topic.equals("stergere")) {
                            continue;
                        }
                        List<News> fetchedNews = fetchNewsFromApi(topic);
                        for (News n : fetchedNews) {
                            n.setTopic(topic);
                            publishNews(n);
                        }
                    }
                    Thread.sleep(SLEEP_INTERVAL_MS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        newsApiThread.setDaemon(true);
        newsApiThread.start();
    }

    // obtine stiri de la newsapi
    private List<News> fetchNewsFromApi(String queryTopic) {
        List<News> resultList = new ArrayList<>();
        if (this.newsApiKey == null || this.newsApiKey.isEmpty()) {
            System.out.println("[" + id + "] NEWS_API_KEY nu este configurat. nu se pot prelua stiri.");
            return resultList;
        }
        String endpoint = "https://newsapi.org/v2/everything?q=" + queryTopic
                + "&apiKey=" + this.newsApiKey
                + "&pageSize=5"
                + "&sortBy=publishedAt";

        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder responseBody = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBody.append(line);
                }
                Gson gson = new GsonBuilder().create();
                NewsApiResponse apiResponse = gson.fromJson(responseBody.toString(), NewsApiResponse.class);
                if (apiResponse != null && apiResponse.articles != null) {
                    for (NewsApiArticle article : apiResponse.articles) {
                        String title = (article.title != null) ? article.title : "no title";
                        String content = (article.description != null && !article.description.isEmpty())
                                ? article.description
                                : (article.content != null ? article.content : "no content");
                        News n = new News(this.id, title, content, queryTopic);
                        resultList.add(n);
                    }
                }
            } else {
                System.out.println("[" + id + "] NewsAPI raspuns HTTP status: " + status);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
        return resultList;
    }

    // gestioneaza fisierul de id (n1, n2, n3)
    public void manageIdFile(int nodeIndex) {
        String caleFisier = "id.txt";
        File fisier = new File(caleFisier);
        try {
            if (!fisier.exists()) {
                if (fisier.createNewFile()) {
                    System.out.println("Fisierul id.txt a fost creat cu succes.");
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(caleFisier))) {
                        writer.write("N1\nN2\nN3\n");
                    }
                } else {
                    System.out.println("Nu s-a putut crea fisierul id.txt.");
                    System.exit(1);
                }
            }

            try (BufferedReader bufferedReader = new BufferedReader(new FileReader(fisier))) {
                String linie;
                int currentLine = 0;
                while ((linie = bufferedReader.readLine()) != null) {
                    currentLine++;
                    if (currentLine == nodeIndex) {
                        this.setId(linie.trim());
                        System.out.println("ID-ul acestui client este: " + this.id);
                        break;
                    }
                }
            }
            if (this.id == null) {
                System.out.println("ID-ul pentru nodul " + nodeIndex + " nu a fost gasit in id.txt.");
                System.exit(1);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // thread heartbeat - trimite heartbeat si verifica timeout


public void startHeartbeatThread() {
    Thread heartbeatThread = new Thread(() -> {
        while (true) {
            try {
                // Dacă e singurul nod din inel, nu face heartbeat
                if (getActiveNodes().size() == 1) {
                    Thread.sleep(15000); // Așteaptă 15 secunde înainte de a verifica din nou
                    continue;
                }

                // Trimite heartbeat request către succesor
                if (ringManager.getSuccessor() != null
                        && !ringManager.getSuccessor().equals(getId())) {
                    String msg = "heartbeat_request:" + this.id;
                    publishOnTopic("ring_heartbeat", msg);
                    writeToLogFile("Heartbeat request trimis catre succesor: " + ringManager.getSuccessor());
                }

                long now = System.currentTimeMillis();

                // Verifică succesor
                long succDiff = now - lastHeartbeatReceived.get();
                if (succDiff > HEARTBEAT_TIMEOUT_MS
                        && ringManager.getSuccessor() != null
                        && !ringManager.getSuccessor().equals(getId())) {
                    writeToLogFile("[RingManager] Timeout Heartbeat detectat. Succesorul "
                            + ringManager.getSuccessor() + " este considerat cazut.");
                    // System.out.println("[RingManager] Timeout Heartbeat detectat. Succesorul "
                    //         + ringManager.getSuccessor() + " este considerat cazut.");
                    ringManager.handleSuccessorFailure();
                }

                // Verifică predecesor
                long predDiff = now - lastPredecessorHeartbeatReceived.get();
                if (predDiff > HEARTBEAT_TIMEOUT_MS
                        && ringManager.getPredecessor() != null
                        && !ringManager.getPredecessor().equals(getId())) {
                    writeToLogFile("[RingManager] Timeout Heartbeat detectat. Predecesorul "
                            + ringManager.getPredecessor() + " este considerat cazut.");
                    // System.out.println("[RingManager] Timeout Heartbeat detectat. Predecesorul "
                    //         + ringManager.getPredecessor() + " este considerat cazut.");
                    ringManager.handlePredecessorFailure();
                }

                Thread.sleep(10000); // Așteaptă 10 secunde înainte de a trimite următorul heartbeat
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    });
    heartbeatThread.setDaemon(true);
    heartbeatThread.start();
}


    // obtine lista de topicuri la care este abonat
    public List<String> getSubscribedTopics() {
        return Collections.unmodifiableList(subscribedTopics);
    }

    // Metoda pentru a elimina un nod din activeNodes
    public void removeActiveNode(String nodeId) {
        activeNodes.remove(nodeId);
    }

    public synchronized AtomicLong getLastHeartbeatReceived() {
        return lastHeartbeatReceived;
    }

    public synchronized AtomicLong getLastPredecessorHeartbeatReceived() {
        return lastPredecessorHeartbeatReceived;
    }

}
