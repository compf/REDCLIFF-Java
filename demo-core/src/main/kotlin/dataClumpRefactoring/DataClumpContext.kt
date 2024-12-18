package dataClumpRefactoring
/**
 * Type definition for data clumps context
 */
data class DataClumpTypeContext(
    val type: String,
    val key: String,
    val probability: Double?,
    val from_file_path: String,
    val from_class_or_interface_name: String,
    val from_class_or_interface_key: String,
    val from_method_name: String?,
    val from_method_key: String?,
    val to_file_path: String,
    val to_class_or_interface_name: String,
    val to_class_or_interface_key: String,
    val to_method_name: String?,
    val to_method_key: String?,
    val data_clump_type: DataClumpType,
    val data_clump_type_additional: Map<String, Any>? = null,
    val data_clump_data: Map<String, DataClumpsVariableFromContext>
)

data class DataClumpsVariableFromContext(
    val key: String,
    val name: String,
    val type: String,
    val modifiers: List<String>? = null,
    val position: Position,
    val probability: Double?,
    val to_variable: DataClumpsVariableToContext
)

data class DataClumpsVariableToContext(
    val key: String,
    val name: String,
    val type: String,
    val position: Position,
    val modifiers: List<String>? = null
)

data class Position(
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int
)

data class SuggestedNameWithDataClumpTypeContext(
    val suggestedName: String,
    val context: DataClumpTypeContext
)

enum class DataClumpType {
    fields_to_fields_data_clump,
    parameters_to_fields_data_clump,
    parameters_to_parameters_data_clump
}

data class DataClumpsTypeContext(
    val report_version: String,
    val detector: DataClumpsDetectorContext,
    val data_clumps: Map<String, DataClumpTypeContext>,
    val report_timestamp: String,
    val target_language: String,
    val report_summary: ReportSummary,
    val project_info: ProjectInfo
)

data class DataClumpsDetectorContext(
    // Define properties for DataClumpsDetectorContext if available in your TypeScript code
    val name: String,
    val url: String?,
    val version: String,
    val options: Any?

)

data class ReportSummary(
    val amount_data_clumps: Int?,
    val amount_files_with_data_clumps: Int?,
    val amount_classes_or_interfaces_with_data_clumps: Int?,
    val amount_methods_with_data_clumps: Int?,
    val fields_to_fields_data_clump: Int?,
    val parameters_to_fields_data_clump: Int?,
    val parameters_to_parameters_data_clump: Int?,
    val additional: Map<String, Any>?
)

data class ProjectInfo(
    val project_url: String?,
    val project_name: String?,
    val project_version: String?,
    val project_commit_hash: String?,
    val project_tag: String?,
    val project_commit_date: String?,
    val number_of_files: Int?,
    val number_of_classes_or_interfaces: Int?,
    val number_of_methods: Int?,
    val number_of_data_fields: Int?,
    val number_of_method_parameters: Int?,
    val additional: Map<String, Any>?
)
