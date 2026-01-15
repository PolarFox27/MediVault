package ssd.medivault.logserver.service;

import ssd.medivault.logserver.crypto.HmacService;
import ssd.medivault.logserver.model.LogEntry;
import ssd.medivault.logserver.storage.AppendOnlyLogStore;

public class LogChainService {
    private final AppendOnlyLogStore store;
    private final HmacService hmac;
    private String lastHash;
    private long lastTimestamp = 0L;
    
    public LogChainService(AppendOnlyLogStore store, HmacService hmac) {
        this.store = store;
        this.hmac = hmac;
        this.lastHash = store.readLastHash();
    }

    public synchronized void appendRaw(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            System.err.println("Rejected empty log line");
            return;
        }
        
        String payload = raw + "|" + lastHash;
        String entryHash = hmac.hmac(payload);
        String[] parts = raw.split("\\|", 6); // timestamp|level|actor|action|target|extraData
        
        if (parts.length < 6) {
            System.err.println("Rejected malformed log line: " + raw);
            return;
        }
        
        long ts;
        try {
            ts = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid timestamp, using server time");
            ts = System.currentTimeMillis();
        }
        
        if (ts < lastTimestamp) {
            System.err.println("Rejected log with timestamp earlier than previous entry: " + raw);
            return;
        }
        lastTimestamp = ts;
        
        LogEntry entry = new LogEntry(ts, parts[1], parts[2], parts[3], parts[4], parts[5], lastHash, entryHash);
        store.append(entry);
        lastHash = entryHash;
    }
}

