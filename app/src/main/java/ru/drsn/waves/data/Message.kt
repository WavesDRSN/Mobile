package ru.drsn.waves.data

data class Message(
    val id: String, // Уникальный ID сообщения
    val text: String, // Текст сообщения
    val senderId: String, // ID отправителя
    val timestamp: Long // Время отправки (в мс)
    // Можно добавить другие поля: статус (отправлено, доставлено, прочитано), тип контента и т.д.
)
