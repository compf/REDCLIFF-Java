package org.jetbrains.research.refactoringDemoPlugin.parsedAstTypes

open class ParameterTypeContext : AstElementTypeContext() {
    var modifiers: ArrayList<String> = ArrayList()
    var ignore = false
}