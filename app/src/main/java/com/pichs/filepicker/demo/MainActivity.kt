package com.pichs.filepicker.demo

import com.pichs.filepicker.FilePicker
import com.pichs.filepicker.demo.databinding.ActivityMainBinding
import com.pichs.xbase.binding.BindingActivity
import com.pichs.xbase.kotlinext.fastClick
import java.io.File

class MainActivity : BindingActivity<ActivityMainBinding>() {


    override fun afterOnCreate() {

        binding.btnSelectFile.fastClick {
            FilePicker.with(this)
                .selectAll()
                .setMaxFileSize(200 * 1024 * 1024) // 设置最大文件大小为200MB
                .setRequestCode(1029)
                .setOnSelectCallback {

                }
                .build()
                .start()
        }

    }


}