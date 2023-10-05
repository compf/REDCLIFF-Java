package com.funbiscuit.idea.plugin.formatter;

import com.funbiscuit.idea.plugin.formatter.report.*;
import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.commandLine.StdIoMessageOutput;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.options.SchemeImportException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSettingsLoader;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import javax.tools.JavaFileManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Command(mixinStandardHelpOptions = true,
        name = "formatter",
        description = "Formats and rearranges code according IDEA Code Style settings")
public class FormatCommand implements Callable<Integer> {
    private static final StdIoMessageOutput messageOutput = StdIoMessageOutput.INSTANCE;
    private static final String PROJECT_DIR_PREFIX = "idea.reformat.";
    private static final String PROJECT_DIR_SUFFIX = ".tmp";

    private boolean recursive = true;

    @Option(names = {"-m", "--mask"}, split = ",", paramLabel = "<mask>",
            description = "A comma-separated list of file masks")
    private List<String> masks = List.of();

    @Option(names = {"--report"}, description = "Where to save report (by default not saved). Supported extensions: '.html'")
    private Path report;

    @Parameters(index = "1..*", paramLabel = "<file>", description = "A path to a file or a directory")
    private List<Path> files = List.of();

    @Parameters(index = "0", hidden = true)
    private String command;

    private Path projectPath;
    private Project project;
    private FileProcessor fileProcessor;

    private static Reporter createReporterForFile(Path file) {
        if (file == null) {
            return null;
        }
        if (file.toString().endsWith(".html")) {
            return new HtmlReporter();
        } else {
            return null;
        }
    }

    @Override
    public Integer call() throws Exception {
        Predicate<String> fileNamePredicate = masks.stream()
                .map(m -> m
                        .replace(".", "\\.")
                        .replace("*", ".*")
                        .replace("?", ".")
                        .replace("+", "\\+"))
                .map(Pattern::compile)
                .map(Pattern::asMatchPredicate)
                .reduce(Predicate::or)
                .orElse(p -> true);

        var reporter = createReporterForFile(report);
        if (report != null && reporter == null) {
            messageOutput.info("Given report file extension is not supported\n");
            return CommandLine.ExitCode.SOFTWARE;
        }

        createTempProject();

        //loadSettings(style);

        fileProcessor = new FileFormatter();

        List<Path> allFiles = new ArrayList<>();
        messageOutput.info("Counting files...\n");
        for (var path : files) {
            try (var stream = Files.walk(path, recursive ? Integer.MAX_VALUE : 1)) {
                List<Path> dirFiles = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> fileNamePredicate.test(p.toString()))
                        .toList();
                allFiles.addAll(dirFiles);
            }
        }
        messageOutput.info("Processing %d files%n".formatted(allFiles.size()));
        List<FileInfo> fileInfos = new ArrayList<>();

        var lastPrintTime = System.currentTimeMillis();
        for (int i = 0; i < allFiles.size(); i++) {
            Path file = allFiles.get(i);
            System.out.println("file: "+file.getFileName());
            fileInfos.add(processPath(file));

            var now = System.currentTimeMillis();
            if (now - lastPrintTime > 1000L) {
                lastPrintTime = now;
                messageOutput.info("Processed %d/%d files%n".formatted(i + 1, allFiles.size()));
            }
        }
        messageOutput.info("Processed %d files%n".formatted(allFiles.size()));

        RecentProjectsManager.getInstance().removePath(projectPath.toString());


        var withErrors = fileInfos.stream().anyMatch(FileInfo::hasErrors);


        List<ProcessResult> processResults = fileInfos.stream()
                .flatMap(info -> info.getProcessLog().stream()
                        .map(entry -> new ProcessResult(info.getFilename(), entry.level(), entry.message())))
                .sorted(Comparator.comparing(ProcessResult::level).reversed().thenComparing(ProcessResult::filename))
                .toList();

        if (reporter != null) {
            reporter.generate(report, processResults);
        } else {
            // print report to stdout only when no report file is given
            processResults.stream()
                    .filter(status -> status.level() != Level.INFO)
                    .forEach(status -> messageOutput.info(
                            "%s %s: %s%n".formatted(status.filename(), status.level(), status.status())
                    ));
        }

        return withErrors ? CommandLine.ExitCode.SOFTWARE : CommandLine.ExitCode.OK;
    }

    private FileInfo processPath(Path filePath) {
        Exception ex = null;
        var fileInfo = new FileInfo(filePath.toString());
        try {
            processPathInternal(filePath, fileInfo);
        } catch (Exception e) {
            ex = e;
            fileInfo.addWarning(ProcessStatuses.FAILED_TO_PROCESS);
        }
        if (ex != null) {
            ex.printStackTrace();
        }
        return fileInfo;
    }

    private void processPathInternal(Path filePath, FileInfo fileInfo) {
        PsiManager psiManager = PsiManager.getInstance(project);

        var virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(filePath);
        if (virtualFile == null) {
            fileInfo.addWarning(ProcessStatuses.FAILED_TO_OPEN);
            return;
        }

        virtualFile.refresh(false, false);

        if (virtualFile.getFileType().isBinary()) {
            fileInfo.addWarning(ProcessStatuses.SKIPPED_BINARY_FILE);
            return;
        }

        var psiFile = psiManager.findFile(virtualFile);
        if (psiFile == null) {
            fileInfo.addWarning(ProcessStatuses.FAILED_TO_OPEN);
            return;
        }

        // TODO check is java file?

        fileProcessor.processFile(psiFile, fileInfo);
    }

    private void loadSettings(Path stylePath) throws SchemeImportException {
        VirtualFile file = VfsUtil.findFile(stylePath, true);
        if (file == null) {
            throw new AppException("Failed to read style settings");
        }
        var codeStyle = new CodeStyleSettingsLoader().loadSettings(file);
        CodeStyle.setMainProjectSettings(project, codeStyle);
    }

    private void createTempProject() throws IOException {
        var projectUID = UUID.randomUUID().toString();
        projectPath = FileUtil
                .createTempDirectory(PROJECT_DIR_PREFIX, projectUID + PROJECT_DIR_SUFFIX)
                .toPath();

        project = ProjectManagerEx.getInstanceEx()
                .openProject(projectPath, OpenProjectTask.build().asNewProject());

        if (project == null) {
            throw new AppException("Failed to open temporary project");
        }
    }
}
