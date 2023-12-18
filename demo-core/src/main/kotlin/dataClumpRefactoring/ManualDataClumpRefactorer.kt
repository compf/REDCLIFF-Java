package dataClumpRefactoring

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.*
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.changeSignature.ParameterInfoImpl
import com.intellij.refactoring.extractclass.ExtractClassProcessor
import com.intellij.refactoring.introduceParameterObject.IntroduceParameterObjectProcessor
import com.intellij.refactoring.introduceparameterobject.JavaIntroduceParameterObjectClassDescriptor
import com.intellij.refactoring.util.classMembers.MemberInfo
import org.jetbrains.research.refactoringDemoPlugin.util.extractKotlinAndJavaClasses
import  com.intellij.openapi.application.WriteAction
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.lang.Language
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import com.intellij.openapi.command.WriteCommandAction;
import java.io.File
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.resolve.calls.util.asCallableReferenceExpression
import org.jetbrains.kotlin.resolve.calls.util.isCallableReference
import kotlin.system.exitProcess
import com.intellij.refactoring.changeSignature.*;
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import java.io.BufferedReader

class ManualDataClumpRefactorer(val projectPath: File) : DataClumpRefactorer(projectPath) {
    fun createClass(
        project: Project,
        className: String,
        directory: PsiDirectory,
        packageName: String,
        relevantVariables: List<PsiVariable>
    ): PsiClass {
        var psiClass:PsiClass?=null
        try {
            commitAll(project)
            waitForIndexing(project)
             psiClass= WriteAction.compute<PsiClass, Throwable> {
                val file = directory.findFile("Point.java")
                val createdClass = JavaDirectoryService.getInstance().createClass(
                    directory, className, JavaTemplateUtil.INTERNAL_CLASS_TEMPLATE_NAME, false,
                    mapOf("PACKAGE_NAME" to packageName)
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
                    val field = JavaPsiFacade.getElementFactory(project).createField(variable.name!!, variable.type)
                    psiClass.add(field)
                    val getterName="get${variable.name!!.replaceFirstChar { it.uppercase() }}"
                    val getter = JavaPsiFacade.getElementFactory(project).createMethodFromText(
                        "public ${variable.type.canonicalText } ${getterName}(){return ${variable.name};}",
                        psiClass
                    )
                    val setterName="set${variable.name!!.replaceFirstChar { it.uppercase() }}"
                    val setter = JavaPsiFacade.getElementFactory(project).createMethodFromText(
                        "public void ${setterName}(${variable.type.canonicalText} ${variable.name}){this.${variable.name}=${variable.name};}",
                        psiClass
                    )
                    psiClass.add(getter)
                    psiClass.add(setter)

                }
                val constructor=JavaPsiFacade.getElementFactory(project).createConstructor()
                for (variable in relevantVariables) {
                    val parameter = JavaPsiFacade.getElementFactory(project).createParameter(variable.name!!, variable.type)
                    constructor.parameterList.add(parameter)
                    constructor.body?.add(JavaPsiFacade.getElementFactory(project).createStatementFromText("this.${variable.name}=${variable.name};",constructor))
                }
                psiClass.add(constructor)
                nameClassMap[className] = psiClass



            }
        }catch (e: Throwable) {
            e.printStackTrace()
            throw e
        }
        commitAll(project)
        return psiClass!!



    }

    fun findClass(className: String): PsiClass {
        return nameClassMap[className]!!
    }

    fun addExtractedClassParameter(project: Project,dataClumpMethod:PsiMethod,extractedClass: PsiClass,relevantVariables: Set<String>){
        val methodUsagesAndoVerrides=collectMethodUsages(project,dataClumpMethod)
        for(method in methodUsagesAndoVerrides.second){
            val type=JavaPsiFacade.getElementFactory(method.project).createType(extractedClass)
            val parameter = JavaPsiFacade.getElementFactory(method.project).createParameter(extractedClass.name!!.replaceFirstChar { it.lowercase() }, type)
            WriteCommandAction.runWriteCommandAction(project){
                method.parameterList.add(parameter)
            }
            //updateParameterUsages(project,method,extractedClass,parameter,relevantVariables)
            updateMethodUsages(project,method,extractedClass,relevantVariables)
        }







    }
    fun updateVariableUsage(project: Project,extractedClass: PsiClass,identifier:PsiIdentifier,nameService:IdentifierNameService){
            val getterName=nameService.getGetterName(extractedClass,identifier.text!!)
            val method=identifier.getParentOfType<PsiMethod>(true)
        if(method==null){
            return;
        }
            val currentClass=identifier.getParentOfType<PsiClass>(true)
            val objectName =if(identifier is  PsiField) nameService.getFieldName(extractedClass,currentClass) else nameService.getParameterName(extractedClass,method)
            val getterCall=JavaPsiFacade.getElementFactory(method.project).createExpressionFromText("${objectName}.${getterName}()",method)


                if(identifier.parent is PsiAssignmentExpression && (identifier.parent as PsiAssignmentExpression).lExpression==identifier) {
                    WriteCommandAction.runWriteCommandAction(project) {
                        var setterCall = JavaPsiFacade.getElementFactory(method.project).createExpressionFromText(
                            "${objectName}.${nameService.getSetterName(extractedClass,identifier.text!!)}(${(identifier.parent as PsiAssignmentExpression).rExpression!!.text})",
                            method
                        )
                        identifier.parent.replace(setterCall)
                        //(element.parent as PsiAssignmentExpression).rExpression?.replace(getterCall)
                    }
                }
                else  {
                    val parent = identifier.parent
                    WriteCommandAction.runWriteCommandAction(project){
                        identifier.replace(getterCall)
                    }

                    print(parent)
                }
                commitAll(project)



    }
    fun updateElementFromUsageInfo(project: Project,usageInfo: UsageInfo,element:PsiElement,nameService:IdentifierNameService) {
        val symbolType=UsageType.values()[usageInfo.symbolType]
        val man = VirtualFileManager.getInstance()
        if(usageInfo.extractedClassPath==null) return
        val extractedClassFile= PsiManager.getInstance(project).findFile(man.findFileByUrl(usageInfo.extractedClassPath)!!)!!
        val extractedClass=extractedClassFile.childrenOfType<PsiClass>().first()
        when(symbolType){
            UsageType.VariableUsed->{
                updateVariableUsage(project,extractedClass,element as PsiIdentifier,PrimitiveNameService())
            }
            else->{}
        }
    }
    fun getElement(project:Project,usageInfo: UsageInfo):PsiElement{
        val bufferedReader: BufferedReader = File(usageInfo.filePath.substring("file://".length)).bufferedReader()
        val fileContent = bufferedReader.use { it.readText() }
        val offset=this.calculateOffset(fileContent,usageInfo.range.startLine,usageInfo.range.startColumn)
        val man = VirtualFileManager.getInstance()
        val vFile = man.findFileByUrl(usageInfo.filePath)!!
        val dataClumpFile = PsiManager.getInstance(project).findFile(vFile)!!


        val element=dataClumpFile.findElementAt(offset)
        println(usageInfo.name)
        println(usageInfo.range.startLine.toString() + " " + usageInfo.range.startColumn.toString())
        print(element)
        println()
        return element!!







    }
    enum class UsageType{VariableUsed,VariableDeclared,MethodUSed,MethodDeclared}

    fun collectMethodUsages(project:Project,method: PsiMethod):Pair<Iterable<PsiReference>,Iterable<PsiMethod>>{
        waitForIndexing(project)

        val methodUsages = ReferencesSearch.search(method,GlobalSearchScope.allScope(method.project),true).findAll()
        val overrides=OverridingMethodsSearch.search(method,GlobalSearchScope.allScope(method.project),true).findAll()

        return Pair(methodUsages,overrides)


    }
    fun updateMethodUsages(project:Project,method: PsiMethod,extractedClass: PsiClass,relevantVariables: Set<String>):Unit{



    }
    override fun refactorDataClumpEndpoint(
        dataClumpType: String,
        project: Project,
        suggestedClassName: String,
        classProbablyExisting: Boolean,
        ep: DataClumpEndpoint,
        relevantParameters: Set<String>
    ) {
        val man = VirtualFileManager.getInstance()
        val vFile = man.findFileByUrl(ep.filePath)!!
        val dataClumpFile = PsiManager.getInstance(project).findFile(vFile)!!
        val dataClumpClass = dataClumpFile.findDescendantOfType<PsiClass> { it.name == ep.className }!!
        val packageName = getPackageName(dataClumpFile)
        if (ep.dataClumpType == "parameters") {
            val methodData = this.getMethodAndParamsToRefactor(dataClumpClass, ep.methodName!!, relevantParameters)

            val extractedClass = if (classProbablyExisting) findClass(suggestedClassName) else createClass(
                project,
                suggestedClassName,
                dataClumpFile.parent!!,
                packageName,
               methodData._3)

            addExtractedClassParameter(project,methodData._1,extractedClass,relevantParameters)

        } else {

        }

    }
}
