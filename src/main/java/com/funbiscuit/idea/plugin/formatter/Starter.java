package com.funbiscuit.idea.plugin.formatter;

import com.intellij.formatting.commandLine.StdIoMessageOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.ui.jcef.JBCefApp;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;

import java.io.IOException;
import java.util.List;

public class Starter implements ApplicationStarter {
    private static final Logger LOG = Logger.getInstance(Starter.class);
    private static final StdIoMessageOutput messageOutput = StdIoMessageOutput.INSTANCE;

    @Override
    public String getCommandName() {
        return "formatter";
    }

    @Override
    public void main(@NotNull List<String> args) {
        System.out.println("AST PARSER");

        String projectPath = ".";


        ProjectManager projectManager = ProjectManager.getInstance();
        try {
            Project project = projectManager.loadAndOpenProject(projectPath);
            StartupManager.getInstance(project).runWhenProjectIsInitialized(() -> {
                // Your code after project is loaded
                System.out.println("Project is loaded");

                //ExternalSystemUtil.refreshProject(project, GradleConstants.SYSTEM_ID);

                JavaFileAnalyzer analyzer = new JavaFileAnalyzer(project);
                analyzer.analyze();

                //startAnalysis(args, project);
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (JDOMException e) {
            throw new RuntimeException(e);
        }

    }



    private void startAnalysis(@NotNull List<String> args, Project project){
        try {
            // call it here so it doesn't log warning in the middle of processing
            JBCefApp.isSupported();
            int exitCode = new CommandLine(new FormatCommand(project))
                    .setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
                        if (ex instanceof AppException appException) {
                            messageOutput.info(appException.getMessage() + "\n");
                            System.exit(CommandLine.ExitCode.SOFTWARE);
                        } else {
                            LOG.error(ex);
                        }

                        return CommandLine.ExitCode.SOFTWARE;
                    })
                    .execute(args.toArray(String[]::new));

            if (exitCode != CommandLine.ExitCode.OK) {
                System.exit(exitCode);
            }
        } catch (Throwable t) {
            System.exit(CommandLine.ExitCode.SOFTWARE);
        }
        ((ApplicationEx) ApplicationManager.getApplication()).exit(true, true);
    }
}
