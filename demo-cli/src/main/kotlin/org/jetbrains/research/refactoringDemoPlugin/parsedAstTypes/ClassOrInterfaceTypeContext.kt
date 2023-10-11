package org.jetbrains.research.refactoringDemoPlugin.parsedAstTypes

class ClassOrInterfaceTypeContext : AstElementTypeContext() {
    var modifiers: ArrayList<String> = ArrayList()
    var fields: HashMap<String, MemberFieldParameterTypeContext> = HashMap()
    var methods: HashMap<String, MethodTypeContext> = HashMap()
    var file_path: String? = null
    var anonymous = false
    var auxclass // true: wont be analysed. the class is only an aux class in order to support the hierarchy.
            = false
    var implements_: ArrayList<String> = ArrayList()
    var extends_: ArrayList<String> = ArrayList() // Languages that support multiple inheritance include: C++, Common Lisp
    var definedInClassOrInterfaceTypeKey // key of the class or interface where this class or interface is defined
            : String? = null
    var innerDefinedClasses: HashMap<String, ClassOrInterfaceTypeContext> = HashMap()
    var innerDefinedInterfaces: HashMap<String, ClassOrInterfaceTypeContext> = HashMap()

    init {
        //println("ClassOrInterfaceTypeContext constructor called")
    }
}