package dataClumpRefactoring
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.psi.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.changeSignature.ParameterInfoImpl
import com.intellij.refactoring.extractclass.ExtractClassProcessor
import com.intellij.refactoring.introduceParameterObject.IntroduceParameterObjectProcessor
import com.intellij.refactoring.introduceparameterobject.JavaIntroduceParameterObjectClassDescriptor
import com.intellij.refactoring.util.classMembers.MemberInfo
import org.jetbrains.research.refactoringDemoPlugin.util.extractKotlinAndJavaClasses
import java.io.File
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.kotlin.lombok.utils.decapitalize
import com.intellij.openapi.application.WriteAction
import com.intellij.util.IncorrectOperationException
import com.intellij.psi.impl.source.PsiParameterImpl;
import javaslang.Tuple
import javaslang.Tuple3
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import kotlin.io.path.Path

class DataClumpEndpoint(val filePath: String, val className: String, val methodName: String?,val dataClumpType:String,val dataClumpKey:String, val position: Position) {}

open class DataClumpRefactorer(private val projectPath:File) {
    fun getURI(path: String): String? {
        try {
            return "file://" + java.nio.file.Paths.get(projectPath.toPath().toString(), path).toString()
        } catch (e: Exception) {
            println("Error while creating path")
            println(e)
            return null
        }
    }
    public final val  DATA_CLUMP_TYPE_FIELDS="fields"
    public final val DATA_CLUMP_TYPE_PARAMETERS="parameters"
    fun refactorDataClump(project:Project,suggestedNameWithDataClumpTypeContext: SuggestedNameWithDataClumpTypeContext){
        val context=suggestedNameWithDataClumpTypeContext.context
        val suggestedClassName=suggestedNameWithDataClumpTypeContext.suggestedName
        val endpoints = arrayOf(
            DataClumpEndpoint(
                getURI(context.from_file_path)!!,
                context.from_class_or_interface_name,
                context.from_method_name,
                if(context.from_method_name==null) DATA_CLUMP_TYPE_FIELDS else DATA_CLUMP_TYPE_PARAMETERS,
                suggestedNameWithDataClumpTypeContext.context.key,
                context.data_clump_data.values.map { it.position }.first()
            ),
            DataClumpEndpoint(
                getURI(context.to_file_path)!!,
                context.to_class_or_interface_name,
                context.to_method_name,
                if(context.to_method_name==null) DATA_CLUMP_TYPE_FIELDS else DATA_CLUMP_TYPE_PARAMETERS,
                suggestedNameWithDataClumpTypeContext.context.key,
                context.data_clump_data.values.map { it.to_variable.position }.first()

            )
        )
    println(endpoints[0].filePath +" " + endpoints[1].filePath)
        var loopedOnce=false
        for (ep in endpoints) {
            if(!java.nio.file.Files.exists(Path((ep.filePath.replace("file://",""))))){
                println("ignoring "+ep.filePath)
                continue;
            }

            loopedOnce=refactorDataClumpEndpoint(ep.dataClumpType,project, suggestedClassName,suggestedClassName in nameClassMap || loopedOnce ,ep,context.data_clump_data.values.map { it.name }.toSet())

            commit(project,ep.filePath)

        }
        FileDocumentManager.getInstance().saveAllDocuments()
    }
    fun calculateOffset(text: CharSequence, lineNumber: Int, columnNumber: Int): Int {
        var offset = 0
        val lines = text.split('\n')

        for (i in 0 until lineNumber ) {
            offset += lines[i].length+1;// Add 1 for the newline character
        }

        return offset + columnNumber
    }
    val nameClassMap= mutableMapOf<String,PsiClass>()
    fun getPackageName(file: PsiFile): String {
        if(file is PsiJavaFile){
            return (file as PsiJavaFile).packageName
        }
        return "org/example";
    }
    fun commit(project: Project,dir:VirtualFile){
        VfsUtil.markDirtyAndRefresh(true, true, true, dir)
        commitAll(project)
    }
    fun commit(project:Project,uri:String){
        val man = VirtualFileManager.getInstance()
        val dir = man.findFileByUrl(uri)!!.parent
        commit(project,dir)
    }
    fun commitAll(project: Project){
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        FileDocumentManager.getInstance().saveAllDocuments()
    }
    fun waitForIndexing(project: Project) {
        ApplicationManager.getApplication().invokeAndWait {
            while (DumbService.getInstance(project).isDumb) {
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }
    }
    fun replaceClassOccurences(project: Project,oldClassName:String,newClassName:String)
    {
        val lowerCaseOld=oldClassName.decapitalize()
        val lowerCaseNew=newClassName.decapitalize()
       for(cls in project.extractKotlinAndJavaClasses()){
           println(cls)
           var text=java.nio.file.Files.readString(cls.containingFile.virtualFile.toNioPath())
           text=text.replace(oldClassName,newClassName).replace(lowerCaseOld,lowerCaseNew)
              java.nio.file.Files.writeString(cls.containingFile.virtualFile.toNioPath(),text)
           if(cls.name==oldClassName){
              java.nio.file.Files.delete(cls.containingFile.virtualFile.toNioPath())
           }


       }
        val session= RefreshQueue.getInstance().createSession(false,true){

        }
        session.launch()
    }
    protected fun getMethodAndParamsToRefactor(dataClumpClass:PsiClass?,methodName:String,relevantParameters:Set<String>,offset:Int): Tuple3<PsiMethod, List<ParameterInfoImpl>, List<PsiParameter>>? {

            val allMethods = dataClumpClass!!.findMethodsByName(methodName, false)
        val method = allMethods.filter { it.parameterList.parameters.size>=relevantParameters.size  && it.parameterList.parameters.map { it.name }.containsAll(relevantParameters)}.minByOrNull { Math.abs(Math.abs(it.startOffset - offset)) }
    if(method==null){
      println(methodName+" not found with parameters "+relevantParameters.joinToString(",")+" in "+dataClumpClass.name)
        return null
    }
        val allParams=method.parameterList.parameters
        var index = 0;
        val parameters=mutableListOf<PsiParameter>()
        val parameterInfos = mutableListOf<ParameterInfoImpl>()
        for (param in method.parameterList.parameters) {
            println(param.name)
            if (param.name in relevantParameters) {
                parameterInfos.add(ParameterInfoImpl(index, param.name!!, param.type))
                parameters.add(param)
                index++

            }
        }
        return Tuple3(method,parameterInfos.toList(),parameters)

    }
    protected fun getFieldsToRefactor(dataClumpClass:PsiClass?,relevantParameters:Set<String>): List<PsiField>{
        val fields = mutableListOf<PsiField>()
        for (field in dataClumpClass!!.fields) {
            println(field.name)
            if (field.name in relevantParameters) {
                fields.add(field)
            }
        }
        return fields.toList()
    }
    protected open fun refactorDataClumpEndpoint(dataClumpType:String, project: Project, suggestedClassName: String, classProbablyExisting:Boolean, ep:DataClumpEndpoint, relevantParameters:Set<String>): Boolean {
        val man = VirtualFileManager.getInstance()
        val vFile = man.findFileByUrl(ep.filePath)!!
        val dataClumpFile = PsiManager.getInstance(project).findFile(vFile)!!
        val packageName = getPackageName(dataClumpFile)
        val moveDestination =
            KeepLocationMoveDestination()
        val dataClumpClass =
            (dataClumpFile as PsiClassOwner)
                .classes
                .filter { it.name == ep.className }
                .first()
        if(dataClumpType=="parameters"){
            val position=ep.position
            val  data=getMethodAndParamsToRefactor(dataClumpClass,ep.methodName!!,relevantParameters,calculateOffset(dataClumpFile.text,position.startLine,position.startColumn))
            if(data==null){
                return false
            }
            val method=data._1
            val parameterInfos=data._2
            if(parameterInfos.size< MIN_SIZE_OF_DATA_CLUMP){
                return false
            }
            val descriptor:JavaIntroduceParameterObjectClassDescriptor;
                if(classProbablyExisting){
                    descriptor=JavaKeepExistingClassIntroduceParameterObjectDescriptor(packageName,moveDestination,nameClassMap[suggestedClassName]!!,false,"public",parameterInfos.toTypedArray(),method,true)
                }else{
                    descriptor= JavaIntroduceParameterObjectClassDescriptor(
                        suggestedClassName,
                        packageName,
                        moveDestination,
                        classProbablyExisting,
                        false,
                        "public",
                        parameterInfos.toTypedArray(),
                        method,
                        true
                    )
                    descriptor.existingClass=dataClumpClass
                }


            val processor = IntroduceParameterObjectProcessor(method, descriptor, parameterInfos, false)

            println("### running")
            processor.run()
            nameClassMap[suggestedClassName]=descriptor.existingClass
            return true
        }

        else if(dataClumpType=="fields"){

            val members=getFieldsToRefactor(dataClumpClass,relevantParameters)

            var extractedClassName=suggestedClassName
            if(classProbablyExisting){
                extractedClassName+= System.currentTimeMillis()

            }
            val extractClassProcessor= ExtractClassProcessor(dataClumpClass,members,emptyList(),emptyList(),packageName,moveDestination,extractedClassName,"public",true,emptyList(),false)
            extractClassProcessor.run()

            if(classProbablyExisting){
                commit(project,ep.filePath)
               //replaceClassOccurences(project,extractClassProcessor.createdClass.name!!,suggestedClassName)
                val realClass=nameClassMap[suggestedClassName]!!
                 commit(project,ep.filePath)
                 val session= RefreshQueue.getInstance().createSession(false,true){

                 }
                 session.launch()
                waitForIndexing(project)
                 val allClasses=project.extractKotlinAndJavaClasses()
                 val references= ReferencesSearch.search(extractClassProcessor.createdClass, GlobalSearchScope.allScope(project)).findAll()
                println("References count "+ references.size)
                /*  val references2= ReferencesSearch.search(allClasses[2], GlobalSearchScope.allScope(project)).findAll()
                 val hallo=5
                 for(r in refList){
                     val methodParent=r.element.findParentOfType<PsiMethod>()
                     val fieldParent=r.element.findParentOfType<PsiField>()
                     val instantiationParent=r.element.findParentOfType<PsiNewExpression>()
                     val paramParent=r.element.findParentOfType<PsiParameter>()
                     val variableParent=r.element.findParentOfType<PsiVariable>()
                     WriteAction.run<IncorrectOperationException>(){
                         try {
                             if(instantiationParent!=null){
                                 instantiationParent.classReference!!.replace(realClass)
                             }
                             else if(paramParent!=null){
                                 paramParent.typeElement!!.replace(realClass)
                             }
                             else if(fieldParent!=null){
                                 fieldParent.typeElement!!.replace(realClass)
                             }
                             else if(methodParent!=null){
                                 methodParent.returnTypeElement!!.replace(realClass)
                             }
                             else if(variableParent!=null){
                                 variableParent.typeElement!!.replace(realClass)
                             }
                         }catch (ex:Exception){
                             println("test")
                         }



                     }


                 }*/
            }
            else{
                nameClassMap[suggestedClassName]=extractClassProcessor.createdClass
            }

        }
        return true
    }

     fun findClassRec(classes: Array<PsiClass>, className: String): PsiClass?{
        for (cls in classes){
            if(cls.name==className){
                return cls
            }
            val innerClasses=cls.innerClasses
            if(innerClasses.isNotEmpty()){
                val innerClass=findClassRec(innerClasses,className)
                if(innerClass!=null){
                    return innerClass
                }
            }
        }
        return null
     }
}