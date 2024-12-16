package com.kdroid.seforim.database.common.filesutils

import com.kdroid.seforim.database.builders.tableofcontent.model.TableOfContent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.File

@OptIn(ExperimentalSerializationApi::class)
internal fun saveToProtobuf(data: List<TableOfContent>, fileName: String) {
    // Convert the data to Protobuf
    val protobufData = ProtoBuf.encodeToByteArray(data)
    // Save the data to a file
    File(fileName).writeBytes(protobufData)
}

internal fun saveToJson(data: List<TableOfContent>, fileName: String) {
    // Convert the data to a JSON string
    val jsonData = Json.encodeToString(data)
    // Save to a file
    File(fileName).writeText(jsonData)
}