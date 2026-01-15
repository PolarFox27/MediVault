package ssd.medivault.logserver.model;

public final class LogEntry {
    public final long timestamp;
    public final String level;
    public final String actor;
    public final String action;
    public final String target;
    public final String extraData;
    public final String previousHash;
    public final String entryHash;

    public LogEntry(long timestamp, String level, String actor, String action, String target, String extraData, String previousHash, String entryHash) {
        this.timestamp = timestamp;
        this.level = level;
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.extraData = extraData;
        this.previousHash = previousHash;
        this.entryHash = entryHash;
    }

    public String serialize() {
        return timestamp + "|" + level + "|" + actor + "|" + action + "|" + target + "|" + extraData + "|" + previousHash + "|" + entryHash;
    }
}

