package ru.drsn.waves.domain.model.p2p

import com.google.gson.annotations.SerializedName

/**
 * Полезная нагрузка для информации о профиле пользователя.
 */
data class UserProfilePayload(
    @SerializedName("user_id")
    val userId: String, // Никнейм пользователя, чей это профиль

    @SerializedName("display_name")
    val displayName: String, // Отображаемое имя

    @SerializedName("avatar_url")
    val avatarUrl: String?,

    @SerializedName("status_message")
    val statusMessage: String?
    // Другие поля профиля
)