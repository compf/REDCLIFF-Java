package org.jetbrains.research.refactoringDemoPlugin

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import com.google.gson.GsonBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.research.pluginUtilities.openRepository.getKotlinJavaRepositoryOpener
import org.jetbrains.research.refactoringDemoPlugin.util.extractElementsOfType
import org.jetbrains.research.refactoringDemoPlugin.util.extractModules
import org.jetbrains.research.refactoringDemoPlugin.util.findPsiFilesByExtension
import org.jetbrains.uast.UAnchorOwner
import org.jetbrains.uast.UComment
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement
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

    private fun Module.getKDocs(extension: String) =
        this.findPsiFilesByExtension(extension).map { it.extractElementsOfType(PsiComment::class.java) }.flatten()
            .toSet()

    /**
     * Walks through files in the project, extracts all methods in each Java and Korlin file
     * and saves the method name and the corresponding JavaDoc to the output file.
     */
    override fun run() {
        val repositoryOpener = getKotlinJavaRepositoryOpener()
        val gson = GsonBuilder().setPrettyPrinting().create()
        repositoryOpener.openProjectWithResolve(input.toPath()) { project ->
            ApplicationManager.getApplication().invokeAndWait {
                val modules = project.extractModules()

                modules.extractClassesViaPsi().also {
                    File("$output/results_with_psi.json").writeText(gson.toJson(it))
                }
            }
            true
        }
        exitProcess(0)
    }

    private fun List<Module>.extractClassesViaPsi() = this.flatMap { module ->
        val javaClasses = module.getPsiClasses("java")

        javaClasses.map { psiClass ->
            val qualifiedName = psiClass.qualifiedName
            val superClasses = psiClass.supers.map { it.qualifiedName }.joinToString(", ")

            DatasetItem(psiClass.name ?: "", "Hello", qualifiedName, superClasses)
        }
    }

    private fun Module.getPsiClasses(extension: String): List<PsiClass> {
        val psiFiles = this.findPsiFilesByExtension(extension)
        return psiFiles.flatMap { it.extractElementsOfType(PsiClass::class.java) }
    }

    data class DatasetItem(val methodName: String, val javaDoc: String, val qualifiedName: String?, val superClasses: String?)
}
