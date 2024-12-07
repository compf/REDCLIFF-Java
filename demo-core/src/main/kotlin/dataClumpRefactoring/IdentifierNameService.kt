package dataClumpRefactoring

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
interface NameValidityChecker{
    fun isParameterNameValid(name:String,method: PsiMethod):Boolean
    fun isFieldNameValid(name:String,fieldHolderClass: PsiClass):Boolean
}
class StubNameValidityChecker:NameValidityChecker{
    override fun isParameterNameValid(name: String, method: PsiMethod): Boolean {
        return true
    }

    override fun isFieldNameValid(name: String, fieldHolderClass: PsiClass): Boolean {
        return true
    }
}
interface IdentifierNameService {
    fun getGetterName( variableName: String):String
    fun getSetterName( variableName: String):String
    fun getParameterName(extractedClassName:String,method:PsiMethod):String
    fun getFieldName(extractedClassName: String, fieldHolderClass: PsiClass?):String

    fun getNameValidityChecker():NameValidityChecker
}
open class PrimitiveNameService(private val validityChecker: NameValidityChecker):IdentifierNameService{
    override fun getGetterName(variableName: String): String {
      return "get${variableName.replaceFirstChar { it.uppercase() }}"
    }

    override fun getSetterName(variableName: String): String {
        return "set${variableName.replaceFirstChar { it.uppercase() }}"
    }

    override fun getParameterName(extractedClassName: String, method: PsiMethod): String {
       return extractedClassName.replaceFirstChar { it.lowercase() }
    }

    override fun getFieldName(extractedClassName: String, fieldHolderClass: PsiClass?): String {
        return extractedClassName.replaceFirstChar { it.lowercase() }
    }

    override fun getNameValidityChecker(): NameValidityChecker {
        return validityChecker
    }


}

class RecordPrimitiveNameService(private val validityChecker: NameValidityChecker):PrimitiveNameService(validityChecker){
    override fun getGetterName(variableName: String): String {
        return variableName
      }
      override fun getSetterName(variableName: String): String {
        throw Exception("Not possible")
    }

  
}
