package com.funbiscuit.idea.plugin.formatter.report;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface Reporter {
    void generate(Path output, List<ProcessResult> processResults) throws IOException;
}
