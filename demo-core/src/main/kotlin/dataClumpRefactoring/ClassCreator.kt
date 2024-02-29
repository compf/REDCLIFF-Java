package dataClumpRefactoring

import com.intellij.ide.fileTemplates.JavaTemplateUtil
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.psi.*
import java.io.File
import java.nio.file.Path

abstract class ClassCreator(paramNameClassMap:Map<String,String>?) {
    val nameClassPathMap= mutableMapOf<String,String>()
    init {
        if(paramNameClassMap!=null){
            nameClassPathMap.putAll(paramNameClassMap)
        }
    }
    fun getOrCreateClass(project: Project,
                         className: String,
                         dataClumpFile: PsiFile,
                         relevantVariables: List<Pair<String,String>>,
                         nameService: IdentifierNameService):PsiClass{
        if(!nameClassPathMap.containsKey(className)){
          createClass(project,className,dataClumpFile,relevantVariables,nameService)
            ProjectUtils.commitAll(project)
            ProjectUtils.waitForIndexing(project)
        }
        val man = VirtualFileManager.getInstance()
        val path= nameClassPathMap[className]
        val vFile = man.refreshAndFindFileByUrl(path!!)
        val file = PsiManager.getInstance(project).findFile(vFile!!)!!


        val extractedClass =
            (file as PsiClassOwner)
                .classes
                .filter { it.name == className }
                .first()
        return  extractedClass

    }
    abstract fun createClass(project: Project,
                             className: String,
                             dataClumpFile: PsiFile,
                             relevantVariables: List<Pair<String,String>>,
                             nameService: IdentifierNameService)
}
class PsiClassCreator(paramNameClassMap :Map<String,String>? ): ClassCreator(paramNameClassMap){
    override fun createClass(project: Project,
                             className: String,
                             dataClumpFile: PsiFile,
                             relevantVariables: List<Pair<String,String>>,
                             nameService: IdentifierNameService) {
        var extractedClass: PsiClass? = null
        try {
            ProjectUtils.commitAll(project)
            ProjectUtils.waitForIndexing(project)
            extractedClass = WriteAction.compute<PsiClass, Throwable> {
                val packageName =(dataClumpFile as PsiJavaFile).packageName
                val createdClass = JavaDirectoryService.getInstance().createClass(
                    dataClumpFile.containingDirectory, className,
                    JavaTemplateUtil.INTERNAL_CLASS_TEMPLATE_NAME, false,
                    mapOf("PACKAGE_NAME" to packageName),
                )
                return@compute createdClass
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
        }
        try {
            WriteCommandAction.runWriteCommandAction(project) {
                for (variable in relevantVariables) {
                    val type= JavaPsiFacade.getElementFactory(project).createTypeFromText(variable.second,extractedClass)
                    val field = JavaPsiFacade.getElementFactory(project).createField(variable.first!!, type)
                    extractedClass.add(field)
                    val getterName = nameService.getGetterName(variable.first!!)
                    val getter = JavaPsiFacade.getElementFactory(project).createMethodFromText(
                        "public ${variable.second} ${getterName}(){return ${variable.first};}",
                        extractedClass
                    )
                    val setterName =nameService.getSetterName(variable.first!!)
                    val setter = JavaPsiFacade.getElementFactory(project).createMethodFromText(
                        "public void ${setterName}(${variable.second} ${variable.first}){this.${variable.first}=${variable.first};}",
                        extractedClass
                    )
                    extractedClass.add(getter)
                    extractedClass.add(setter)

                }
                val constructor = JavaPsiFacade.getElementFactory(project).createConstructor()
                for (variable in relevantVariables) {
                    val type= JavaPsiFacade.getElementFactory(project).createTypeFromText(variable.second,extractedClass)

                    val parameter =
                        JavaPsiFacade.getElementFactory(project).createParameter(variable.first!!, type)
                    constructor.parameterList.add(parameter)
                    constructor.body?.add(
                        JavaPsiFacade.getElementFactory(project)
                            .createStatementFromText("this.${variable.first}=${variable.first};", constructor)
                    )
                }
                extractedClass.add(constructor)
                nameClassPathMap[className] = extractedClass.containingFile.virtualFile.url


            }
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
        }

        ProjectUtils.waitForIndexing(project)
        ProjectUtils.commitAll(project)
        val session= RefreshQueue.getInstance().createSession(false,true){

        }
        session.launch()
    }
    }



class ManualJavaClassCreator(paramNameClassMap :Map<String,String>? ) : ClassCreator(paramNameClassMap) {

    fun replaceFileName(originalPath: String, newFileName: String): String {
        val originalFile = File(originalPath)
        val parentDir = originalFile.parent
        return File(parentDir, newFileName).path
    }

    override fun createClass(
        project: Project,
        className: String,
        dataClumpFile: PsiFile,
        relevantVariables: List<Pair<String, String>>,
        nameService: IdentifierNameService
    ) {
        val packageName =(dataClumpFile as PsiJavaFile).packageName
        var text="package ${packageName};\n"
        text+="public class ${className}{\n"
        for (variable in relevantVariables) {
            text+="private ${variable.second} ${variable.first};\n"
            val getterName = nameService.getGetterName(variable.first)
            text+="public ${variable.second} ${getterName}(){return ${variable.first};}\n"
            val setterName = nameService.getSetterName(variable.first)
            text+="public void ${setterName}(${variable.second} ${variable.first}){this.${variable.first}=${variable.first};}\n"
        }
        // constructor
        text+="public ${className}("
       text+= relevantVariables.joinToString(",") { "${it.second} ${it.first}" }
        text+="){\n"
        for (variable in relevantVariables) {
            text+="this.${variable.first}=${variable.first};\n"
        }
        text+="}\n"
        text+="}\n"
       val newPath=replaceFileName(dataClumpFile.containingFile.virtualFile.path,className+".java")
        java.nio.file.Files.writeString(Path.of(newPath),text)
        nameClassPathMap[className] = "file://"+newPath

    }
}




