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
    private val output by option(help = "Path to  context where extracted paths are stored").file(canBeFile = true, mustExist = false)
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

        VirtualFileManager.getInstance().syncRefresh()
        val projectManager = ProjectManagerEx.getInstanceEx()
        projectManager.closeAndDisposeAllProjects(true)
        val refactorer= dataClumpRefactoring.ManualDataClumpRefactorer(myProjectPath)

        val project=projectManager.loadAndOpenProject(myProjectPath.absolutePath)!!
        PsiManager.getInstance(project).dropPsiCaches()
        val session=RefreshQueue.getInstance().createSession(false,true){

        }

        session.launch()

        try{
            val typeToken = object : TypeToken<Map<String, List<UsageInfo>>>() {}.type
            val usages=Gson().fromJson<Map<String,List<UsageInfo>>> (java.nio.file.Files.readString(usageContextPath!!.toPath()),typeToken)
            val usageElementMap=mutableMapOf<UsageInfo,PsiElement>()
            for(key in usages.keys){

               for(usg in usages[key]!!){


                   val pos = usg.range
                   val ele=refactorer.getElement(
                       project,
                      usg
                   )
                   ele?.let{
                       usageElementMap[usg]=ele
                   }

               }


            }
            val sorted=usageElementMap.entries.sortedWith(compareBy({it.key.symbolType},{-calcDepth(it.value)}))
            val nameService=PrimitiveNameService()
            for(pair in sorted){
                try{
                    refactorer.updateElementFromUsageInfo(project,pair.key,pair.value,nameService)
                }
                catch (ex:Throwable){
                    ex.printStackTrace()
                }

            }
        }

        catch (ex:Throwable){
            ex.printStackTrace()
        }




        println("### starting refactor")
        println("### finnished refactor")

        println("### saving")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        println("### exiting")
        Thread.sleep(10*1000)
        exitProcess(0)
    }
}
