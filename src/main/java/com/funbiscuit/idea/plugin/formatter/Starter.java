package com.funbiscuit.idea.plugin.formatter;

import com.intellij.formatting.commandLine.StdIoMessageOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.DumbService;
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

            String projectPath = "/data/";




            ProjectManager projectManager = ProjectManager.getInstance();
            try {
                System.out.println("Load and open project");
                Project project = projectManager.loadAndOpenProject(projectPath);
                ProjectSystemId projectSystemId = new ProjectSystemId("GRADLE");

                System.out.println("Start refreshing project");
                ExternalSystemUtil.refreshProject(
                        project,
                        projectSystemId,
                        "/data",
                        new ExternalProjectRefreshCallback() {
                            public void onSuccess(DataNode<ProjectData> externalProject) {
                                System.out.println("Project dependencies synchronized successfully.");
                                System.out.println("Start indexing project");

                                // Use DumbService to wait for indexing to complete
                                DumbService.getInstance(project).runWhenSmart(() -> {
                                    System.out.println("Project indexed successfully.");

                                    System.out.println("Wait for read access");
                                    ApplicationManager.getApplication().runReadAction(() -> {
                                        System.out.println("Start analysis");
                                        // Your code that needs read access goes here
                                        JavaFileAnalyzer analyzer = new JavaFileAnalyzer(project);
                                        analyzer.analyze();
                                    });
                                });
                            }

                            public void onFailure(@NotNull String errorMessage, @NotNull String errorDetails) {
                                System.err.println("Failed to synchronize project dependencies: " + errorMessage);
                                LOG.error("Failed to synchronize project dependencies: " + errorDetails);
                            }
                        }, // Passing null for the callback since there's no direct callback in this method
                        false,
                        ProgressExecutionMode.MODAL_SYNC
                );
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
