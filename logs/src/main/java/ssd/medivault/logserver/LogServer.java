package ssd.medivault.logserver;

import ssd.medivault.logserver.service.LogChainService;
import ssd.medivault.logserver.storage.AppendOnlyLogStore;
import ssd.medivault.logserver.crypto.HmacService;
import ssd.medivault.logserver.transport.LocalLogReceiver;

import java.nio.file.Path;

public class LogServer {

    public static void main(String[] args) throws Exception {
        Path logFile = Path.of("audit.log");
        Path socketPath = Path.of("/tmp/medivault-audit.sock");

        HmacService hmacService = new HmacService();
        AppendOnlyLogStore store = new AppendOnlyLogStore(logFile);
        LogChainService chainService = new LogChainService(store, hmacService);

        int port = 5555; // local-only

        LocalLogReceiver receiver = new LocalLogReceiver(port, chainService);
        receiver.start(); // blocking

    }
}

