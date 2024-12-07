package dataClumpRefactoring

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.childrenOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType


class ManualDataClumpRefactorer(
    private val project: Project,
    val refFinder: ReferenceFinder,
    val classCreator: ClassCreator
) : DataClumpRefactorer(project) {

    fun updateDocComment(
        method: PsiMethod,
        extractedClass: PsiClass,
        nameService: IdentifierNameService,
        relevantParameters: Set<String>
    ) {
        val docComment = method.docComment
        if (docComment != null) {
            WriteCommandAction.runWriteCommandAction(method.project) {
                for (tags in docComment.findTagsByName("param")) {
                    if (tags.dataElements[0].text in relevantParameters) {
                        tags.delete()
                    }
                }
                val paramText = "The ${extractedClass.name} object"
                docComment.add(
                    JavaPsiFacade.getElementFactory(method.project).createDocTagFromText(
                        "@param ${
                            nameService.getParameterName(
                                extractedClass.name!!,
                                method
                            )
                        } ${paramText}"
                    )
                )
            }
        }

    }

    fun updateMethodSignature(
        project: Project,
        method: PsiMethod,
        extractedClass: PsiClass,
        relevantParameterNames: Array<String>,
        nameService: IdentifierNameService
    ) {
        if (method.parameterList.parameters.none { it.name in relevantParameterNames }) {
            println("No relevant parameters found")
            return
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
        updateDocComment(method, extractedClass, nameService, relevantParameterNames.toSet())
        WriteCommandAction.runWriteCommandAction(project) {
            optimizeCode(project, method)

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
        val setterName = nameService.getSetterName(identifier.text!!)
        val objectName = nameService.getFieldName(extractedClass.name!!, identifier.getParentOfType<PsiClass>(true))

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
            val getterCall =
                getGetterCallText(nameService, extractedClass, identifier, method, currentClass, isParameter)
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
    ): String? {
        if (element !is PsiReferenceExpression && element !is PsiIdentifier) {
            return null
        }
        val identifier = if (element is PsiIdentifier) element.text else element.lastChild.text
        val getterName = nameService.getGetterName(identifier)
        val objectName =
            if (isParameter) nameService.getParameterName(extractedClass.name!!, method) else nameService.getFieldName(
                extractedClass.name!!,
                currentClass
            )
        return "${objectName}.${getterName}()"
    }

    fun updateVariableUsage(
        project: Project,
        extractedClass: PsiClass,
        element: PsiElement,
        nameService: IdentifierNameService,
        isParameter: Boolean
    ) {
        val identifier = if (element is PsiIdentifier) element.parent else element
        println("variable usage ${identifier.text}")
        val method = identifier.getParentOfType<PsiMethod>(true)
        if (method == null) {
            println("WARNING: No method found")
            return
        }
        val currentClass = identifier.getParentOfType<PsiClass>(true)
        val objectName =
            if (isParameter) nameService.getParameterName(extractedClass.name!!, method) else nameService.getFieldName(
                extractedClass.name!!,
                currentClass
            )
        val getterText = getGetterCallText(nameService, extractedClass, identifier, method, currentClass, isParameter)
        if (getterText == null) {
            println("WARNING: getter text is null")
            return
        }
        val getterCall = JavaPsiFacade.getElementFactory(method.project).createExpressionFromText(
            getterText,
            method
        )


        if (isOnLeftSideOfAssignemt(identifier)) {
            commitAll(project)
            WriteCommandAction.runWriteCommandAction(project) {
                var setterCall = JavaPsiFacade.getElementFactory(method.project).createExpressionFromText(
                    "${objectName}.${
                        nameService.getSetterName(
                            (if (identifier is PsiIdentifier) identifier.text else identifier.lastChild.text)!!
                        )
                    }(${
                        getRightSideOfAssignent(
                            identifier,
                            nameService,
                            extractedClass,
                            isParameter,
                            method,
                            currentClass
                        )
                    })", method

                )

                val assignmentExpression = identifier.getParentOfType<PsiAssignmentExpression>(true)!!
                var newEle = identifier.lastChild as PsiElement
                newEle = newEle.replace(setterCall)
                assignmentExpression.replace(newEle.parent)
            }
        } else if (identifier.getParentOfType<PsiPostfixExpression>(true) != null || identifier.getParentOfType<PsiPostfixExpression>(
                true
            ) != null
        ) {
            handlePostfixPrefixOperation(identifier, extractedClass, nameService)
        } else {
            val parent = identifier.parent
            commitAll(project)

            WriteCommandAction.runWriteCommandAction(project) {
                identifier.lastChild.replace(getterCall)
                if (identifier.nextSibling != null && (identifier.nextSibling.text == "," || identifier.nextSibling.text == ".")) {
                }
            }

        }
        commitAll(project)


    }


    fun updateFieldDeclarations(
        project: Project,
        element: PsiElement,
        extractedClass: PsiClass,
        variableNames: Array<String>,
        nameService: IdentifierNameService,
        fieldName: String
    ) {
        val field = element as PsiField

        val containingClass = field.getParentOfType<PsiClass>(true)
        if (containingClass == null) {
            println("No containing class found")
            return
        }
        val type = JavaPsiFacade.getElementFactory(project).createType(extractedClass)
        val extractedFieldName = nameService.getFieldName(extractedClass.name!!, containingClass)
        var extractedField =
            containingClass.childrenOfType<PsiField>().firstOrNull { it.name == extractedFieldName }
        val constructor =
            extractedClass.constructors.firstOrNull { it.parameterList.parameters.size == variableNames.size }
        if (constructor == null) {
            println(variableNames.joinToString(","))
            println(extractedClass.constructors.map { it.parameterList.parameters.size })
            println(field.text)
            println(extractedClass.qualifiedName)
            throw Exception("No fitting constructor found")
            return
        }
        if (extractedField == null) {
            extractedField = JavaPsiFacade.getElementFactory(project).createField(extractedFieldName, type)

            extractedField.initializer = JavaPsiFacade.getElementFactory(project)
                .createExpressionFromText(
                    "new ${extractedClass.qualifiedName}(${
                        Array(variableNames.size) { "null" }.joinToString(",")
                    })", extractedField
                )
            WriteCommandAction.runWriteCommandAction(project) {
                containingClass.add(extractedField!!)
            }
            extractedField =
                containingClass.childrenOfType<PsiField>().firstOrNull { it.name == extractedFieldName }
        }
        val constructorCall = extractedField!!.initializer as PsiCall
        val paramPos = constructor.parameterList.parameters.indexOfFirst { it.name == fieldName }

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
            optimizeCode(project, extractedField)

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
        variableNames: Array<String>,
        allMethodParameters: List<String>
    ) {
        println(element.text)
        val nullableExprList = element.getParentOfType<PsiMethodCallExpression>(true)
        if (nullableExprList == null) {
            println("WARNING: no method call found")
            return
        }
        val exprList = nullableExprList


        println("method usage ${exprList.text}")
        if (exprList.text.contains("${extractedClass.name}(")) {
            println("already updated")
            return
        } else {
            println("not updated")
        }
        if (exprList.argumentList.expressions.size < variableNames.size) {
            println("WARNING: argument count mismatch")
            return
        }
        val containingMethod = element.getParentOfType<PsiMethod>(true)!!
        val constructor =
            extractedClass.constructors.firstOrNull { it.parameterList.parameters.size == variableNames.size }
        if (constructor == null) {
            println("WARNING: No fitting constructor found")
            println(variableNames.joinToString(","))
            println(extractedClass.constructors.map { it.parameterList.parameters.size })
            println(exprList.text)
            println(extractedClass.qualifiedName)
            throw Exception("No fitting constructor found")
            return
        }

        val argsInOrder = Array<String>(variableNames.size) { "" }
        val argsToDelete = mutableSetOf<Int>()
        var doesHaveVarArgs = false
        for (variableName in variableNames) {
            val paramPos = allMethodParameters.indexOfFirst { it == variableName }
            if (paramPos == allMethodParameters.size - 1 && exprList.argumentList.expressions.size > variableNames.size) {
                doesHaveVarArgs = true
            }
            println("param pos $paramPos $variableName ${allMethodParameters.joinToString(",")}")
            if (paramPos == -1) {
                println("WARNING: parameter not found $variableName ${method.parameterList.text}")
                continue
            }
            val constructorParamPos = constructor.parameterList.parameters.indexOfFirst { it.name == variableName }
            println(exprList.text)
            println(paramPos)
            println(allMethodParameters.joinToString(","))
            argsInOrder[constructorParamPos] = exprList.argumentList.expressions[paramPos].text
            argsToDelete.add(paramPos)

        }
        if (doesHaveVarArgs) {
            argsInOrder[argsInOrder.size - 1] = exprList.argumentList.expressions.toList().subList(
                exprList.argumentList.expressions.size - (exprList.argumentList.expressions.size - allMethodParameters.size) - 1,
                exprList.argumentList.expressions.size
            ).map { it.text }.joinToString(",")
            var counter = exprList.argumentList.expressions.size - 1
            for (i in 0..(exprList.argumentList.expressions.size - allMethodParameters.size)) {
                argsToDelete.add(counter)
                counter--
            }

        }
        val insertionPos =
            method.parameterList.parameters.indexOfFirst { it.type.canonicalText == extractedClass.qualifiedName }
        val MAX_LINE_LENGTH = 90
        val statement = exprList.getParentOfType<PsiStatement>(true)!!.text
        val tooLong = statement.length > MAX_LINE_LENGTH
        val whiteSpace = if (tooLong) "\n" else ""
        println("too long ${statement.length} $tooLong $statement")
        var newExpr = JavaPsiFacade.getElementFactory(project)
            .createExpressionFromText(
                "new ${extractedClass.name}($whiteSpace${argsInOrder.joinToString(",$whiteSpace")})",
                exprList
            )
        val otherParameters =
            variableNames.toSet().minus(containingMethod.parameterList.parameters.map { it.name }.toSet())
        if (otherParameters.none() || containingMethod.parameterList.parameters.any {
                it.name == nameService.getParameterName(
                    extractedClass.name!!,
                    containingMethod
                )
            }) {
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
                    println("deleting " + arg.text)
                    arg.delete()
                }
                counter++
            }
            if (exprList.argumentList.expressionCount == 0) {
                exprList.argumentList.add(newExpr)

            } else {
                val insertionPos = if (insertionPos == -1) exprList.argumentList.expressions.size - 1
                else if (insertionPos == 0) 0
                else insertionPos - 1
                exprList.argumentList.addAfter(newExpr, exprList.argumentList.expressions[insertionPos])
            }
            optimizeCode(project, exprList)
            commitAll(project)
        }


    }

    private fun optimizeCode(project: Project, element: PsiElement) {
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(element)
        //CodeStyleManager.getInstance(project).reformat(element.containingFile)
        //JavaCodeStyleManager.getInstance(project).optimizeImports(element.containingFile)

    }




    fun nop() {

    }


    fun calcDepth(element: PsiElement): Int {
        var depth = 0
        var currentElement: PsiElement? = element
        while (currentElement != null) {
            depth++
            currentElement = currentElement.parent
        }
        return depth
    }

    fun sortReferencesByDepth(references: Iterable<PsiElement>): List<PsiElement> {
        return references.sortedBy { -calcDepth(it) }
    }

    fun handleOverridingMethods(
        baseMethod: PsiMethod,
        relevantParameters: Set<String>,
        extractedClass: PsiClass,
        nameService: IdentifierNameService,
        refFinder: ReferenceFinder
    ) {
        val overrides = refFinder.findMethodOverrides(baseMethod)
        val project = baseMethod.project
        for (overridingMethod in overrides) {

            for (param in overridingMethod.parameterList.parameters) {
                if (param.name !in relevantParameters) continue
                val references = sortReferencesByDepth(refFinder.findParameterUsages(param))
                for (element in references) {

                    updateVariableUsage(overridingMethod.project, extractedClass, element, nameService, true)
                }

            }
            val allParams = overridingMethod.parameterList.parameters.map { it.name }
            val overridingMethodUsages = refFinder.findMethodUsages(overridingMethod)
            updateMethodSignature(
                project,
                overridingMethod,
                extractedClass,
                relevantParameters.toTypedArray(),
                nameService
            )

            for (ref in overridingMethodUsages) {
                val element = ref
                updateMethodUsage(
                    project,
                    extractedClass,
                    element,
                    overridingMethod,
                    nameService,
                    relevantParameters.toTypedArray(),
                    allParams
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
        relevantParameters: Set<String>,
    ): Boolean {

        val man = VirtualFileManager.getInstance()
        val vFile = man.findFileByUrl(ep.filePath)!!
        vFile.refresh(false, true)
        val dataClumpFile = PsiManager.getInstance(project).findFile(vFile)!!
        val nameService = this.classCreator.createNameService()


        val dataClumpClass = findClassRec((dataClumpFile as PsiClassOwner).classes, ep.className)
        if (dataClumpType == DATA_CLUMP_TYPE_PARAMETERS) {

            val data = getMethodAndParamsToRefactor(
                dataClumpClass,
                ep.methodName!!,
                relevantParameters,
                calculateOffset(dataClumpFile.text, ep.position.startLine, ep.position.startColumn)
            )
            if (data == null) {
                // happens if already refactored
                println("No data found parameters")
                return false
            }

            val extractedClass = classCreator.getOrCreateClass(
                project,
                suggestedClassName,
                ep.dataClumpKey,
                dataClumpFile,
                data._3,
                nameService
            )
            val method = data._1
            val methodParameters = method.parameterList.parameters.map { it.name }
            val methodUsages = refFinder.findMethodUsages(method)
            handleOverridingMethods(method, relevantParameters, extractedClass, nameService, refFinder)
            for (param in method.parameterList.parameters) {
                if (param.name !in relevantParameters) {
                    println("ignore  parameter ${param.name}")
                    continue
                }
                val references = sortReferencesByDepth(refFinder.findParameterUsages(param))
                for (element in references) {
                    updateVariableUsage(project, extractedClass, element, nameService, true)
                }

            }
            updateMethodSignature(project, method, extractedClass, relevantParameters.toTypedArray(), nameService)

            for (ref in methodUsages) {
                val element = ref
                updateMethodUsage(
                    project,
                    extractedClass,
                    element,
                    method,
                    nameService,
                    relevantParameters.toTypedArray(),
                    methodParameters
                )
            }




            return true
        } else if (dataClumpType == DATA_CLUMP_TYPE_FIELDS) {
            val data = getFieldsToRefactor(dataClumpClass, relevantParameters)
            if (data.none()) {
                println("fields No data found  ${dataClumpClass!!.name} ${relevantParameters}")
                return false
            }
            val extractedClass =
                classCreator.getOrCreateClass(
                    project,
                    suggestedClassName,
                    ep.dataClumpKey,
                    dataClumpFile,
                    data,
                    nameService
                )
            for (field in data) {
                if (field.name !in relevantParameters) continue
                val fieldUsages = refFinder.findFieldUsages(field)
                println("looking at field ${field.text} in class " + dataClumpClass!!.name)
                for (ref in fieldUsages) {
                    val element = ref
                    updateVariableUsage(project, extractedClass, element, nameService, false)
                }
                updateFieldDeclarations(
                    project,
                    field,
                    extractedClass,
                    relevantParameters.toTypedArray(),
                    nameService,
                    field.name
                )

            }
            return true
        }
        return false

    }
}







