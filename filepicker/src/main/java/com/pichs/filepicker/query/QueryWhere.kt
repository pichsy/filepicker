package com.pichs.filepicker.query

import android.os.Parcelable
import android.provider.MediaStore
import kotlinx.parcelize.Parcelize

/**
 * 以什么文件名字
 */
@Parcelize
data class QueryWhere(
    var section: String? = null,
    var sectionArgs: Array<String>? = null,
) : Parcelable {

    class Builder {
        private var sectionBuilder: StringBuffer? = null
        private var sectionArgsList: MutableList<String>? = null

        private fun initSection() {
            if (sectionBuilder == null) {
                sectionBuilder = StringBuffer()
            }
            if (sectionArgsList == null) {
                sectionArgsList = mutableListOf()
            }
        }

        fun leftBracket(): Builder {
            initSection()
            sectionBuilder?.append("(")
            return this
        }

        fun rightBracket(): Builder {
            initSection()
            sectionBuilder?.append(")")
            return this
        }

        fun and(): Builder {
            initSection()
            if (isNotEndWithAndOr()) {
                sectionBuilder?.append(" AND ")
            }
            return this
        }

        fun or(): Builder {
            initSection()
            if (isNotEndWithAndOr()) {
                sectionBuilder?.append(" OR ")
            }
            return this
        }

        fun removeEndAndOr(): Builder {
            if (!isNotEndWithAndOr()) {
                sectionBuilder?.let { secb ->
                    // 判断最后一个是不是AND或者OR
                    if (secb.endsWith(" AND ")) {
                        val lastIndexAnd = secb.lastIndexOf(" AND ")
                        if (lastIndexAnd != -1) {
                            sectionBuilder = StringBuffer(secb.substring(0, lastIndexAnd))
                            return this
                        }
                    }
                    if (secb.endsWith(" OR ")) {
                        val lastIndexOr = secb.lastIndexOf(" OR ")
                        if (lastIndexOr != -1) {
                            sectionBuilder = StringBuffer(secb.substring(0, lastIndexOr))
                            return this
                        }
                    }
                }
            }
            return this
        }

        /**
         * 本质是匹配文件夹名字的前缀是否是参数这个
         * @param folderName 文件夹名字
         */
        fun folderNameStartWith(folderName: String): Builder {
            initSection()
            sectionBuilder?.append("${MediaStore.Files.FileColumns.DATA} LIKE ? ")
            sectionArgsList?.add("$folderName%")
            return this
        }

        fun folderNameNotStartWith(folderName: String): Builder {
            initSection()
            sectionBuilder?.append("${MediaStore.Files.FileColumns.DATA} NOT LIKE ? ")
            sectionArgsList?.add("$folderName%")
            return this
        }

        fun fileNameEquals(fileName: String): Builder {
            initSection()
            sectionBuilder?.append("${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? ")
            sectionArgsList?.add(fileName)
            return this
        }

        fun fileNameStartWith(startWith: String): Builder {
            initSection()
            sectionBuilder?.append("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? ")
            sectionArgsList?.add("$startWith%")
            return this
        }

        fun fileNameNotStartWith(startWith: String): Builder {
            initSection()
            sectionBuilder?.append("${MediaStore.Files.FileColumns.DISPLAY_NAME} NOT LIKE ? ")
            sectionArgsList?.add("$startWith%")
            return this
        }

        fun fileNameEndWith(endWith: String): Builder {
            initSection()
            sectionBuilder?.append("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? ")
            sectionArgsList?.add("%$endWith")
            return this
        }

        fun fileNameNotEndWith(endWith: String): Builder {
            initSection()
            sectionBuilder?.append("${MediaStore.Files.FileColumns.DISPLAY_NAME} NOT LIKE ? ")
            sectionArgsList?.add("%$endWith")
            return this
        }

        fun fileNameContains(contains: String): Builder {
            initSection()
            sectionBuilder?.append("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? ")
            sectionArgsList?.add("%$contains%")
            return this
        }

        fun fileNameNotContains(contains: String): Builder {
            initSection()
            sectionBuilder?.append("${MediaStore.Files.FileColumns.DISPLAY_NAME} NOT LIKE ? ")
            sectionArgsList?.add("%$contains%")
            return this
        }

        /**
         * 根据文件类型查询
         * @param mimeType 文件类型，完整的类型
         */
        fun mimeTypeEquals(mimeType: String): Builder {
            initSection()
            sectionBuilder?.append("${MediaStore.Files.FileColumns.MIME_TYPE} = ? ")
            sectionArgsList?.add(mimeType)
            return this
        }


        fun mimeTypeNotEquals(mimeType: String): Builder {
            initSection()
            sectionBuilder?.append("${MediaStore.Files.FileColumns.MIME_TYPE} != ? ")
            sectionArgsList?.add(mimeType)
            return this
        }

        /**
         * 根据文件类型前缀
         */
        fun mimeTypeStartWith(mimeTypePrefix: String): Builder {
            initSection()
            sectionBuilder?.append("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? ")
            sectionArgsList?.add("$mimeTypePrefix%")
            return this
        }

        fun mimeTypeNotStartWith(mimeTypePrefix: String): Builder {
            initSection()
            sectionBuilder?.append("${MediaStore.Files.FileColumns.MIME_TYPE} NOT LIKE ? ")
            sectionArgsList?.add("$mimeTypePrefix%")
            return this
        }

        /**
         * 根据文件类型包含
         */
        fun mimeTypeContains(mimeTypeContains: String): Builder {
            initSection()
            sectionBuilder?.append("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? ")
            sectionArgsList?.add("%$mimeTypeContains%")
            return this
        }

        fun mimeTypeNotContains(mimeTypeContains: String): Builder {
            initSection()
            sectionBuilder?.append("${MediaStore.Files.FileColumns.MIME_TYPE} NOT LIKE ? ")
            sectionArgsList?.add("%$mimeTypeContains%")
            return this
        }

        internal fun mediaTypeEquals(mediaType: Int): Builder {
            initSection()
            sectionBuilder?.append("${MediaStore.Files.FileColumns.MEDIA_TYPE} =? ")
            sectionArgsList?.add(mediaType.toString())
            return this
        }

        internal fun mediaTypeNotEquals(mediaType: Int): Builder {
            initSection()
            sectionBuilder?.append("${MediaStore.Files.FileColumns.MEDIA_TYPE} !=? ")
            sectionArgsList?.add(mediaType.toString())
            return this
        }

        fun sizeGreaterThan(size: Int): Builder {
            initSection()
            sectionBuilder?.append("${MediaStore.Files.FileColumns.SIZE} > ? ")
            sectionArgsList?.add("${size}")
            return this
        }

        fun sizeLessThan(size: Int): Builder {
            initSection()
            sectionBuilder?.append("${MediaStore.Files.FileColumns.SIZE} < ? ")
            sectionArgsList?.add("$size")
            return this
        }

        private fun isSqlStartEmpty(): Boolean {
            return sectionBuilder.isNullOrEmpty() || sectionBuilder.isNullOrBlank()
        }

        private fun buildSection(): String? {
            if (sectionBuilder != null) {
                // 如果有条件，则添加括号
                sectionBuilder?.insert(0, "(")
                sectionBuilder?.append(")")
            }
            return sectionBuilder?.toString()
        }

        private fun buildArgs(): Array<String>? {
            return sectionArgsList?.toTypedArray()
        }

        fun build(): QueryWhere {
            return QueryWhere(
                section = buildSection(),
                sectionArgs = buildArgs()
            )
        }

        /**
         * 判断是否以suffix结尾
         */
        private fun isNotEndWithAndOr(): Boolean {
            if (sectionBuilder == null) {
                return true
            }

            if (sectionBuilder?.endsWith(" AND ") == true) {
                return false
            }

            if (sectionBuilder?.endsWith(" OR ") == true) {
                return false
            }

            return true
        }
    }

    operator fun plus(other: QueryWhere): QueryWhere {
        val sec = if (this.section.isNullOrEmpty()) {
            other.section
        } else if (other.section.isNullOrEmpty()) {
            this.section
        } else {
            "${this.section} AND ${other.section}"
        }
        val args = if (this.sectionArgs.isNullOrEmpty()) {
            other.sectionArgs
        } else if (other.sectionArgs.isNullOrEmpty()) {
            this.sectionArgs
        } else {
            this.sectionArgs?.plus(other.sectionArgs ?: emptyArray())
        }
        return QueryWhere(section = sec, sectionArgs = args)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QueryWhere

        if (section != other.section) return false
        if (sectionArgs != null) {
            if (other.sectionArgs == null) return false
            if (!sectionArgs.contentEquals(other.sectionArgs)) return false
        } else if (other.sectionArgs != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = section?.hashCode() ?: 0
        result = 31 * result + (sectionArgs?.contentHashCode() ?: 0)
        return result
    }

}

