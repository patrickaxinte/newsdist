# NewsDist

NewsDist is a Java-based distributed news system built on top of MQTT. Each running instance acts as a node in a ring, can publish and consume news by topic, keeps a local persistent cache, and replicates data to a successor node for resilience.

The application combines:
- MQTT messaging (publish/subscribe) for node communication and news distribution
- ring-based node discovery and topology management
- heartbeat-based failure detection and ring repair
- local news persistence and deduplication
- optional NewsAPI ingestion for periodic external news import

## Scope

This project is focused on a **distributed console client** that demonstrates core distributed-systems behaviors in a lightweight setup:

- multi-node participation in a logical ring (`N1`, `N2`, `N3`, ...)
- dynamic node join/leave handling
- successor/predecessor maintenance
- heartbeat monitoring and failure handling
- news publication by topic
- topic subscription/unsubscription from a CLI menu
- local news delete flow (author-owned news)
- replication of news to successor nodes
- persistence to local file and reload on reconnect/start

It is suitable for local experiments and demos of distributed communication patterns over MQTT.

## Tech Stack

- Java (Maven project)
- Eclipse Paho MQTT Client (`org.eclipse.paho.client.mqttv3`)
- Gson (`com.google.code.gson:gson`)

## Project Structure

- `src/main/java/org/example/App.java` - entry point
- `src/main/java/org/example/Client/Client.java` - core MQTT client logic, CLI, ring behavior
- `src/main/java/org/example/Client/RingManager.java` - ring topology + failure handling
- `src/main/java/org/example/Client/NewsList.java` - local storage, deduplication, persistence
- `src/main/resources/application.properties` - NewsAPI configuration (`NEWS_API_KEY`)
- `id.txt` - node IDs mapped by startup index

## Prerequisites

1. Java (JDK 8+ recommended)
2. Maven
3. At least one running MQTT broker on `tcp://localhost:1883`
   - optional fallback broker on `tcp://localhost:1884`
4. (Optional) NewsAPI key if you want periodic external news fetching

## Configuration

### 1) Node IDs

The application reads node IDs from `id.txt` based on startup index:

- index `1` -> first line (for example `N1`)
- index `2` -> second line (for example `N2`)
- etc.

Make sure `id.txt` contains enough node IDs for the number of clients you start.

### 2) NewsAPI key (optional)

Add your key to:

`/home/runner/work/newsdist/newsdist/src/main/resources/application.properties`

Example:

```properties
NEWS_API_KEY=your_api_key_here
```

If the key is missing, the app still runs; only NewsAPI ingestion is skipped.

## Build

From the repository root:

```bash
cd /home/runner/work/newsdist/newsdist
mvn clean package
```

This creates a runnable fat JAR in `target/` via the Maven Assembly plugin.

## Run

Run one node:

```bash
java -jar target/MqttClient-1.0-SNAPSHOT-jar-with-dependencies.jar <nodeIndex>
```

Example:

```bash
java -jar target/MqttClient-1.0-SNAPSHOT-jar-with-dependencies.jar 1
```

To simulate a distributed setup, open multiple terminals and start multiple nodes with different indexes:

```bash
java -jar target/MqttClient-1.0-SNAPSHOT-jar-with-dependencies.jar 1
java -jar target/MqttClient-1.0-SNAPSHOT-jar-with-dependencies.jar 2
java -jar target/MqttClient-1.0-SNAPSHOT-jar-with-dependencies.jar 3
```

## Using the CLI

After startup, each node exposes a console menu with options such as:

- list available topics
- subscribe/unsubscribe to topics
- list news for subscribed topics
- view details of a selected news item
- add a news item
- delete a local-owned news item
- exit

Default topics include: `technology`, `crypto`, `ai`, `blockchain`, `health`, `vremea`, `sport`, `filme`, and `stergere`.

## Runtime Behavior Notes

- Nodes announce themselves on `ring_discovery`.
- Heartbeats are exchanged on `ring_heartbeat`.
- News is replicated to the successor on `replicate/<nodeId>`.
- News is persisted to `src/main/resources/persisted_news.txt`.
- Logs are appended to `src/main/java/org/example/logs.txt`.

## Troubleshooting

- **Cannot connect to broker**: verify MQTT broker is running on `localhost:1883` (and optionally `1884`).
- **Invalid node index**: ensure you pass a numeric index and `id.txt` has that line.
- **No external news fetched**: verify `NEWS_API_KEY` is set and valid.
- **No news displayed**: ensure the node is subscribed to the relevant topic.
