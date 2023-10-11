package org.jetbrains.research.refactoringDemoPlugin.parsedAstTypes

open class AstElementTypeContext {
    var name: String? = null
    var key: String? = null
    var type: String? = null
    var hasTypeVariable // Some types are variable, e.g. List<T> but not List<Number>
            = false
    var position: AstPosition? = null

    init {
        //println("AstElementTypeContext constructor called")
    }
}