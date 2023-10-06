package com.funbiscuit.idea.plugin.formatter;

import com.intellij.formatting.commandLine.StdIoMessageOutput;
import com.intellij.ide.impl.OpenProjectTask;
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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.util.indexing.FileBasedIndex;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;
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




            try {
                System.out.println("Load and open project");
                Project project = ProjectManagerEx.getInstanceEx().loadAndOpenProject(projectPath);

                configureSdk(project);

                ProjectSystemId projectSystemId = new ProjectSystemId(GradleConstants.SYSTEM_ID.getId());
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

                                        checkRequirements(project);

                                        System.out.println("Start analysis");
                                        // Your code that needs read access goes here
                                        JavaFileAnalyzer analyzer = new JavaFileAnalyzer(project);
                                        analyzer.analyze();
                                        System.exit(CommandLine.ExitCode.SOFTWARE);
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

    private void checkForJDK(){
        String pathToJavaHome = "/usr/lib/jvm/java-17-openjdk-amd64";
        String jdkPath = pathToJavaHome;  // Replace with the actual path to your JDK
        VirtualFile jdkHome = LocalFileSystem.getInstance().findFileByPath(jdkPath);
        if (jdkHome != null) {
            ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
            SdkType javaSdkType = JavaSdk.getInstance();
            String sdkName = "Java 17";  // Or any other appropriate name
            Sdk newSdk = jdkTable.createSdk(sdkName, javaSdkType);
            SdkModificator sdkModificator = newSdk.getSdkModificator();
            sdkModificator.setHomePath(jdkHome.getPath());

            // Wrap the commitChanges() and addJdk() inside a write action
            ApplicationManager.getApplication().runWriteAction(() -> {
                sdkModificator.commitChanges();
                jdkTable.addJdk(newSdk);
            });
        } else {
            System.out.println("JDK path not found.");
        }
    }

    private void configureSdk(Project project) {
        System.out.println("Configuring SDK");

        checkForJDK();

        Sdk desiredSdk = null;
        Sdk[] sdks = ProjectJdkTable.getInstance().getAllJdks();
        System.out.println("Found " + sdks.length + " SDKs");
        for (Sdk sdk : sdks) {
            System.out.println("Found SDK: " + sdk.getName());
            if (sdk.getName().contains("17")) {  // or any other version you're looking for
                desiredSdk = sdk;
                break;
            }
        }

        if (desiredSdk != null) {
            System.out.println("Desired SDK: " + desiredSdk.getName());
            System.out.println("Desired SDK path: "+ desiredSdk.getHomePath());

            JavaSdkVersion version = JavaSdk.getInstance().getVersion(desiredSdk);
            System.out.println("SDK Version: " + version);

            System.out.println("Setting SDK for project");

            // Wrap the commitChanges() and addJdk() inside a write action
            Sdk finalDesiredSdk = desiredSdk;
            ApplicationManager.getApplication().runWriteAction(() -> {
                ProjectRootManager.getInstance(project).setProjectSdk(finalDesiredSdk);

                System.out.println("Configuring SDK for modules");
                ModuleManager moduleManager = ModuleManager.getInstance(project);
                for (Module module : moduleManager.getModules()) {
                    System.out.println("Configuring SDK for module: " + module.getName());
                    ModuleRootModificationUtil.setModuleSdk(module, finalDesiredSdk);
                }
            });



        } else {
            System.out.println("Desired SDK not found.");
        }
    }

    private void checkRequirements(Project project){
        Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
        if (projectSdk == null) {
            System.out.println("No SDK set for the project.");
        } else {
            System.out.println("Project SDK: " + projectSdk.getName());
        }
        ModuleManager moduleManager = ModuleManager.getInstance(project);
        for (Module module : moduleManager.getModules()) {
            Sdk moduleSdk = ModuleRootManager.getInstance(module).getSdk();
            if (moduleSdk == null) {
                System.out.println("No SDK set for module: " + module.getName());
            } else {
                System.out.println("Module SDK for " + module.getName() + ": " + moduleSdk.getName());
            }
        }
        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
        PsiClass arrayListClass = javaPsiFacade.findClass("java.util.ArrayList", GlobalSearchScope.allScope(project));
        if (arrayListClass == null) {
            System.out.println("ArrayList class not found in the project's scope.");
        } else {
            System.out.println("ArrayList class found.");
        }
        LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
        for (Library library : libraryTable.getLibraries()) {
            System.out.println("Library: " + library.getName());
        }
    }
}
