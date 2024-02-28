package org.jetbrains.research.refactoringDemoPlugin

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
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
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.getTrustedState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.stateStore
import javaslang.collection.TreeMap
import org.jetbrains.kotlin.idea.codeInsight.shorten.ensureNoRefactoringRequestsBeforeRefactoring
import org.jetbrains.research.refactoringDemoPlugin.util.getAllRelevantVariables
import org.jetbrains.uast.evaluation.toConstant
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
inline fun <reified T> genericType() = object: TypeToken<T>() {}.type
enum class DataClumpContextInformation(val position:Int,val initializer: (dataPath:Path,DataClumpContextData)->Unit){
    DataclumpDetector(4,{dataPath,contextData->
        val path= dataPath.resolve("dataClumpDetectorContext.json")
        val json= java.nio.file.Files.readString(path)
        val context= Gson().fromJson<DataClumpsTypeContext>(json,DataClumpsTypeContext::class.java)
        contextData.dataClumps=context
    }),
    NameFinding(6,{dataPath,contextData->
        val path= dataPath.resolve("nameFindingContext.json")
        val json= java.nio.file.Files.readString(path)
        val context= Gson().fromJson<Map<String,String>>(json,Map::class.java)
        contextData.classNames=context
    }),
    ClassExtracting(7,{dataPath,contextData->
        val path= dataPath.resolve("classExtractionContext.json")
        val json= java.nio.file.Files.readString(path)
        val context= Gson().fromJson<Map<String,String>>(json,Map::class.java)
        contextData.extractedClassPaths=context
    }),
    ReferenceFinding(8, {dataPath,contextData->
        val path= dataPath.resolve("usageFindingContext.json")
        val json= java.nio.file.Files.readString(path)
        val typeToken =genericType<Map<String, ArrayList<UsageInfo>>>()

        val context= Gson().fromJson<Map<String, ArrayList<UsageInfo>>>(json,typeToken)
        contextData.usageInfos=context
    })

}
class DataClumpContextData{
    var dataClumps:DataClumpsTypeContext?=null
    var usageInfos:Map<String,Iterable<UsageInfo>>?=null
    var usageFinder:ReferenceFinder?=null
    var classNames:Map<String,String>?=null
    var extractedClassPaths:Map<String,String>?=null
}
class DataClumpRefactorer : CliktCommand() {
    private val myProjectPath by
    argument(help = "Path to the project").file(mustExist = true, canBeFile = false)

    //https://github.com/JetBrains/intellij-community/blob/cb1f19a78bb9a4db29b33ff186cdb60ceab7f64c/java/java-impl-refactorings/src/com/intellij/refactoring/encapsulateFields/JavaEncapsulateFieldHelper.java#L86
    private val dataPath by
    argument(help = "Path to the context data").file(mustExist = true, canBeFile = false).optional()
    private val availableContexts by argument(help = "Integer that identifies which contexts are available").optional()

    interface ProjectLoader{
        fun loadProject(path: Path,executor:PluginExecutor):Unit
    }
    class PluginExecutor(val myProjectPath:File,val dataPath:Path,val availableContexts:Int){
        val dataClumpContextData=DataClumpContextData()
        init {
            for(context in DataClumpContextInformation.values()){
                val mask=1 shl context.position
                if(availableContexts and mask!=0){
                    context.initializer(dataPath,dataClumpContextData)
                }
            }

        }


        fun executePlugin(project: Project){
            ApplicationManager.getApplication().invokeAndWait() {

                this.dataClumpContextData.usageFinder=UsageInfoBasedFinder(project,dataClumpContextData.usageInfos!!)
               val refactorer= ManualDataClumpRefactorer(myProjectPath,dataClumpContextData.usageFinder!!,ManualJavaClassCreator(dataClumpContextData.extractedClassPaths))
                var counter=0
                for ((key, value) in dataClumpContextData.dataClumps!!.data_clumps) {
                    this.dataClumpContextData.usageFinder!!.updateDataClumpKey(key)
                    println("Starting refactor $key")
                    refactorer.refactorDataClump(project, SuggestedNameWithDataClumpTypeContext(dataClumpContextData.classNames!![key]!!, value))
                    println("### refactored $key")
                    refactorer.commitAll(project)
                    counter++

                }
                println("### starting refactor")
                println("### finnished refactor")

                println("### saving")
                println("### exiting")
                Thread.sleep(10*1000)
                exitProcess(0)
                    }

        }
    }
    class OpenProjectWithResolveLoader:ProjectLoader{
        override fun loadProject(path: Path,executor: PluginExecutor): Unit {
            val opener=getKotlinJavaRepositoryOpener()
            var result= opener.openProjectWithResolve(path) {
                val project = it
                executor.executePlugin(project)
                return@openProjectWithResolve true

            }
        }
    }
    class OpenSingleProjectLoader:ProjectLoader{
        /*
        partially works with
            gradle project created in IntelliJ
            clean gradle project
            maven projects in IntelliJ
          completely works with
            intelliJ projects
            maven projects
        * */

        override fun loadProject(path: Path,executor: PluginExecutor): Unit {
            val opener=getKotlinJavaRepositoryOpener()
            try{
                var result= opener.openSingleProject(path) {
                    val project = it
                   executor.executePlugin(project)
                    return@openSingleProject true

                }
            }
            catch (e:Exception){
                println("### error")
                println(e)
                throw e
            }
        }
    }
    /*
    works with
        intellij projects
     works partially with
         intelli maven project (only new files)
         intelli gradle
     */
    class OpenProjectLoader:ProjectLoader{
        override fun loadProject(path: Path,executor: PluginExecutor): Unit {
            try {
                val result=ProjectUtil.openProject(path.toString(),null,true)
                executor.executePlugin(result!!)
            }catch (e:Exception) {
                println("### error")
                println(e)
                throw e
            }

        }
    }
    class OpenOrImportProjectLoader:ProjectLoader{
        /*
        partially works with
             raw gradle project
             raw maven project
             intelliJ maven project
             intelij gradle project
         fully works with
             intelliJ projects


         */
        override fun loadProject(path: Path,executor: PluginExecutor): Unit {
            try {
                val project=ProjectUtil.openOrImport(path,null,false)
                executor.executePlugin(project!!)

            }catch (e:Exception) {
                println("### error")
                println(e)
                throw e
            }

        }
    }
    /*
    Works with
        raw mavem project
        intellij project
    partially works with
        raw gradle project
        intelliJ gradle project
        intelliJ maven project

     */
    class LoadAndOpenProjectLoader:ProjectLoader{
        override fun loadProject(path: Path,executor: PluginExecutor): Unit {
            try {
                val projectManager = ProjectManager.getInstance()
                val project = projectManager.loadAndOpenProject(path.toString())!!
                executor.executePlugin(project)
            }
            catch (e:Exception){
                println("### error")
                println(e)
                throw e
            }

        }
    }
    fun decodeToInt(value:String):Int{
        var base=10
        val trimmed=value
        if(value.startsWith("0x")){
            base=16
            trimmed.substring(2)
        }
        else if(value.startsWith("0b")){
            base=2
            trimmed.substring(2)
        }
        else if(value.startsWith("0")){
            base=8
            trimmed.substring(1)
        }
        return Integer.parseInt(trimmed,base)
    }

    override fun run() {
        println("### starting refactor")

        //VirtualFileManager.getInstance().syncRefresh()
        val projectManager = ProjectManagerEx.getInstanceEx()
        //projectManager.loadProject()
       // projectManager.closeAndDisposeAllProjects(true)



        val opener=LoadAndOpenProjectLoader()
        print("init")
        var project:Project?=null
        try{
            val availableContext=availableContexts?.let { decodeToInt(it) } ?: Int.MAX_VALUE
            val executor=PluginExecutor(myProjectPath, dataPath!!.toPath(),availableContext)
             opener.loadProject(myProjectPath.toPath(),executor)
        }
        catch (e:Exception){
            println("### error")
          e.printStackTrace()
            throw e
        }



        exitProcess(0)
    }
}
