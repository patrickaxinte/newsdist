package server.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ConfigManager {
    private static volatile ConfigManager instance;
    private final Properties properties;

    private ConfigManager() throws IOException {
        properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new IOException("config.properties not found");
            }
            properties.load(input);
        }
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            synchronized (ConfigManager.class) {
                if (instance == null) {
                    try {
                        instance = new ConfigManager();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to initialize ConfigManager: " + e.getMessage());
                    }
                }
            }
        }
        return instance;
    }

    public String getNodeId() {
        String nodeId = properties.getProperty("node.id");
        if (nodeId == null || nodeId.isEmpty()) {
            throw new RuntimeException("node.id is not set in config.properties");
        }
        return nodeId;
    }

    public String getDefaultHostname() {
        return properties.getProperty("default.hostname", "127.0.0.1");
    }

    public int getServerPort() {
        String portStr = properties.getProperty("server.port");
        if (portStr != null) {
            try {
                return Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Invalid server.port in config.properties");
            }
        }
        return 5001; // Default port
    }

    public String getLogFilePath() {
        return properties.getProperty("log.file.path", "server.log");
    }


    public Map<String, Integer> getNeighborNodes() {
        String nodesStr = properties.getProperty("neighbor.nodes");
        Map<String, Integer> neighborNodes = new HashMap<>();
        if (nodesStr != null && !nodesStr.isEmpty()) {
            String[] nodes = nodesStr.split(",");
            for (String node : nodes) {
                String[] parts = node.split(":");
                if (parts.length == 2) {
                    String nodeId = parts[0].trim();
                    int port = Integer.parseInt(parts[1].trim());
                    neighborNodes.put(nodeId, port);
                } else {
                    throw new RuntimeException("Invalid neighbor node format: " + node);
                }
            }
        }
        return neighborNodes;
    }
}

//package server.utils;
//
//import java.io.IOException;
//import java.io.InputStream;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Properties;
//
//public class ConfigManager {
//    private static volatile ConfigManager instance;
//    private final Properties properties;
//
//    private ConfigManager() throws IOException {
//        properties = new Properties();
//        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
//            if (input == null) {
//                throw new IOException("config.properties not found");
//            }
//            properties.load(input);
//        }
//    }
//
//    public static ConfigManager getInstance() {
//        if (instance == null) {
//            synchronized (ConfigManager.class) {
//                if (instance == null) {
//                    try {
//                        instance = new ConfigManager();
//                    } catch (IOException e) {
//                        throw new RuntimeException("Failed to initialize ConfigManager: " + e.getMessage());
//                    }
//                }
//            }
//        }
//        return instance;
//    }
//
//    public String getNodeId() {
//        String nodeId = properties.getProperty("node.id");
//        if (nodeId == null || nodeId.isEmpty()) {
//            throw new RuntimeException("node.id is not set in config.properties");
//        }
//        return nodeId;
//    }
//
//    public String getDefaultHostname() {
//        return properties.getProperty("default.hostname", "127.0.0.1");
//    }
//
//    public int getServerPort() {
//        String portStr = properties.getProperty("server.port");
//        if (portStr != null) {
//            try {
//                return Integer.parseInt(portStr);
//            } catch (NumberFormatException e) {
//                throw new RuntimeException("Invalid server.port in config.properties");
//            }
//        }
//        return 5001; // Default port
//    }
//
//    public String getLogFilePath() {
//        return properties.getProperty("log.file.path", "server.log");
//    }
//
//    public Map<String, Integer> getNeighborNodes() {
//        String nodesStr = properties.getProperty("neighbor.nodes");
//        Map<String, Integer> neighborNodes = new HashMap<>();
//        if (nodesStr != null && !nodesStr.isEmpty()) {
//            String[] nodes = nodesStr.split(",");
//            for (String node : nodes) {
//                String[] parts = node.split(":");
//                if (parts.length == 2) {
//                    String nodeId = parts[0].trim();
//                    int port = Integer.parseInt(parts[1].trim());
//                    neighborNodes.put(nodeId, port);
//                } else {
//                    throw new RuntimeException("Invalid neighbor node format: " + node);
//                }
//            }
//        }
//        return neighborNodes;
//    }
//
//    public String getSuccessorNodeId() {
//        String successorId = properties.getProperty("node.successor.id");
//        if (successorId == null || successorId.isEmpty()) {
//            throw new RuntimeException("node.successor.id is not set in config.properties");
//        }
//        return successorId;
//    }
//
//    public int getSuccessorPort() {
//        String successorPortStr = properties.getProperty("node.successor.port");
//        if (successorPortStr != null) {
//            try {
//                return Integer.parseInt(successorPortStr);
//            } catch (NumberFormatException e) {
//                throw new RuntimeException("Invalid node.successor.port in config.properties");
//            }
//        }
//        throw new RuntimeException("node.successor.port is not set in config.properties");
//    }
//
//    public String getPredecessorNodeId() {
//        String predecessorId = properties.getProperty("node.predecessor.id");
//        if (predecessorId == null || predecessorId.isEmpty()) {
//            throw new RuntimeException("node.predecessor.id is not set in config.properties");
//        }
//        return predecessorId;
//    }
//
//    public int getPredecessorPort() {
//        String predecessorPortStr = properties.getProperty("node.predecessor.port");
//        if (predecessorPortStr != null) {
//            try {
//                return Integer.parseInt(predecessorPortStr);
//            } catch (NumberFormatException e) {
//                throw new RuntimeException("Invalid node.predecessor.port in config.properties");
//            }
//        }
//        throw new RuntimeException("node.predecessor.port is not set in config.properties");
//    }
//
//    public String getTimezone() {
//        return properties.getProperty("log.timezone", "Europe/Bucharest");
//    }
//
//    public String getProperty(String key, String defaultValue) {
//        return properties.getProperty(key, defaultValue);
//    }
//
//
//}
