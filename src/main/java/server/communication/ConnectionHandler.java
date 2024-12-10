package server.communication;

import server.utils.Logger;

import java.io.*;
import java.net.Socket;
import server.processing.NodeMessageProcessor;


public class ConnectionHandler implements Runnable {
    private final Socket socket;
    private final Logger logger = Logger.getInstance();

    public ConnectionHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (
                InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output))
        ) {
            String message;
            while ((message = reader.readLine()) != null) {
                logger.log("Received message: " + message);

                // Process the message
                String response = NodeMessageProcessor.processMessage(message);

                // Send response if necessary
                if (response != null) {
                    writer.write(response);
                    writer.newLine();
                    writer.flush();
                }
            }
        } catch (IOException e) {
            logger.error("IOException in ConnectionHandler: " + e.getMessage());
        } finally {
            closeSocket();
        }
    }

    private void closeSocket() {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
                logger.log("Socket closed: " + socket.getInetAddress());
            } catch (IOException e) {
                logger.error("Error closing socket: " + e.getMessage());
            }
        }
    }

}
