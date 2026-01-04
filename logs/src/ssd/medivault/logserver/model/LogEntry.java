package ssd.medivault.logserver.model;

public final class LogEntry {
    public final long timestamp;
    public final String actor;
    public final String action;
    public final String target;
    public final String previousHash;
    public final String entryHash;

    public LogEntry(long timestamp, String actor, String action, String target, String previousHash, String entryHash) {
        this.timestamp = timestamp;
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.previousHash = previousHash;
        this.entryHash = entryHash;
    }

    public String serialize() {
        return timestamp + "|" + actor + "|" + action + "|" + target + "|" + previousHash + "|" + entryHash;
    }
}

