package org.jetbrains.research.refactoringDemoPlugin

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.research.pluginUtilities.openRepository.getKotlinJavaRepositoryOpener
import java.nio.file.Path

interface ProjectLoader {
    fun loadProject(path: Path, executor: DataClumpContextData.DataClumpRefactorer.PluginExecutor): Unit
}

fun getLoader(loaderName: String): ProjectLoader {
    return when (loaderName) {
        "OpenProjectWithResolveLoader" -> OpenProjectWithResolveLoader()
        "OpenSingleProjectLoader" -> OpenSingleProjectLoader()
        "OpenProjectLoader" -> OpenProjectLoader()
        "OpenOrImportProjectLoader" -> OpenOrImportProjectLoader()
        "LoadAndOpenProjectLoader" -> LoadAndOpenProjectLoader()
        else -> OpenProjectWithResolveLoader()
    }
}

class OpenProjectWithResolveLoader : ProjectLoader {
    override fun loadProject(path: Path, executor: DataClumpContextData.DataClumpRefactorer.PluginExecutor): Unit {
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

    override fun loadProject(path: Path, executor: DataClumpContextData.DataClumpRefactorer.PluginExecutor): Unit {
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
    override fun loadProject(path: Path, executor: DataClumpContextData.DataClumpRefactorer.PluginExecutor): Unit {
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

class OpenOrImportProjectLoader :ProjectLoader {
    /*
partially works with
     raw gradle project
     raw maven project
     intelliJ maven project
     intelij gradle project
 fully works with
     intelliJ projects


 */
    override fun loadProject(path: Path, executor: DataClumpContextData.DataClumpRefactorer.PluginExecutor): Unit {
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
    override fun loadProject(path: Path, executor: DataClumpContextData.DataClumpRefactorer.PluginExecutor): Unit {
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