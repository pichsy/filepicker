package com.pichs.filepicker.query

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


@Parcelize
enum class QueryType(val type: String) : Parcelable {
    IMAGE("image"),
    VIDEO("video"),
    AUDIO("audio"),
    GIF("image/gif"),
    NONE("none"),
}