package dataClumpRefactoring

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.idea.search.usagesSearch.searchReferencesOrMethodReferences
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.research.refactoringDemoPlugin.util.extractKotlinAndJavaClasses

interface ReferenceFinder {

    fun findFieldUsages(field: PsiField): List<PsiElement>
    fun findParameterUsages(parameter:PsiParameter):List<PsiElement>

   fun  findMethodUsages(method:PsiMethod):List<PsiElement>
  fun  findMethodOverrides(method:PsiMethod):List<PsiMethod>






}
class FullReferenceFinder : ReferenceFinder {
    override fun findFieldUsages(field: PsiField): List<PsiElement> {
        var relevantClasses: List<PsiClass>
        if(field.modifierList!!.hasModifierProperty("public")){
           relevantClasses=field.project.extractKotlinAndJavaClasses()
       }
        else{
              relevantClasses=listOf(field.containingClass!!)

       }
        val result= mutableListOf<PsiElement>()
        for(cls in relevantClasses){
           result.addAll(PsiTreeUtil.findChildrenOfType(cls,PsiReferenceExpression::class.java).filter { it.isReferenceTo(field) }.map { it.element})
        }
        return result
    }

    override fun findParameterUsages(parameter: PsiParameter): List<PsiElement> {
        val result= mutableListOf<PsiElement>()
        val method=parameter.getParentOfType<PsiMethod>(true)!!

        result.addAll(PsiTreeUtil.findChildrenOfType(method,PsiReferenceExpression::class.java).filter { it.isReferenceTo(parameter) }.map { it.element })

        return result
    }

    override fun findMethodUsages(method: PsiMethod): List<PsiElement> {
        val result= mutableListOf<PsiElement>()
        val relevantClasses=method.project.extractKotlinAndJavaClasses()
        for(cls in relevantClasses){
            result.addAll(PsiTreeUtil.findChildrenOfType(cls,PsiReferenceExpression::class.java).filter { it.isReferenceTo(method) }.map { it.element })
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