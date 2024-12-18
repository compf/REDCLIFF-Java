package dataClumpRefactoring

/**
 * Type definition for usage context
 */
class UsageInfo(val name:String, val symbolType:Int, val range:Position, val filePath:String,val originKey:String) {


    enum class UsageType{MethodDeclared,VariableDeclared,VariableUsed,MethodUsed}

}

