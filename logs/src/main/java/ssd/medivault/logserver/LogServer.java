package ssd.medivault.logserver;

import ssd.medivault.logserver.service.LogChainService;
import ssd.medivault.logserver.storage.AppendOnlyLogStore;
import ssd.medivault.logserver.crypto.HmacService;
import ssd.medivault.logserver.transport.LocalLogReceiver;

import java.nio.file.Path;

public class LogServer {
    public static void main(String[] args) throws Exception {
        Path logFile = Path.of(System.getenv().getOrDefault("AUDIT_LOG_FILE", "audit.log"));
        int port = Integer.parseInt(System.getenv().getOrDefault("AUDIT_PORT", "5555"));
        
        HmacService hmacService = new HmacService();
        AppendOnlyLogStore store = new AppendOnlyLogStore(logFile);
        LogChainService chainService = new LogChainService(store, hmacService);
        LocalLogReceiver receiver = new LocalLogReceiver(port, chainService);
        
        receiver.start();
    }
}

