package dataClumpRefactoring
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.changeSignature.ParameterInfoImpl
import com.intellij.refactoring.extractclass.ExtractClassProcessor
import com.intellij.refactoring.introduceParameterObject.IntroduceParameterObjectProcessor
import com.intellij.refactoring.util.classMembers.MemberInfo
import java.io.File
class DataClumpEndpoint(val filePath: String, val className: String, val methodName: String?,val dataClumpType:String) {}

class DataClumpRefactorer(private val projectPath:File) {
    fun getURI(path: String): String? {
        try {
            return "file://" + java.nio.file.Paths.get(projectPath.toPath().toString(), path).toString()
        } catch (e: Exception) {
            println("Error while creating path")
            println(e)
            return null
        }
    }
    fun refactorDataClump(project:Project,suggestedNameWithDataClumpTypeContext: SuggestedNameWithDataClumpTypeContext){
        val context=suggestedNameWithDataClumpTypeContext.context
        val suggestedClassName=suggestedNameWithDataClumpTypeContext.suggestedName
        val dataClumpTypeSplitted=context.data_clump_type.split("_")
        val endpoints = arrayOf(
            DataClumpEndpoint(
                getURI(context.from_file_path)!!,
                context.from_class_or_interface_name,
                context.from_method_name,
                dataClumpTypeSplitted[0]
            ),
            DataClumpEndpoint(
                getURI(context.to_file_path)!!,
                context.to_class_or_interface_name,
                context.to_method_name,
                dataClumpTypeSplitted[2]
            )
        )

        var loopedOnce=false
        for (ep in endpoints) {

            refactorDataClumpEndpoint(ep.dataClumpType,project, suggestedClassName,suggestedClassName in nameClassMap || loopedOnce ,ep,context.data_clump_data.values.map { it.name }.toSet())
            loopedOnce=true

        }
        FileDocumentManager.getInstance().saveAllDocuments()
    }
    fun calculateOffset(text: CharSequence, lineNumber: Int, columnNumber: Int): Int {
        var offset = 0
        val lines = text.split('\n')

        for (i in 0 until lineNumber - 1) {
            offset += lines[i].length + 1 // Add 1 for the newline character
        }

        return offset + columnNumber - 1
    }
    private val nameClassMap= mutableMapOf<String,PsiClass>()
    fun getPackageName(file: PsiFile): String {
        if(file is PsiJavaFile){
            return (file as PsiJavaFile).packageName
        }
        return "org/example";
    }
    fun commit(project: Project){
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        FileDocumentManager.getInstance().saveAllDocuments()
    }

    private fun refactorDataClumpEndpoint(dataClumpType:String,project: Project, suggestedClassName: String,classProbablyExisting:Boolean,ep:DataClumpEndpoint,relevantParameters:Set<String>){
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

            val allMethods = dataClumpClass!!.findMethodsByName(ep.methodName!!, false)
            val method = allMethods[0]
            var index = 0;
            val parameterInfos = mutableListOf<ParameterInfoImpl>()
            for (param in method.parameterList.parameters) {
                println(param.name)
                if (param.name in relevantParameters) {
                    parameterInfos.add(ParameterInfoImpl(index, param.name!!, param.type))
                    index++

                }
            }


            val descriptor =
                com.intellij.refactoring.introduceparameterobject.JavaIntroduceParameterObjectClassDescriptor(
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
            val processor = IntroduceParameterObjectProcessor(allMethods[0], descriptor, parameterInfos, false)

            println("### running")
            processor.run()
            nameClassMap[suggestedClassName]=descriptor.existingClass
        }

        else if(dataClumpType=="fields"){

            val fields = mutableListOf<PsiField>()
            val members=mutableListOf<MemberInfo>()
            for (field in dataClumpClass.fields) {
                println(field.name)
                if (field.name in relevantParameters) {
                    fields.add(field)
                    members.add(MemberInfo(field))
                }
            }
            var extractedClassName=suggestedClassName
            if(classProbablyExisting){
                extractedClassName+= System.currentTimeMillis()

            }
            val extractClassProcessor= ExtractClassProcessor(dataClumpClass,fields,emptyList(),emptyList(),packageName,moveDestination,extractedClassName,"public",true,emptyList(),false)
            extractClassProcessor.run()

            if(classProbablyExisting){
                val realClass=nameClassMap[suggestedClassName]!!
                commit(project)
                val session= RefreshQueue.getInstance().createSession(false,true){

                }
                session.launch()
                val references= ReferencesSearch.search(extractClassProcessor.createdClass, GlobalSearchScope.projectScope(project)).findAll()
                /*for(r in refList){
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
    }
}