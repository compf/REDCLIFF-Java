package com.funbiscuit.idea.plugin.formatter;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class JavaFileAnalyzer {

    private final Project project;

    public JavaFileAnalyzer(Project project) {
        this.project = project;
    }

    public void analyze() {
        System.out.println("Analyzing project: " + project.getName());

        // search for all java classes in project

        List<PsiClass> allClasses = new ArrayList<>();
        Collection<VirtualFile> virtualFiles = new ArrayList<>();

        List<VirtualFile> allFiles = new ArrayList<>();
        VirtualFile baseDir = project.getBaseDir();
        VfsUtilCore.iterateChildrenRecursively(baseDir, virtualFile -> true, virtualFile -> {
            if (!virtualFile.isDirectory() && virtualFile.getFileType() == JavaFileType.INSTANCE) {
                allFiles.add(virtualFile);
            }
            return true;
        });
        System.out.println("A: Found " + allFiles.size() + " java files");
        virtualFiles.addAll(allFiles);


        // Does not work
        //virtualFiles = com.intellij.psi.search.FileTypeIndex.getFiles(JavaFileType.INSTANCE,
        //        GlobalSearchScope.projectScope(project));
        //        System.out.println("B: Found " + virtualFiles.size() + " java files");

        for (VirtualFile virtualFile : virtualFiles) {
            PsiFile currentFile = PsiManager.getInstance(project).findFile(virtualFile);

            System.out.println("File: " + currentFile.getName());

            Collection<PsiClass> classesInFile = PsiTreeUtil.findChildrenOfType(currentFile, PsiClass.class);
            for (PsiClass c : classesInFile) {
                if (c.getQualifiedName() != null) {
                    traverseClasses(c);
                }
            }
        }

    }

    private void traverseClasses(PsiClass currentClass){
        // print the class with all important information
        System.out.println("");

        // print the class name
        System.out.println("Class: " + currentClass.getName() + " QN: " + currentClass.getQualifiedName());

        // print the superclass
        PsiClass superClass = currentClass.getSuperClass();
        if (superClass != null) {
            System.out.println("--Extends: " + superClass.getQualifiedName());
        } else {
            System.out.println("--No superclass detected or not in scope");
        }

        PsiClass[] supers = currentClass.getSupers();
        System.out.println("--Supers: " + supers.length);
        for(PsiClass superClazz : supers){
            System.out.println("--Super: " + superClazz.getQualifiedName());
        }

        // print the implemented interfaces
        PsiClass[] interfaces = currentClass.getInterfaces();

        System.out.println("--Implements: " + interfaces.length);

        System.out.println("Using Reference");

        PsiReferenceList extendsList = currentClass.getExtendsList();
        PsiReferenceList implementsList = currentClass.getImplementsList();

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

        System.out.println("");

    }
}
