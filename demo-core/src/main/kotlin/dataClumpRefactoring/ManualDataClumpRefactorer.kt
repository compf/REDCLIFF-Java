package dataClumpRefactoring

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.PsiManager
import  com.intellij.openapi.application.WriteAction
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.openapi.command.WriteCommandAction;
import java.io.File
import com.intellij.psi.util.childrenOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.uast.java.JavaConstructorUCallExpression
import java.io.BufferedReader
import java.nio.file.Path

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

    fun updateMethodSignature(project: Project,method:PsiMethod,extractedClass: PsiClass,relevantParameterNames: Array<String>,nameService: IdentifierNameService){
        if(method.parameterList.parameters.none{it.name in relevantParameterNames}){
            return;
        }

            val type=JavaPsiFacade.getElementFactory(method.project).createType(extractedClass)
            val parameter = JavaPsiFacade.getElementFactory(method.project).createParameter(nameService.getParameterName(extractedClass,method), type)
            WriteCommandAction.runWriteCommandAction(project){
                method.parameterList.add(parameter)
        }
        for(paramName in relevantParameterNames){
          val param=method.parameterList.parameters.find { it.name==paramName }
            if(param!=null){
                WriteCommandAction.runWriteCommandAction(project){
                    param.delete()
                }
            }
        }
        commitAll(project)

    }
    fun isOnLeftSideOfAssignemt(element:PsiElement):Boolean{
       var curr:PsiElement?=element
        var prev:PsiElement?=element
        while(curr !is  PsiAssignmentExpression){
            prev=curr
            curr=curr?.parent
            if(curr==null) return false
        }
        return (curr as PsiAssignmentExpression).lExpression==prev
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


                if(isOnLeftSideOfAssignemt(identifier)){
                    WriteCommandAction.runWriteCommandAction(project) {
                        var setterCall = JavaPsiFacade.getElementFactory(method.project).createExpressionFromText(
                            "${objectName}.${nameService.getSetterName(extractedClass,identifier.text!!)}(${(identifier.getParentOfType<PsiAssignmentExpression>(true) as PsiAssignmentExpression).rExpression!!.text})",
                            method
                        )
                        identifier.getParentOfType<PsiAssignmentExpression>(true)!!.replace(setterCall)
                        //(element.parent as PsiAssignmentExpression).rExpression?.replace(getterCall)
                    }
                }
                else  {
                    val parent = identifier.parent
                    WriteCommandAction.runWriteCommandAction(project){
                        identifier.replace(getterCall)
                    }

                    println(parent)
                }
                commitAll(project)



    }
    fun calcUniqueKey(usageInfo: UsageInfo):String{
        return usageInfo.extractedClassPath+usageInfo.filePath+usageInfo.name!!
    }
    fun updateFieldDeclarations(project:Project,element:PsiElement,extractedClass: PsiClass,usageInfo: UsageInfo,nameService: IdentifierNameService){
        val field=element.getParentOfType<PsiField>(true)!!
        val containingClass=field.getParentOfType<PsiClass>(true)
        if(containingClass==null){
            return
        }
        val type=JavaPsiFacade.getElementFactory(project).createType(extractedClass)
        val extractedFieldName=nameService.getFieldName(extractedClass,containingClass)
        var extractedField= containingClass!!.childrenOfType<PsiField>().firstOrNull(){it.name==extractedFieldName}
        val constructor=extractedClass.constructors.first { it.parameterList.parameters.size==usageInfo.variableNames.size }
       if(extractedField==null){
                extractedField=JavaPsiFacade.getElementFactory(project).createField(extractedFieldName,type)
           extractedField.initializer=JavaPsiFacade.getElementFactory(project).createExpressionFromText("new ${extractedClass.qualifiedName}(${Array(usageInfo.variableNames.size) { "null3" }.joinToString (",")})",extractedField)
                WriteCommandAction.runWriteCommandAction(project){
                    containingClass.add(extractedField)
                }
            }
        val constructorCall=extractedField.initializer as PsiCall
       val paramPos=constructor.parameterList.parameters.indexOfFirst{it.name==usageInfo.name}

       val argValue=if(field.initializer==null) getDefaultValueAsStringForType(field.type) else field.initializer!!.text
        WriteCommandAction.runWriteCommandAction(project){
            constructorCall.argumentList!!.expressions[paramPos].replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(argValue,field))
            println("QWERTZ "+constructorCall.argumentList!!.expressions[paramPos].text)
            field.delete()
            commitAll(project)
        }


    }


    fun getDefaultValueAsStringForType(type: PsiType): String {
        val result= when (type.canonicalText) {
            "int" -> "0"
            "short" -> "0"
            "long" -> "0L"
            "float" -> "0.0f"
            "double" -> "0.0"
            "char" -> "'\\u0000'"
            "byte" -> "0"
            "boolean" -> "false"
            else -> "null"
        }
        println(type.canonicalText + " " + result)
        return  result
    }



    fun updateMethodUsage(project:Project,extractedClass: PsiClass,element:PsiElement,usageInfo: UsageInfo) {

        val exprList = element.getParentOfType<PsiMethodCallExpression>(true)!!
        val method = keyElementMap[usageInfo.originKey]!! as PsiMethod
        val constructor=extractedClass.constructors.first { it.parameterList.parameters.size==usageInfo.variableNames.size }
        if(constructor.parameterList.parameters.size!=exprList.argumentList.expressions.size){
            return
        }
        val variableNames=keyVariableNamesMap[usageInfo.originKey]!!
        val argsInOrder= Array<String>(variableNames.size){""}
        val argsToDelete= mutableSetOf<Int>()
        for(variableName in variableNames){
            val paramPos=usageInfo.variableNames.indexOfFirst{ it==variableName }
            val constructorParamPos=constructor.parameterList.parameters.indexOfFirst { it.name==variableName }
            argsInOrder[constructorParamPos]=exprList.argumentList.expressions[paramPos].text
            argsToDelete.add(paramPos)

        }
        val newExpr=JavaPsiFacade.getElementFactory(project).createExpressionFromText("new ${extractedClass.qualifiedName}(${argsInOrder.joinToString(",")})",exprList)
        WriteCommandAction.runWriteCommandAction(project){
            exprList.argumentList.add(newExpr)
            var counter=0
            for( arg in exprList.argumentList.expressions){
                if(counter in argsToDelete){
                    arg.delete()
                }
                counter++
            }
        }

    }

    fun updateElementFromUsageInfo(project: Project,usageInfo: UsageInfo,element:PsiElement,nameService:IdentifierNameService) {
        val symbolType= UsageInfo.UsageType.values()[usageInfo.symbolType]
        if(usageInfo.name=="printMax"){
            toString();
        }
        val man = VirtualFileManager.getInstance()

        if(usageInfo.extractedClassPath==null) return
        println(getURI(usageInfo.extractedClassPath))
        val extractedClassFile= PsiManager.getInstance(project).findFile(man.findFileByUrl(getURI(usageInfo.extractedClassPath)!!)!!)!!
        val extractedClass=extractedClassFile.childrenOfType<PsiClass>().first()
        when(symbolType){
            UsageInfo.UsageType.VariableUsed->{
                println("####")
                if(usageInfo.range.startLine==21){
                    toString()
                }
                println(usageInfo.symbolType)
                updateVariableUsage(project,extractedClass,element as PsiIdentifier,nameService)
                println("####")
                //git reset --hard && git clean -df
            }
            UsageInfo.UsageType.MethodDeclared->{
                val key=usageInfo.originKey
                val method=element.getParentOfType<PsiMethod>(true)!!
                keyElementMap[key]=method
                keyVariableNamesMap[key]=usageInfo.variableNames.toSet()
                updateMethodSignature(project,method,extractedClass,usageInfo.variableNames,nameService)
            }
            UsageInfo.UsageType.MethodUsed->{
                updateMethodUsage(project,extractedClass,element,usageInfo)
            }
            UsageInfo.UsageType.VariableDeclared->{
                updateFieldDeclarations(project,element,extractedClass,usageInfo,nameService)
            }
        }
    }
    val keyElementMap= mutableMapOf<String,PsiElement>()
    val keyVariableNamesMap= mutableMapOf<String,Set<String>>()
    fun isValidElement(element:PsiElement,usageType: UsageInfo.UsageType):Boolean{
        if( element is PsiWhiteSpace || element is PsiComment)return false
        if( usageType== UsageInfo.UsageType.VariableUsed && element.parent?.let { it.nextSibling  is PsiExpressionList} == true) return false
        return true
    }
    fun getElement(project:Project,usageInfo: UsageInfo):PsiElement?{
        if(usageInfo.name=="printMax"){
            toString();
        }
        println(usageInfo.name)
        val bufferedReader: BufferedReader =Path.of(this.projectPath.absolutePath,usageInfo.filePath).toFile().bufferedReader()
        val fileContent = bufferedReader.use { it.readText() }
        val offset=this.calculateOffset(fileContent,usageInfo.range.startLine,usageInfo.range.startColumn)
        val man = VirtualFileManager.getInstance()
        val vFile = man.findFileByUrl(getURI(usageInfo.filePath)!!)!!
        vFile.refresh(false,true)
        val dataClumpFile = PsiManager.getInstance(project).findFile(vFile)!!


        val element=dataClumpFile.findElementAt(offset)
        if(isValidElement(element!!, UsageInfo.UsageType.values()[usageInfo.symbolType])){
            println(usageInfo.name)
            println(usageInfo.range.startLine.toString() + " " + usageInfo.range.startColumn.toString())
            print(element)
            println()
            return element!!
        }
        else{
            return null
        }








    }


}
