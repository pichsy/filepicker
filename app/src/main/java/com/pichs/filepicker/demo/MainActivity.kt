package com.pichs.filepicker.demo

import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.pichs.filepicker.demo.databinding.ActivityMainBinding
import com.pichs.filepicker.demo.tabs.AvatarCropFragment
import com.pichs.filepicker.demo.tabs.BasicSelectFragment
import com.pichs.filepicker.demo.tabs.DirectCameraFragment
import com.pichs.filepicker.demo.tabs.DirectVideoFragment
import com.pichs.filepicker.demo.tabs.EditInPreviewFragment
import com.pichs.filepicker.demo.tabs.EditOnClickFragment
import com.pichs.filepicker.demo.tabs.FileTypeFragment
import com.pichs.filepicker.demo.tabs.RulesFragment
import com.pichs.xbase.binding.BindingActivity
import com.pichs.xbase.kotlinext.fastClick

/**
 * Demo 首页：ViewPager2 + TabLayout，每种 API 能力一个 Tab（Fragment），互不干涉。
 *
 * | Tab | 内容 |
 * |---|---|
 * | 普通选择 | 类型 / 数量 / 大小 / 相机入口 / 预览可编辑 |
 * | 规则 | 分类型限量、互斥、GIF 占额度 |
 * | 文件 | 音频 / 文档 / PDF / APK / 压缩包 / 自定义后缀 |
 * | 头像裁剪 | 比例、可拖拽、锁比例、初始框大小 |
 * | 点击编辑 | 点图直接进编辑弹窗（单图） |
 * | 预览编辑 | 先选、在预览里点「编辑」 |
 * | 拍照直出 | 直接相机 → 编辑 / 仅拍照 / 头像裁剪 |
 * | 录像直出 | 直接录像 → 完成直接返回 |
 *
 * 每个 Tab 自己负责：选项 → 「开始」→ 结果网格，互不影响。
 */
class MainActivity : BindingActivity<ActivityMainBinding>() {

    private val tabTitles = listOf(
        "普通选择", "规则", "文件", "头像裁剪", "点击编辑", "预览编辑", "拍照直出", "录像直出",
    )

    private val fragmentFactories: List<() -> Fragment> = listOf(
        { BasicSelectFragment() },
        { RulesFragment() },
        { FileTypeFragment() },
        { AvatarCropFragment() },
        { EditOnClickFragment() },
        { EditInPreviewFragment() },
        { DirectCameraFragment() },
        { DirectVideoFragment() },
    )

    override fun afterOnCreate() {
        binding.ivSettings.fastClick {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = fragmentFactories.size
            override fun createFragment(position: Int): Fragment = fragmentFactories[position].invoke()
        }
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }
}
