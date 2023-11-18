package org.jetbrains.research.refactoringDemoPlugin

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
    val data_clump_type: String,
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