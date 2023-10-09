package org.jetbrains.research.refactoringDemoPlugin.parsedAstTypes

class MethodTypeContext : AstElementTypeContext() {
    var modifiers: ArrayList<String> = ArrayList()
    var overrideAnnotation = false
    var returnType: String? = null
    var parameters: ArrayList<MethodParameterTypeContext> = ArrayList()
    var classOrInterfaceKey: String? = null
}