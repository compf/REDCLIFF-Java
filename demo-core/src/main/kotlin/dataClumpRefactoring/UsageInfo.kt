package dataClumpRefactoring


class UsageInfo(val name:String, val symbolType:Int, val range:Position, val filePath:String, val extractedClassPath:String?,val variableNames: Array<String>,val originKey:String,val isParameter:Boolean) {


    enum class UsageType{MethodDeclared,VariableDeclared,VariableUsed,MethodUsed}

}

