package ssd.medivault.logserver.service;

import ssd.medivault.logserver.crypto.HmacService;
import ssd.medivault.logserver.model.LogEntry;
import ssd.medivault.logserver.storage.AppendOnlyLogStore;

public class LogChainService {
    private final AppendOnlyLogStore store;
    private final HmacService hmac;
    private String lastHash;
    
    public LogChainService(AppendOnlyLogStore store, HmacService hmac) {
        this.store = store;
        this.hmac = hmac;
        this.lastHash = store.readLastHash();
    }

    public synchronized void appendRaw(String raw) {
        long ts = System.currentTimeMillis();
        String payload = ts + "|" + raw + "|" + lastHash;
        String entryHash = hmac.hmac(payload);
        LogEntry entry = new LogEntry(ts, "SERVER", raw, "-", lastHash, entryHash);
        store.append(entry);
        lastHash = entryHash;
    }
}

