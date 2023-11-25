package me.rhunk.snapenhance.core.messaging;

enum class ExportFormat(
    val extension: String,
){
    JSON("json"),
    TEXT("txt"),
    HTML("html");
}
