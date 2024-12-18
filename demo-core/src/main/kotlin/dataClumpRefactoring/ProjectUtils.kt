package dataClumpRefactoring

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager

/**
 * Util methods for committing changes in the project
 */
class ProjectUtils {
    companion object{
        fun commit(project: Project, dir: VirtualFile){
            VfsUtil.markDirtyAndRefresh(true, true, true, dir)
            commitAll(project)
        }
        fun commit(project: Project, uri:String){
            val man = VirtualFileManager.getInstance()
            val dir = man.findFileByUrl(uri)!!.parent
            commit(project,dir)
        }
        fun commitAll(project: Project){
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            FileDocumentManager.getInstance().saveAllDocuments()
        }
        fun waitForIndexing(project: Project) {
            ApplicationManager.getApplication().invokeAndWait {
                while (DumbService.getInstance(project).isDumb) {
                    try {
                        Thread.sleep(100)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}