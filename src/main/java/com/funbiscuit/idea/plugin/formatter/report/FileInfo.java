package com.funbiscuit.idea.plugin.formatter.report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FileInfo {
    private final String filename;
    private final List<LogEntry> processLog = new ArrayList<>();

    public FileInfo(String filename) {
        this.filename = filename;
    }

    public void addInfo(String message) {
        processLog.add(new LogEntry(Level.INFO, message));
    }

    public void addWarning(String message) {
        processLog.add(new LogEntry(Level.WARN, message));
    }

    public void addError(String message) {
        processLog.add(new LogEntry(Level.ERROR, message));
    }

    public boolean hasErrors() {
        return processLog.stream().anyMatch(p -> p.level() == Level.ERROR);
    }

    public String getFilename() {
        return filename;
    }

    public List<LogEntry> getProcessLog() {
        return processLog.stream().sorted(Comparator.comparing(LogEntry::level).reversed()).toList();
    }
}
