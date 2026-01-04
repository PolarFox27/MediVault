package ssd.medivault.logging;

import java.io.IOException;

public class AuditLogger {
    private static AuditLogger instance;
    private final LogClient client;

    private AuditLogger(String host, int port) {
        this.client = new LogClient(host, port);
    }

    /**
     * Get the singleton instance of AuditLogger.
     * @param host log server host
     * @param port log server port
     * @return instance
     */
    public static AuditLogger getInstance(String host, int port) {
        if (instance == null) {
            instance = new AuditLogger(host, port);
        }
        return instance;
    }

    /**
     * Log an action to the log server.
     *
     * @param actor  who performs the action (e.g., "USER:123", "DOCTOR:42", "SERVER")
     * @param action what action is performed (e.g., "UPLOAD_FILE", "LOGIN_SUCCESS")
     * @param target target of the action (e.g., "PATIENT:123", "NONE")
     * @param extraData optional extra info (filename, IP, etc.)
     */
    public void logAction(String actor, String action, String target, String extraData) {
        long timestamp = System.currentTimeMillis();
        String logLine = String.format("%d|%s|%s|%s|%s",
                timestamp,
                actor != null ? actor : "SERVER",
                target != null ? target : "-",
                action != null ? action : "-",
                extraData != null ? extraData : "-");

        try {
            client.sendLog(logLine);
        } catch (IOException e) {
            System.err.println("Failed to send log: " + e.getMessage());
        }
    }

    // Convenience overload without extraData
    public void logAction(String actor, String action, String target) {
        logAction(actor, action, target, "-");
    }
}

