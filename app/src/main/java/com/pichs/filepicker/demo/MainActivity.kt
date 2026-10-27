package com.pichs.filepicker.demo

import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
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
 * 这里不用 TabLayoutMediator，改成「手动双向绑定」：
 * - 点 Tab → setCurrentItem(position, false)（关掉平滑滚动，避免快速点击时动画打架、选中项和页面错位）
 * - 滑动页面 → 选中对应 Tab
 * 两边都加判断，避免互相触发形成死循环。
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

        // 1. 先给 ViewPager2 设置适配器
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = fragmentFactories.size
            override fun createFragment(position: Int): Fragment = fragmentFactories[position].invoke()
        }

        // 2. 手动加 Tab（不依赖 TabLayoutMediator，避免它自动滚动/快速点击时的错位）
        binding.tabLayout.removeAllTabs()
        tabTitles.forEach { title ->
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(title))
        }

        // 3. Tab 点击 → 切 ViewPager2（false = 不要平滑动画，立即到位）
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (binding.viewPager.currentItem != tab.position) {
                    binding.viewPager.setCurrentItem(tab.position, false)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        // 4. 页面滑动 → 选中对应 Tab
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (binding.tabLayout.selectedTabPosition != position) {
                    binding.tabLayout.getTabAt(position)?.select()
                }
            }
        })

        // 5. 初始选中第一个
        binding.viewPager.setCurrentItem(0, false)
        binding.tabLayout.getTabAt(0)?.select()
    }
}
