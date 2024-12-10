package server.processing;

import server.heartbeat.HeartBeatManager;
import server.models.NodeMessage;
import server.utils.JSONParser;
import server.utils.Logger;

import java.io.IOException;

public class NodeMessageProcessor {
    private static final Logger logger = Logger.getInstance();
    private static HeartBeatManager heartBeatManager;

    // Initialize the HeartBeatManager reference
    public static void setHeartBeatManager(HeartBeatManager manager) {
        heartBeatManager = manager;
    }

    public static String processMessage(String message) {
        try {
            // Parse the message into a NodeMessage object
            NodeMessage nodeMessage = JSONParser.parseNodeMessage(message);

            // Handle the message based on its type
            switch (nodeMessage.getAction()) {
                case "HEARTBEAT":
                    handleHeartbeat(nodeMessage);
                    break;
                case "NODE_JOIN":
                    handleNodeJoin(nodeMessage);
                    break;
                case "NODE_LEAVE":
                    handleNodeLeave(nodeMessage);
                    break;
                case "DATA_REPLICATION":
                    handleDataReplication(nodeMessage);
                    break;
                default:
                    logger.error("Unknown node action: " + nodeMessage.getAction());
                    break;
            }

            // Return a response if necessary
            return null;
        } catch (IOException e) {
            logger.error("JSON parsing error in NodeMessageProcessor: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error processing node message: " + e.getMessage());
        }
        return null;
    }

    private static void handleHeartbeat(NodeMessage nodeMessage) {
        String neighborNodeId = nodeMessage.getNodeId();
        if (heartBeatManager != null) {
            heartBeatManager.updateHeartbeat(neighborNodeId);
        } else {
            logger.error("HeartBeatManager is not initialized.");
        }
    }

    private static void handleNodeJoin(NodeMessage nodeMessage) {
        String newNodeId = nodeMessage.getNodeId();
        int newNodePort = Integer.parseInt(nodeMessage.getData()); // Assuming port is sent in data field
        if (heartBeatManager != null) {
            heartBeatManager.addNeighbor(newNodeId, newNodePort);
            logger.log("Node " + newNodeId + " joined on port " + newNodePort);
        } else {
            logger.error("HeartBeatManager is not initialized.");
        }
    }

    private static void handleNodeLeave(NodeMessage nodeMessage) {
        String leavingNodeId = nodeMessage.getNodeId();
        if (heartBeatManager != null) {
            heartBeatManager.removeNeighbor(leavingNodeId);
            logger.log("Node " + leavingNodeId + " has left.");
        } else {
            logger.error("HeartBeatManager is not initialized.");
        }
    }

    private static void handleDataReplication(NodeMessage nodeMessage) {
        // Implementation to handle data replication
    }
}

//package server.processing;
//
//import server.heartbeat.HeartBeatManager;
//import server.models.NodeMessage;
//import server.utils.JSONParser;
//import server.utils.Logger;
//
//import java.io.IOException;
//
//public class NodeMessageProcessor {
//    private static HeartBeatManager heartBeatManager;
//    private static final Logger logger = Logger.getInstance();
//
//    public static void setHeartBeatManager(HeartBeatManager manager) {
//        heartBeatManager = manager;
//    }
//
//    public static String processMessage(String message) {
//        try {
//            NodeMessage nodeMessage = JSONParser.parseNodeMessage(message);
//            switch (nodeMessage.getAction()) {
//                case "HEARTBEAT":
//                    logger.log("Heartbeat received from: " + nodeMessage.getNodeId());
//                    break;
//                case "NODE_FAILURE":
//                    handleNodeFailure(nodeMessage);
//                    break;
//                case "UPDATE_RING":
//                    handleRingUpdate(nodeMessage);
//                    break;
//                default:
//                    logger.error("Unknown action: " + nodeMessage.getAction());
//            }
//        } catch (IOException e) {
//            logger.error("Error processing message: " + e.getMessage());
//        }
//        return null;
//    }
//
//    private static void handleNodeFailure(NodeMessage message) {
//        String failedNode = message.getData();
//        if (failedNode.equals(heartBeatManager.getSuccessor())) {
//            logger.log("Successor failed. Updating topology.");
//            heartBeatManager.setSuccessor(message.getNodeId(), message.getNodePort());
//        }
//    }
//
//    private static void handleRingUpdate(NodeMessage message) {
//        String successorId = message.getData();
//        int successorPort = Integer.parseInt(message.getExtraData());
//        heartBeatManager.setSuccessor(successorId, successorPort);
//    }
//}
