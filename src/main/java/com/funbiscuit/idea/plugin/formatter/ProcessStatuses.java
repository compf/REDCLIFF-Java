package com.funbiscuit.idea.plugin.formatter;

/**
 * @author skokurin
 * @since 08.07.2023
 */
public final class ProcessStatuses {
    public static final String SKIPPED_BINARY_FILE = "Skipped, binary file";
    public static final String FAILED_TO_OPEN = "Failed to open";
    public static final String FAILED_TO_PROCESS = "Failed to process";
    public static final String SKIPPED_READ_ONLY = "Skipped, read only";
    public static final String FORMATTED = "Formatted";
    public static final String FORMATTED_WELL = "Formatted well";
    public static final String NEEDS_FORMATTING = "Needs formatting";

    private ProcessStatuses() {
    }
}
