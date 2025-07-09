package com.pichs.filepicker.utils

import com.pichs.filepicker.R
import com.pichs.filepicker.entity.MediaEntity

object FilePickerIconUtils {

    fun getFileIcon(mediaEntity: MediaEntity): Int {
        return when {
            mediaEntity.isAudio() -> {
                if (mediaEntity.isMp3()) {
                    R.drawable.filepicker_ic_music_mp3
                } else if (mediaEntity.isWav()) {
                    R.drawable.filepicker_ic_music_wav
                } else if (mediaEntity.isM4a()) {
                    R.drawable.filepicker_ic_music_m4a
                } else if (mediaEntity.isFlac()) {
                    R.drawable.filepicker_ic_music_flac
                } else {
                    R.drawable.filepicker_ic_music_common
                }
            }

            mediaEntity.isWordDoc() -> {
                R.drawable.filepicker_ic_file_word
            }

            mediaEntity.isExcel() -> {
                R.drawable.filepicker_ic_file_excel
            }

            mediaEntity.isPdf() -> {
                R.drawable.filepicker_ic_file_pdf
            }

            mediaEntity.isPPT() -> {
                R.drawable.filepicker_ic_file_ppt
            }

            mediaEntity.isTxt() -> {
                R.drawable.filepicker_ic_file_txt
            }

            mediaEntity.isArchive() -> {
                R.drawable.filepicker_ic_file_zip
            }

            mediaEntity.isApk() -> {
                R.drawable.filepicker_ic_file_apk
            }

            mediaEntity.isImage() -> {
                if (mediaEntity.isGif()) {
                    R.drawable.filepicker_ic_file_gif
                } else if (mediaEntity.isJpeg()) {
                    R.drawable.filepicker_ic_file_jpg
                } else if (mediaEntity.isPng()) {
                    R.drawable.filepicker_ic_file_png
                } else if (mediaEntity.isWebp()) {
                    R.drawable.filepicker_ic_file_webp
                } else {
                    R.drawable.filepicker_ic_file_image_common
                }
            }

            mediaEntity.isVideo() -> {
                R.drawable.filepicker_ic_file_video
            }

            else -> {
                R.drawable.filepicker_ic_file_common
            }
        }
    }

}