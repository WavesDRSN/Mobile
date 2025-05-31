package ru.drsn.waves.domain.model.chat

data class DomainQuotedMessage(
    val messageId: String,
    val senderName: String,
    val contentPreview: String
)
