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

    @SerializedName("avatar_file_id") // Уникальный ID для сессии передачи файла аватара (если аватар передается P2P)
    val avatarFileId: String? = null,
    @SerializedName("avatar_file_name")
    val avatarFileName: String? = null,
    @SerializedName("avatar_mime_type")
    val avatarMimeType: String? = null,
    @SerializedName("avatar_file_size")
    val avatarFileSize: Long? = null,

    @SerializedName("avatar_remote_url") // Если аватар - это просто ссылка на внешний ресурс
    val avatarRemoteUrl: String? = null,

    @SerializedName("status_message")
    val statusMessage: String?
    // Другие поля профиля
)