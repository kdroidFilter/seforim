package com.kdroid.seforim.database.builders.tableofcontent

import com.kdroid.seforim.database.builders.tableofcontent.api.fetchTableOfContents
import com.kdroid.seforim.database.common.constants.GENERATED_FOLDER
import com.kdroid.seforim.database.common.filesutils.saveToJson
import com.kdroid.seforim.database.common.filesutils.saveToProtobuf
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
      //  saveToProtobuf(fetchTableOfContents(), "$GENERATED_FOLDER/tableOfContent.proto")
        saveToJson(fetchTableOfContents(), "$GENERATED_FOLDER/tableOfContent.json")
    }
}
