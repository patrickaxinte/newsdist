package server.heartbeat;

import server.models.NodeMessage;
import server.utils.JSONParser;
import server.utils.Logger;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class HeartBeatManager implements Runnable {
    private final Logger logger = Logger.getInstance();

    // Map to track last heartbeat time from each node (NodeID -> LastHeartbeatTimestamp)
    private final Map<String, Long> nodeHeartbeats = new ConcurrentHashMap<>();

    // ConcurrentMap of neighbor nodes (NodeID -> Port)
    private final Map<String, Integer> neighborNodes;

    // Map to track heartbeat failures for retries (NodeID -> FailureCount)
    private final Map<String, Integer> heartbeatFailures = new ConcurrentHashMap<>();

    // Set to track failed nodes
    private final Set<String> failedNodes = ConcurrentHashMap.newKeySet();

    // Heartbeat interval in milliseconds
    private final long heartbeatInterval = 5000;

    // Heartbeat timeout threshold in milliseconds
    private final long heartbeatTimeout = 10000;

    // Grace period at startup to prevent premature failure detection
    private final long startupGracePeriod = 15000; //15 seconds

    // Maximum retries before marking a node as failed
    private final int maxRetries = 3;

    // This node's identifier
    private final String nodeId;

    // Default hostname for all neighbor nodes
    private final String hostname;

    // Running flag for graceful shutdown
    private volatile boolean running = true;

    // Atomic counters for logging and metrics
    private final AtomicInteger heartbeatsSent = new AtomicInteger(0);
    private final AtomicInteger heartbeatsReceived = new AtomicInteger(0);
    private final AtomicInteger nodesFailed = new AtomicInteger(0);

    // Start time for managing the grace period
    private final long startTime = System.currentTimeMillis();

    public HeartBeatManager(Map<String, Integer> neighborNodes, String nodeId, String hostname) {
        this.neighborNodes = new ConcurrentHashMap<>(neighborNodes);
        this.nodeId = nodeId;
        this.hostname = hostname;
    }

    @Override
    public void run() {
        logger.log("HeartBeatManager started.");
        while (running && !Thread.currentThread().isInterrupted()) {
            sendHeartbeats();
            checkNodeStatuses();

            try {
                Thread.sleep(heartbeatInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("HeartBeatManager interrupted: " + e.getMessage());
            }
        }
        logger.log("HeartBeatManager stopped.");
    }

    public void stop() {
        running = false;
    }

    private void sendHeartbeats() {
        for (Map.Entry<String, Integer> entry : getNeighborNodesSnapshot()) {
            String neighborNodeId = entry.getKey();
            int port = entry.getValue();

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(hostname, port), 2000); // 2-second timeout
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
                    NodeMessage heartbeatMessage = new NodeMessage("HEARTBEAT", null, nodeId);
                    String message = JSONParser.toJson(heartbeatMessage);
                    writer.write(message);
                    writer.newLine();
                    writer.flush();

                    heartbeatsSent.incrementAndGet();
                    logger.log("Sent heartbeat to node " + neighborNodeId + " on port " + port);

                    // Reset heartbeat failure count on success
                    heartbeatFailures.remove(neighborNodeId);
                }
            } catch (IOException e) {
                // Increment failure count
                int failures = heartbeatFailures.getOrDefault(neighborNodeId, 0) + 1;
                heartbeatFailures.put(neighborNodeId, failures);

                logger.error("Failed to send heartbeat to node " + neighborNodeId + " (Attempt " + failures + "/" + maxRetries + "): " + e.getMessage());

                // If max retries reached, mark node as failed
                if (failures >= maxRetries && !failedNodes.contains(neighborNodeId)) {
                    logger.error("Node " + neighborNodeId + " is unresponsive after " + maxRetries + " attempts.");
                    handleNodeFailure(neighborNodeId);
                }
            }
        }
    }

    private void checkNodeStatuses() {
        long currentTime = System.currentTimeMillis();

        if (currentTime - startTime < startupGracePeriod) {
            return; // Skip checks during the startup grace period
        }

        for (String neighborNodeId : getNeighborNodeIdsSnapshot()) {
            Long lastHeartbeat = nodeHeartbeats.get(neighborNodeId);
            if (lastHeartbeat == null || (currentTime - lastHeartbeat) > heartbeatTimeout) {
                if (!failedNodes.contains(neighborNodeId)) {
                    failedNodes.add(neighborNodeId);
                    logger.error("Node " + neighborNodeId + " is unresponsive. Last heartbeat at " + lastHeartbeat);
                    handleNodeFailure(neighborNodeId);
                }
            } else {
                // Node is responsive, remove from failedNodes if previously marked
                if (failedNodes.remove(neighborNodeId)) {
                    logger.log("Node " + neighborNodeId + " is responsive again.");
                }
            }
        }
    }

    public void updateHeartbeat(String neighborNodeId) {
        nodeHeartbeats.put(neighborNodeId, System.currentTimeMillis());
        heartbeatsReceived.incrementAndGet();
        logger.log("Received heartbeat from node " + neighborNodeId + ". Total received: " + heartbeatsReceived.get());

        // If the node was previously failed, mark it as active
        if (failedNodes.remove(neighborNodeId)) {
            logger.log("Node " + neighborNodeId + " is now responsive again.");
        }

        // Reset failure count
        heartbeatFailures.remove(neighborNodeId);
    }

    private void handleNodeFailure(String neighborNodeId) {
        failedNodes.add(neighborNodeId);
        nodesFailed.incrementAndGet();
        logger.log("Handling failure of node " + neighborNodeId);
        // Additional failure handling logic can be added here
    }

    // Utility methods to fetch snapshots of neighbor nodes and IDs
    private Set<Map.Entry<String, Integer>> getNeighborNodesSnapshot() {
        return new HashSet<>(neighborNodes.entrySet());
    }

    private Set<String> getNeighborNodeIdsSnapshot() {
        return new HashSet<>(neighborNodes.keySet());
    }

    // Public methods for managing neighbors
    public void addNeighbor(String neighborNodeId, int port) {
        neighborNodes.put(neighborNodeId, port);
        logger.log("Added neighbor node: " + neighborNodeId + " on port " + port);
    }

    public void removeNeighbor(String neighborNodeId) {
        neighborNodes.remove(neighborNodeId);
        nodeHeartbeats.remove(neighborNodeId);
        logger.log("Removed neighbor node: " + neighborNodeId);
    }

    // Getters for metrics
    public int getHeartbeatsSent() {
        return heartbeatsSent.get();
    }

    public int getHeartbeatsReceived() {
        return heartbeatsReceived.get();
    }

    public int getNodesFailed() {
        return nodesFailed.get();
    }
}

//package server.heartbeat;
//
//import server.models.NodeMessage;
//import server.utils.JSONParser;
//import server.utils.Logger;
//
//import java.io.BufferedWriter;
//import java.io.OutputStreamWriter;
//import java.io.IOException;
//import java.net.Socket;
//import java.net.InetSocketAddress;
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.atomic.AtomicInteger;
//
//public class HeartBeatManager implements Runnable {
//    private final Logger logger = Logger.getInstance();
//
//    // Maps and metrics
//    private final Map<String, Long> nodeHeartbeats = new ConcurrentHashMap<>();
//    private final Map<String, Integer> heartbeatFailures = new ConcurrentHashMap<>();
//    private final AtomicInteger heartbeatsSent = new AtomicInteger(0);
//    private final AtomicInteger heartbeatsReceived = new AtomicInteger(0);
//    private final AtomicInteger nodesFailed = new AtomicInteger(0);
//
//    // Heartbeat settings
//    private final long heartbeatInterval = 5000;
//    private final long heartbeatTimeout = 10000;
//    private final long startupGracePeriod = 10000; // 10 seconds
//    private final int maxRetries = 3;
//
//    // Node-specific attributes
//    private final String nodeId;
//    private final String hostname;
//    private volatile String successorId;
//    private volatile int successorPort;
//    private volatile String predecessorId;
//    private volatile int predecessorPort;
//
//    // Start time to manage grace period
//    private final long startTime = System.currentTimeMillis();
//
//    private volatile boolean running = true;
//
//    public HeartBeatManager(String nodeId, String hostname, String successorId, int successorPort) {
//        this.nodeId = nodeId;
//        this.hostname = hostname;
//        this.successorId = successorId;
//        this.successorPort = successorPort;
//    }
//
//    // Predecessor and Successor Management
//    public void setPredecessor(String predecessorId, int predecessorPort) {
//        this.predecessorId = predecessorId;
//        this.predecessorPort = predecessorPort;
//        logger.log("Predecessor set to " + predecessorId + " on port " + predecessorPort);
//    }
//
//    public void setSuccessor(String successorId, int successorPort) {
//        this.successorId = successorId;
//        this.successorPort = successorPort;
//        logger.log("Successor set to " + successorId + " on port " + successorPort);
//    }
//
//    public String getSuccessor() {
//        return successorId;
//    }
//
//    public int getSuccessorPort() {
//        return successorPort;
//    }
//
//    @Override
//    public void run() {
//        logger.log("HeartBeatManager started.");
//        while (running) {
//            sendHeartbeatToSuccessor();
//            checkNodeStatuses();
//
//            try {
//                Thread.sleep(heartbeatInterval);
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                logger.error("HeartBeatManager interrupted.");
//            }
//        }
//        logger.log("HeartBeatManager stopped.");
//    }
//
//    private void sendHeartbeatToSuccessor() {
//        if (successorId == null) return;
//
//        try (Socket socket = new Socket()) {
//            socket.connect(new InetSocketAddress(hostname, successorPort), 2000);
//            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
//                NodeMessage heartbeat = new NodeMessage("HEARTBEAT", null, nodeId);
//                writer.write(JSONParser.toJson(heartbeat));
//                writer.newLine();
//                writer.flush();
//                heartbeatsSent.incrementAndGet();
//                logger.log("Heartbeat sent to successor: " + successorId);
//            }
//        } catch (IOException e) {
//            logger.error("Failed to send heartbeat to successor: " + successorId);
//            handleSuccessorFailure();
//        }
//    }
//
//    private void handleSuccessorFailure() {
//        if (predecessorId != null) {
//            try (Socket socket = new Socket()) {
//                socket.connect(new InetSocketAddress(hostname, predecessorPort), 2000);
//                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
//                    NodeMessage update = new NodeMessage("NODE_FAILURE", successorId, nodeId);
//                    writer.write(JSONParser.toJson(update));
//                    writer.newLine();
//                    writer.flush();
//                    logger.log("Informed predecessor of successor failure.");
//                }
//            } catch (IOException e) {
//                logger.error("Failed to notify predecessor about successor failure.");
//            }
//        }
//        successorId = null; // Successor needs to be updated dynamically
//    }
//
//    private void checkNodeStatuses() {
//        long currentTime = System.currentTimeMillis();
//
//        // Skip checks during the startup grace period
//        if (currentTime - startTime < startupGracePeriod) {
//            return;
//        }
//
//        for (Map.Entry<String, Long> entry : nodeHeartbeats.entrySet()) {
//            String neighborNodeId = entry.getKey();
//            long lastHeartbeat = entry.getValue();
//            if ((currentTime - lastHeartbeat) > heartbeatTimeout) {
//                logger.error("Node " + neighborNodeId + " is unresponsive.");
//                handleNodeFailure(neighborNodeId);
//            }
//        }
//    }
//
//    public void updateHeartbeat(String neighborNodeId) {
//        nodeHeartbeats.put(neighborNodeId, System.currentTimeMillis());
//        heartbeatsReceived.incrementAndGet();
//        logger.log("Received heartbeat from node " + neighborNodeId + ". Total received: " + heartbeatsReceived.get());
//    }
//
//    private void handleNodeFailure(String neighborNodeId) {
//        nodesFailed.incrementAndGet();
//        logger.log("Handling failure of node " + neighborNodeId);
//    }
//
//    public void stop() {
//        running = false;
//    }
//
//    // Getters for metrics
//    public int getHeartbeatsSent() {
//        return heartbeatsSent.get();
//    }
//
//    public int getHeartbeatsReceived() {
//        return heartbeatsReceived.get();
//    }
//
//    public int getNodesFailed() {
//        return nodesFailed.get();
//    }
//}

//package server.heartbeat;
//
//import server.models.NodeMessage;
//import server.utils.JSONParser;
//import server.utils.Logger;
//
//import java.io.BufferedWriter;
//import java.io.OutputStreamWriter;
//import java.io.IOException;
//import java.net.Socket;
//import java.net.InetSocketAddress;
//import java.util.Map;
//import java.util.Set;
//import java.util.HashSet;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.atomic.AtomicInteger;
//
//public class HeartBeatManager implements Runnable {
//    private final Logger logger = Logger.getInstance();
//
//    // Maps and metrics
//    private final Map<String, Long> nodeHeartbeats = new ConcurrentHashMap<>();
//    private final Map<String, Integer> heartbeatFailures = new ConcurrentHashMap<>();
//    private final Map<String, Integer> neighborNodes; // Added neighborNodes
//    private final AtomicInteger heartbeatsSent = new AtomicInteger(0);
//    private final AtomicInteger heartbeatsReceived = new AtomicInteger(0);
//    private final AtomicInteger nodesFailed = new AtomicInteger(0);
//
//    // Heartbeat settings
//    private final long heartbeatInterval = 5000;
//    private final long heartbeatTimeout = 10000;
//    private final long startupGracePeriod = 10000; // 10 seconds
//    private final int maxRetries = 3;
//
//    // Node-specific attributes
//    private final String nodeId;
//    private final String hostname;
//    private volatile String successorId;
//    private volatile int successorPort;
//    private volatile String predecessorId;
//    private volatile int predecessorPort;
//
//    // Start time to manage grace period
//    private final long startTime = System.currentTimeMillis();
//
//    private volatile boolean running = true;
//
//    public HeartBeatManager(Map<String, Integer> neighborNodes, String nodeId, String hostname) {
//        this.neighborNodes = new ConcurrentHashMap<>(neighborNodes);
//        this.nodeId = nodeId;
//        this.hostname = hostname;
//        logger.log("HeartBeatManager initialized for node: " + nodeId);
//    }
//
//    // Predecessor and Successor Management
//    public void setPredecessor(String predecessorId, int predecessorPort) {
//        this.predecessorId = predecessorId;
//        this.predecessorPort = predecessorPort;
//        logger.log("Predecessor set to " + predecessorId + " on port " + predecessorPort);
//    }
//
//    public void setSuccessor(String successorId, int successorPort) {
//        this.successorId = successorId;
//        this.successorPort = successorPort;
//        logger.log("Successor set to " + successorId + " on port " + successorPort);
//    }
//
//    public String getSuccessor() {
//        return successorId;
//    }
//
//    public int getSuccessorPort() {
//        return successorPort;
//    }
//
//    @Override
//    public void run() {
//        logger.log("HeartBeatManager started for node: " + nodeId);
//        while (running) {
//            sendHeartbeatToSuccessor();
//            checkNodeStatuses();
//
//            try {
//                Thread.sleep(heartbeatInterval);
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                logger.error("HeartBeatManager interrupted.");
//            }
//        }
//        logger.log("HeartBeatManager stopped.");
//    }
//
//    private void sendHeartbeatToSuccessor() {
//        if (successorId == null) {
//            logger.log("No successor defined for node " + nodeId + ". Skipping heartbeat.");
//            return;
//        }
//
//        logger.log("Attempting to send heartbeat to successor: " + successorId + " on port " + successorPort);
//        try (Socket socket = new Socket()) {
//            socket.connect(new InetSocketAddress(hostname, successorPort), 2000);
//            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
//                NodeMessage heartbeat = new NodeMessage("HEARTBEAT", null, nodeId);
//                writer.write(JSONParser.toJson(heartbeat));
//                writer.newLine();
//                writer.flush();
//
//                heartbeatsSent.incrementAndGet();
//                logger.log("Heartbeat successfully sent to successor: " + successorId);
//            }
//        } catch (IOException e) {
//            logger.error("Failed to send heartbeat to successor: " + successorId + ". Error: " + e.getMessage());
//            handleSuccessorFailure();
//        }
//    }
//
//    private void handleSuccessorFailure() {
//        if (predecessorId != null) {
//            logger.log("Notifying predecessor of successor failure...");
//            try (Socket socket = new Socket()) {
//                socket.connect(new InetSocketAddress(hostname, predecessorPort), 2000);
//                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
//                    NodeMessage update = new NodeMessage("NODE_FAILURE", successorId, nodeId);
//                    writer.write(JSONParser.toJson(update));
//                    writer.newLine();
//                    writer.flush();
//                    logger.log("Predecessor notified about successor failure.");
//                }
//            } catch (IOException e) {
//                logger.error("Failed to notify predecessor about successor failure. Error: " + e.getMessage());
//            }
//        }
//        logger.error("Successor " + successorId + " marked as failed.");
//        successorId = null;
//    }
//
//    private void checkNodeStatuses() {
//        long currentTime = System.currentTimeMillis();
//
//        // Skip checks during the startup grace period
//        if (currentTime - startTime < startupGracePeriod) {
//            logger.log("Skipping node status checks during grace period.");
//            return;
//        }
//
//        for (Map.Entry<String, Long> entry : nodeHeartbeats.entrySet()) {
//            String neighborNodeId = entry.getKey();
//            long lastHeartbeat = entry.getValue();
//            if ((currentTime - lastHeartbeat) > heartbeatTimeout) {
//                logger.error("Node " + neighborNodeId + " is unresponsive.");
//                handleNodeFailure(neighborNodeId);
//            } else {
//                logger.log("Node " + neighborNodeId + " is active. Last heartbeat received at " + lastHeartbeat);
//            }
//        }
//    }
//
//    public void updateHeartbeat(String neighborNodeId) {
//        nodeHeartbeats.put(neighborNodeId, System.currentTimeMillis());
//        heartbeatsReceived.incrementAndGet();
//        logger.log("Received heartbeat from node " + neighborNodeId + ". Total received: " + heartbeatsReceived.get());
//    }
//
//    private void handleNodeFailure(String neighborNodeId) {
//        nodesFailed.incrementAndGet();
//        logger.log("Handling failure of node " + neighborNodeId);
//    }
//
//    public void stop() {
//        running = false;
//    }
//
//    // Utility methods to fetch snapshots of neighbor nodes and IDs
//    private Set<Map.Entry<String, Integer>> getNeighborNodesSnapshot() {
//        return new HashSet<>(neighborNodes.entrySet());
//    }
//
//    private Set<String> getNeighborNodeIdsSnapshot() {
//        return new HashSet<>(neighborNodes.keySet());
//    }
//
//    // Public methods for managing neighbors
//    public void addNeighbor(String neighborNodeId, int port) {
//        neighborNodes.put(neighborNodeId, port);
//        logger.log("Added neighbor node: " + neighborNodeId + " on port " + port);
//    }
//
//    public void removeNeighbor(String neighborNodeId) {
//        neighborNodes.remove(neighborNodeId);
//        nodeHeartbeats.remove(neighborNodeId);
//        logger.log("Removed neighbor node: " + neighborNodeId);
//    }
//
//    // Getters for metrics
//    public int getHeartbeatsSent() {
//        return heartbeatsSent.get();
//    }
//
//    public int getHeartbeatsReceived() {
//        return heartbeatsReceived.get();
//    }
//
//    public int getNodesFailed() {
//        return nodesFailed.get();
//    }
//}
