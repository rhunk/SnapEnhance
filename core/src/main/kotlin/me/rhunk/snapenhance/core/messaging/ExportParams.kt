package me.rhunk.snapenhance.core.messaging

import me.rhunk.snapenhance.common.data.ContentType

class ExportParams(
    val exportFormat: ExportFormat = ExportFormat.HTML,
    val messageTypeFilter: List<ContentType>? = null,
    val amountOfMessages: Int? = null,
    val downloadMedias: Boolean = false,
)
