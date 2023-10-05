package com.funbiscuit.idea.plugin.formatter;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.*;

public class JavaFileAnalyzer {

    private final Project project;

    public JavaFileAnalyzer(Project project) {
        this.project = project;
    }

    public void analyze() {
        VirtualFile baseDir = project.getBaseDir();
        VfsUtilCore.iterateChildrenRecursively(baseDir, virtualFile -> true, virtualFile -> {
            if (!virtualFile.isDirectory() && virtualFile.getFileType() == JavaFileType.INSTANCE) {
                processJavaFile(virtualFile);
            }
            return true;
        });
    }

    private void processJavaFile(VirtualFile file) {
        PsiManager psiManager = PsiManager.getInstance(project);
        PsiFile psiFile = psiManager.findFile(file);
        if (psiFile instanceof PsiJavaFile) {
            PsiJavaFile javaFile = (PsiJavaFile) psiFile;
            for (PsiClass psiClass : javaFile.getClasses()) {
                String className = psiClass.getQualifiedName();
                PsiReferenceList extendsList = psiClass.getExtendsList();
                PsiReferenceList implementsList = psiClass.getImplementsList();

                System.out.println("Class: " + className);
                if (extendsList != null) {
                    for (PsiJavaCodeReferenceElement reference : extendsList.getReferenceElements()) {
                        System.out.println("  Extends: " + reference.getQualifiedName());
                    }
                }
                if (implementsList != null) {
                    for (PsiJavaCodeReferenceElement reference : implementsList.getReferenceElements()) {
                        System.out.println("  Implements: " + reference.getQualifiedName());
                    }
                }
            }
        }
    }
}
