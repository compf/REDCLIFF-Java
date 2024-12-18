package org.jetbrains.research.refactoringDemoPlugin

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.types.file
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFileManager
import dataClumpRefactoring.*
import java.io.File
import java.nio.file.Path
import kotlin.system.exitProcess

object PluginRunner : ApplicationStarter {
    @Deprecated("Specify it as `id` for extension definition in a plugin descriptor")
    override val commandName: String
        get() = "DemoPluginCLI"

    override val requiredModality: Int
        get() = ApplicationStarter.NOT_IN_EDT

    override fun main(args: List<String>) {
        DataClumpRefactorer().main(args.drop(1))
    }
}

class DataClumpFinderRunner : CliktCommand() {
    private val input by
        argument(help = "Path to the project").file(mustExist = true, canBeFile = false)
    private val output by argument(help = "Output directory").file(canBeFile = true)

    override fun run() {
        VirtualFileManager.getInstance().syncRefresh()
        val projectManager = ProjectManager.getInstance()
        val project = projectManager.loadAndOpenProject(input.toPath().toString())!!
        val finder = DataClumpFinder(project)
        val context = finder.run()
        val json = Gson().toJson(context)
        java.nio.file.Files.writeString(output.toPath(), json)
        exitProcess(0)
    }
}

inline fun <reified T> genericType() = object : TypeToken<T>() {}.type

enum class RefactorMode {
    None,
    Manual,
    Automatic,
    FindUsages
}
/** contains the context data loaded from the respective paths */
class DataClumpContextData(
    val dataClumpContextPath: String?,
    val referenceFindingContextePath: String?,
    val classNamesContextPath: String?,
    val extractedClassContextsPath: String?,
    val refactorMode: String?,
    val loaderName: String?
) {
    var dataClumps: DataClumpsTypeContext? = null
    var usageFinder: ReferenceFinder? = null
    var classNames: Map<String, String>? = null
    var classCreator: ClassCreator? = null
    var refactorer: dataClumpRefactoring.DataClumpRefactorer? = null
    /** based on the provided paths, initialize the context data */
    fun initialize(project: Project, projectPath: File, dataPath: String) {
        if (dataClumpContextPath != null) {
            val json = java.nio.file.Files.readString(Path.of(dataClumpContextPath))
            val typeToken = genericType<DataClumpsTypeContext>()
            val context = Gson().fromJson<DataClumpsTypeContext>(json, typeToken)
            this.dataClumps = context
        } else {
            val finder = DataClumpFinder(project)
            this.dataClumps = finder.run()
        }
        if (
            referenceFindingContextePath != null &&
                java.nio.file.Files.exists(Path.of(referenceFindingContextePath))
        ) {
            val json = java.nio.file.Files.readString(Path.of(referenceFindingContextePath))
            println(json)
            val typeToken = genericType<Map<String, ArrayList<UsageInfo>>>()
            val context = Gson().fromJson<Map<String, ArrayList<UsageInfo>>>(json, typeToken)
            this.usageFinder = UsageInfoBasedFinder(project, context)
        } else {
            this.usageFinder = PsiReferenceFinder()
        }
        if (classNamesContextPath != null) {
            val json = java.nio.file.Files.readString(Path.of(classNamesContextPath))
            val context = Gson().fromJson<Map<String, String>>(json, Map::class.java)
            this.classNames = context
        } else {
            this.classNames = generatePrimitiveClassNames(this.dataClumps!!.data_clumps)
        }
        if (refactorMode != RefactorMode.FindUsages.name) {
            if (extractedClassContextsPath != null) {
                val json = java.nio.file.Files.readString(Path.of(extractedClassContextsPath))
                val context = Gson().fromJson<Map<String, String>>(json, Map::class.java)
                this.classCreator = ManualJavaClassCreator(context)
            } else {
                this.classCreator = ManualJavaClassCreator(null)
            }
        }
        if (refactorMode == RefactorMode.Manual.name) {
            this.refactorer =
                ManualDataClumpRefactorer(project, this.usageFinder!!, this.classCreator!!)
        } else {
            if (refactorMode == RefactorMode.Automatic.name) {
                this.refactorer = DataClumpRefactorer(project)
            } else if (refactorMode == RefactorMode.FindUsages.name) {
                val serializer = UsageSerializer()
                serializer.run(project, this.dataClumps!!, referenceFindingContextePath!!)
                this.refactorer = NoRefactoringRunner(project)
            } else {
                this.refactorer = NoRefactoringRunner(project)
            }
        }
    }
}
/** Used if name suggestion context not available */
private fun generatePrimitiveClassNames(
    dataClumps: Map<String, DataClumpTypeContext>
): Map<String, String> {
    val result = mutableMapOf<String, String>()
    for ((key, value) in dataClumps) {
        val className =
            value.data_clump_data.values
                .map {
                    val validTypeName =
                        makeValidJavaIdentifier(it.type).replaceFirstChar { it.uppercase() }
                    it.name.replaceFirstChar { it.uppercase() } + "_" + validTypeName
                }
                .sorted()
                .joinToString("_")
        result[key] = className
    }
    return result
}

private fun makeValidJavaIdentifier(input: String): String {

    val toReplace = arrayOf(".", "[", "]", "<", ">")
    var result = input
    for (value in toReplace) {
        result = result.replace(value, "_")
    }
    val MAX_LENGTH = 16
    val length = Math.min(MAX_LENGTH, result.length)
    return result.substring(result.length - length)
}

class DataClumpRefactorer : CliktCommand() {
    private val myProjectPath by
        argument(help = "Path to the project").file(mustExist = true, canBeFile = false)

    private val dataPath by
        argument(help = "Path to the context data")
            .file(mustExist = true, canBeFile = true)
            .optional()

    class PluginExecutor(
        val myProjectPath: File,
        val contextPath: Path,
        val dataClumpContextData: DataClumpContextData
    ) {

        fun executePlugin(project: Project) {
            ApplicationManager.getApplication().invokeAndWait {
                dataClumpContextData.initialize(project, myProjectPath, contextPath.toString())
                val refactorer = dataClumpContextData.refactorer!!
                var counter = 0
                val count = dataClumpContextData.dataClumps!!.data_clumps.size.toDouble()
                val data_clumps =
                    dataClumpContextData.dataClumps!!.data_clumps.values.sortedByDescending {
                        it.data_clump_data.size
                    }
                for (value in data_clumps) {
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

                // just to make sure that everything is serialized, but is of course not ideal
                Thread.sleep(10 * 1000)
                exitProcess(0)
            }
        }
    }

    override fun run() {
        println("### starting refactor")

        val DefaultLoaderName = "OpenProjectWithResolveLoader"
        val contextPath = dataPath!!.toPath()
        try {
            val dataClumpContextData =
                Gson()
                    .fromJson(
                        java.nio.file.Files.readString(contextPath),
                        DataClumpContextData::class.java
                    )
            val loaderName = dataClumpContextData.loaderName ?: DefaultLoaderName
            val loader = getLoader(loaderName)
            print("init")
            var project: Project? = null
            try {
                val executor =
                    PluginExecutor(myProjectPath, dataPath!!.toPath(), dataClumpContextData)
                loader.loadProject(myProjectPath.toPath(), executor)
            } catch (e: Exception) {
                println("### error")
                e.printStackTrace()
                throw e
            }

            exitProcess(0)
        } catch (e: Exception) {
            println("### error")
            println(e)
            throw e
        }
    }
}
