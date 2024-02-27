package dataClumpRefactoring

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.idea.search.usagesSearch.searchReferencesOrMethodReferences
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.research.refactoringDemoPlugin.util.extractKotlinAndJavaClasses

interface ReferenceFinder {

    fun findFieldUsage(field: PsiField): List<PsiReference>
    fun findParameterUsage(parameter:PsiParameter):List<PsiReference>

   fun  findMethodUsage(method:PsiMethod):List<PsiReference>
  fun  findMethodOverrides(method:PsiMethod):List<PsiMethod>






}
class FullReferenceFinder : ReferenceFinder {
    override fun findFieldUsage(field: PsiField): List<PsiReference> {
        var relevantClasses= emptyList<PsiClass>()
       if(field.modifierList!!.hasModifierProperty("public")){
           relevantClasses=field.project.extractKotlinAndJavaClasses()
       }
        else{
              relevantClasses=listOf(field.containingClass!!)

       }
        val result= mutableListOf<PsiReference>()
        for(cls in relevantClasses){
           result.addAll(PsiTreeUtil.findChildrenOfType(cls,PsiReferenceExpression::class.java).filter { it.isReferenceTo(field) })
        }
        return result
    }

    override fun findParameterUsage(parameter: PsiParameter): List<PsiReference> {
        val result= mutableListOf<PsiReference>()
        val method=parameter.getParentOfType<PsiMethod>(true)!!

        result.addAll(PsiTreeUtil.findChildrenOfType(method,PsiReferenceExpression::class.java).filter { it.isReferenceTo(parameter) })

        return result
    }

    override fun findMethodUsage(method: PsiMethod): List<PsiReference> {
        val result= mutableListOf<PsiReference>()
        val relevantClasses=method.project.extractKotlinAndJavaClasses()
        for(cls in relevantClasses){
            result.addAll(PsiTreeUtil.findChildrenOfType(cls,PsiReferenceExpression::class.java).filter { it.isReferenceTo(method) })
        }
        return result
    }

    override fun findMethodOverrides(method: PsiMethod): List<PsiMethod> {
        val result= mutableListOf<PsiMethod>()
        val relevantClasses=method.project.extractKotlinAndJavaClasses()
        for(cls in relevantClasses){
            result.addAll(PsiTreeUtil.findChildrenOfType(cls,PsiMethod::class.java).filter { it.findSuperMethods().contains(method) })
        }
        return result
    }
}