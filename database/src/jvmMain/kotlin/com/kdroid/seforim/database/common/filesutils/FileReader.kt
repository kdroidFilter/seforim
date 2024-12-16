package com.kdroid.seforim.database.common.filesutils

import com.kdroid.seforim.core.model.TableOfContent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.File

@OptIn(ExperimentalSerializationApi::class)
internal fun readFromProtobuf(fileName: String): List<TableOfContent> {
    // Read binary data from the file
    val fileBytes = File(fileName).readBytes()

    // Decode the data into Kotlin objects
    return ProtoBuf.decodeFromByteArray(ListSerializer(TableOfContent.serializer()), fileBytes)
}