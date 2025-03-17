package ru.drsn.waves.webrtc.utils

data class DataModel(
    var target: String,
    var sender: String,
    var data: String,
    var type: DataModelType
) {
}