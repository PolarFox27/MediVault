package ssd.medivault.logging;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuditLogger {

    @Value("${log.server.host:127.0.0.1}")
    private String host;

    @Value("${log.server.port:5555}")
    private int port;

    /**
     * Log an action to the log server.
     *
     * @param level the log severity level
     * @param address the remote IP address of the user who performed the action
     * @param actor  who performs the action (e.g., "USER:123", "DOCTOR:42", "SERVER")
     * @param action what action is performed (e.g., "UPLOAD_FILE", "LOGIN_SUCCESS")
     * @param target target of the action (e.g., "PATIENT:123", "NONE")
     * @param extraData optional extra info (filename, IP, etc.)
     */
    public void logAction(Level level, String address, String actor, String action, String target, String extraData) {
        long timestamp = System.currentTimeMillis();
        String logLine = String.format("%d|%s|%s|%s|%s|%s|%s",
                timestamp,
                level != null ? level.toString() : "UNDEFINED",
                address != null ? address.replace('|', '_') : "localhost",
                actor != null ? actor.replace('|', '_') : "SERVER",
                action != null ? action.replace('|', '_') : "-",
                target != null ? target.replace('|', '_') : "-",
                extraData != null ? extraData.replace('|', '_') : "-");

        try {
            new LogClient(host, port).sendLog(logLine);
        } catch (IOException e) {
            System.err.println("Failed to send log: " + e.getMessage());
        }
    }

    // Convenience overload without extraData
    public void logAction(Level level, String address, String actor, String action, String target) {
        logAction(level, address, actor, action, target, "-");
    }

    public enum Level {
        INFO,
        WARN,
        ERROR,
        FATAL
    }
}

