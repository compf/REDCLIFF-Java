package dataClumpRefactoring


class UsageInfo(val name:String, val symbolType:Int, val range:Position, val filePath:String,val originKey:String) {


    enum class UsageType{MethodDeclared,VariableDeclared,VariableUsed,MethodUsed}

}

