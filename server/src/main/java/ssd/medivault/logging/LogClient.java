package ssd.medivault.logging;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class LogClient {
    private final String host;
    private final int port;

    public LogClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Send a single log entry to the logging server.
     * 
     * @param logLine fully formatted log line
     * @throws IOException if connection fails
     */
    public void sendLog(String logLine) throws IOException {
        try (Socket socket = new Socket(host, port);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
            writer.write(logLine);
            writer.newLine();
            writer.flush();
        }
    }
}

