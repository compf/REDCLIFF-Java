package org.jetbrains.research.refactoringDemoPlugin

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.options.default
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
import org.jetbrains.research.pluginUtilities.openRepository.getKotlinJavaRepositoryOpener
import org.jetbrains.research.refactoringDemoPlugin.util.extractElementsOfType
import org.jetbrains.research.refactoringDemoPlugin.util.extractModules
import org.jetbrains.research.refactoringDemoPlugin.util.findPsiFilesByExtension
import com.intellij.refactoring.introduceParameterObject.*
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import io.ktor.util.date.*
import com.intellij.openapi.project.ex.ProjectManagerEx
import dataClumpRefactoring.*
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.ReadAction
import java.nio.file.Path

object PluginRunner : ApplicationStarter {
    @Deprecated("Specify it as `id` for extension definition in a plugin descriptor")
    override val commandName: String
        get() = "DemoPluginCLI"

    override val requiredModality: Int
        get() =ApplicationStarter.NOT_IN_EDT

    override fun main(args: List<String>) {
        DataClumpRefactorer().main(args.drop(1))
    }
}
class DataClumpFinderRunner :CliktCommand(){
    private val input by
    argument(help = "Path to the project").file(mustExist = true, canBeFile = false)
    private val output by argument(help = "Output directory").file(canBeFile = true)
    override fun run(){
        VirtualFileManager.getInstance().syncRefresh()
        val projectManager = ProjectManager.getInstance()
        val project = projectManager.loadAndOpenProject(input.toPath().toString())!!
        val finder= DataClumpFinder(project)
        val context=finder.run()
       val json= Gson().toJson(context)
        java.nio.file.Files.writeString(output.toPath(),json)
        exitProcess(0)

    }
}

class DataClumpRefactorer : CliktCommand() {
    private val myProjectPath by
    argument(help = "Path to the project").file(mustExist = true, canBeFile = false)
    private val dcContextPath by option(help = "Path to data clump type context file").file(canBeFile = true, mustExist = true)
    private val usageContextPath by option(help = "Path to  usage type context file").file(canBeFile = true, mustExist = true)
    private val runnerType by option(help = "Path to  name finding context file").default("manual")
    //https://github.com/JetBrains/intellij-community/blob/cb1f19a78bb9a4db29b33ff186cdb60ceab7f64c/java/java-impl-refactorings/src/com/intellij/refactoring/encapsulateFields/JavaEncapsulateFieldHelper.java#L86

    fun calcDepth(element:PsiElement):Int{
        var depth=0
        var currentElement:PsiElement?=element
        while(currentElement!=null){
            depth++
            currentElement=currentElement.parent
        }
        return depth
    }
    override fun run() {
        println("### starting refactor")

        //VirtualFileManager.getInstance().syncRefresh()
       // val projectManager = ProjectManagerEx.getInstanceEx()
       // projectManager.closeAndDisposeAllProjects(true)
        val refactorer= dataClumpRefactoring.ManualDataClumpRefactorer(myProjectPath)
        //var projectPath="/home/compf/data/uni/master/sem4/intelliJTest"
        val opener=getKotlinJavaRepositoryOpener()
        print("init")
        try {
            val a = opener.openProjectWithResolve(myProjectPath.toPath()) {


                val project = it
                ApplicationManager.getApplication().invokeAndWait {
                    val dcContext = Gson().fromJson<DataClumpsTypeContext>(
                        java.nio.file.Files.readString(dcContextPath!!.toPath()),
                        DataClumpsTypeContext::class.java
                    )

                    var counter=0
                    for ((key, value) in dcContext.data_clumps) {
                        println("Starting refactor $key")
                        refactorer.refactorDataClump(project, SuggestedNameWithDataClumpTypeContext("Test"+counter, value))
                        println("### refactored $key")
                        refactorer.commitAll(project)
                        counter++

                    }
                }











                return@openProjectWithResolve true
            }
        }
        catch (ex:Throwable){
            throw ex
        }







        println("### starting refactor")
        println("### finnished refactor")

        println("### saving")
        println("### exiting")
        Thread.sleep(10*1000)
        exitProcess(0)
    }
}
