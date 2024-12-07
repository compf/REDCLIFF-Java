package dataClumpRefactoring

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPackage
import com.intellij.refactoring.MoveDestination
import com.intellij.refactoring.PackageWrapper
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap

class KeepLocationMoveDestination: MoveDestination {
    override fun getTargetDirectory(source: PsiDirectory?): PsiDirectory {
        return source!!
    }

    override fun getTargetDirectory(source: PsiFile?): PsiDirectory {
        return source!!.containingDirectory
    }

    override fun getTargetPackage(): PackageWrapper {
        TODO("Not yet implemented")
    }

    override fun getTargetIfExists(source: PsiDirectory?): PsiDirectory {
        return source!!
    }

    override fun getTargetIfExists(source: PsiFile): PsiDirectory {
       return source.containingDirectory
    }

    override fun verify(source: PsiFile?): String {
        return ""
    }

    override fun verify(source: PsiDirectory?): String {
       return ""
    }

    override fun verify(source: PsiPackage?): String {
       return ""
    }

    override fun analyzeModuleConflicts(
        elements: MutableCollection<out PsiElement>,
        conflicts: MultiMap<PsiElement, String>,
        usages: Array<out UsageInfo>?
    ) {

    }

    override fun isTargetAccessible(project: Project, place: VirtualFile): Boolean {
        return true
    }
}