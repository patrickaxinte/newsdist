package org.example.Client;

public class Broker {
    private final String ipBroker;
    private boolean isRunning;

    public Broker(String ipBroker, boolean isRunning) {
        this.ipBroker = ipBroker;
        this.isRunning = isRunning;
    }

    public String getIpBroker() {
        return ipBroker;
    }

    public void setRunning(boolean running) {
        isRunning = running;
    }
}
