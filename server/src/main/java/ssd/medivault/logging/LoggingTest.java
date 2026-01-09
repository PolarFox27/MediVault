package ssd.medivault.logging;

public class LoggingTest {

    public static void main(String[] args) {
        // Initialize AuditLogger with the logging server address
        AuditLogger logger = AuditLogger.getInstance("127.0.0.1", 5555);

        // Test logs
        // ACTOR , ACTION , TARGET , EXTRADATA
        logger.logAction("SERVER", "SYSTEM_START", "NONE");
        logger.logAction("USER:123", "UPLOAD_FILE", "PATIENT:123", "blood_test.pdf");
        logger.logAction("DOCTOR:42", "VIEW_RECORD", "PATIENT:123");

        System.out.println("Test logs sent. Check the logging server output or audit.log file.");
    }
}

