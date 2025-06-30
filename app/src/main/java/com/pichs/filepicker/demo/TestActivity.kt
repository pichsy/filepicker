package com.pichs.filepicker.demo

import com.pichs.filepicker.demo.databinding.ActivityTestBinding
import com.pichs.xbase.binding.BindingActivity

class TestActivity : BindingActivity<ActivityTestBinding>() {

    override fun afterOnCreate() {
        supportFragmentManager.beginTransaction()
            .replace(binding.flContainer.id, TestFragment())
            .commitAllowingStateLoss()
    }
}