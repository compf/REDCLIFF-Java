package org.jetbrains.research.refactoringDemoPlugin

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import com.google.gson.GsonBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import org.jetbrains.kotlin.idea.hierarchy.overrides.isOverrideHierarchyElement
import org.jetbrains.research.pluginUtilities.openRepository.getKotlinJavaRepositoryOpener
import org.jetbrains.research.refactoringDemoPlugin.parsedAstTypes.*
import org.jetbrains.research.refactoringDemoPlugin.util.extractElementsOfType
import org.jetbrains.research.refactoringDemoPlugin.util.extractModules
import org.jetbrains.research.refactoringDemoPlugin.util.findPsiFilesByExtension
import java.io.File
import kotlin.system.exitProcess


object PluginRunner : ApplicationStarter {
    @Deprecated("Specify it as `id` for extension definition in a plugin descriptor")
    override val commandName: String
        get() = "DemoPluginCLI"

    override val requiredModality: Int
        get() = ApplicationStarter.NOT_IN_EDT

    override fun main(args: List<String>) {
        JavaKotlinDocExtractor().main(args.drop(1))
    }
}

class JavaKotlinDocExtractor : CliktCommand() {
    private val input by argument(help = "Path to the project").file(mustExist = true, canBeFile = false)
    private val output by argument(help = "Output directory").file(canBeFile = true)

    /**
     * Walks through files in the project, extracts all methods in each Java and Korlin file
     * and saves the method name and the corresponding JavaDoc to the output file.
     */
    override fun run() {
        println("Starting")
        deleteAllFilesRecursiveInFolder(output)

        // Delete caches
        val projectManager = ProjectManager.getInstance()
        val project = projectManager.loadAndOpenProject(input.toPath().toString())
        try{
            if (project != null) {
                //projectManager.closeAndDispose(project)
            }
        } catch (e: Exception){
            println("Error while closing project")
            println(e)
        }

        val repositoryOpener = getKotlinJavaRepositoryOpener()
        repositoryOpener.openProjectWithResolve(input.toPath()) { project ->
            ApplicationManager.getApplication().invokeAndWait {
                val modules = project.extractModules()
                for (module in modules) {
                    println("Processing module: " + module.name)
                    try {
                        extractClassesViaPsi(module)
                    } catch (e: Exception) {
                        println("Error while processing module: " + module.name)
                        println(e)
                    }
                }
            }
            true
        }
        println("Done")

        exitProcess(0)
    }


    private fun deleteAllFilesRecursiveInFolder(folder: File){
        println("Deleting all files in folder: "+folder.path)
        deleteAllFilesInDirectory(folder)
    }

    private fun deleteAllFilesInDirectory(folder: File){
        if(folder.isDirectory){
            for(file in folder.listFiles()){
                deleteAllFilesInDirectory(file)
            }
        }
        folder.delete()
    }

    private fun hasTypeVariable(psiClass: PsiClass): Boolean{
        for (typeParameter in psiClass.typeParameters) {
            if (typeParameter is PsiTypeParameter) { // TODO: always true?
                return true
            }
        }
        return false;
    }

    private fun hasTypeVariable(type: PsiType): Boolean {
        var hasTypeVar = false

        type.accept(object : PsiTypeVisitor<Unit>() {
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
        })

        return hasTypeVar
    }

    private fun extractClassesViaPsi(module: Module){
        val javaClasses = module.getPsiClasses("java")

        for(psiClass in javaClasses) {
            println("Processing (in module: "+module.name+") class: "+psiClass.name)
            // check if psiClass is a genric like E from class MyList<E> then skip it
            if(psiClass is PsiTypeParameter){
                println("Skipping class: "+psiClass.name+" because it is a generic")
                continue
            }

            processClass(psiClass, module)
        }
        println("Done with module: "+module.name)
    }

    private fun processClass(psiClass: PsiClass, module: Module){
        println("Processing class: "+psiClass.name)
        val classContext = visitClassOrInterface(psiClass)

        println("Getting gson instance in class: "+psiClass.name)
        //val it = DatasetItem(psiClass.name ?: "", "Hello", qualifiedName, superClasses)

        val fileName = module.name+"/"+classContext.key + ".json"
        println("Writing to file: "+fileName)
        println("Class file path: "+psiClass.containingFile.virtualFile.path);
        val outputFile = File("$output/"+fileName);
        outputFile.parentFile.mkdirs() // create parent directories if they do not exist
        val fileContent = objectToString(classContext)
        outputFile.writeText(fileContent, Charsets.UTF_8)
    }

    private fun objectToString(classContext: ClassOrInterfaceTypeContext): String{
        val gson = GsonBuilder().setPrettyPrinting().create()
        var asJsonString = gson.toJson(classContext)
        var fileContent = asJsonString;

        fileContent = fileContent.replace("\\u003c", "<")
        fileContent = fileContent.replace("\\u003e", ">")


        return fileContent;
    }

    private fun visitClassOrInterface(psiClass: PsiClass): ClassOrInterfaceTypeContext{
        println("visitClassOrInterface: "+psiClass.name)

        println("Creating classContext")
        val classContext = ClassOrInterfaceTypeContext()
        println("Created classContext")

        println("Extracting class informations")
        extractClassInformations(psiClass,classContext)

        classContext.file_path = psiClass.containingFile.virtualFile.path

        println("Extracting fields")
        extractFields(psiClass,classContext)

        println("Extracting methods")
        extractMethods(psiClass,classContext)

        println("Extracting extends and implements")
        extractExtendsAndImplements(psiClass,classContext)

        /**
        // get all classes and interfaces that are defined inside this class
        val innerClasses = psiClass.innerClasses
        for(innerClass in innerClasses){
            val innerClassContext = visitClassOrInterface(innerClass)
            classContext.innerDefinedClasses[innerClassContext.key] = innerClassContext
        }
        val innerInterfaces = psiClass.innerClasses
        for(innerInterface in innerInterfaces){
            val innerInterfaceContext = visitClassOrInterface(innerInterface)
            classContext.innerDefinedInterfaces[innerInterfaceContext.key] = innerInterfaceContext
        }
        */

        // if this class is an inner class, get the outer class or interface
        val outerClass = psiClass.containingClass
        if(outerClass != null){
            val outClassKey = getClassOrInterfaceKey(outerClass)
            classContext.definedInClassOrInterfaceTypeKey = outClassKey
        }

        println("Done with visitClassOrInterface: "+psiClass.name)
        return classContext
    }

    private fun extractClassInformations(psiClass: PsiClass,classContext: ClassOrInterfaceTypeContext){
        classContext.name = psiClass.name ?: ""
        classContext.key = getClassOrInterfaceKey(psiClass)
        val isInterface = psiClass.isInterface
        classContext.type = "class"
        if(isInterface) {
            classContext.type = "interface"
        }

        var nameRange = psiClass.textRange
        val nameTextRange = psiClass.nameIdentifier?.textRange
        if(nameTextRange!=null){
            nameRange = nameTextRange
        }

        classContext.hasTypeVariable = hasTypeVariable(psiClass)

        classContext.position = getAstPosition(nameRange,psiClass.project,psiClass.containingFile)
        classContext.anonymous = psiClass.name == null

        // Extract the modifiers
        classContext.modifiers = getModifiers(psiClass.modifierList)
    }

    private fun extractExtendsAndImplements(psiClass: PsiClass, classContext: ClassOrInterfaceTypeContext){
        // Extract the interfaces this class implements
        val implementsLists = psiClass.implementsList
        if (implementsLists != null) {
            val superinterfaces = implementsLists.referenceElements
            for (superinterface in superinterfaces) {
                val fullQualifiedName: String = superinterface.qualifiedName
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
                val fullQualifiedName: String = superclass.qualifiedName
                if (fullQualifiedName != null) {
                    classContext.extends_.add(fullQualifiedName)
                }
            }
        }
    }

    private fun getClassOrInterfaceKey(psiClass: PsiClass): String{
        val packageName = psiClass.qualifiedName?.split(".")?.dropLast(1)?.joinToString(".")
        return if(packageName != null) "$packageName.${psiClass.name}" else psiClass.name ?: ""
    }

    private fun getAstPosition(textRange: TextRange, project: Project, file: PsiFile): AstPosition {
        val document = PsiDocumentManager.getInstance(project).getDocument(file)
        val position = AstPosition()
        if (document != null) {
            val startOffset = textRange.startOffset
            val endOffset = textRange.endOffset
            position.startLine = document.getLineNumber(startOffset) + 1
            position.endLine = document.getLineNumber(endOffset) + 1
            position.endColumn = endOffset - document.getLineStartOffset(position.endLine - 1) + 1
            position.startColumn = startOffset - document.getLineStartOffset(position.startLine - 1) + 1
        } else {
            // Handle the case where the document is null, maybe log an error or throw an exception
        }
        return position
    }

    private fun extractFields(psiClass: PsiClass,classContext: ClassOrInterfaceTypeContext){
        val classKey = getClassOrInterfaceKey(psiClass)
        val memberFieldKeyPre = classKey + "/memberField/"

        println("Extracting fields for class: "+classKey)
        println("psiClass.fields.size: "+psiClass.fields.size);
        println("all fields size: "+psiClass.allFields.size)
        val fields = psiClass.fields
        for(field in fields){
            println("-- field: "+field.name)
            println("---- field.text: "+field.text)
            println("---- field.textRange: "+field.textRange)
            println("---- field.type: "+field.type)
            println("---- field.type.canonicalText: "+field.type.canonicalText)

            val fieldContext = MemberFieldParameterTypeContext()

            // Set the properties of the fieldContext based on the field
            val fieldName: String = field.name
            fieldContext.name = fieldName

            fieldContext.type = field.type.canonicalText;


            //TODO check if this is correct
            fieldContext.hasTypeVariable = hasVariableTypeVariable(field)

            // Set the position
            fieldContext.position = getAstPosition(field.nameIdentifier.textRange,psiClass.project,psiClass.containingFile)

            fieldContext.classOrInterfaceKey = classKey;

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

    private fun extractMethods(psiClass: PsiClass, classContext: ClassOrInterfaceTypeContext){
        val classOrInterfaceKey = getClassOrInterfaceKey(psiClass)

        // If you want to only get the fields of the top-level class and not any inner classes, you would need to add a check to exclude fields that belong to inner classes. One way to do this could be to check the parent of each field and see if it's the top-level class node.

        // If you want to only get the fields of the top-level class and not any inner classes, you would need to add a check to exclude fields that belong to inner classes. One way to do this could be to check the parent of each field and see if it's the top-level class node.
        val methods = psiClass.methods
        for (method in methods) {
            if(method.isConstructor){ // skip constructors
                continue
            }

            val methodContext = MethodTypeContext()
            // Set the properties of the methodContext based on the method
            methodContext.name = method.name
            methodContext.type = method.returnType?.canonicalText ?: null

            //System.out.println("----------------");
            //System.out.println("methodContext.name: "+methodContext.name);

            var nameRange = method.textRange
            val nameTextRange = method.nameIdentifier?.textRange
            if(nameTextRange!=null){
                nameRange = nameTextRange
            }
            // Set the position
            methodContext.position = getAstPosition(nameRange,psiClass.project,psiClass.containingFile)
            methodContext.classOrInterfaceKey = classOrInterfaceKey

            // Extract the modifiers and check for @Override annotation
            methodContext.modifiers = getModifiers(method.modifierList)


            methodContext.overrideAnnotation = method.hasAnnotation("Override") // quick way to check if method is overridden
            // if method is overridden set overrideAnnotation to true
            if(!methodContext.overrideAnnotation){
                val superMethods = method.findSuperMethods()
                if(superMethods.isNotEmpty()){
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
                if(paramTextRange!=null){
                    paramRange = paramTextRange
                }
                parameterContext.position = getAstPosition(paramRange,psiClass.project,psiClass.containingFile)

                // Extract the modifiers
                parameterContext.modifiers = getModifiers(parameter.modifierList)

                //parameterContext.methodKey = methodContext.key;
                // We cant set the methodKey directly, since the method key is not yet defined

                // Add the parameterContext to the methodContext.parameters
                methodContext.parameters.add(parameterContext)
            }

            // set method key
            // Java method key is the method signature. The signature is: method name + parameters (type and order)
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
                parameterContext.key = methodContextParametersKey + "/parameter/" + parameterContext.name
            }
            methodContext.key = methodContextParametersKey
            for (parameterContext in methodContext.parameters) {
                parameterContext.methodKey = methodContext.key
            }


            // Add the methodContext to the classContext.methods
            classContext.methods[methodContext.key!!] = methodContext
        }
    }

    private fun getModifiers(modifierList: PsiModifierList?): ArrayList<String>{
        val returnList = ArrayList<String>()
        if(modifierList == null){
            return returnList
        }


        // TODO
        //  "modifiers": [
        //        "@Override\n",
        //        "public"
        //      ],
        val modifiers = modifierList.text.split(" ")
        for(modifier in modifiers){
            if(modifier != ""){
                returnList.add(modifier)
            }
        }
        return returnList
    }

    private fun Module.getPsiClasses(extension: String): List<PsiClass> {
        val psiFiles = this.findPsiFilesByExtension(extension)
        return psiFiles.flatMap { it.extractElementsOfType(PsiClass::class.java) }
    }

    data class DatasetItem(val methodName: String, val javaDoc: String, val qualifiedName: String?, val superClasses: String?)
}
