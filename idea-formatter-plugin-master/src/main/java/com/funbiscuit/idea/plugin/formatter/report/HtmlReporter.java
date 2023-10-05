package com.funbiscuit.idea.plugin.formatter.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class HtmlReporter implements Reporter {
    private static final String ROW_TEMPLATE = """
            <tr>
                <th class="%3$s">%1$s</th>
                <td class="has-text-centered %3$s p-2">%2$s</td>
            </tr>
                        """;
    private static final String TABLE_BEGIN = """
                <table class="table is-fullwidth">
                    <thead>
                        <tr>
                            <th>File</th>
                            <th class="has-text-centered">Status</th>
                        </tr>
                    </thead>
                    <tbody>
            """;
    private static final String TABLE_END = "</tbody></table>";
    private static final String HTML_BEGIN = """
            <!DOCTYPE html>
            <html lang="en-us">
            <head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Formatter report</title>
                <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@0.9.1/css/bulma.min.css"></head>
            <body>
                <div class="container">
                <nav class="level mt-6">
                    <div class="level-item has-text-centered">
                        <div>
                            <p class="heading">Total files</p>
                            <p class="title has-text-success"><abbr>%s</abbr></p>
                        </div>
                    </div>
                        <div class="level-item has-text-centered">
                        <div>
                            <p class="heading">Warnings</p>
                            <p class="title has-text-warning"><abbr>%s</abbr></p>
                        </div>
                    </div>
                        <div class="level-item has-text-centered">
                        <div>
                            <p class="heading">Errors</p>
                            <p class="title has-text-danger"><abbr>%s</abbr></p>
                        </div>
                    </div>
                </nav>
                        """;
    private static final String HTML_END = """
                    </div>
                    <footer class="footer">
                        <div class="content has-text-centered">
                            <p class="heading">Date: %s</p>
                        </div>
                    </footer>
                </body>
            </html>
            """;
    private static final Map<Level, String> LEVEL_CLASSES = Map.of(
            Level.INFO, "",
            Level.WARN, "has-background-warning",
            Level.ERROR, "has-background-danger"
    );

    @Override
    public void generate(Path output, List<ProcessResult> processResults) throws IOException {
        long warnings = processResults.stream().filter(processResult -> processResult.level() == Level.WARN).count();
        long errors = processResults.stream().filter(processResult -> processResult.level() == Level.ERROR).count();

        try (var writer = Files.newBufferedWriter(output)) {
            writer.write(HTML_BEGIN.formatted(processResults.size(), warnings, errors));
            writer.write(TABLE_BEGIN);
            for (ProcessResult processResult : processResults) {
                writer.write(ROW_TEMPLATE.formatted(
                        processResult.filename(), processResult.status(), LEVEL_CLASSES.get(processResult.level())
                ));
            }
            writer.write(TABLE_END);
            writer.write(HTML_END.formatted(Instant.now()));
        }
    }
}
