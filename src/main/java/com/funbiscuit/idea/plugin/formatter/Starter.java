package com.funbiscuit.idea.plugin.formatter;

import com.intellij.formatting.commandLine.StdIoMessageOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.jcef.JBCefApp;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;

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

        try {
            // call it here so it doesn't log warning in the middle of processing
            JBCefApp.isSupported();
            int exitCode = new CommandLine(new FormatCommand())
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
