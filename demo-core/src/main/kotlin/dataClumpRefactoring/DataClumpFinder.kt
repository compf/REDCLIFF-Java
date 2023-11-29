package dataClumpRefactoring

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.research.refactoringDemoPlugin.util.*
import java.util.*


class DataClumpFindingResult(val variablePairs: MutableList<Pair<PsiVariable, PsiVariable>> = mutableListOf<Pair<PsiVariable, PsiVariable>>()) {

    fun hasEnoughItems(): Boolean {
        return variablePairs.size >= MIN_SIZE_OF_DATA_CLUMP
    }
}

const val MIN_SIZE_OF_DATA_CLUMP = 3
fun buildKey(classKey: String?, methodKey: String?, varKey: String?): String {
    return classKey + "_" + methodKey + "_" + varKey
}

fun getPosition(element: PsiElement): Position {

    val containingFile: PsiFile = element.getContainingFile()
    val project = containingFile.project
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    val document = psiDocumentManager.getDocument(containingFile)!!
    val startLine = document.getLineNumber(element.startOffset)
    val endLine = document.getLineNumber(element.endOffset)
    return Position(
        startLine,
        element.startOffset - document.getLineStartOffset(startLine),
        endLine,
        element.endOffset - document.getLineStartOffset(endLine)
    )

}

class DataClumpFinder(private val project: Project) {

    fun run(): DataClumpsTypeContext {
        //Thread.sleep(10000)
        val variables = project.getAllRelevantVariables(MIN_SIZE_OF_DATA_CLUMP)
        val detectorContext = DataClumpsDetectorContext("Redlciffe", "", "1.0", "")
        val dataClumpMap = mutableMapOf<String, DataClumpTypeContext>()

        var i = 0
        while (i < variables.size) {
            var j = i + 1
            while (j < variables.size) {
                val var1 = variables[i].toTypedArray()
                val var2 = variables[j].toTypedArray()
                val dataClumpInfo = checkDataClump(var1, var2)
                if (dataClumpInfo != null) {
                    val fromClass = var1[0].getParentOfType<PsiClass>(true)!!
                    val toClass = var1[0].getParentOfType<PsiClass>(true)!!
                    var fromMethod: PsiMethod? = null
                    var toMethod: PsiMethod? = null

                    if (var1[0].getParentOfType<PsiMethod>(true) != null) {
                        fromMethod = var1[0].getParentOfType<PsiMethod>(true)
                    }
                    if (var2[0].getParentOfType<PsiMethod>(true) != null) {
                        toMethod = var2[0].getParentOfType<PsiMethod>(true)
                    }
                    val dataClumpType = if (fromMethod == null && toMethod == null) {
                        DataClumpType.fields_to_fields_data_clump
                    } else if (fromMethod == null && toMethod != null || fromMethod != null && toMethod == null) {
                        DataClumpType.parameters_to_fields_data_clump
                    } else {
                        DataClumpType.parameters_to_parameters_data_clump
                    }


                    DataClumpType.fields_to_fields_data_clump
                    val dataClumpDataMap = mutableMapOf<String, DataClumpsVariableFromContext>()
                    val dcContext = DataClumpTypeContext(
                        "data_clump", createKey(var1, var2), 1.0,
                        var1[0].containingFile.virtualFile.path,
                        fromClass.name!!,
                        fromClass.qualifiedName!!,
                        fromMethod?.name,
                        buildKey(fromClass.qualifiedName, fromMethod?.name, null),


                        var2[0].containingFile.virtualFile.path,
                        toClass.name!!,
                        toClass.qualifiedName!!,
                        toMethod?.name,
                        buildKey(toClass.qualifiedName, toMethod?.name, null),
                        dataClumpType,
                        null,
                        dataClumpDataMap


                    )
                    dataClumpMap[dcContext.key] = dcContext
                    for (pair in dataClumpInfo.variablePairs) {
                        val fromContext = DataClumpsVariableFromContext(
                            buildKey(fromClass.qualifiedName, fromMethod?.name, pair.first.name), pair.first.name!!,
                            pair.first.type.canonicalText,
                            null,
                            getPosition(pair.first),
                            1.0,
                            DataClumpsVariableToContext(
                                buildKey(toClass.qualifiedName, toMethod?.name, pair.second.name),
                                pair.second.name!!,
                                pair.second.type.canonicalText, getPosition(pair.second), null
                            )
                        )
                        dataClumpDataMap[fromContext.key] = fromContext
                    }



                    j++
                }
                i++
            }


        }
        val projectInfo =
            ProjectInfo(project.basePath, project.name, "1.0", null, null, null, null, null, null, null, null, null)
        val reportSummary = ReportSummary(0, 0, 0, 0, 0, 0, 0, null)
        val context =
            DataClumpsTypeContext("1.0", detectorContext, dataClumpMap, "0", "java", reportSummary, projectInfo)
        return context
    }

    private fun createKey(var1: Array<PsiVariable>, var2: Array<PsiVariable>): String {
        return UUID.randomUUID().toString()
    }

    private fun isNameSimiliar(var1: PsiVariable, var2: PsiVariable): Boolean {
        return var1.name == var2.name
    }

    private fun isTypeSimiliar(var1: PsiVariable, var2: PsiVariable): Boolean {
        return var1.type == var2.type;
    }

    fun checkDataClump(variables1: Array<PsiVariable>, variables2: Array<PsiVariable>): DataClumpFindingResult? {

        val result = DataClumpFindingResult()
        for (var1 in variables1) {
            for (var2 in variables2) {
                if (isNameSimiliar(var1, var2) && isTypeSimiliar(var1, var2)) {
                    result.variablePairs.add(Pair(var1, var2))
                }
            }
        }
        if (result.hasEnoughItems()) {
            return result
        }
        return null
    }
}
