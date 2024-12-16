package com.kdroid.seforim.database.builders.tableofcontent

import com.kdroid.seforim.database.builders.tableofcontent.api.fetchTableOfContents
import com.kdroid.seforim.database.common.constants.GENERATED_FOLDER
import com.kdroid.seforim.database.common.constants.TABLE_OF_CONTENTS
import com.kdroid.seforim.database.common.filesutils.saveToJson
import com.kdroid.seforim.database.common.filesutils.saveToProtobuf
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        saveToProtobuf(fetchTableOfContents(), "$GENERATED_FOLDER/$TABLE_OF_CONTENTS.proto")
        saveToJson(fetchTableOfContents(), "$GENERATED_FOLDER/$TABLE_OF_CONTENTS.json")
    }
}
