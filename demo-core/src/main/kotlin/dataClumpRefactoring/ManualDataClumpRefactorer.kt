package dataClumpRefactoring

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.PsiManager
import com.intellij.openapi.command.WriteCommandAction;
import java.io.File
import com.intellij.psi.util.childrenOfType
import org.jetbrains.kotlin.idea.completion.argList
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import java.io.BufferedReader
import java.nio.file.Path

class ManualDataClumpRefactorer(val projectPath: File) : DataClumpRefactorer(projectPath) {


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
    fun handlePostfixPrefixOperation(identifier: PsiIdentifier,extractedClass: PsiClass,nameService: IdentifierNameService) {
        val prefix = identifier.getParentOfType<PsiPrefixExpression>(true)
        val expr = identifier.getParentOfType<PsiPostfixExpression>(true) ?: prefix
        val getterName = nameService.getGetterName(extractedClass, identifier.text!!)
        val setterName = nameService.getSetterName(extractedClass, identifier.text!!)
        val objectName = nameService.getFieldName(extractedClass, identifier.getParentOfType<PsiClass>(true))

        val command="${objectName}.${setterName}(${objectName}.${getterName}()+1);/*TODO postfix/prefix replaced*/"
        WriteCommandAction.runWriteCommandAction(identifier.project) {
            expr!!.parent!!.replace(JavaPsiFacade.getElementFactory(identifier.project).createStatementFromText(command, expr))
        }
        commitAll(identifier.project)
    }

    private fun getRightSideOfAssignent(identifier: PsiIdentifier, nameService: IdentifierNameService,extractedClass: PsiClass): String {
    val assignmentExpression=identifier.getParentOfType<PsiAssignmentExpression>(true)!!
        val operators= arrayOf("+=","-=","*=","/=","%=","&=","|=","^=","<<=",">>=",">>>=")
        if(operators.any{it==assignmentExpression.operationSign.text}){
            val getterName = nameService.getGetterName(extractedClass, identifier.text!!)
            return "${getterName}() ${assignmentExpression.operationSign.text.substring(0,assignmentExpression.operationSign.text.length-1)} ${assignmentExpression.rExpression!!.text}"
        }
        else{
            return assignmentExpression.rExpression!!.text
        }
    }

    fun updateVariableUsage(project: Project, extractedClass: PsiClass, identifier:PsiIdentifier, nameService:IdentifierNameService, usageInfo: UsageInfo){
            val getterName=nameService.getGetterName(extractedClass,identifier.text!!)
            val method=identifier.getParentOfType<PsiMethod>(true)
        if(method==null){
            return;
        }
            val currentClass=identifier.getParentOfType<PsiClass>(true)
            val objectName =if(usageInfo.isParameter) nameService.getParameterName(extractedClass,method) else "this."+nameService.getFieldName(extractedClass,currentClass)
            val getterCall=JavaPsiFacade.getElementFactory(method.project).createExpressionFromText("${objectName}.${getterName}()",method)


                if(isOnLeftSideOfAssignemt(identifier)){
                    WriteCommandAction.runWriteCommandAction(project) {
                        var setterCall = JavaPsiFacade.getElementFactory(method.project).createExpressionFromText(
                            "${objectName}.${nameService.getSetterName(extractedClass,identifier.text!!)}(${getRightSideOfAssignent(identifier,nameService,extractedClass)})",method)


                        identifier.getParentOfType<PsiAssignmentExpression>(true)!!.replace(setterCall)
                        //(element.parent as PsiAssignmentExpression).rExpression?.replace(getterCall)
                    }
                }
                else if(identifier.getParentOfType<PsiPostfixExpression>(true) !=null|| identifier.getParentOfType<PsiPostfixExpression>(true)!=null){
                    handlePostfixPrefixOperation(identifier,extractedClass,nameService)
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
           extractedField.initializer=JavaPsiFacade.getElementFactory(project).createExpressionFromText("new ${extractedClass.qualifiedName}(${Array(usageInfo.variableNames.size) { "null" }.joinToString (",")})",extractedField)
                WriteCommandAction.runWriteCommandAction(project){
                    containingClass.add(extractedField!!)
                }
           extractedField= containingClass!!.childrenOfType<PsiField>().firstOrNull(){it.name==extractedFieldName}
            }
        val constructorCall=extractedField!!.initializer as PsiCall
       val paramPos=constructor.parameterList.parameters.indexOfFirst{it.name==usageInfo.name}

       val argValue=if(field.initializer==null) getDefaultValueAsStringForType(field.type) else field.initializer!!.text
        WriteCommandAction.runWriteCommandAction(project){
            constructorCall.argumentList!!.expressions[paramPos].replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(argValue,field))
            println("QWERTZ "+constructorCall.argumentList!!.expressions[paramPos].text)
            if(field.nextSibling.text==","){
                field.nextSibling.delete()
            }
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



    fun updateMethodUsage(project:Project,extractedClass: PsiClass,element:PsiElement,usageInfo: UsageInfo,nameService: IdentifierNameService) {
        val method=keyElementMap[usageInfo.originKey] as PsiMethod

        val exprList = element.getParentOfType<PsiMethodCallExpression>(true)!!
        val containingMethod=element.getParentOfType<PsiMethod>(true)!!
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
        val insertionPos=method.parameterList.parameters.indexOfFirst { it.type.canonicalText==extractedClass.qualifiedName }

        var newExpr=JavaPsiFacade.getElementFactory(project).createExpressionFromText("new ${extractedClass.qualifiedName}(${argsInOrder.joinToString(",")})",exprList)
        if(method.parameterList.parameters[insertionPos].type.canonicalText==extractedClass.qualifiedName){
            val paramName=nameService.getParameterName(extractedClass,containingMethod)
            val name=if(containingMethod.parameterList.parameters.any{it.name==paramName})paramName else nameService.getFieldName(extractedClass,containingMethod.getParentOfType<PsiClass>(true))
            newExpr=JavaPsiFacade.getElementFactory(project).createExpressionFromText(name,containingMethod)
        }
        WriteCommandAction.runWriteCommandAction(project){

            var counter=0
            for( arg in exprList.argumentList.expressions){
                if(counter in argsToDelete){
                    arg.delete()
                }
                counter++
            }
            if(exprList.argumentList.expressionCount==0){
                exprList.argumentList.add(newExpr)

            }
            else{
                exprList.argumentList.addAfter(newExpr,exprList.argumentList.expressions[insertionPos])
            }
            commitAll(project)
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
                updateVariableUsage(project,extractedClass,element as PsiIdentifier,nameService,usageInfo)
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
                updateMethodUsage(project,extractedClass,element,usageInfo,nameService)
            }
            UsageInfo.UsageType.VariableDeclared->{
                updateFieldDeclarations(project,element,extractedClass,usageInfo,nameService)
            }
        }
    }
    fun nop(){

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
        if(element==null){
            nop()
        }
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
