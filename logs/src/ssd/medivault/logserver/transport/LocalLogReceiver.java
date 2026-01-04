package ssd.medivault.logserver.transport;

import ssd.medivault.logserver.service.LogChainService;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class LocalLogReceiver {

    private final int port;
    private final LogChainService chainService;

    public LocalLogReceiver(int port, LogChainService chainService) {
        this.port = port;
        this.chainService = chainService;
    }

    public void start() throws IOException {
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Log server listening on port " + port);
            while (true) {
                Socket client = server.accept();
                handleClient(client);
            }
        }
    }

    private void handleClient(Socket client) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    chainService.appendRaw(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
}

