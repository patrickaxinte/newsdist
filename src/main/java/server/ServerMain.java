package server;

import server.communication.SocketHandler;
import server.heartbeat.HeartBeatManager;
import server.processing.NodeMessageProcessor;
import server.utils.ConfigManager;
import server.utils.Logger;

import java.util.Map;

public class ServerMain {
    public static void main(String[] args) {
        // Load configuration settings
        ConfigManager config = ConfigManager.getInstance();

        // Initialize Logger
        Logger logger = Logger.getInstance();
        logger.log("Server is starting...");

        // Get neighbor nodes from configuration
        Map<String, Integer> neighborNodes = config.getNeighborNodes();

        // Get nodeId and hostname from configuration
        String nodeId = config.getNodeId();
        String hostname = config.getDefaultHostname();

        // Initialize HeartBeatManager
        HeartBeatManager heartBeatManager = new HeartBeatManager(neighborNodes, nodeId, hostname);
        Thread heartBeatThread = new Thread(heartBeatManager);
        heartBeatThread.start();

        // Initialize NodeMessageProcessor with HeartBeatManager
        NodeMessageProcessor.setHeartBeatManager(heartBeatManager);

        // Initialize SocketHandler
        int port = config.getServerPort();
        SocketHandler socketHandler = new SocketHandler(port);
        Thread socketThread = new Thread(socketHandler);
        socketThread.start();

        // Start metrics thread
        Thread metricsThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60000); // Log metrics every 60 seconds
                    logger.log("Heartbeats Sent: " + heartBeatManager.getHeartbeatsSent());
                    logger.log("Heartbeats Received: " + heartBeatManager.getHeartbeatsReceived());
                    logger.log("Nodes Failed: " + heartBeatManager.getNodesFailed());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Metrics thread interrupted.");
                    break;
                }
            }
        });
        metricsThread.start();

        logger.log("Server is running...");

        // Add shutdown hook to stop the HeartBeatManager gracefully
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.log("Shutting down server...");
            heartBeatManager.stop();
            heartBeatThread.interrupt();
            socketThread.interrupt();
            metricsThread.interrupt();
            logger.log("Server shutdown complete.");
        }));
    }
}

//package server;
//
//import server.communication.SocketHandler;
//import server.heartbeat.HeartBeatManager;
//import server.processing.NodeMessageProcessor;
//import server.utils.ConfigManager;
//import server.utils.Logger;
//
//public class ServerMain {
//    public static void main(String[] args) {
//        ConfigManager config = ConfigManager.getInstance();
//        Logger logger = Logger.getInstance();
//
//        String nodeId = config.getNodeId();
//        String hostname = config.getDefaultHostname();
//
//        String successorId = config.getSuccessorNodeId();
//        int successorPort = config.getSuccessorPort();
//
//        HeartBeatManager heartBeatManager = new HeartBeatManager(nodeId, hostname, successorId, successorPort);
//        Thread heartbeatThread = new Thread(heartBeatManager);
//        heartbeatThread.start();
//
//        NodeMessageProcessor.setHeartBeatManager(heartBeatManager);
//
//        int port = config.getServerPort();
//        SocketHandler socketHandler = new SocketHandler(port);
//        Thread socketThread = new Thread(socketHandler);
//        socketThread.start();
//
//        logger.log("Server started for node: " + nodeId);
//    }
//}
