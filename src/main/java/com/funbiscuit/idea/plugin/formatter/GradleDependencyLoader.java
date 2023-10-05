package com.funbiscuit.idea.plugin.formatter;

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
//import org.jetbrains.plugins.gradle.util.GradleConstants;

public class GradleDependencyLoader {

    private final Project project;

    public GradleDependencyLoader(Project project) {
        this.project = project;
    }

    public void refreshGradleDependencies() {
        System.out.println("refreshGradleDependencies start");
        ProjectSystemId projectSystemId = new ProjectSystemId("GRADLE");
        ExternalSystemUtil.refreshProject(
                project,
                projectSystemId,
                project.getProjectFilePath(),
                null, // Passing null for the callback since there's no direct callback in this method
                false,
                ProgressExecutionMode.MODAL_SYNC
        );
        System.out.println("refreshGradleDependencies finished");
    }
}