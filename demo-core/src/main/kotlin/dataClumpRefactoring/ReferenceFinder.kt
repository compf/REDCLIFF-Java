package dataClumpRefactoring

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.idea.search.usagesSearch.searchReferencesOrMethodReferences
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.research.refactoringDemoPlugin.util.extractKotlinAndJavaClasses

interface ReferenceFinder {

    fun findFieldUsages(field: PsiField): List<PsiElement>
    fun findParameterUsages(parameter:PsiParameter):List<PsiElement>

   fun  findMethodUsages(method:PsiMethod):List<PsiElement>
  fun  findMethodOverrides(method:PsiMethod):List<PsiMethod>

  fun updateDataClumpKey(dcKey:String){

  }




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


class UsageInfoBasedFinder (val project:Project,val usagesMap:Map<String,Iterable<UsageInfo>>): ReferenceFinder {
    private var usageMapElementa=mutableMapOf<String,Iterable<PsiElement>>()
    private var  usageElements:Iterable<PsiElement> =emptyList<PsiElement>()
    private var usages:Iterable<UsageInfo> =emptyList<UsageInfo>()
    init {

        for((key,value) in usagesMap){
            usageMapElementa[key]=value.map { getElementByPosition(it.filePath,it.range) }
        }
    }
    fun getElementByPosition(path:String, pos:Position):PsiElement{
        val fullPath="file://" + java.nio.file.Paths.get(project.basePath!!, path).toString()
        println(fullPath)
        val man = VirtualFileManager.getInstance()
        val vFile = man.findFileByUrl(fullPath)!!

        var file=PsiManager.getInstance(project).findFile(vFile)!!
        val document=PsiDocumentManager.getInstance(project).getDocument(file)!!
        val startOffset=document.getLineStartOffset(pos.startLine)+pos.startColumn
        val endOffset=document.getLineStartOffset(pos.endLine)+pos.endColumn
        return file.findElementAt(startOffset)!!.getParentOfType<PsiElement>(false)!!
    }

    override fun updateDataClumpKey(dcKey: String) {
       usageElements=usageMapElementa[dcKey]!!
        usages=usagesMap[dcKey]!!
    }
    override fun findMethodOverrides(method: PsiMethod): List<PsiMethod> {
        val methodPos=method.startOffset
        val result= mutableListOf<PsiMethod>()
        var index=0
       for(pair in usages.zip(usageElements)){
           val usage=pair.first
              val element=pair.second
           if(usage.symbolType==UsageInfo.UsageType.MethodDeclared.ordinal){
               if(usage.name==method.name){

                   if(element is PsiMethod){
                       result.add(element)
                   }
                   else{
                       result.add(element.parent as PsiMethod)
                   }
               }

           }
           index++
       }
        return result
    }

    override fun findFieldUsages(field: PsiField): List<PsiElement> {
        val result= mutableListOf<PsiElement>()
        for(pair in usages.zip(usageElements)){
            val usage=pair.first
            val element=pair.second
            if(usage.symbolType==UsageInfo.UsageType.VariableUsed.ordinal){
                if(usage.name==field.name){
                    result.add(element)
                }
            }
        }
        return result
    }

    override fun findMethodUsages(method: PsiMethod): List<PsiElement> {
        val result= mutableListOf<PsiElement>()
        for(pair in usages.zip(usageElements)){
            val usage=pair.first
            val element=pair.second
            if(usage.symbolType==UsageInfo.UsageType.MethodUsed.ordinal){
                if(usage.name==method.name){
                    result.add(element)
                }
            }
        }
        return result
    }

    override fun findParameterUsages(parameter: PsiParameter): List<PsiElement> {
        val result= mutableListOf<PsiElement>()
        for(pair in usages.zip(usageElements)){
            val usage=pair.first
            val element=pair.second
            if(usage.symbolType==UsageInfo.UsageType.VariableUsed.ordinal){
                if(usage.name==parameter.name){
                    result.add(element)
                }
            }
        }
        return result
    }

}