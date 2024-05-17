package dataClumpRefactoring

import com.intellij.ide.fileTemplates.JavaTemplateUtil
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.kotlin.idea.core.script.ucache.relativeName
import java.io.File
import java.nio.file.Path


abstract class ClassCreator(dcKeyClassPathMap:Map<String,String>?) {
    val dataClumpKeyClassPathMap= mutableMapOf<String,String>()
    val dataClumpItemsClassPathMap= mutableMapOf<String,String>()
    init {
        if(dcKeyClassPathMap!=null){
            dataClumpKeyClassPathMap.putAll(dcKeyClassPathMap)
        }
    }
    fun createDataClumpItemKey(variables: List<PsiVariable>):String{
        return variables.sortedBy { it.name }.joinToString(";\n") {  it.type.canonicalText+" "+it.name }
    }
    fun loadClass(project: Project,path: String):PsiClass? {
        val man = VirtualFileManager.getInstance()
        val vFile = man.refreshAndFindFileByUrl(path!!)
        val file = PsiManager.getInstance(project).findFile(vFile!!)!!


        val extractedClass =
            (file as PsiClassOwner)
                .classes
                .first()
        return  extractedClass
    }
    fun getOrCreateClass(project: Project,
                         className: String,
                         dataClumpKey:String,
                         dataClumpFile: PsiFile,
                         relevantVariables: List<PsiVariable>,
                         nameService: IdentifierNameService):PsiClass{
        val itemKey=createDataClumpItemKey(relevantVariables)
        if(dataClumpKeyClassPathMap.containsKey(dataClumpKey)){

            return loadClass(project,dataClumpKeyClassPathMap[dataClumpKey]!!)!!
        }
        else if(dataClumpItemsClassPathMap.containsKey(itemKey)) {
            return loadClass(project,dataClumpItemsClassPathMap[itemKey]!!)!!
        }

        else{
            val path=createClass(project,className,dataClumpFile,relevantVariables,nameService)
            ProjectUtils.commitAll(project)
            ProjectUtils.waitForIndexing(project)
            dataClumpKeyClassPathMap[dataClumpKey]= path
            dataClumpItemsClassPathMap[itemKey]= path
            return loadClass(project,path)!!

        }



    }
    abstract fun createClass(project: Project,
                             className: String,
                             dataClumpFile: PsiFile,
                             relevantVariables: List<PsiVariable>,
                             nameService: IdentifierNameService):String
}
class PsiClassCreator(paramNameClassMap :Map<String,String>? ): ClassCreator(paramNameClassMap){
    override fun createClass(project: Project,
                             className: String,
                             dataClumpFile: PsiFile,
                             relevantVariables: List<PsiVariable>,
                             nameService: IdentifierNameService) : String {
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
           return  WriteCommandAction.runWriteCommandAction<String>(project) {
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
               ProjectUtils.commitAll(project)
                ProjectUtils.waitForIndexing(project)
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
                return@runWriteCommandAction extractedClass.containingFile.virtualFile.url


            }
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
        }

        /*ProjectUtils.waitForIndexing(project)
        ProjectUtils.commitAll(project)
        val session= RefreshQueue.getInstance().createSession(false,true){

        }
        session.launch()*/
    }
    }



class ManualJavaClassCreator(paramNameClassMap :Map<String,String>? ) : ClassCreator(paramNameClassMap) {

    fun replaceFileName(originalPath: String, newFileName: String): String {
        val originalFile = File(originalPath)
        val parentDir = originalFile.parent
        return File(parentDir, newFileName).path
    }
    fun nop(){}
    private  fun getTypeText(variable: PsiVariable, isParameter:Boolean, isLast:Boolean):String{
        val type=PsiTypesUtil.getPsiClass(variable.type)
        //val javaSdk = JavaSdk.getInstance()
        //java.nio.file.Files.writeString(Path.of("/home/compf/data/log_types"),"${variable.type.canonicalText} % ${variable.type.javaClass} ${type?.qualifiedName}\n",java.nio.file.StandardOpenOption.APPEND)
        var text= if(type!=null) type.qualifiedName!! else variable.type.canonicalText!!
        if(!isParameter){
            text=text.replace("...","[]")
        }
        if(isParameter && isLast){
            text=text.replace("[]","...")
        }
        return text
    }
    override fun createClass(
        project: Project,
        className: String,
        dataClumpFile: PsiFile,
        relevantVariables: List<PsiVariable>,
        nameService: IdentifierNameService
    ):String {
        if(relevantVariables.none()) {
            nop()
        }
        val fourWhitespce="    "
        val packageName =(dataClumpFile as PsiJavaFile).packageName
        var text="package ${packageName};\n"
        text+="public class ${className}{\n"
        for (variable in relevantVariables) {
            val type=PsiTypesUtil.getPsiClass(variable.type)
            println("extractled class "+ variable.name + " "+variable.type.canonicalText)
            text+="\tprivate ${getTypeText(variable,false,false)} ${variable.name};\n\n"
            val getterName = nameService.getGetterName(variable.name!!)
            text+="\tpublic ${getTypeText(variable,false,false)} ${getterName}(){\n\t\treturn ${variable.name};\n\t}\n\n"
            val setterName = nameService.getSetterName(variable.name!!)
            text+="\tpublic void ${setterName}(${getTypeText(variable,true,false)} ${variable.name}){\n\t\tthis.${variable.name}=${variable.name};\n\t}\n\n"
        }
        // constructor
        text+="\tpublic ${className}("
       text+= relevantVariables.joinToString(",") { "${getTypeText(it,true,relevantVariables.indexOf(it)==relevantVariables.size-1)} ${it.name}" }
        text+="){\n"
        for (variable in relevantVariables) {
            text+="\t\tthis.${variable.name}=${variable.name};\n"
        }
        text+="\t}\n"
        text+="}\n\n"
        text=text.replace("\t",fourWhitespce)
       val newPath=replaceFileName(dataClumpFile.containingFile.virtualFile.path,className+".java")
        java.nio.file.Files.writeString(Path.of(newPath),text)
      return  "file://"+newPath

    }
}




