package dataClumpRefactoring

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod

/**
 * This interface is responsible for checking the validity of the names of the fields and parameters.
 */
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

/**
 * This interface is responsible for generating the names of the fields, parameters, getters, and setters.
 */
interface IdentifierNameService {

    /**
     * return a suitable getter name for the variable
     */
    fun getGetterName( variableName: String):String

    /**
     * return a suitable setter name for the variable
     */
    fun getSetterName( variableName: String):String

    /**
     * return a suitable parameter name for the variable
     */
    fun getParameterName(extractedClassName:String,method:PsiMethod):String

    /**
     * return a suitable field name for the variable
     */
    fun getFieldName(extractedClassName: String, fieldHolderClass: PsiClass?):String

    /**
     * returns the name validity checker
     */
    fun getNameValidityChecker():NameValidityChecker
}

/**
 * Simply concatenate get or set with the variable name, or return the variable name itself
 */
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

/**
 * Simply return the variable name itself for getter, and throw an exception for setter
 */
class RecordPrimitiveNameService(private val validityChecker: NameValidityChecker):PrimitiveNameService(validityChecker){
    override fun getGetterName(variableName: String): String {
        return variableName
      }
      override fun getSetterName(variableName: String): String {
        throw Exception("Not possible")
    }

  
}
