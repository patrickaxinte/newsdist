package server.communication;

import server.utils.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocketHandler implements Runnable {
    private ServerSocket serverSocket;
    private final int port;
    private final Logger logger = Logger.getInstance();

    // Thread pool for handling node connections
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    public SocketHandler(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            logger.log("SocketHandler is listening on port " + port);

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket nodeSocket = serverSocket.accept();
                    logger.log("New node connection from " + nodeSocket.getInetAddress());

                    // Handle the node connection
                    ConnectionHandler nodeHandler = new ConnectionHandler(nodeSocket);

                    // Submit the handler to the thread pool
                    threadPool.submit(nodeHandler);
                } catch (SocketException e) {
                    if (serverSocket.isClosed()) {
                        logger.log("Server socket is closed. Exiting SocketHandler.");
                        break;
                    } else {
                        logger.error("SocketException in SocketHandler: " + e.getMessage());
                    }
                } catch (IOException e) {
                    logger.error("IOException in SocketHandler: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.error("Failed to start SocketHandler on port " + port + ": " + e.getMessage());
        } finally {
            closeServerSocket();
            threadPool.shutdownNow();
        }
    }

    private void closeServerSocket() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                logger.log("Server socket closed.");
            } catch (IOException e) {
                logger.error("Error closing server socket: " + e.getMessage());
            }
        }
    }
}
