package ru.drsn.waves.domain.model.p2p

import com.google.gson.annotations.SerializedName

enum class P2pMessageType {
    @SerializedName("chat_message") // Важно для Gson, если имена полей в JSON отличаются
    CHAT_MESSAGE,

    @SerializedName("user_profile_info")
    USER_PROFILE_INFO,

    @SerializedName("onion_route_setup")
    ONION_ROUTE_SETUP,

    @SerializedName("onion_data_forward")
    ONION_DATA_FORWARD,

    @SerializedName("file_chunk") // Передача части файла
    FILE_CHUNK,

    @SerializedName("file_transfer_complete") // Отправитель сообщает о завершении
    FILE_TRANSFER_COMPLETE, // Может также содержать final_hash

    @SerializedName("file_transfer_error") // Сообщение об ошибке передачи (от любой из сторон)
    FILE_TRANSFER_ERROR
}
