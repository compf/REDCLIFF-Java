package dataClumpRefactoring
import com.intellij.psi.*
import com.intellij.psi.util.PropertyUtilBase
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.refactoring.MoveDestination
import com.intellij.refactoring.changeSignature.ParameterInfoImpl
import  com.intellij.refactoring.introduceparameterobject.JavaIntroduceParameterObjectClassDescriptor

/**
 * Used to allow the base DataClumpRefactoring to keep the existing class and introduce a parameter object,
 * is a little bit hacky, but it works.
 */
class JavaKeepExistingClassIntroduceParameterObjectDescriptor(
    packageName: String?,
    moveDestination: MoveDestination?,
    existingClass: PsiClass,
    createInnerClass: Boolean,
    newVisibility: String?,
    paramsToMerge: Array<out ParameterInfoImpl>?,
    method: PsiMethod?,
    generateAccessors: Boolean
) : JavaIntroduceParameterObjectClassDescriptor(
    existingClass.name!!,
    packageName,
    moveDestination,
    true,
    createInnerClass,
    newVisibility,
    paramsToMerge,
    method,
    generateAccessors
) {
    init {
        this.existingClass=existingClass
    }

    override fun findCompatibleConstructorInExistingClass(method: PsiMethod?): PsiMethod? {
        for(constr in existingClass.constructors){
            if(isConstructorCompatible(constr)){
                return constr
            }
        }
        return null
    }
    fun isConstructorCompatible(constr:PsiMethod):Boolean{
        val constrParamTypes=constr.parameterList.parameters.map { it.type.canonicalText }.toSet()
        val fieldTypes=paramsToMerge.map { it.typeWrapper.getType(constr).canonicalText }.toSet()
        return  constrParamTypes==fieldTypes
    }



}