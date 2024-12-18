package dataClumpRefactoring

import com.intellij.ide.fileTemplates.JavaTemplateUtil
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import java.io.File
import java.nio.file.Path

/**
 * Creates a class if it does not exist
 */
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
        val vFile = man.refreshAndFindFileByUrl(path)
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
    /**
     * creates a non-existing class
     * @param project the project
     * @param className the name of the class
     * @param dataClumpFile the file containing the data clump
     * @param relevantVariables the variables that are part of the data clump
     * @param nameService the name service
     * @return the path of the created class
     */
    abstract fun createClass(project: Project,
                             className: String,
                             dataClumpFile: PsiFile,
                             relevantVariables: List<PsiVariable>,
                             nameService: IdentifierNameService):String
   open fun createNameService():IdentifierNameService{
        return PrimitiveNameService(StubNameValidityChecker())
    }
}

/**
 * Use the PSI to create a class
 */
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


    }
    }


enum class MemberType{Fields,Getter,Setter,Constructor}
enum class  ByMemberTypeOrVariableOrder{
    /**First write all operations of a member, then the next (e.g. all getters first) */
    MemberTypeOrder,
    /** First write all operation for variable (e.g. getX, setX), then do this for the next variable */
    VariableOrder
}
val defaultMemberOrder= arrayOf(
    MemberType.Fields,
    MemberType.Getter,
    MemberType.Setter,
    MemberType.Constructor

)

/**
 * helps to iterate over the members of a class
 * e.g. fields, getters, setters, constructors
 */
 class MemberIterator(val variables: List<PsiVariable>, val memberOrder: Array<MemberType>, val order:ByMemberTypeOrVariableOrder) : Iterator<Pair<PsiVariable,MemberType>> {
     var index1=0
     var index2=0
     val index1Max=if(order==ByMemberTypeOrVariableOrder.MemberTypeOrder) memberOrder.size else variables.size
     val index2Max=if(order==ByMemberTypeOrVariableOrder.MemberTypeOrder) variables.size else memberOrder.size
     override fun hasNext(): Boolean {

         return index1<index1Max && index2<index2Max
     }
     fun handleConstructor(){
         if(order==ByMemberTypeOrVariableOrder.MemberTypeOrder){
             index1++
         }
         else{
             index2++
         }
     }

     override fun next(): Pair<PsiVariable, MemberType> {
         val memberIndex = if (order == ByMemberTypeOrVariableOrder.MemberTypeOrder) index1 else index2
         val variableIndex = if (order == ByMemberTypeOrVariableOrder.MemberTypeOrder) index2 else index1
         val pair = Pair(variables[variableIndex], memberOrder[memberIndex])
         if(memberOrder[memberIndex] == MemberType.Constructor) {
             handleConstructor()
         }
         else{
             index2++
             if (index2 >= index2Max) {
                 index2 = 0
                 index1++
             }
         }

            return pair
     }




 }
private  fun getTypeText(variable: PsiVariable, typePosKindInfo: ManualJavaClassCreator.TypePositionAndKind):String{
    val type=PsiTypesUtil.getPsiClass(variable.type)
    var text= if(type!=null) type.qualifiedName!! else variable.type.canonicalText
    if(typePosKindInfo== ManualJavaClassCreator.TypePositionAndKind.NoParameter){
        text=text.replace("...","[]")
    }
    if(typePosKindInfo== ManualJavaClassCreator.TypePositionAndKind.ParameterLast){
        text=text.replace("[]","...")
    }
    return text
}
fun replaceFileName(originalPath: String, newFileName: String): String {
    val originalFile = File(originalPath)
    val parentDir = originalFile.parent
    return File(parentDir, newFileName).path
}
fun getHeader(dataClumpFile: PsiFile):String{
    val result= mutableListOf<String>()
   val lines= java.nio.file.Files.readString(Path.of(dataClumpFile.virtualFile.path)).split("\n").filter { it.trim()!="" }
    if(!lines[0].startsWith("/*")){
        return ""
    }
    else{
        val lastIndex=lines.indexOfFirst { it.contains("*/") }
        if(lastIndex==-1){
            return ""
        }
        for(i in 0..lastIndex){
            result.add(lines[i])
        }
        return result.joinToString("\n")
    }

}

/**
 * Manually creates a class using String concatenation
 */
class ManualJavaClassCreator(paramNameClassMap :Map<String,String>?, val memberOrder:Array<MemberType> =defaultMemberOrder ) : ClassCreator(paramNameClassMap) {

    fun nop(){}

    enum class TypePositionAndKind{
        ParameterNotLast,ParameterLast,NoParameter
    }
    fun createByType(variable: PsiVariable, memberType: MemberType, nameService: IdentifierNameService, allVariables: List<PsiVariable>, className: String):String{
        return when(memberType){
            MemberType.Fields->createField(variable)
            MemberType.Getter->createGetter(variable,nameService)
            MemberType.Setter->createSetter(variable,nameService)
            MemberType.Constructor->createConstructor(allVariables, className)
        }
    }
    fun createField(variable: PsiVariable):String{
        return "\tprivate ${variable.type.canonicalText} ${variable.name};\n"
    }
    fun createGetter(variable: PsiVariable, nameService: IdentifierNameService):String{
        val getterName = nameService.getGetterName(variable.name!!)
        val typeText=getTypeText(variable,TypePositionAndKind.NoParameter)
        return "\tpublic $typeText ${getterName}() {\n\t\treturn ${variable.name};\n\t}\n"
    }
    fun createSetter(variable: PsiVariable, nameService: IdentifierNameService):String{
        val setterName = nameService.getSetterName(variable.name!!)
        val typeText=getTypeText(variable,TypePositionAndKind.ParameterLast)
        return "\tpublic void ${setterName}($typeText ${variable.name}) {\n\t\tthis.${variable.name}=${variable.name};\n\t}\n"
    }
    fun createConstructor(relevantVariables: List<PsiVariable>, className: String):String{
        var text="\tpublic $className\t("
        text+= relevantVariables.joinToString(",") {
            val typePosKindInfo=if(it==relevantVariables.last()) TypePositionAndKind.ParameterLast else TypePositionAndKind.ParameterNotLast
             getTypeText(it,typePosKindInfo)+" "+it.name
        }
        text+="){\n"
        for (variable in relevantVariables) {
            text+="\t\tthis.${variable.name}\t=\t${variable.name};\n"
        }
        text+="}"
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
        text+="public class ${className} {\n"
        val iterator=MemberIterator(relevantVariables,if(memberOrder.isEmpty()) defaultMemberOrder else memberOrder,ByMemberTypeOrVariableOrder.MemberTypeOrder)
        while(iterator.hasNext()){
            val (variable,memberType)=iterator.next()
            text+="\n"+createByType(variable,memberType,nameService,relevantVariables,className)
        }
        text+="\n}\n"
        text=text.replace("\t",fourWhitespce)
       val newPath=replaceFileName(dataClumpFile.containingFile.virtualFile.path,className+".java")
        java.nio.file.Files.writeString(Path.of(newPath), getHeader(dataClumpFile)+"\n"+ text)
      return  "file://"+newPath

    }
}

/**
 * Creates a record instead of a class
 */
class RecordCreator(dcKeyClassPathMap: Map<String, String>?) : ClassCreator(dcKeyClassPathMap){
    override fun createClass(project: Project,
                             className: String,
                             dataClumpFile: PsiFile,
                             relevantVariables: List<PsiVariable>,
                             nameService: IdentifierNameService):String {
        val packageName =(dataClumpFile as PsiJavaFile).packageName
        var text="package ${packageName};\n"
        text+="public record ${className}(\n"
        text+= relevantVariables.joinToString(",\n") {
            "${getTypeText(it,ManualJavaClassCreator.TypePositionAndKind.NoParameter)} ${it.name}"
        }
        text+="){\n"

        text+="}\n"
        val newPath=replaceFileName(dataClumpFile.containingFile.virtualFile.path,className+".java")
        java.nio.file.Files.writeString(Path.of(newPath), getHeader(dataClumpFile)+"\n"+ text)
        return  "file://"+newPath
    }
    override fun createNameService():IdentifierNameService{
        return  RecordPrimitiveNameService(StubNameValidityChecker())
    }
}




