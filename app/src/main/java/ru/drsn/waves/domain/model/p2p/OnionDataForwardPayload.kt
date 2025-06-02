package ru.drsn.waves.domain.model.p2p

import com.google.gson.annotations.SerializedName

/**
 * Полезная нагрузка для пересылки данных через onion-маршрут.
 * Содержимое `data` будет зашифровано слоями "луковой" маршрутизации.
 */
data class OnionDataForwardPayload(
    @SerializedName("route_id")
    val routeId: String, // Идентификатор установленного маршрута

    @SerializedName("data") // Это могут быть байты другого P2pMessageEnvelope, зашифрованные для конечного получателя
    val data: String // Base64 закодированные зашифрованные данные
)