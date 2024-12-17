package dataClumpRefactoring

import com.google.gson.Gson
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespace
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.research.refactoringDemoPlugin.util.extractKotlinAndJavaClasses
import java.nio.file.Path

interface ReferenceFinder {

    fun findFieldUsages(field: PsiField): List<PsiElement>
    fun findParameterUsages(parameter:PsiParameter):List<PsiElement>

   fun  findMethodUsages(method:PsiMethod):List<PsiElement>
  fun  findMethodOverrides(method:PsiMethod):List<PsiMethod>

  fun updateDataClumpKey(dcKey:String){

  }




}
class PsiReferenceFinder:ReferenceFinder
{
    override fun findFieldUsages(field: PsiField): List<PsiElement> {
       return ReferencesSearch.search(field).map { it.element }.toList()
    }

    override fun findParameterUsages(parameter: PsiParameter): List<PsiElement> {
        return ReferencesSearch.search(parameter).map { it.element }.toList()
    }

    override fun findMethodUsages(method: PsiMethod): List<PsiElement> {
        return ReferencesSearch.search(method).map { it.element }.toList()
    }

    override fun findMethodOverrides(method: PsiMethod): List<PsiMethod> {
        val res= OverridingMethodsSearch.search(method,GlobalSearchScope.allScope(method.project),true).map { it }.toList()
        return res
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
    fun nop(){
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

fun getElementByPosition(project:Project,path:String, pos:Position):PsiElement{
    var fullPath=""
    if(Path.of(path).isAbsolute){
      fullPath="file://"+path
    }
    else{
        fullPath="file://"+Path.of(project.basePath!!,path).toAbsolutePath().toString()
    }
    println(fullPath)
    val man = VirtualFileManager.getInstance()
    val vFile = man.findFileByUrl(fullPath)!!

    var file=PsiManager.getInstance(project).findFile(vFile)!!
    val document=PsiDocumentManager.getInstance(project).getDocument(file)!!
    val startOffset=document.getLineStartOffset(pos.startLine)+pos.startColumn
    val endOffset=document.getLineStartOffset(pos.endLine)+pos.endColumn
    return file.findElementAt(startOffset)!!.getParentOfType<PsiElement>(false)!!
}
class UsageInfoBasedFinder (val project:Project,val usagesMap:Map<String,Iterable<UsageInfo>>): ReferenceFinder {
    private var usageMapElementa=mutableMapOf<String,Iterable<PsiElement>>()
    private var  usageElements:Iterable<PsiElement> =emptyList<PsiElement>()
    private var usages:Iterable<UsageInfo> =emptyList<UsageInfo>()
    init {

        for((key,value) in usagesMap){
            usageMapElementa[key]=value.map { getElementByPosition(project,it.filePath,it.range) }
        }
    }


    override fun updateDataClumpKey(dcKey: String) {
        println(dcKey)
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
class UsageSerializer{
    fun correctElement(element:PsiElement):PsiElement?{
        if(element is PsiParameter || element is PsiField){
            return element
        }

        val asParam=element.getParentOfType<PsiParameter>(true)
        if (asParam!=null){
            return asParam
        }
        val asField=element.getParentOfType<PsiField>(true)
        if (asField!=null){
            return asField
        }
        val leftSibling=element.getPrevSiblingIgnoringWhitespace(false)
        if(leftSibling is PsiParameter || leftSibling is PsiField){
            return leftSibling
        }
        val rightSibling=element.nextSibling
        if(rightSibling is PsiParameter || rightSibling is PsiField){
            return rightSibling
        }
        return null

    }
    val finder=PsiReferenceFinder()
    fun run(project: Project,   dataClumps: DataClumpsTypeContext,dataPath:String){
        val usages= mutableMapOf <String,MutableList<UsageInfo>>()
        for(dataClump in dataClumps.data_clumps){
            usages[dataClump.key]= mutableListOf()
            var firstDataClump=true

            for( dataClumpData in dataClump.value.data_clump_data){
                val elements= arrayOf(
                    getElementByPosition(project,dataClump.value.from_file_path,dataClumpData.value.position),
                    getElementByPosition(project,dataClump.value.to_file_path,dataClumpData.value.to_variable.position)
                )
              for(ele in elements){
                  val eleCorrected=correctElement(ele)
                  if(eleCorrected==null){
                      continue
                  }
                  val asParam=eleCorrected as? PsiParameter
                    if (asParam!=null){
                        val parameterUsages=finder.findParameterUsages(asParam)
                        for(parameterUsage in parameterUsages){
                            usages[dataClump.key]!!.add(UsageInfo(
                                asParam.name,UsageInfo.UsageType.VariableUsed.ordinal,
                                getPosition(parameterUsage),
                                parameterUsage.containingFile.virtualFile.path,dataClumpData.key))

                        }
                    val asMethod=asParam.getParentOfType<PsiMethod>(true)!!
                    val methodUsages=finder.findMethodUsages(asMethod)
                       for(methodUsage in methodUsages){
                            usages[dataClump.key]!!.add(UsageInfo(
                                asMethod.name,UsageInfo.UsageType.MethodUsed.ordinal,
                                getPosition(methodUsage),
                                methodUsage.containingFile.virtualFile.path,dataClumpData.key))

                        }
                  }
                  firstDataClump=false
                    val asField=eleCorrected as? PsiField
                    if (asField!=null){
                        val fieldUsages=finder.findFieldUsages(asField)
                        for(fieldUsage in fieldUsages){
                            usages[dataClump.key]!!.add(UsageInfo(
                                asField.name,UsageInfo.UsageType.VariableUsed.ordinal,
                                getPosition(fieldUsage),
                                fieldUsage.containingFile.virtualFile.path,dataClumpData.key))

                        }
                    }
              }
            }



        }
        val parent=java.io.File(dataPath).parent

        java.nio.file.Files.writeString(Path.of(dataPath),Gson().toJson(usages))
    }
}