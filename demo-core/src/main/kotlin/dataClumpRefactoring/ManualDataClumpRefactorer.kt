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
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import com.intellij.openapi.command.WriteCommandAction;
import java.io.File

class ManualDataClumpRefactorer(projectPath: File) : DataClumpRefactorer(projectPath) {
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
    fun addExtractedClassParameter(project: Project,method:PsiMethod,extractedClass: PsiClass,relevantVariables: List<PsiVariable>){
        val type=JavaPsiFacade.getElementFactory(method.project).createType(extractedClass)
        val parameter = JavaPsiFacade.getElementFactory(method.project).createParameter(extractedClass.name!!.replaceFirstChar { it.lowercase() }, type)
        WriteCommandAction.runWriteCommandAction(project){
            method.parameterList.add(parameter)
        }

        updateParameterUsages(project,method,extractedClass,parameter,relevantVariables)
    }
    fun updateParameterUsages(project: Project,method:PsiMethod,extractedClass: PsiClass,parameter:PsiParameter,relevantParameters: List<PsiVariable>){
        for(param in relevantParameters){
            val getterCall=JavaPsiFacade.getElementFactory(method.project).createExpressionFromText("${parameter.name}.get${param.name!!.replaceFirstChar { it.uppercase() }}()",method)
            val usages = ReferencesSearch.search(param,method.resolveScope).findAll()
            for (usage in usages) {
                val element = usage.element
                if(element.parent is PsiAssignmentExpression && (element.parent as PsiAssignmentExpression).lExpression==element) {
                    WriteCommandAction.runWriteCommandAction(project) {
                        var setterCall = JavaPsiFacade.getElementFactory(method.project).createExpressionFromText(
                            "${parameter.name}.set${param.name!!.replaceFirstChar { it.uppercase() }}(${(element.parent as PsiAssignmentExpression).rExpression!!.text})",
                            method
                        )
                        element.parent.replace(setterCall)
                        (element.parent as PsiAssignmentExpression).rExpression?.replace(getterCall)
                    }
                }
                else  {
                    val parent = element.parent
                    WriteCommandAction.runWriteCommandAction(project){
                        usage.element.replace(getterCall)
                    }

                    print(parent)
                }
                commitAll(project)
            }
        }

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
            addExtractedClassParameter(project,methodData._1,extractedClass,methodData._3)

        } else {

        }

    }
}
