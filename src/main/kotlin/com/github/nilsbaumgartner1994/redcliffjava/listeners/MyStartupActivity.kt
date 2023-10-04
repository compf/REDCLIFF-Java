package com.github.nilsbaumgartner1994.redcliffjava.listeners

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.RefreshQueue

internal class MyStartupActivity : StartupActivity {

    override fun runActivity(project: Project) {
        // Ensure all files are up-to-date
        traverseFiles(project.baseDir)
    }

    private fun traverseFiles(file: VirtualFile) {
        if (file.isDirectory) {
            for (child in file.children) {
                traverseFiles(child)
            }
        } else {
            // Process individual file
            println("Processing file: ${file.path}")
        }
    }
}
