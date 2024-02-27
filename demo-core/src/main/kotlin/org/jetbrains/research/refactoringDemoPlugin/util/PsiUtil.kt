package org.jetbrains.research.refactoringDemoPlugin.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.hierarchy.overrides.isOverrideHierarchyElement
import org.jetbrains.kotlin.psi.KtFile
fun  Project.getAllRelevantVariables(minSize:Int):ArrayList<List<PsiVariable>>{
    val result=ArrayList<List<PsiVariable>>()
    val classes=extractKotlinAndJavaClasses()
    for(cls in classes){
        if(cls.fields.size>=minSize){
            result.add( cls.fields.toList())
        }

        for(method in cls.methods){
            if(method.parameterList.parameters.size>=minSize){
                result.add( method.parameterList.parameters.toList())
            }


        }
    }
    return result
}
/*
    Extracts all Kotlin and Java classes from the project.
 */
fun Project.extractKotlinAndJavaClasses(): List<PsiClass> =
    this.extractPsiFiles { it.extension == "java" || it.extension == "kt" }.mapNotNull { file ->
        when (file) {
            is PsiJavaFile -> file.classes.toList()
            is KtFile -> file.classes.toList()
            else -> null
        }
    }.flatten()

fun Project.extractPsiFiles(filePredicate: (VirtualFile) -> Boolean): MutableSet<PsiFile> {
    val projectPsiFiles = mutableSetOf<PsiFile>()
    val projectRootManager = ProjectRootManager.getInstance(this)
    val psiManager = PsiManager.getInstance(this)
    var root=projectRootManager.contentRoots
    if(root.isEmpty()){
        root= arrayOf(this.projectFile)
    }
    root.mapNotNull { myRoot ->
        VfsUtilCore.iterateChildrenRecursively(myRoot, null) { virtualFile ->
            if (!filePredicate(virtualFile) || virtualFile.canonicalPath == null) {
                return@iterateChildrenRecursively true
            }
            val psi = psiManager.findFile(virtualFile) ?: return@iterateChildrenRecursively true
            projectPsiFiles.add(psi)
        }
    }
    return projectPsiFiles
}

fun Project.extractModules(): List<Module> {
    return ModuleManager.getInstance(this).modules.toList()
}

/** Finds [PsiFile] in module by given file extension. */
fun Module.findPsiFilesByExtension(extension: String): List<PsiFile> {
    val psiManager = PsiManager.getInstance(project)
    return FilenameIndex.getAllFilesByExt(project, extension, moduleContentScope)
        .mapNotNull { psiManager.findFile(it) }
        .toList()
}

fun <T : PsiElement> PsiElement.extractElementsOfType(psiElementClass: Class<T>): MutableCollection<T> =
    PsiTreeUtil.collectElementsOfType(this, psiElementClass)
