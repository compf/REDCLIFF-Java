package com.funbiscuit.idea.plugin.formatter;

import com.funbiscuit.idea.plugin.formatter.report.FileInfo;
import com.intellij.lang.FileASTNode;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;

import java.util.Objects;

public class FileFormatter implements FileProcessor {

    @Override
    public void processFile(PsiFile originalFile, FileInfo fileInfo) {

        String originalContent = originalFile.getText();
        FileDocumentManager documentManager = FileDocumentManager.getInstance();
        VirtualFile virtualFile = PsiUtilCore.getVirtualFile(originalFile);
        var document = documentManager.getDocument(Objects.requireNonNull(virtualFile));
        if (!documentManager.requestWriting(Objects.requireNonNull(document), null)) {
            fileInfo.addWarning(ProcessStatuses.SKIPPED_READ_ONLY);
            return;
        }

        if(originalFile instanceof PsiJavaFile){
            PsiJavaFile psiJavaFile = (PsiJavaFile) originalFile;
            final PsiClass[] classes = psiJavaFile.getClasses();
            for(PsiClass currentClass : classes){
                traverseClasses(currentClass);
            }
        }

    }

    private void traverseClasses(PsiClass element) {
        if (element instanceof PsiClass) {
            PsiClass psiClass = (PsiClass) element;

            System.out.println("Class: " + psiClass.getName() + " QN: " + psiClass.getQualifiedName());

            // Print the superclass
            PsiClass superClass = psiClass.getSuperClass();
            if (superClass != null) {
                System.out.println("--Extends: " + superClass.getQualifiedName());
            } else {
                System.out.println("--No superclass detected or not in scope");
            }

            // Print the implemented interfaces
            PsiClass[] interfaces = psiClass.getInterfaces();
            if (interfaces.length > 0) {
                System.out.println("--Implements:");
                for (PsiClass implementedInterface : interfaces) {
                    System.out.println("---- " + implementedInterface.getQualifiedName());
                }
            } else {
                System.out.println("--Not implementing an interface");
            }

            // Get all attributes (fields) of the class
            PsiField[] fields = psiClass.getFields();
            System.out.println("--Attributes:");
            for (PsiField field : fields) {
                // Process each field
                System.out.println("---- " + field.getName());
            }

            // Get all methods of the class
            PsiMethod[] methods = psiClass.getMethods();
            System.out.println("--Methods:");
            for (PsiMethod method : methods) {
                // Process each method
                System.out.println("---- " + method.getName());
            }
        }
    }

}
