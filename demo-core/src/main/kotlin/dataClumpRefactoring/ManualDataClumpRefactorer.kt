package dataClumpRefactoring

import com.intellij.ide.fileTemplates.JavaTemplateUtil
import com.intellij.model.search.Searcher
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.PsiManager
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import java.io.File
import com.intellij.psi.util.childrenOfType
import org.jetbrains.kotlin.idea.completion.argList
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.util.collectionUtils.concat
import java.io.BufferedReader
import java.nio.file.Path

class ManualDataClumpRefactorer(val projectPath: File) : DataClumpRefactorer(projectPath) {


    fun updateMethodSignature(
        project: Project,
        method: PsiMethod,
        extractedClass: PsiClass,
        relevantParameterNames: Array<String>,
        nameService: IdentifierNameService
    ) {
        if (method.parameterList.parameters.none { it.name in relevantParameterNames }) {
            return;
        }

        val type = JavaPsiFacade.getElementFactory(method.project).createType(extractedClass)
        val parameter = JavaPsiFacade.getElementFactory(method.project)
            .createParameter(nameService.getParameterName(extractedClass.name!!, method), type)
        WriteCommandAction.runWriteCommandAction(project) {
            method.parameterList.add(parameter)
        }
        for (paramName in relevantParameterNames) {
            val param = method.parameterList.parameters.find { it.name == paramName }
            if (param != null) {
                WriteCommandAction.runWriteCommandAction(project) {
                    param.delete()
                }
            }
        }
        commitAll(project)

    }

    fun isOnLeftSideOfAssignemt(element: PsiElement): Boolean {
        return element.parent is PsiAssignmentExpression && (element.parent as PsiAssignmentExpression).lExpression == element

    }

    fun handlePostfixPrefixOperation(
        identifier: PsiElement,
        extractedClass: PsiClass,
        nameService: IdentifierNameService
    ) {
        val prefix = identifier.getParentOfType<PsiPrefixExpression>(true)
        val expr = identifier.getParentOfType<PsiPostfixExpression>(true) ?: prefix
        val getterName = nameService.getGetterName(identifier.text!!)
        val setterName = nameService.getSetterName( identifier.text!!)
        val objectName = nameService.getFieldName(extractedClass.name!!,identifier.getParentOfType<PsiClass>(true))

        val command = "${objectName}.${setterName}(${objectName}.${getterName}()+1);/*TODO postfix/prefix replaced*/"
        WriteCommandAction.runWriteCommandAction(identifier.project) {
            expr!!.parent!!.replace(
                JavaPsiFacade.getElementFactory(identifier.project).createStatementFromText(command, expr)
            )
        }
        commitAll(identifier.project)
    }

    private fun getRightSideOfAssignent(
        identifier: PsiElement,
        nameService: IdentifierNameService,
        extractedClass: PsiClass,
       isParameter: Boolean,
        method: PsiMethod,
        currentClass: PsiClass?
    ): String {
        val assignmentExpression = identifier.getParentOfType<PsiAssignmentExpression>(true)!!
        val operators = arrayOf("+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>=")
        if (operators.any { it == assignmentExpression.operationSign.text }) {
            val getterCall = getGetterCallText(nameService, extractedClass, identifier, method, currentClass,isParameter)
            return "$getterCall ${
                assignmentExpression.operationSign.text.substring(
                    0,
                    assignmentExpression.operationSign.text.length - 1
                )
            } ${assignmentExpression.rExpression!!.text}"
        } else {
            return assignmentExpression.rExpression!!.text
        }
    }

    private fun getGetterCallText(
        nameService: IdentifierNameService,
        extractedClass: PsiClass,
        element: PsiElement,
        method: PsiMethod,
        currentClass: PsiClass?,
        isParameter: Boolean
    ): String {
        val identifier=if(element is PsiIdentifier) element.text else element.lastChild.text
        val getterName = nameService.getGetterName(identifier)
        val objectName =
            if (isParameter) nameService.getParameterName(extractedClass.name!!,method) else nameService.getFieldName(
                extractedClass.name!!,
                currentClass
            )
        return "${objectName}.${getterName}()"
    }

    fun updateVariableUsage(
        project: Project,
        extractedClass: PsiClass,
        identifier: PsiElement,
        nameService: IdentifierNameService,
        isParameter: Boolean
    ) {
        val getterName = nameService.getGetterName(identifier.text!!)
        val method = identifier.getParentOfType<PsiMethod>(true)
        if (method == null) {
            return;
        }
        val currentClass = identifier.getParentOfType<PsiClass>(true)
        val objectName =
            if (isParameter) nameService.getParameterName(extractedClass.name!!, method) else nameService.getFieldName(
                extractedClass.name!!,
                currentClass
            )
        val getterCall = JavaPsiFacade.getElementFactory(method.project).createExpressionFromText(
            getGetterCallText(nameService, extractedClass, identifier, method, currentClass,isParameter),
            method
        )


        if (isOnLeftSideOfAssignemt(identifier)) {
            WriteCommandAction.runWriteCommandAction(project) {
                var setterCall = JavaPsiFacade.getElementFactory(method.project).createExpressionFromText(
                    "${objectName}.${
                        nameService.getSetterName(
                            (if(identifier is PsiIdentifier) identifier.text else identifier.lastChild.text)!!
                        )
                    }(${getRightSideOfAssignent(identifier, nameService,extractedClass,isParameter, method, currentClass)})",method

                )

                val assignmentExpression = identifier.getParentOfType<PsiAssignmentExpression>(true)!!
                var newEle = identifier.lastChild as PsiElement
                newEle = newEle.replace(setterCall)
                assignmentExpression.replace(newEle.parent)
                //(element.parent as PsiAssignmentExpression).rExpression?.replace(getterCall)
            }
        } else if (identifier.getParentOfType<PsiPostfixExpression>(true) != null || identifier.getParentOfType<PsiPostfixExpression>(
                true
            ) != null
        ) {
            handlePostfixPrefixOperation(identifier, extractedClass, nameService)
        } else {
            val parent = identifier.parent
            WriteCommandAction.runWriteCommandAction(project) {
                identifier.lastChild.replace(getterCall)
            }

        }
        commitAll(project)


    }

    fun calcUniqueKey(usageInfo: UsageInfo): String {
        return usageInfo.extractedClassPath + usageInfo.filePath + usageInfo.name!!
    }

    fun updateFieldDeclarations(
        project: Project,
        element: PsiElement,
        extractedClass: PsiClass,
        variableNames: Array<String>,
        nameService: IdentifierNameService,
        fieldName:String
    ) {
        val field = element as PsiField
        val containingClass = field.getParentOfType<PsiClass>(true)
        if (containingClass == null) {
            return
        }
        val type = JavaPsiFacade.getElementFactory(project).createType(extractedClass)
        val extractedFieldName = nameService.getFieldName(extractedClass.name!!, containingClass)
        var extractedField =
            containingClass!!.childrenOfType<PsiField>().firstOrNull() { it.name == extractedFieldName }
        val constructor =
            extractedClass.constructors.first { it.parameterList.parameters.size == variableNames.size }
        if (extractedField == null) {
            extractedField = JavaPsiFacade.getElementFactory(project).createField(extractedFieldName, type)
            //extractedField.modifierList!!.replace(JavaPsiFacade.getElementFactory(project).createKeyword("public"))
            //extractedField.modifierList!!.setModifierProperty("public",true)
            extractedField.initializer = JavaPsiFacade.getElementFactory(project)
                .createExpressionFromText("new ${extractedClass.qualifiedName}(${
                    Array(variableNames.size) { "null" }.joinToString(",")
                })", extractedField
                )
            WriteCommandAction.runWriteCommandAction(project) {
                containingClass.add(extractedField!!)
            }
            extractedField =
                containingClass!!.childrenOfType<PsiField>().firstOrNull() { it.name == extractedFieldName }
        }
        val constructorCall = extractedField!!.initializer as PsiCall
        val paramPos = constructor.parameterList.parameters.indexOfFirst { it.name == fieldName}

        val argValue =
            if (field.initializer == null) getDefaultValueAsStringForType(field.type) else field.initializer!!.text
        WriteCommandAction.runWriteCommandAction(project) {
            extractedField.modifierList!!.setModifierProperty(
                "protected",
                field.modifierList!!.hasModifierProperty("protected")
            )
            extractedField.modifierList!!.setModifierProperty(
                "public",
                field.modifierList!!.hasModifierProperty("public")
            )
            constructorCall.argumentList!!.expressions[paramPos].replace(
                JavaPsiFacade.getElementFactory(project).createExpressionFromText(argValue, field)
            )
            if (field.nextSibling.text == ",") {
                field.nextSibling.delete()
            }
            field.delete()
            commitAll(project)
        }


    }


    fun getDefaultValueAsStringForType(type: PsiType): String {
        val result = when (type.canonicalText) {
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
        return result
    }


    fun updateMethodUsage(
        project: Project,
        extractedClass: PsiClass,
        element: PsiElement,
        method: PsiMethod,
        nameService: IdentifierNameService,
        variableNames: Array<String>
    ) {

        val exprList = element.getParentOfType<PsiMethodCallExpression>(true)!!
        val containingMethod = element.getParentOfType<PsiMethod>(true)!!
        val constructor =
            extractedClass.constructors.first { it.parameterList.parameters.size == variableNames.size }
        if (constructor.parameterList.parameters.size != exprList.argumentList.expressions.size) {
            return
        }
        val argsInOrder = Array<String>(variableNames.size) { "" }
        val argsToDelete = mutableSetOf<Int>()
        for (variableName in variableNames) {
            val paramPos = variableNames.indexOfFirst { it == variableName }
            val constructorParamPos = constructor.parameterList.parameters.indexOfFirst { it.name == variableName }
            argsInOrder[constructorParamPos] = exprList.argumentList.expressions[paramPos].text
            argsToDelete.add(paramPos)

        }
        val insertionPos =
            method.parameterList.parameters.indexOfFirst { it.type.canonicalText == extractedClass.qualifiedName }

        var newExpr = JavaPsiFacade.getElementFactory(project)
            .createExpressionFromText("new ${extractedClass.qualifiedName}(${argsInOrder.joinToString(",")})", exprList)
        if (method.parameterList.parameters[insertionPos].type.canonicalText == extractedClass.qualifiedName) {
            val paramName = nameService.getParameterName(extractedClass.name!!, containingMethod)
            val name =
                if (containingMethod.parameterList.parameters.any { it.name == paramName }) paramName else nameService.getFieldName(
                    extractedClass.name!!,
                    containingMethod.getParentOfType<PsiClass>(true)
                )
            newExpr = JavaPsiFacade.getElementFactory(project).createExpressionFromText(name, containingMethod)
        }
        WriteCommandAction.runWriteCommandAction(project) {

            var counter = 0
            for (arg in exprList.argumentList.expressions) {
                if (counter in argsToDelete) {
                    arg.delete()
                }
                counter++
            }
            if (exprList.argumentList.expressionCount == 0) {
                exprList.argumentList.add(newExpr)

            } else {
                exprList.argumentList.addAfter(newExpr, exprList.argumentList.expressions[insertionPos])
            }
            commitAll(project)
        }


    }

    fun updateElementFromUsageInfo(
        project: Project,
        usageInfo: UsageInfo,
        element: PsiElement,
        nameService: IdentifierNameService
    ) {

    }

    fun nop() {

    }
    val nameClassPathMap = mutableMapOf<String, String>()


    fun findClassRec(classes:Array<PsiClass>,className:String):PsiClass?{
        for (cl in classes){
            if(cl.name==className){
                return cl
            }
            val res=findClassRec(cl.innerClasses,className)
            if(res!=null){
                return res
            }
        }

        return null
    }
    fun calcDepth(element:PsiElement):Int{
        var depth=0
        var currentElement:PsiElement?=element
        while(currentElement!=null){
            depth++
            currentElement=currentElement.parent
        }
        return depth
    }
    fun sortReferencesByDepth(references:Iterable<PsiReference>):List<PsiElement>{
        return references.sortedBy { -calcDepth(it.element) }.map { it.element }
    }
    val classCreator=ManualJavaClassCreator()
    fun handleOverridingMethods(baseMethod:PsiMethod,relevantParameters: Set<String>,extractedClass: PsiClass,nameService: IdentifierNameService){
        val overrides=OverridingMethodsSearch.search(baseMethod).findAll()
        val project=baseMethod.project
       for(overridingMethod in overrides){

            for (param in overridingMethod.parameters) {
                if(param.name !in relevantParameters) continue
                val references = sortReferencesByDepth(ReferencesSearch.search(param.sourceElement!!).findAll())
                for (element in references) {

                    updateVariableUsage(overridingMethod.project, extractedClass, element, nameService, true)
                }

            }
            val overridingMethodUsages=ReferencesSearch.search(overridingMethod).findAll()
            updateMethodSignature(project, overridingMethod, extractedClass, relevantParameters.toTypedArray(), nameService)

            for (ref in overridingMethodUsages) {
                val element = ref.element
                updateMethodUsage(
                    project,
                    extractedClass,
                    element,
                    overridingMethod,
                    nameService,
                    relevantParameters.toTypedArray()
                )
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
    ): Boolean {
        val man = VirtualFileManager.getInstance()
        val vFile = man.findFileByUrl(ep.filePath)!!
        val dataClumpFile = PsiManager.getInstance(project).findFile(vFile)!!
        val nameService = PrimitiveNameService(StubNameValidityChecker())


        val dataClumpClass =findClassRec((dataClumpFile as PsiClassOwner).classes,ep.className!!)


        if(dataClumpClass==null){
            nop()
        }
        val targetPackageName = dataClumpFile.packageName

        if (dataClumpType == "parameters") {

            val data = getMethodAndParamsToRefactor(dataClumpClass, ep.methodName!!, relevantParameters)
            val extractedClass =
                classCreator.getOrCreateClass(project, suggestedClassName, dataClumpFile,ep.nameTypesPair, nameService)
            val method = data._1

            val methodUsages = ReferencesSearch.search(method).findAll()
            handleOverridingMethods(method,relevantParameters,extractedClass,nameService)
            for (param in method.parameterList.parameters) {
                if(param.name !in relevantParameters) continue
                val references = sortReferencesByDepth( ReferencesSearch.search(param).findAll())
                for (element in references) {
                    updateVariableUsage(project, extractedClass, element, nameService, true)
                }

            }
            updateMethodSignature(project, method, extractedClass, relevantParameters.toTypedArray(), nameService)

            for (ref in methodUsages) {
                val element = ref.element
                updateMethodUsage(
                    project,
                    extractedClass,
                    element,
                    method,
                    nameService,
                    relevantParameters.toTypedArray()
                )
            }




            return true
        }
        else if (dataClumpType == "fields") {
            val data = getFieldsToRefactor(dataClumpClass, relevantParameters)
            val extractedClass =
                classCreator.getOrCreateClass(project, suggestedClassName, dataClumpFile, ep.nameTypesPair, nameService)
            for (field in data) {
                if(field.name !in relevantParameters) continue
                val fieldUsages=ReferencesSearch.search(field).findAll()
                for (ref in fieldUsages) {
                    val element = ref.element
                    updateVariableUsage(project, extractedClass, element, nameService, false)
                }
                updateFieldDeclarations(project, field, extractedClass, relevantParameters.toTypedArray(),nameService,field.name!!)

            }
            return true
        }
        return false

    }

    val keyElementMap = mutableMapOf<String, PsiElement>()
    val keyVariableNamesMap = mutableMapOf<String, Set<String>>()
    fun isValidElement(element: PsiElement, usageType: UsageInfo.UsageType): Boolean {
        if (element is PsiWhiteSpace || element is PsiComment) return false
        if (usageType == UsageInfo.UsageType.VariableUsed && element.parent?.let { it.nextSibling is PsiExpressionList } == true) return false
        return true
    }

    fun getElement(project: Project, usageInfo: UsageInfo): PsiElement? {
        val bufferedReader: BufferedReader =
            Path.of(this.projectPath.absolutePath, usageInfo.filePath).toFile().bufferedReader()
        val fileContent = bufferedReader.use { it.readText() }
        val offset = this.calculateOffset(fileContent, usageInfo.range.startLine, usageInfo.range.startColumn)
        val man = VirtualFileManager.getInstance()
        val vFile = man.findFileByUrl(getURI(usageInfo.filePath)!!)!!
        vFile.refresh(false, true)
        val dataClumpFile = PsiManager.getInstance(project).findFile(vFile)!!


        val element = dataClumpFile.findElementAt(offset)
        if (isValidElement(element!!, UsageInfo.UsageType.values()[usageInfo.symbolType])) {
            return element!!
        } else {
            return null
        }


    }


}
