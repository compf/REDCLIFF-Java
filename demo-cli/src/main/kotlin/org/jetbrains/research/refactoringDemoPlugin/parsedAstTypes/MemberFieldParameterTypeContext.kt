package org.jetbrains.research.refactoringDemoPlugin.parsedAstTypes

class MemberFieldParameterTypeContext : ParameterTypeContext() {
    var memberFieldKey: String? = null
    var classOrInterfaceKey: String? = null
}