package ssd.medivault.logserver.storage;

import ssd.medivault.logserver.model.LogEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class AppendOnlyLogStore {
    private final Path logFile;

    public AppendOnlyLogStore(Path logFile) throws IOException {
        this.logFile = logFile;
        if (!Files.exists(logFile)) {
            Files.createFile(logFile);
        }
    }

    public synchronized void append(LogEntry entry) {
        try {
            Files.writeString(logFile, entry.serialize() + "\n", StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public String readLastHash() {
        try {
            List<String> lines = Files.readAllLines(logFile);
            if (lines.isEmpty()) return "GENESIS";
            String last_line = lines.get(lines.size() - 1);
            if(last_line.trim().isEmpty()) return "GENESIS";
            return last_line.split("\\|")[8];
        } catch (Exception e) {
            return "GENESIS";
        }
    }
}

