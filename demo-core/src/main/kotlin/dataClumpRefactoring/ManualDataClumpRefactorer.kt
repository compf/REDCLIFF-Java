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
            .createParameter(nameService.getParameterName(extractedClass, method), type)
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
        val getterName = nameService.getGetterName(extractedClass, identifier.text!!)
        val setterName = nameService.getSetterName(extractedClass, identifier.text!!)
        val objectName = nameService.getFieldName(extractedClass, identifier.getParentOfType<PsiClass>(true))

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
        val getterName = nameService.getGetterName(extractedClass, identifier)
        val objectName =
            if (isParameter) nameService.getParameterName(extractedClass, method) else nameService.getFieldName(
                extractedClass,
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
        val getterName = nameService.getGetterName(extractedClass, identifier.text!!)
        val method = identifier.getParentOfType<PsiMethod>(true)
        if (method == null) {
            return;
        }
        val currentClass = identifier.getParentOfType<PsiClass>(true)
        val objectName =
            if (isParameter) nameService.getParameterName(extractedClass, method) else nameService.getFieldName(
                extractedClass,
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
                            extractedClass,
                            (if(identifier is PsiIdentifier) identifier.text else identifier.lastChild.text)!!
                        )
                    }(${getRightSideOfAssignent(identifier, nameService,extractedClass,isParameter, method, currentClass)})",method

                )

                val assignmentExpression = identifier.getParentOfType<PsiAssignmentExpression>(true)!!
                var newEle = identifier as PsiElement
                newEle = newEle.replace(setterCall)
                assignmentExpression.replace(newEle)
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
                identifier.replace(getterCall)
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
        val extractedFieldName = nameService.getFieldName(extractedClass, containingClass)
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
            val paramName = nameService.getParameterName(extractedClass, containingMethod)
            val name =
                if (containingMethod.parameterList.parameters.any { it.name == paramName }) paramName else nameService.getFieldName(
                    extractedClass,
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

    fun createOrGetExtractedClass(
        project: Project,
        className: String,
        dataClumpFile: PsiFile,
        relevantVariables: List<Pair<String,String>>,
        nameService: IdentifierNameService
    ): PsiClass {
        if (!nameClassPathMap.containsKey(className)) {
            var extractedClass: PsiClass? = null
            try {
                commitAll(project)
                waitForIndexing(project)
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
                        val type=JavaPsiFacade.getElementFactory(project).createTypeFromText(variable.second,extractedClass)
                        val field = JavaPsiFacade.getElementFactory(project).createField(variable.first!!, type)
                        extractedClass.add(field)
                        val getterName = nameService.getGetterName(extractedClass, variable.first!!)
                        val getter = JavaPsiFacade.getElementFactory(project).createMethodFromText(
                            "public ${variable.second} ${getterName}(){return ${variable.first};}",
                            extractedClass
                        )
                        val setterName = "set${variable.first!!.replaceFirstChar { it.uppercase() }}"
                        val setter = JavaPsiFacade.getElementFactory(project).createMethodFromText(
                            "public void ${setterName}(${variable.second} ${variable.first}){this.${variable.first}=${variable.first};}",
                            extractedClass
                        )
                        extractedClass.add(getter)
                        extractedClass.add(setter)

                    }
                    val constructor = JavaPsiFacade.getElementFactory(project).createConstructor()
                    for (variable in relevantVariables) {
                        val type=JavaPsiFacade.getElementFactory(project).createTypeFromText(variable.second,extractedClass)

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

            waitForIndexing(project)
            commitAll(project)
            val session= RefreshQueue.getInstance().createSession(false,true){

            }
            session.launch()
        }

        val man = VirtualFileManager.getInstance()
        val vFile = man.findFileByUrl(nameClassPathMap[className]!!)!!
        val file = PsiManager.getInstance(project).findFile(vFile)!!


        val extractedClass =
            (file as PsiClassOwner)
                .classes
                .filter { it.name == className }
                .first()
        return  extractedClass


    }
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
        val nameService = PrimitiveNameService()


        val dataClumpClass =findClassRec((dataClumpFile as PsiClassOwner).classes,ep.className!!)


        if(dataClumpClass==null){
            nop()
        }
        val targetPackageName = dataClumpFile.packageName

        if (dataClumpType == "parameters") {

            val data = getMethodAndParamsToRefactor(dataClumpClass, ep.methodName!!, relevantParameters)
            val extractedClass =
                createOrGetExtractedClass(project, suggestedClassName, dataClumpFile,ep.nameTypesPair, nameService)
            val method = data._1

            val methodUsages = ReferencesSearch.search(method).findAll()
            OverridingMethodsSearch.search(method).forEach {
                val overridingMethod = it

                for (param in overridingMethod.parameters) {
                    if(param.name !in relevantParameters) continue
                    val references = ReferencesSearch.search(param.sourceElement!!).findAll()
                    for (ref in references) {
                        val element = ref.element
                        updateVariableUsage(project, extractedClass, element, nameService, true)
                    }

                }
                updateMethodSignature(project, overridingMethod, extractedClass, relevantParameters.toTypedArray(), nameService)
            }
            for (param in method.parameterList.parameters) {
                if(param.name !in relevantParameters) continue
                val references = ReferencesSearch.search(param).findAll()
                for (ref in references) {
                    val element = ref.element
                    updateVariableUsage(project, extractedClass, element, nameService, true)
                }

            }

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
            updateMethodSignature(project, method, extractedClass, relevantParameters.toTypedArray(), nameService)




            return true
        }
        else if (dataClumpType == "fields") {
            val data = getFieldsToRefactor(dataClumpClass, relevantParameters)
            val extractedClass =
                createOrGetExtractedClass(project, suggestedClassName, dataClumpFile, ep.nameTypesPair, nameService)
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
