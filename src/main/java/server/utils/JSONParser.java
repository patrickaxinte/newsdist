package server.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import server.models.NodeMessage;

import java.io.IOException;

public class JSONParser {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static NodeMessage parseNodeMessage(String json) throws IOException {
        return objectMapper.readValue(json, NodeMessage.class);
    }

    public static String toJson(NodeMessage nodeMessage) throws IOException {
        return objectMapper.writeValueAsString(nodeMessage);
    }

    // Other parsing methods if necessary
}
