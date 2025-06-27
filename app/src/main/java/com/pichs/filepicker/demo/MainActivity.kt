package com.pichs.filepicker.demo

import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.pichs.filepicker.FilePicker
import com.pichs.filepicker.demo.databinding.ActivityMainBinding
import com.pichs.xbase.binding.BindingActivity
import com.pichs.xbase.kotlinext.fastClick

class MainActivity : BindingActivity<ActivityMainBinding>() {


    override fun afterOnCreate() {

        binding.btnSelectFile.fastClick {
            XXPermissions.with(this)
                .unchecked()
                .permission(
                    Permission.READ_MEDIA_IMAGES,
                    Permission.READ_MEDIA_VIDEO,
                    Permission.READ_MEDIA_AUDIO,
                ).request { permissions, all ->
                    if (all) {
                        selectFile()
                    } else {
                        // 请授权后继续
                        // 权限请求失败
                        XXPermissions.startPermissionActivity(this, permissions)
                    }
                }
        }
    }

    fun selectFile() {
        FilePicker.with(this).selectAll().setMaxFileSize(200 * 1024 * 1024) // 设置最大文件大小为200MB
            .setRequestCode(1029).setOnSelectCallback {}.build().start()
    }


}