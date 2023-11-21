package org.jetbrains.research.refactoringDemoPlugin

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import java.io.File
import kotlin.system.exitProcess
import org.jetbrains.kotlin.lombok.utils.decapitalize
import org.jetbrains.research.pluginUtilities.openRepository.getKotlinJavaRepositoryOpener
import org.jetbrains.research.refactoringDemoPlugin.parsedAstTypes.*
import org.jetbrains.research.refactoringDemoPlugin.util.extractElementsOfType
import org.jetbrains.research.refactoringDemoPlugin.util.extractModules
import org.jetbrains.research.refactoringDemoPlugin.util.findPsiFilesByExtension
import com.intellij.refactoring.introduceParameterObject.*
import  com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.JavaRefactoringFactory;
import com.intellij.openapi.roots.ProjectFileIndex

object PluginRunner : ApplicationStarter {
    @Deprecated("Specify it as `id` for extension definition in a plugin descriptor")
    override val commandName: String
        get() = "DemoPluginCLI"

    override val requiredModality: Int
        get() = ApplicationStarter.ANY_MODALITY

    override fun main(args: List<String>) {
        DataClumpRefactorer().main(args.drop(1))
    }
}

class DataClumpRefactorer : CliktCommand() {
    private val input by
    argument(help = "Path to the project").file(mustExist = true, canBeFile = false)
    private val output by argument(help = "Output directory").file(canBeFile = true)
    fun calculateOffset(text: CharSequence, lineNumber: Int, columnNumber: Int): Int {
        var offset = 0
        val lines = text.split('\n')

        for (i in 0 until lineNumber - 1) {
            offset += lines[i].length + 1 // Add 1 for the newline character
        }

        return offset + columnNumber - 1
    }

    fun createExtractedClass(project: Project, className: String, file: PsiFile): PsiClass? {
        println("### Creating class")
        val oldFile = file.containingDirectory.getVirtualFile().findChild(className + ".java")
        if (oldFile != null) {
            oldFile.delete(this)
        }
        val paramMap = mutableMapOf<String, String>()
        paramMap.put("PACKAGE_NAME", "javatest")
        val result =
            JavaDirectoryService.getInstance()
                .createClass(
                    file.containingDirectory,
                    className,
                    com.intellij
                        .ide
                        .fileTemplates
                        .JavaTemplateUtil
                        .INTERNAL_CLASS_TEMPLATE_NAME,
                    false,
                    paramMap
                )

        println("### finnished class")
        return result
    }

    fun getURI(path: String): String? {
        try {
            return "file://" + java.nio.file.Paths.get(input.toPath().toString(), path).toString()
        } catch (e: Exception) {
            println("Error while creating path")
            println(e)
            return null
        }
    }

    fun getPackageName(file: PsiFile): String {
        return "javatest";
    }

    class DataClumpEndpoint(val filePath: String, val className: String, val methodName: String?) {}
    val createdClassMap= mutableSetOf<String>()
    fun introduceParameterObject(project: Project, context: DataClumpTypeContext, suggestedClassName: String) {

        val man = VirtualFileManager.getInstance()
        val endpoints = arrayOf(
            DataClumpEndpoint(
                getURI(context.from_file_path)!!,
                context.from_class_or_interface_name,
                context.from_method_name!!
            ),
            DataClumpEndpoint(
                getURI(context.to_file_path)!!,
                context.to_class_or_interface_name,
                context.to_method_name!!
            )
        )
        val relevantParameters = context.data_clump_data.values.map { it.name }.toSet()
        var loopedOnce=false
        for (ep in endpoints) {


            val vFile = man.findFileByUrl(ep.filePath)!!
            val dataClumpFile = PsiManager.getInstance(project).findFile(vFile)!!
            val packageName = getPackageName(dataClumpFile)
            val dataClumpClass =
                (dataClumpFile as PsiClassOwner)
                    .classes
                    .filter { it.name == ep.className }
                    .first()
            val allMethods = dataClumpClass!!.findMethodsByName(ep.methodName!!, false)
            val method = allMethods[0]
            var index = 0;
            val parameterInfos = mutableListOf<ParameterInfoImpl>()
            for (param in method.parameterList.parameters) {
                println(param.name)
                if (param.name in relevantParameters) {
                    parameterInfos.add(ParameterInfoImpl(index, param.name!!, param.type))
                    index++

                }
            }

            val moveDestination =
                JavaRefactoringFactory.getInstance(project).createSourceFolderPreservingMoveDestination(packageName)
            val descriptor =
                com.intellij.refactoring.introduceparameterobject.JavaIntroduceParameterObjectClassDescriptor(
                    suggestedClassName,
                    packageName,
                    moveDestination,
                    suggestedClassName in createdClassMap || loopedOnce,
                    false,
                    "public",
                    parameterInfos.toTypedArray(),
                    method,
                    true
                )
            descriptor.existingClass=dataClumpClass
            val processor = IntroduceParameterObjectProcessor(allMethods[0], descriptor, parameterInfos, false)
            println("### running")
            processor.run()
            loopedOnce=true

        }
        createdClassMap.add(suggestedClassName)
        com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveAllDocuments()



    }


    fun refactorDataClumpContainingMethod(
        project: Project,
        method: PsiMethod,
        extractedClass: PsiClass,
        context: DataClumpTypeContext
    ) {

        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            println("### Start refactor method")
            val relevantParameters = context.data_clump_data.values.map { it.name }.toSet()
            for (param in method.parameterList.parameters) {
                println(param.name)
                if (param.name in relevantParameters) {

                    param.delete()
                }
            }
            println("### modify method")
            val type =
                JavaPsiFacade.getInstance(project)
                    .getElementFactory()
                    .createType(extractedClass)
            val psiParam =
                PsiElementFactory.getInstance(project)
                    .createParameter(extractedClass.name!!.decapitalize(), type)
            method.parameterList.add(psiParam)
            println("### modify method finnished")
            println(method.isValid())
            println(method.text)
            val doc = PsiDocumentManager.getInstance(project).getDocument(method.containingFile)!!
            com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveDocument(doc)
        }

    }

    val testJSON =
    """{
        'type':'data_clump',
        'key':'parameters_to_parameters_data_clump-lib/src/main/java/javatest/MathStuff.java-javatest.MathStuff/method/printLength(int x, int y, int z)-javatest.MathStuff/method/printMax(int x, int y, int z)-xyz',
        'probability':1,
        'from_file_path':'src/main/java/javatest/MathStuff.java'
        ,'from_class_or_interface_name':'MathStuff'
        ,'from_class_or_interface_key':'javatest.MathStuff',
        'from_method_name':'printLength',
        'from_method_key':'javatest.MathStuff/method/printLength(int x, int y, int z)',
        'to_file_path':'src/main/java/javatest/MathStuff.java',
        'to_class_or_interface_name':'MathStuff',
        to_class_or_interface_key':'javatest.MathStuff',
        'to_method_name':'printMax',
        'to_method_key':'javatest.MathStuff/method/printMax(int x, int y, int z)',
        'data_clump_type':'parameters_to_parameters_data_clump',
        'data_clump_data':{
            'javatest.MathStuff/method/printLength(int x, int y, int z)/parameter/x':{
                'key':'javatest.MathStuff/method/printLength(int x, int y, int z)/parameter/x',
                'name':'x',
                'type':'int',
                'probability':1,
                'modifiers':[],
                'to_variable':{
                    'key':'javatest.MathStuff/method/printMax(int x, int y, int z)/parameter/x',
                    'name':'x',
                    'type':'int',
                    'modifiers':[],
                    'position':{
                        'startLine':13,'
                        startColumn':30,
                        'endLine':13,
                        'endColumn':31
                    }
                },
                'position':{
                    'startLine':5,
                    'startColumn':33,
                    'endLine':5,
                    'endColumn':34
                }
            },
            'javatest.MathStuff/method/printLength(int x, int y, int z)/parameter/y':{
                'key':'javatest.MathStuff/method/printLength(int x, int y, int z)/parameter/y',
                'name':'y',
                'type':'int',
                'probability':1,
                'modifiers':[],
                'to_variable':{
                    'key':'javatest.MathStuff/method/printMax(int x, int y, int z)/parameter/y',
                    'name':'y',
                    'type':'int',
                    'modifiers':[],
                    'position':{
                        'startLine':13,
                        'startColumn':37,
                        'endLine':13,
                        'endColumn':38
                    }
                },
                'position':{
                    'startLine':5,
                    'startColumn':40,
                    'endLine':5,
                    'endColumn':41
                }
            },
            'javatest.MathStuff/method/printLength(int x, int y, int z)/parameter/z':{
                'key':'javatest.MathStuff/method/printLength(int x, int y, int z)/parameter/z',
                'name':'z',
                'type':'int',
                'probability':1,
                'modifiers':[],
                'to_variable':{
                    'key':'javatest.MathStuff/method/printMax(int x, int y, int z)/parameter/z',
                    'name':'z',
                    'type':'int',
                    'modifiers':[],
                    'position':{
                        'startLine':13,
                        'startColumn':44,
                        'endLine':13,
                        'endColumn':45
                    }
                },
                'position':{
                    'startLine':5,
                    'startColumn':47,
                    'endLine':5,
                    'endColumn':48
                }
            }
        }
    }"""

    override fun run() {

        VirtualFileManager.getInstance().syncRefresh()
        val projectManager = ProjectManager.getInstance()
        val project = projectManager.loadAndOpenProject(input.toPath().toString())!!
        PsiManager.getInstance(project).dropPsiCaches()
        val context =
            Gson().fromJson<DataClumpTypeContext>(testJSON, DataClumpTypeContext::class.java)
            println("### Start refactor")
            introduceParameterObject(project, context, "Point")
            println("### finnished refactor")



        /*while (true){
            println("Please INPUT!!!!")
            val my_input= readLine()!!
            val json=Gson().fromJson(my_input,Map::class.java)
            val splitted=my_input?.split("\t\t")!!
            val filePath=splitted[0]
            val lineNumber=splitted[1].toInt()
            val columnNumber=splitted[2].toInt()
            val psiFile = PsiManager.getInstance(project).findFile(VirtualFileManager.getInstance().findFileByUrl("file://$filePath")!!)!!
            psiFile.let {
                val offset = calculateOffset(it.text, lineNumber, columnNumber)

                val element=it.findElementAt(offset)
                print(element.toString())
            }


        }*/
        println("### saving")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        println("### exiting")
        exitProcess(0)
    }
}

class JavaKotlinDocExtractor : CliktCommand() {
    private val input by
    argument(help = "Path to the project").file(mustExist = true, canBeFile = false)
    private val output by argument(help = "Output directory").file(canBeFile = true)

    // Thread safe Map
    private val visitedClasses = mutableMapOf<String, Boolean>()

    /**
     * Walks through files in the project, extracts all methods in each Java and Korlin file and
     * saves the method name and the corresponding JavaDoc to the output file.
     */
    override fun run() {
        println("Starting")
        deleteAllFilesRecursiveInFolder(output)

        // Delete caches
        val projectManager = ProjectManager.getInstance()
        val project = projectManager.loadAndOpenProject(input.toPath().toString())
        try {
            if (project != null) {
                // projectManager.closeAndDispose(project)
            }
        } catch (e: Exception) {
            println("Error while closing project")
            println(e)
        }

        val repositoryOpener = getKotlinJavaRepositoryOpener()
        repositoryOpener.openProjectWithResolve(input.toPath()) { project ->
            ApplicationManager.getApplication().invokeAndWait {
                val modules = project.extractModules()
                parseAllModulesSourceCodeToAstClasses(modules)
                parseAllModulesAuxClassesToAstClasses(modules)
            }
            true
        }
        println("Done")

        exitProcess(0)
    }

    private fun parseAllModulesAuxClassesToAstClasses(modules: List<Module>) {
        parseAllModulesToAstClasses(modules, false)
    }

    private fun parseAllModulesSourceCodeToAstClasses(modules: List<Module>) {
        parseAllModulesToAstClasses(modules, true)
    }

    private fun parseAllModulesToAstClasses(modules: List<Module>, onlySourceCode: Boolean) {
        for (module in modules) {
            println("Processing module: " + module.name)
            try {
                parseModuleToAstClasses(module, onlySourceCode)
            } catch (e: Exception) {
                println("Error while processing module: " + module.name)
                println(e)
            }
        }
    }

    private fun deleteAllFilesRecursiveInFolder(folder: File) {
        println("Deleting all files in folder: " + folder.path)
        deleteAllFilesInDirectory(folder)
    }

    private fun deleteAllFilesInDirectory(folder: File) {
        if (folder.isDirectory) {
            for (file in folder.listFiles()) {
                deleteAllFilesInDirectory(file)
            }
        }
        folder.delete()
    }

    private fun hasTypeVariable(psiClass: PsiClass): Boolean {
        for (typeParameter in psiClass.typeParameters) {
            if (typeParameter is PsiTypeParameter) { // TODO: always true?
                return true
            }
        }
        return false
    }

    private fun hasTypeVariable(type: PsiType): Boolean {
        var hasTypeVar = false

        type.accept(
            object : PsiTypeVisitor<Unit>() {
                override fun visitType(type: PsiType) {
                    if (type is PsiClassType) {
                        val resolve = type.resolve()
                        if (resolve is PsiTypeParameter) {
                            hasTypeVar = true
                        }
                        for (typeArg in type.parameters) {
                            typeArg.accept(this)
                        }
                    }
                }
            }
        )

        return hasTypeVar
    }

    private fun log(psiClass: PsiClass, message: String) {
        val debug = psiClass.name.equals("Caffeine")
        if (debug) {
            println("LOG: " + psiClass.name + ": " + message)
        }
    }

    private fun parseModuleToAstClasses(module: Module, onlySourceCode: Boolean) {
        val javaClasses = module.getPsiClasses("java")

        for (psiClass in javaClasses) {
            log(psiClass, "Processing (in module: " + module.name + ") class: " + psiClass.name)
            // check if psiClass is a genric like E from class MyList<E> then skip it

            if (onlySourceCode) { // Step 1. Parse all source code classes
                val isSourceCode = true
                processClass(psiClass, module, isSourceCode)
            } else { // Step 2. Parse all aux classes, since we have all source code classes already
                // parsed
                processSupersForClassAndInterfaces(psiClass, module)
            }
        }
        println("Done with module: " + module.name)
    }

    private fun processSupersForClassAndInterfaces(psiClass: PsiClass, module: Module) {
        // println("Processing Aux class: "+psiClass.name+" in module: "+module.name)
        // get all super classes and interfaces, since we have all source files already parsed
        val supers = psiClass.supers
        for (superClass in supers) {
            if (superClass is PsiClass) {
                processClass(superClass, module, false)
                // also process their supers
                processSupersForClassAndInterfaces(superClass, module)
            }
        }

        // get all super interfaces
        val superInterfaces = psiClass.interfaces
        for (superInterface in superInterfaces) {
            if (superInterface is PsiClass) {
                processClass(superInterface, module, false)
                // also process their supers
                processSupersForClassAndInterfaces(superInterface, module)
            }
        }
    }

    private fun processClass(psiClass: PsiClass, module: Module, isSourceCode: Boolean) {
        val debug = psiClass.name.equals("Caffeine")

        log(psiClass, "START Processing class: " + psiClass.name + " in module: " + module.name)

        val classContextKey = getClassOrInterfaceKey(psiClass)

        if (classContextKey.equals("java.lang.Object")) {
            log(psiClass, "-- Skipping class: " + psiClass.name + " because it is Object")
            return
        }

        if (psiClass is PsiTypeParameter) {
            log(psiClass, "-- Skipping class: " + psiClass.name + " because it is a generic")
            return
        }

        log(
            psiClass,
            "-- Check if class is already visited: " +
                    psiClass.name +
                    " in module: " +
                    module.name
        )

        // val classContextKey = getClassOrInterfaceKey(psiClass)
        val fileName = module.name + "/" + classContextKey + ".json"

        // check if class is already visited
        if (visitedClasses.containsKey(fileName)) {
            log(psiClass, "-- Skipping class: " + psiClass.name + " because it is already visited")
            return
        } else {
            visitedClasses[fileName] = true
            log(psiClass, "-- class not visited yet: " + psiClass.name)
        }

        val classContext = visitClassOrInterface(psiClass, isSourceCode)
        saveClassContextToFile(classContext, psiClass, fileName)
    }

    private fun saveClassContextToFile(
        classContext: ClassOrInterfaceTypeContext,
        psiClass: PsiClass,
        fileName: String
    ) {
        log(psiClass, "-- Getting gson instance in class: " + psiClass.name)

        log(psiClass, "-- Writing to file: " + fileName)
        log(psiClass, "-- Class file path: " + psiClass.containingFile.virtualFile.path)
        val outputFile = File("$output/" + fileName)
        outputFile.parentFile.mkdirs() // create parent directories if they do not exist
        val fileContent = objectToString(classContext)
        outputFile.writeText(fileContent, Charsets.UTF_8)
    }

    private fun objectToString(classContext: ClassOrInterfaceTypeContext): String {
        val gson = GsonBuilder().setPrettyPrinting().create()
        var asJsonString = gson.toJson(classContext)
        var fileContent = asJsonString

        fileContent = fileContent.replace("\\u003c", "<")
        fileContent = fileContent.replace("\\u003e", ">")

        return fileContent
    }

    private fun visitClassOrInterface(
        psiClass: PsiClass,
        isSourceCode: Boolean
    ): ClassOrInterfaceTypeContext {
        log(psiClass, "-- visitClassOrInterface: " + psiClass.name)

        log(psiClass, "-- Creating classContext")
        val classContext = ClassOrInterfaceTypeContext()
        log(psiClass, "-- Created classContext")

        log(psiClass, "-- Extracting class informations")
        extractClassInformations(psiClass, classContext, isSourceCode)

        classContext.file_path = psiClass.containingFile.virtualFile.path

        log(psiClass, "-- Extracting fields")
        extractFields(psiClass, classContext)

        log(psiClass, "-- Extracting methods")
        extractMethods(psiClass, classContext)

        log(psiClass, "-- Extracting extends and implements")
        extractExtendsAndImplements(psiClass, classContext)

        /**
         * // get all classes and interfaces that are defined inside this class val innerClasses =
         * psiClass.innerClasses for(innerClass in innerClasses){ val innerClassContext =
         * visitClassOrInterface(innerClass) classContext.innerDefinedClasses[innerClassContext.key]
         * = innerClassContext } val innerInterfaces = psiClass.innerClasses for(innerInterface in
         * innerInterfaces){ val innerInterfaceContext = visitClassOrInterface(innerInterface)
         * classContext.innerDefinedInterfaces[innerInterfaceContext.key] = innerInterfaceContext }
         */

        // if this class is an inner class, get the outer class or interface
        val outerClass = psiClass.containingClass
        if (outerClass != null) {
            val outClassKey = getClassOrInterfaceKey(outerClass)
            classContext.definedInClassOrInterfaceTypeKey = outClassKey
        }

        log(psiClass, "-- Done with visitClassOrInterface: " + psiClass.name)
        return classContext
    }

    private fun extractClassInformations(
        psiClass: PsiClass,
        classContext: ClassOrInterfaceTypeContext,
        isSourceCode: Boolean
    ) {
        classContext.name = psiClass.name ?: ""
        classContext.key = getClassOrInterfaceKey(psiClass)
        val isInterface = psiClass.isInterface
        classContext.type = "class"
        if (isInterface) {
            classContext.type = "interface"
        }

        var nameRange = psiClass.textRange
        val nameTextRange = psiClass.nameIdentifier?.textRange
        if (nameTextRange != null) {
            nameRange = nameTextRange
        }

        classContext.hasTypeVariable = hasTypeVariable(psiClass)
        classContext.auxclass = !isSourceCode // if class is not source code, it is an aux class

        classContext.position =
            getAstPosition("extractClass", nameRange, psiClass.project, psiClass.containingFile)
        classContext.anonymous = psiClass.name == null

        // Extract the modifiers
        classContext.modifiers = getModifiers(psiClass.modifierList)
    }

    private fun extractExtendsAndImplements(
        psiClass: PsiClass,
        classContext: ClassOrInterfaceTypeContext
    ) {
        // Extract the interfaces this class implements
        val implementsLists = psiClass.implementsList
        if (implementsLists != null) {
            val superinterfaces = implementsLists.referenceElements
            for (superinterface in superinterfaces) {
                val psiSuperInterface: PsiClass = superinterface.resolve() as PsiClass
                val fullQualifiedName: String = getClassOrInterfaceKey(psiSuperInterface)
                if (fullQualifiedName != null) {
                    classContext.implements_.add(fullQualifiedName)
                }
            }
        }

        // Extract the classes this class extends
        val extendsLists = psiClass.extendsList
        if (extendsLists != null) {
            val superclasses = extendsLists.referenceElements
            for (superclass in superclasses) {
                val psiSuperClass: PsiClass = superclass.resolve() as PsiClass
                val fullQualifiedName: String = getClassOrInterfaceKey(psiSuperClass)
                if (fullQualifiedName != null) {
                    classContext.extends_.add(fullQualifiedName)
                }
            }
        }
    }

    private fun getClassOrInterfaceKey(psiClass: PsiClass): String {
        val packageName = psiClass.qualifiedName?.split(".")?.dropLast(1)?.joinToString(".")
        return if (packageName != null) "$packageName.${psiClass.name}" else psiClass.name ?: ""
    }

    private fun getAstPosition(
        logMessage: String,
        textRangeOptional: TextRange?,
        project: Project,
        file: PsiFile
    ): AstPosition {
        val document = PsiDocumentManager.getInstance(project).getDocument(file)
        val position = AstPosition()
        try {
            if (document != null && textRangeOptional != null) {
                val textRange: TextRange = textRangeOptional
                val startOffset = textRange.startOffset
                val endOffset = textRange.endOffset
                position.startLine = document.getLineNumber(startOffset) + 1
                position.endLine = document.getLineNumber(endOffset) + 1
                position.endColumn =
                    endOffset - document.getLineStartOffset(position.endLine - 1) + 1
                position.startColumn =
                    startOffset - document.getLineStartOffset(position.startLine - 1) + 1
            } else {
                // Handle the case where the document is null, maybe log an error or throw an
                // exception
            }
        } catch (e: Exception) {
            println("Error while getting position for: " + logMessage)
            println(e)
        }
        return position
    }

    private fun extractFields(psiClass: PsiClass, classContext: ClassOrInterfaceTypeContext) {
        val classKey = getClassOrInterfaceKey(psiClass)
        val memberFieldKeyPre = classKey + "/memberField/"

        log(psiClass, "-- Extracting fields for class: " + classKey)
        val fields = psiClass.fields
        for (field in fields) {

            log(psiClass, "---- field: " + field.name)
            log(psiClass, "------ field.text: " + field.text)
            log(psiClass, "------ field.textRange: " + field.textRange)
            log(psiClass, "------ field.type: " + field.type)
            log(psiClass, "------ field.type.canonicalText: " + field.type.canonicalText)

            val fieldContext = MemberFieldParameterTypeContext()

            // Set the properties of the fieldContext based on the field
            val fieldName: String = field.name
            fieldContext.name = fieldName

            fieldContext.type = field.type.canonicalText

            // TODO check if this is correct
            fieldContext.hasTypeVariable = hasVariableTypeVariable(field)

            // Set the position
            fieldContext.position =
                getAstPosition(
                    "extractField",
                    field.nameIdentifier.textRange,
                    psiClass.project,
                    psiClass.containingFile
                )

            fieldContext.classOrInterfaceKey = classKey

            // Extract the modifiers
            fieldContext.modifiers = getModifiers(field.modifierList)

            fieldContext.key = memberFieldKeyPre + fieldName

            // Add the fieldContext to the classContext.fields
            classContext.fields[fieldContext.key!!] = fieldContext
        }
    }

    private fun hasVariableTypeVariable(variable: PsiVariable): Boolean {
        val type = variable.type
        return hasTypeVariable(type)
    }

    private fun extractMethods(psiClass: PsiClass, classContext: ClassOrInterfaceTypeContext) {
        val classOrInterfaceKey = getClassOrInterfaceKey(psiClass)

        // If you want to only get the fields of the top-level class and not any inner classes, you
        // would need to add a check to exclude fields that belong to inner classes. One way to do
        // this could be to check the parent of each field and see if it's the top-level class node.

        // If you want to only get the fields of the top-level class and not any inner classes, you
        // would need to add a check to exclude fields that belong to inner classes. One way to do
        // this could be to check the parent of each field and see if it's the top-level class node.
        val methods = psiClass.methods
        for (method in methods) {
            if (method.isConstructor) { // skip constructors
                continue
            }

            val methodContext = MethodTypeContext()
            // Set the properties of the methodContext based on the method
            methodContext.name = method.name
            methodContext.type = method.returnType?.canonicalText ?: null

            // System.out.println("----------------");
            // System.out.println("methodContext.name: "+methodContext.name);

            var nameRange = method.textRange
            val nameTextRange = method.nameIdentifier?.textRange
            if (nameTextRange != null) {
                nameRange = nameTextRange
            }
            // Set the position
            methodContext.position =
                getAstPosition(
                    "extractMethod",
                    nameRange,
                    psiClass.project,
                    psiClass.containingFile
                )
            methodContext.classOrInterfaceKey = classOrInterfaceKey

            // Extract the modifiers and check for @Override annotation
            methodContext.modifiers = getModifiers(method.modifierList)

            methodContext.overrideAnnotation =
                method.hasAnnotation("Override") // quick way to check if method is overridden
            // if method is overridden set overrideAnnotation to true
            if (!methodContext.overrideAnnotation) {
                val superMethods = method.findSuperMethods()
                if (superMethods.isNotEmpty()) {
                    methodContext.overrideAnnotation = true
                }
            }

            // Extract the parameters
            val parameters = method.parameterList.parameters
            for (parameter in parameters) {
                val parameterContext = MethodParameterTypeContext()
                parameterContext.name = parameter.name

                parameterContext.type = parameter.type.canonicalText
                parameterContext.hasTypeVariable = hasVariableTypeVariable(parameter)

                // Set the position
                var paramRange = parameter.textRange
                val paramTextRange = parameter.nameIdentifier?.textRange
                if (paramTextRange != null) {
                    paramRange = paramTextRange
                }
                parameterContext.position =
                    getAstPosition(
                        "extractParameter",
                        paramRange,
                        psiClass.project,
                        psiClass.containingFile
                    )

                // Extract the modifiers
                parameterContext.modifiers = getModifiers(parameter.modifierList)

                // parameterContext.methodKey = methodContext.key;
                // We cant set the methodKey directly, since the method key is not yet defined

                // Add the parameterContext to the methodContext.parameters
                methodContext.parameters.add(parameterContext)
            }

            // set method key
            // Java method key is the method signature. The signature is: method name + parameters
            // (type and order)
            var methodContextParametersKey = classOrInterfaceKey + "/method/" + method.name + "("
            val amountParameters: Int = methodContext.parameters.size
            for (i in 0 until amountParameters) {
                val parameterContext: MethodParameterTypeContext = methodContext.parameters.get(i)
                val parameterTypeAndName = parameterContext.type + " " + parameterContext.name
                methodContextParametersKey += parameterTypeAndName
                if (i + 1 < amountParameters) {
                    methodContextParametersKey += ", "
                }
            }
            methodContextParametersKey += ")"
            for (i in 0 until amountParameters) {
                val parameterContext: MethodParameterTypeContext = methodContext.parameters.get(i)
                parameterContext.key =
                    methodContextParametersKey + "/parameter/" + parameterContext.name
            }
            methodContext.key = methodContextParametersKey
            for (parameterContext in methodContext.parameters) {
                parameterContext.methodKey = methodContext.key
            }

            // Add the methodContext to the classContext.methods
            classContext.methods[methodContext.key!!] = methodContext
        }
    }

    private fun getModifiers(modifierList: PsiModifierList?): ArrayList<String> {
        val returnList = ArrayList<String>()
        if (modifierList == null) {
            return returnList
        }

        // Split the text of the PsiModifierList into words
        val words =
            modifierList.text.split(
                "\\s+".toRegex()
            ) // this way we keep the order of the modifiers

        // Check each word to see if it's a valid Java modifier
        for (word in words) {
            if (PsiModifier.MODIFIERS.contains(word)) {
                returnList.add(word)
            }
        }

        return returnList
    }

    private fun Module.getPsiClasses(extension: String): List<PsiClass> {
        val psiFiles = this.findPsiFilesByExtension(extension)
        return psiFiles.flatMap { it.extractElementsOfType(PsiClass::class.java) }
    }
}
