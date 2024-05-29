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
import com.google.gson.reflect.TypeToken
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.getTrustedState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import dataClumpRefactoring.*
import javaslang.collection.TreeMap
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.idea.codeInsight.shorten.ensureNoRefactoringRequestsBeforeRefactoring
import org.jetbrains.research.refactoringDemoPlugin.util.getAllRelevantVariables
import org.jetbrains.uast.evaluation.toConstant
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64

object PluginRunner : ApplicationStarter {
    @Deprecated("Specify it as `id` for extension definition in a plugin descriptor")
    override val commandName: String
        get() = "DemoPluginCLI"

    override val requiredModality: Int
        get() =ApplicationStarter.NOT_IN_EDT

    override fun main(args: List<String>) {
        DataClumpContextData.DataClumpRefactorer().main(args.drop(1))
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
enum class RefactorMode{
    None,Manual,Automatic
}
class DataClumpContextData(val dataClumpContextPath:String?,val referenceFindingContextePath:String?,val classNamesContextPath:String?,val extractedClassContextsPath:String?,val refactorMode:String?) {
    var dataClumps: DataClumpsTypeContext? = null
    var usageFinder: ReferenceFinder? = null
    var classNames: Map<String, String>? = null
    var classCreator: ClassCreator? = null
    var refactorer:dataClumpRefactoring.DataClumpRefactorer?=null

    fun initialize(project: Project,projectPath:File) {
        if (dataClumpContextPath != null) {
            val json = java.nio.file.Files.readString(Path.of(dataClumpContextPath))
            val typeToken = genericType<DataClumpsTypeContext>()
            val context = Gson().fromJson<DataClumpsTypeContext>(json, typeToken)
            this.dataClumps = context
        } else {
            val finder = DataClumpFinder(project)
            this.dataClumps = finder.run()
        }
        if (referenceFindingContextePath != null) {
            val json = java.nio.file.Files.readString(Path.of(referenceFindingContextePath))
            val typeToken = genericType<Map<String, ArrayList<UsageInfo>>>()
            val context = Gson().fromJson<Map<String, ArrayList<UsageInfo>>>(json, typeToken)
            this.usageFinder = UsageInfoBasedFinder(project, context)
        } else {
            this.usageFinder =  PsiReferenceFinder ()
        }
        if (classNamesContextPath != null) {
            val json = java.nio.file.Files.readString(Path.of(classNamesContextPath))
            val context = Gson().fromJson<Map<String, String>>(json, Map::class.java)
            this.classNames = context
        } else {
            this.classNames = generatePrimitiveClassNames(this.dataClumps!!.data_clumps)
        }
        if (extractedClassContextsPath != null) {
            val json = java.nio.file.Files.readString(Path.of(extractedClassContextsPath))
            val context = Gson().fromJson<Map<String, String>>(json, Map::class.java)
            this.classCreator = ManualJavaClassCreator(context)
        } else {
            this.classCreator = ManualJavaClassCreator(null)
        }
        if(refactorMode==RefactorMode.Manual.name){
            this.refactorer=ManualDataClumpRefactorer(projectPath ,this.usageFinder!!,this.classCreator!!)
        }
        else{ if(refactorMode==RefactorMode.Automatic.name){
            this.refactorer=dataClumpRefactoring.DataClumpRefactorer(projectPath)
        }
            else {
                this.refactorer= NoRefactoringRunner(projectPath,this.classCreator!!)
            }

        }
    }

    private fun generatePrimitiveClassNames(dataClumps: Map<String, DataClumpTypeContext>): Map<String, String>? {
        val result = mutableMapOf<String, String>()
        for ((key, value) in dataClumps) {
            val className = value.data_clump_data.values.map {
                val validTypeName=makeValidJavaIdentifier(it.type).replaceFirstChar { it.uppercase() }
                it.name.replaceFirstChar { it.uppercase() } +"_"+ validTypeName


            }.sorted().joinToString("_")
            result[key] = className
        }
        return result
    }
    private fun makeValidJavaIdentifier(input: String): String {

        val toReplace= arrayOf(".","[","]","<",">")
        var result=input
        for(value in toReplace){
            result=result.replace(value,"_")
        }
        val MAX_LENGTH=16
        val length=Math.min(MAX_LENGTH,result.length)
        return result.substring(result.length-length)

    }

    class DataClumpRefactorer : CliktCommand() {
        private val myProjectPath by
        argument(help = "Path to the project").file(mustExist = true, canBeFile = false)

        //https://github.com/JetBrains/intellij-community/blob/cb1f19a78bb9a4db29b33ff186cdb60ceab7f64c/java/java-impl-refactorings/src/com/intellij/refactoring/encapsulateFields/JavaEncapsulateFieldHelper.java#L86
        private val dataPath by
        argument(help = "Path to the context data").file(mustExist = true, canBeFile = true).optional()
        private val availableContexts by argument(help = "Integer that identifies which contexts are available").optional()

        interface ProjectLoader {
            fun loadProject(path: Path, executor: PluginExecutor): Unit
        }
        fun getLoader(loaderName:String):ProjectLoader{
            return when(loaderName){
                "OpenProjectWithResolveLoader"->OpenProjectWithResolveLoader()
                "OpenSingleProjectLoader"->OpenSingleProjectLoader()
                "OpenProjectLoader"->OpenProjectLoader()
                "OpenOrImportProjectLoader"->OpenOrImportProjectLoader()
                "LoadAndOpenProjectLoader"->LoadAndOpenProjectLoader()
                else->OpenProjectWithResolveLoader()
            }
        }

        class PluginExecutor(val myProjectPath: File, val contextPath: Path, val availableContexts: Int) {
            var dataClumpContextData = DataClumpContextData(null, null, null, null,null)

            init {
                dataClumpContextData =
                    Gson().fromJson(java.nio.file.Files.readString(contextPath), DataClumpContextData::class.java)
            }


            fun executePlugin(project: Project) {
                ApplicationManager.getApplication().invokeAndWait() {

                        val dataClumpContextData = this.dataClumpContextData
                        dataClumpContextData.initialize(project, myProjectPath)

                    val refactorer = dataClumpContextData.refactorer!!
                        var counter = 0
                        val count = dataClumpContextData.dataClumps!!.data_clumps!!.size.toDouble()
                        val data_clumps =
                            dataClumpContextData.dataClumps!!.data_clumps.values.sortedByDescending { it.data_clump_data.size }
                        println("Size of data clumps ${data_clumps.size}")
                            for (value in data_clumps) 
                            {
                            this.dataClumpContextData.usageFinder!!.updateDataClumpKey(value.key)
                            println("Starting refactor ${value.key}")
                            refactorer.refactorDataClump(
                                project,
                                SuggestedNameWithDataClumpTypeContext(
                                    dataClumpContextData.classNames!![value.key]!!,
                                    value
                                )
                            )
                            println("### refactored ${value.key}")
                            refactorer.commitAll(project)
                            counter++
                            println(counter / count * 100)


                            }

                        println("### saving")
                        println("### exiting")
                        Thread.sleep(10 * 1000)
                        exitProcess(0)
                    }
                }

            }
        

        class OpenProjectWithResolveLoader : ProjectLoader {
            override fun loadProject(path: Path, executor: PluginExecutor): Unit {
                val opener = getKotlinJavaRepositoryOpener()
                var result = opener.openProjectWithResolve(path) {
                    val project = it
                    executor.executePlugin(project)
                    return@openProjectWithResolve true

                }
            }
        }

        class OpenSingleProjectLoader : ProjectLoader {
            /*
        partially works with
            gradle project created in IntelliJ
            clean gradle project
            maven projects in IntelliJ
          completely works with
            intelliJ projects
            maven projects
        * */

            override fun loadProject(path: Path, executor: PluginExecutor): Unit {
                val opener = getKotlinJavaRepositoryOpener()
                try {
                    var result = opener.openSingleProject(path) {
                        val project = it
                        executor.executePlugin(project)
                        return@openSingleProject true

                    }
                } catch (e: Exception) {
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
        class OpenProjectLoader : ProjectLoader {
            override fun loadProject(path: Path, executor: PluginExecutor): Unit {
                try {
                    val result = ProjectUtil.openProject(path.toString(), null, true)
                    executor.executePlugin(result!!)
                } catch (e: Exception) {
                    println("### error")
                    println(e)
                    throw e
                }

            }
        }

        class OpenOrImportProjectLoader : ProjectLoader {
            /*
        partially works with
             raw gradle project
             raw maven project
             intelliJ maven project
             intelij gradle project
         fully works with
             intelliJ projects


         */
            override fun loadProject(path: Path, executor: PluginExecutor): Unit {
                try {
                    val project = ProjectUtil.openOrImport(path, null, false)
                    executor.executePlugin(project!!)

                } catch (e: Exception) {
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
        class LoadAndOpenProjectLoader : ProjectLoader {
            override fun loadProject(path: Path, executor: PluginExecutor): Unit {
                try {
                    val projectManager = ProjectManager.getInstance()
                    val project = projectManager.loadAndOpenProject(path.toString())!!
                    executor.executePlugin(project)
                } catch (e: Exception) {
                    println("### error")
                    println(e)
                    throw e
                }

            }
        }



        fun decodeToInt(value: String): Int {
            var base = 10
            val trimmed = value
            if (value.startsWith("0x")) {
                base = 16
                trimmed.substring(2)
            } else if (value.startsWith("0b")) {
                base = 2
                trimmed.substring(2)
            } else if (value.startsWith("0")) {
                base = 8
                trimmed.substring(1)
            }
            return Integer.parseInt(trimmed, base)
        }

        override fun run() {
            println("### starting refactor")

            //VirtualFileManager.getInstance().syncRefresh()
            val projectManager = ProjectManagerEx.getInstanceEx()
            //projectManager.loadProject()
            // projectManager.closeAndDisposeAllProjects(true)


            val opener = OpenProjectWithResolveLoader()
            print("init")
            var project: Project? = null
            try {
                val availableContext = availableContexts?.let { decodeToInt(it) } ?: Int.MAX_VALUE
                val executor = PluginExecutor(myProjectPath, dataPath!!.toPath(), availableContext)
                opener.loadProject(myProjectPath.toPath(), executor)
            } catch (e: Exception) {
                println("### error")
                e.printStackTrace()
                throw e
            }



            exitProcess(0)
        }
    }
}
fun addJdk(project: Project, jdkPath: String): Sdk? {
    val javaSdk = JavaSdk.getInstance()
        val projectJdkTable = ProjectJdkTable.getInstance()
    val newSdk=javaSdk.createJdk("hello","/home/compf/data/jdks/jdk-17.0.2",false)
        projectJdkTable.addJdk(newSdk,project)
    val projectRootManager = ProjectRootManager.getInstance(project)
    projectRootManager.projectSdk = newSdk


    return null
}