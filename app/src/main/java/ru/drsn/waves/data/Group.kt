package ru.drsn.waves.data

data class Group(
    val name: String,
    val members: MutableSet<String> = mutableSetOf(),
    val messages: MutableList<String> = mutableListOf() // можешь заменить на `Message` потом
)