package ru.drsn.waves.domain.model.crypto

import com.google.protobuf.ByteString

typealias PublicKey = ByteString

typealias JavaPublicKey = java.security.PublicKey

typealias Signature = ByteString

typealias AuthToken = String

@JvmInline
value class MnemonicPhrase(val value: String)

sealed class InitializationResult {
    data object KeysLoaded : InitializationResult()
    data class KeysGenerated(val mnemonic: MnemonicPhrase) : InitializationResult()
}