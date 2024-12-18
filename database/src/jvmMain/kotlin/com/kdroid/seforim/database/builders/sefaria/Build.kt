package com.kdroid.seforim.database.builders.sefaria

import com.kdroid.seforim.constants.GENERATED_FOLDER
import com.kdroid.seforim.database.builders.sefaria.directories.createDirectoriesAndFilesWithIndex
import com.kdroid.seforim.database.builders.sefaria.tableofcontent.fetchTableOfContents
import com.kdroid.seforim.database.common.constants.TABLE_OF_CONTENTS
import com.kdroid.seforim.database.common.filesutils.readFromProtobuf
import com.kdroid.seforim.database.common.filesutils.saveToProtobuf

suspend fun main () {
    //Get the index of all the books from Sefaria
    saveToProtobuf(fetchTableOfContents(), "$GENERATED_FOLDER/$TABLE_OF_CONTENTS.proto")
    //Create the books and a new Index
    createDirectoriesAndFilesWithIndex(GENERATED_FOLDER, readFromProtobuf("$GENERATED_FOLDER/$TABLE_OF_CONTENTS.proto"), createBooks = true)
}