package server.models;

public class NodeMessage {
    private String action; // e.g., "HEARTBEAT", "NODE_JOIN"
    private String data;   // Additional data for the action
    private String nodeId; // Identifier of the sending node

    // Constructors
    public NodeMessage() {}

    public NodeMessage(String action, String data, String nodeId) {
        this.action = action;
        this.data = data;
        this.nodeId = nodeId;
    }

    // Getter and Setter Methods
    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
}

//package server.models;
//
//public class NodeMessage {
//    private String action;   // e.g., "HEARTBEAT", "NODE_JOIN"
//    private String data;     // Additional data for the action (e.g., port)
//    private String extraData; // For optional extended data
//    private String nodeId;   // Identifier of the sending node
//
//    // Constructors
//    public NodeMessage() {}
//
//    public NodeMessage(String action, String data, String nodeId) {
//        this.action = action;
//        this.data = data;
//        this.nodeId = nodeId;
//    }
//
//    public NodeMessage(String action, String data, String extraData, String nodeId) {
//        this.action = action;
//        this.data = data;
//        this.extraData = extraData;
//        this.nodeId = nodeId;
//    }
//
//    // Getter and Setter Methods
//    public String getAction() {
//        return action;
//    }
//
//    public void setAction(String action) {
//        this.action = action;
//    }
//
//    public String getData() {
//        return data;
//    }
//
//    public void setData(String data) {
//        this.data = data;
//    }
//
//    public String getExtraData() {
//        return extraData;
//    }
//
//    public void setExtraData(String extraData) {
//        this.extraData = extraData;
//    }
//
//    public String getNodeId() {
//        return nodeId;
//    }
//
//    public void setNodeId(String nodeId) {
//        this.nodeId = nodeId;
//    }
//
//    public int getNodePort() {
//        try {
//            return Integer.parseInt(data);
//        } catch (NumberFormatException e) {
//            throw new RuntimeException("Invalid port in NodeMessage data: " + data);
//        }
//    }
//}
