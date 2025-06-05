package ru.drsn.waves.domain.model.chat

enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    FILE,
    SYSTEM // Системные сообщения (например, "Пользователь X присоединился")
}