package ru.drsn.waves.domain.model.p2p

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import timber.log.Timber


object P2pMessageSerializer {
    val gson = Gson()
    private const val TAG = "P2pMessageSerializer"

    fun serialize(envelope: P2pMessageEnvelope): String? {
        return try {
            gson.toJson(envelope)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Ошибка сериализации P2pMessageEnvelope")
            null
        }
    }

    fun deserializeEnvelope(jsonString: String): P2pMessageEnvelope? {
        return try {
            gson.fromJson(jsonString, P2pMessageEnvelope::class.java)
        } catch (e: JsonSyntaxException) {
            Timber.tag(TAG).e(e, "Ошибка JSON синтаксиса при десериализации P2pMessageEnvelope")
            null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Общая ошибка десериализации P2pMessageEnvelope")
            null
        }
    }

    // Функции для сериализации конкретных payload'ов в JSON строку
    fun serializePayload(payload: Any): String? {
        return try {
            gson.toJson(payload)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Ошибка сериализации payload: ${payload::class.java.simpleName}")
            null
        }
    }

    // Функции для десериализации JSON строки в конкретные payload'ы
    // Использование reified inline функций для удобства
    inline fun <reified T> deserializePayload(jsonString: String): T? {
        return try {
            gson.fromJson(jsonString, T::class.java)
        } catch (e: JsonSyntaxException) {
            Timber.tag("P2pMessageSerializer").e(e, "Ошибка JSON синтаксиса при десериализации payload типа ${T::class.java.simpleName}")
            null
        } catch (e: Exception) {
            Timber.tag("P2pMessageSerializer").e(e, "Общая ошибка десериализации payload типа ${T::class.java.simpleName}")
            null
        }
    }
}