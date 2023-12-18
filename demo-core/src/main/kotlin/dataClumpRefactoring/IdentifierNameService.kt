package dataClumpRefactoring

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod

interface IdentifierNameService {
    fun getGetterName(extractedClass: PsiClass, variableName: String):String;
    fun getSetterName(extractedClass: PsiClass, variableName: String):String;
    fun getParameterName(extractedClass: PsiClass,method:PsiMethod):String;
    fun getFieldName(extractedClass: PsiClass, fieldHolderClass: PsiClass?):String;
}
class PrimitiveNameService:IdentifierNameService{
    override fun getGetterName(extractedClass: PsiClass, variableName: String): String {
      return "get${variableName.replaceFirstChar { it.uppercase() }}"
    }

    override fun getSetterName(extractedClass: PsiClass, variableName: String): String {
        return "set${variableName.replaceFirstChar { it.uppercase() }}"
    }

    override fun getParameterName(extractedClass: PsiClass, method: PsiMethod): String {
       return extractedClass.name!!.replaceFirstChar { it.lowercase() }
    }

    override fun getFieldName(extractedClass: PsiClass, fieldHolderClass: PsiClass?): String {
        return extractedClass.name!!.replaceFirstChar { it.lowercase() }
    }

}
