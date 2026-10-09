package com.pichs.filepicker.demo

import android.content.Intent
import com.pichs.filepicker.demo.databinding.ActivitySettingsBinding
import com.pichs.xbase.binding.BindingActivity
import com.pichs.xbase.kotlinext.fastClick

/**
 * 设置页 —— 收纳从首页挪出来的调试入口，让首页只留"选什么 / 选多少 → 开始"这条主链路。
 *
 * 目前只有两项：数据制造（批量生成测试文件）、相机 intent 探测（真机验证页）。
 * 后两项都是 demo 自己的工具，与库本身无关。
 */
class SettingsActivity : BindingActivity<ActivitySettingsBinding>() {

    override fun afterOnCreate() {
        binding.ivBack.fastClick { finish() }

        binding.btnDataFactory.fastClick {
            startActivity(Intent(this, DataFactoryActivity::class.java))
        }

        binding.btnCameraProbe.fastClick {
            startActivity(Intent(this, CameraProbeActivity::class.java))
        }
    }
}
