package ru.drsn.waves.domain.model.p2p

import com.google.gson.annotations.SerializedName

data class FileTransferCompletePayload(
    @SerializedName("file_transfer_id")
    val fileTransferId: String,
    @SerializedName("success") // true, если отправитель считает, что все отправлено успешно
    val success: Boolean,
    @SerializedName("final_hash")
    val finalHash: String? = null
)