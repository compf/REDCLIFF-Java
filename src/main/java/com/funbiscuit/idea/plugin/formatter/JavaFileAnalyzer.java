package com.funbiscuit.idea.plugin.formatter;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.ArrayList;
import java.util.List;

public class JavaFileAnalyzer {

    private final Project project;

    public JavaFileAnalyzer(Project project) {
        this.project = project;
    }

    public void analyze() {
        System.out.println("analyze");

        PsiManager psiManager = PsiManager.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);

        List<PsiClass> allClasses = new ArrayList<>();

        // Iterate over all files in the project using ProjectFileIndex
        ProjectFileIndex projectFileIndex = ProjectFileIndex.getInstance(project);
        projectFileIndex.iterateContent(vFile -> {
            // Check if the file is a Java file
            if (vFile.getFileType() == JavaFileType.INSTANCE) {
                PsiFile psiFile = psiManager.findFile(vFile);
                if (psiFile instanceof PsiJavaFile) {
                    PsiClass[] classesInFile = ((PsiJavaFile) psiFile).getClasses();
                    for (PsiClass psiClass : classesInFile) {
                        allClasses.add(psiClass);
                    }
                }
            }
            return true; // Continue iteration
        });

        System.out.println("get all java classes");
        System.out.println("amount found: " + allClasses.size());

        for (PsiClass psiClass : allClasses) {
            System.out.println("class: " + psiClass.getQualifiedName());
            PsiReferenceList extendsList = psiClass.getExtendsList();
            if (extendsList != null) {
                for (PsiJavaCodeReferenceElement reference : extendsList.getReferenceElements()) {
                    System.out.println("-- "+reference.getQualifiedName());
                    PsiElement resolved = reference.resolve();
                    if (resolved instanceof PsiClass) {
                        System.out.println(((PsiClass) resolved).getQualifiedName());
                    }
                }
            } else {
                System.out.println("No extend list");
            }
        }

        System.out.println("finished");
    }
}
