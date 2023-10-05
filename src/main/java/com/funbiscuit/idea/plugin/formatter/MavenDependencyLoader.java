package com.funbiscuit.idea.plugin.formatter;

public class MavenDependencyLoader {

}
/**

import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public class MavenDependencyLoader {

    private final Project project;

    public MavenDependencyLoader(Project project) {
        this.project = project;
    }

    public void refreshMavenDependencies() {
        MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(project);
        mavenProjectsManager.forceUpdateAllProjectsOrFindAllAvailablePomFiles();
    }
}
 */