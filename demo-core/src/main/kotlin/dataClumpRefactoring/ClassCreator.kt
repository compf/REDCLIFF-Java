package dataClumpRefactoring

import com.intellij.ide.fileTemplates.JavaTemplateUtil
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
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
                         relevantVariables: List<PsiVariable>,
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
                             relevantVariables: List<PsiVariable>,
                             nameService: IdentifierNameService)
}
class PsiClassCreator(paramNameClassMap :Map<String,String>? ): ClassCreator(paramNameClassMap){
    override fun createClass(project: Project,
                             className: String,
                             dataClumpFile: PsiFile,
                             relevantVariables: List<PsiVariable>,
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
                    val type=variable.type
                    val field = JavaPsiFacade.getElementFactory(project).createField(variable.name!!, type)
                    extractedClass.add(field)
                    val getterName = nameService.getGetterName(variable.name!!)
                    val getter = JavaPsiFacade.getElementFactory(project).createMethodFromText(
                        "public ${variable.type.canonicalText} ${getterName}(){return ${variable.name};}",
                        extractedClass
                    )
                    val setterName =nameService.getSetterName(variable.name!!)
                    val setter = JavaPsiFacade.getElementFactory(project).createMethodFromText(
                        "public void ${setterName}(${variable.type.canonicalText} ${variable.name}){this.${variable.name}=${variable.name};}",
                        extractedClass
                    )
                    extractedClass.add(getter)
                    extractedClass.add(setter)

                }
                val constructor = JavaPsiFacade.getElementFactory(project).createConstructor()
                for (variable in relevantVariables) {
                    val type=variable.type

                    val parameter =
                        JavaPsiFacade.getElementFactory(project).createParameter(variable.name!!, type)
                    constructor.parameterList.add(parameter)
                    constructor.body?.add(
                        JavaPsiFacade.getElementFactory(project)
                            .createStatementFromText("this.${variable.name}=${variable.name};", constructor)
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
    fun nop(){}

    override fun createClass(
        project: Project,
        className: String,
        dataClumpFile: PsiFile,
        relevantVariables: List<PsiVariable>,
        nameService: IdentifierNameService
    ) {
        if(relevantVariables.none()) {
            nop()
        }
        val fourWhitespce="    "
        val packageName =(dataClumpFile as PsiJavaFile).packageName
        var text="package ${packageName};\n"
        text+="public class ${className}{\n"
        for (variable in relevantVariables) {
            val type=PsiTypesUtil.getPsiClass(variable.type)
            val typeText=if(type!=null) type.qualifiedName else variable.type.canonicalText
            println("extractled class "+ variable.name + " "+variable.type.canonicalText)
            text+="\tprivate $typeText ${variable.name};\n\n"
            val getterName = nameService.getGetterName(variable.name!!)
            text+="\tpublic $typeText ${getterName}(){\n\t\treturn ${variable.name};\n\t}\n\n"
            val setterName = nameService.getSetterName(variable.name!!)
            text+="\tpublic void ${setterName}(${typeText} ${variable.name}){\n\t\tthis.${variable.name}=${variable.name};\n\t}\n\n"
        }
        // constructor
        text+="\tpublic ${className}("
       text+= relevantVariables.joinToString(",") { "${it.type.canonicalText} ${it.name}" }
        text+="){\n"
        for (variable in relevantVariables) {
            text+="\t\tthis.${variable.name}=${variable.name};\n"
        }
        text+="\t}\n"
        text+="}\n\n"
        text=text.replace("\t",fourWhitespce)
       val newPath=replaceFileName(dataClumpFile.containingFile.virtualFile.path,className+".java")
        java.nio.file.Files.writeString(Path.of(newPath),text)
        nameClassPathMap[className] = "file://"+newPath

    }
}




