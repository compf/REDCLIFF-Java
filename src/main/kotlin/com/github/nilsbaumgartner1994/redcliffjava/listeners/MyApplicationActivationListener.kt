package com.github.nilsbaumgartner1994.redcliffjava.listeners

import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.wm.IdeFrame
import java.io.File
import java.io.FileWriter
import java.io.IOException

internal class MyApplicationActivationListener : ApplicationActivationListener {

    private fun checkConditionAndExecuteAction() {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
        // TODO: Implement your condition check and action execution here
        val shouldRunAction = System.getProperty("runMyAction") == "true"
        if (shouldRunAction) {
            // Execute your action here
            this.runMyAction()
        }
    }

    private fun runMyAction() {
        // TODO: Implement the logic of your action here
    }

    override fun applicationActivated(ideFrame: IdeFrame) {
        thisLogger().warn("applicationActivated");
        this.checkConditionAndExecuteAction();

        // Create and save the file
        try {
            val filePath = "/Users/nbaumgartner/Desktop/fromIntelliJ.txt"
            val file = File(filePath)
            if (!file.exists()) {
                file.createNewFile()
                val writer = FileWriter(file)
                writer.write("Your desired content here")
                writer.close()
            }
        } catch (e: IOException) {
            thisLogger().error("Error creating or writing to file", e)
        }
    }
}
