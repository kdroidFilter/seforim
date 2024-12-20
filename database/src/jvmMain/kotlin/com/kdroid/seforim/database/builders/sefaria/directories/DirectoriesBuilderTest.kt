package com.kdroid.seforim.database.builders.sefaria.directories

import com.kdroid.seforim.constants.GENERATED_FOLDER
import com.kdroid.seforim.database.common.constants.TABLE_OF_CONTENTS
import com.kdroid.seforim.database.common.filesutils.readFromProtobuf
import com.kdroid.seforim.database.common.filesutils.saveToProtobuf

suspend fun main() {
   //  saveToProtobuf(fetchTableOfContents(), "$GENERATED_FOLDER/$TABLE_OF_CONTENTS.proto")
     createDirectoriesAndFilesWithIndex(GENERATED_FOLDER, readFromProtobuf("$GENERATED_FOLDER/$TABLE_OF_CONTENTS.proto"), createBooks = false)
}

