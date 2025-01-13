package org.example.Client;

import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// clasa ringmanager gestioneaza topologia inelara, replicarea stirilor
// si monitorizarea starii nodurilor prin mesaje heartbeat
public class RingManager {

    private final Client client;
    private String predecessorId;
    private String successorId;

    // flag-uri separate pentru a evita spam-ul de gestionare a esecurilor
    private boolean handlingSuccessorFailure = false;
    private boolean handlingPredecessorFailure = false;

    // contoare de esecuri pentru succesor si predecesor
    private final Map<String, Integer> heartbeatFailures = new ConcurrentHashMap<>();
    private final Map<String, Integer> predecessorFailures = new ConcurrentHashMap<>();

    // constructor
    public RingManager(Client client) {
        this.client = client;
        this.predecessorId = null;
        this.successorId = null;
    }

    // seteaza id-ul predecesorului
    public synchronized void setPredecessor(String pred) {
        this.predecessorId = pred;
    }

    // seteaza id-ul succesorului
    public synchronized void setSuccessor(String succ) {
        this.successorId = succ;
    }

    // obtine id-ul predecesorului
    public synchronized String getPredecessor() {
        return predecessorId;
    }

    // obtine id-ul succesorului
    public synchronized String getSuccessor() {
        return successorId;
    }

    // replica o stire catre succesorul nodului
    public void replicateNews(News news) {
        if (successorId == null || successorId.equals(client.getId())) {
            // daca nu avem succesor sau succesorul este propriul nod, nu se face replicare
            return;
        }
        String replicateTopic = "replicate/" + successorId;
        String payload = news.toJson();
        int maxRetries = 3;
        int attempt = 0;
        boolean success = false;

        while (attempt < maxRetries && !success) {
            try {
                client.publishOnTopic(replicateTopic, payload);
                client.writeToLogFile("Replicare stire " + news.getId() + " catre succesorul " + successorId);
                success = true;
            } catch (MqttException e) {
                attempt++;
                client.writeToLogFile("Eroare la replicarea stirii " + news.getId() + " catre succesorul " + successorId + " (incercare " + attempt + "): " + e.getMessage());
                try {
                    Thread.sleep(1000); // pauza inainte de reincercarea replicarii
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (!success) {
            client.writeToLogFile("Esec la replicarea stirii " + news.getId() + " catre succesorul " + successorId + " dupa " + maxRetries + " incercari.");
        }
    }

    // gestioneaza cazul in care succesorul nodului cade
    public synchronized void handleSuccessorFailure() {
        if (handlingSuccessorFailure) return;
        handlingSuccessorFailure = true;

        // Dacă inelul conține un singur nod, nu se face nimic
        if (client.getActiveNodes().size() == 1) {
            handlingSuccessorFailure = false;
            return;
        }

        String succ = getSuccessor();
        if (succ == null || succ.equals(client.getId())) {
            handlingSuccessorFailure = false;
            return;
        }

        int failCount = heartbeatFailures.getOrDefault(succ, 0) + 1;
        heartbeatFailures.put(succ, failCount);

        client.writeToLogFile("[RingManager] esec Heartbeat nr. " + failCount + " pentru succesorul " + succ);

        if (failCount == 3) {
            System.out.println("[RingManager] Esec Heartbeat " + failCount + " la " + succ);
        } else if (failCount >= 5) {
            System.out.println("[RingManager] Timeout Heartbeat detectat. Succesorul " + succ + " este considerat cazut.");
            client.writeToLogFile("Timeout Heartbeat. Succesorul " + succ + " este considerat cazut.");
            client.writeToLogFile("[RingManager] Se cauta un nou succesor...");

            // Trimite "leave:succ" ca mesaj reținut pentru a elimina mesajul "join:succ"
            try {
                if (client.getActiveNodes().contains(succ)) {
                    client.publishOnTopic("ring_discovery", "leave:" + succ, true); // Mesaj reținut
                    client.writeToLogFile("Trimitere mesaj leave pentru " + succ);
                }
            } catch (MqttException e) {
                client.writeToLogFile("[RingManager] Eroare la trimiterea leave pt " + succ + ": " + e.getMessage());
            }

            heartbeatFailures.put(succ, 0); // Reset contor

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            removeNode(succ);
            updateRing();
        }
        handlingSuccessorFailure = false;
    }


    // gestioneaza cazul in care predecesorul nodului cade

    public synchronized void handlePredecessorFailure() {
        if (handlingPredecessorFailure) return;
        handlingPredecessorFailure = true;

        // Dacă inelul conține un singur nod, nu se face nimic
        if (client.getActiveNodes().size() == 1) {
            handlingPredecessorFailure = false;
            return;
        }

        String pred = getPredecessor();
        if (pred == null || pred.equals(client.getId())) {
            handlingPredecessorFailure = false;
            return;
        }

        int failCount = predecessorFailures.getOrDefault(pred, 0) + 1;
        predecessorFailures.put(pred, failCount);

        client.writeToLogFile("[RingManager] esec Heartbeat nr. " + failCount + " pentru predecesorul " + pred);

        if (failCount == 3) {
            System.out.println("[RingManager] Esec Heartbeat " + failCount + " la " + pred);
        } else if (failCount >= 5) {
            System.out.println("[RingManager] Timeout detectat pentru predecesor: " + pred);
            client.writeToLogFile("Timeout detectat pentru predecesor: " + pred);
            client.writeToLogFile("[RingManager] Se cauta un nou predecesor...");

            // Trimite "leave:pred" ca mesaj reținut pentru a elimina mesajul "join:pred"
            try {
                if (client.getActiveNodes().contains(pred)) {
                    client.publishOnTopic("ring_discovery", "leave:" + pred, true); // Mesaj reținut
                    client.writeToLogFile("Trimitere mesaj leave pentru " + pred);
                }
            } catch (MqttException e) {
                client.writeToLogFile("[RingManager] Eroare la trimiterea leave pt " + pred + ": " + e.getMessage());
            }

            predecessorFailures.put(pred, 0); // Reset contor

            try {
                Thread.sleep(2000); // Pauză de 2 secunde pentru propagare
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            removeNode(pred);
            updateRing();
        }
        handlingPredecessorFailure = false;
    }

    public void resetFailureCount(String nodeId) {
        heartbeatFailures.put(nodeId, 0);
        predecessorFailures.put(nodeId, 0);
    }


    // elimina un nod din inel si actualizeaza topologia
    public void removeNode(String nodeId) {
        client.removeActiveNode(nodeId);
        heartbeatFailures.remove(nodeId);
        predecessorFailures.remove(nodeId);

        client.writeToLogFile("Nodul " + nodeId + " a fost eliminat din inel.");
        System.out.println("[" + client.getId() + "] Nodul " + nodeId + " a fost eliminat din inel.");
        updateRing();
    }

    // actualizeaza inelul in functie de nodurile active
    public synchronized void updateRing() {
        List<String> sortedNodes = new ArrayList<>(client.getActiveNodes());
        Collections.sort(sortedNodes, Comparator.comparingInt(nodeId -> client.getNodeIndex(nodeId)));

        int index = sortedNodes.indexOf(client.getId());
        if (index == -1) {
            // Nodul nu este în lista activă (ar trebui să fie, dar verificăm)
            return;
        }

        String oldPredecessor = this.predecessorId;
        String oldSuccessor = this.successorId;

        String newPredecessor = sortedNodes.get((index - 1 + sortedNodes.size()) % sortedNodes.size());
        String newSuccessor = sortedNodes.get((index + 1) % sortedNodes.size());

        if (!newPredecessor.equals(this.predecessorId) || !newSuccessor.equals(this.successorId)) {
            setPredecessor(newPredecessor);
            setSuccessor(newSuccessor);

            // Reset la timp și contor pentru NOUL succesor
            if (!newSuccessor.equals(oldSuccessor)) {
                client.writeToLogFile("Succesor schimbat din " + oldSuccessor + " in " + newSuccessor);
                resetFailureCount(newSuccessor);
                client.getLastHeartbeatReceived().set(System.currentTimeMillis()); // Reset time pentru succesor
            }

            // Reset la timp și contor pentru NOUL predecesor
            if (!newPredecessor.equals(oldPredecessor)) {
                client.writeToLogFile("Predecesor schimbat din " + oldPredecessor + " in " + newPredecessor);
                resetFailureCount(newPredecessor);
                client.getLastPredecessorHeartbeatReceived().set(System.currentTimeMillis()); // Reset time pentru predecesor
            }

            client.writeToLogFile("Succesor nou: " + newSuccessor + ", Predecesor nou: " + newPredecessor);
            System.out.println("[" + client.getId() + "] Succesor nou: " + newSuccessor + ", Predecesor nou: " + newPredecessor);
        }
    }

}
