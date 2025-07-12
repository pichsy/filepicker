package com.pichs.filepicker.demo.newpicker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.pichs.filepicker.demo.R

/**
 * 本地媒体选择器演示 Activity
 */
class LocalMediaPickerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_media_picker)
        initFragment()
    }

    private fun initFragment() {
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LocalMediaPickerFragment.newInstance())
                .commit()
        }
    }

}
