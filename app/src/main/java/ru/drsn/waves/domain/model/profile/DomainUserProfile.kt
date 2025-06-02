package ru.drsn.waves.domain.model.profile

/**
 * Доменная модель для хранения данных профиля пользователя.
 * @param userId Никнейм пользователя (он же является его уникальным ID).
 * @param displayName Отображаемое имя пользователя.
 * @param statusMessage Статус или "о себе".
 * @param avatarUri Локальный URI или удаленный URL аватара.
 */
data class DomainUserProfile(
    val userId: String, // Никнейм
    val displayName: String,
    val statusMessage: String?,
    val lastLocalEditTimestamp: Long,
    val avatarUri: String? // Может быть локальным URI или удаленным URL
)
