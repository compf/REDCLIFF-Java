package dataClumpRefactoring

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiManager
import java.io.File

class NoRefactoringRunner(projectPath: File):DataClumpRefactorer(projectPath){
    override fun refactorDataClumpEndpoint(
        dataClumpType: String,
        project: Project,
        suggestedClassName: String,
        classProbablyExisting: Boolean,
        ep: DataClumpEndpoint,
        relevantParameters: Set<String>
    ): Boolean {
        val man = VirtualFileManager.getInstance()
        val vFile = man.findFileByUrl(ep.filePath)!!
        vFile.refresh(false, true)
        val nameService=PrimitiveNameService(StubNameValidityChecker())
        val dataClumpFile = PsiManager.getInstance(project).findFile(vFile)!!
        val dataClumpClass =findClassRec((dataClumpFile as PsiClassOwner).classes,ep.className)
        println(ep.filePath +" " + ep.className +" "+ep.methodName +" "+ep.position.startLine+" "+ep.position.startColumn +" "+ep.dataClumpType)
        val variables=if(ep.dataClumpType==DATA_CLUMP_TYPE_PARAMETERS) getMethodAndParamsToRefactor(dataClumpClass ,ep.methodName!!,relevantParameters,calculateOffset(dataClumpFile.text,ep.position.startLine,ep.position.startColumn))?._3 else  getFieldsToRefactor(dataClumpClass,relevantParameters)
        if(variables==null){
            return false
        }
        return true
    }
}
