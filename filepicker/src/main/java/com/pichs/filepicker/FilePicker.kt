package com.pichs.filepicker

import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.pichs.filepicker.empty.CallbackFragment
import com.pichs.filepicker.entity.MediaEntity
import kotlin.collections.toMutableList

fun interface OnSelectCallback {
    fun onCallback(list: MutableList<MediaEntity>)
}

class FilePicker {

    private var builder: Builder? = null

    fun setBuilder(builder: Builder): FilePicker {
        this.builder = builder
        return this
    }

    companion object {

        const val DEFAULT_REQUEST_CODE = 11021

        private val _instance by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            FilePicker()
        }

        fun get(): FilePicker {
            return _instance
        }

        fun with(activity: FragmentActivity): Builder {
            return Builder(activity).apply {
                get().setBuilder(this)
            }
        }

        fun with(fragment: Fragment): Builder {
            return Builder(fragment).apply {
                get().setBuilder(this)
            }
        }

        fun ofImage(): String {
            return FilePickerFragment.SELECT_TYPE_IMAGE
        }

        fun ofVideo(): String {
            return FilePickerFragment.SELECT_TYPE_VIDEO
        }

        fun ofAll(): String {
            return FilePickerFragment.SELECT_TYPE_ALL
        }

        /**
         * 将 MediaEntity 列表转换为路径列表
         */
        fun convertToPathList(list: MutableList<MediaEntity>): MutableList<String> {
            return list.map { it ->
                it.path ?: ""
            }.toMutableList()
        }
    }


    class Builder {
        private var mActivity: FragmentActivity? = null
        private var mFragment: Fragment? = null

        var mUiConfig: FilePickerUIConfig = FilePickerUIConfig()
            private set

        constructor(activity: FragmentActivity) {
            this.mActivity = activity
        }

        constructor(fragment: Fragment) {
            this.mFragment = fragment
        }

        fun getActivity(): FragmentActivity? {
            return mActivity
        }

        fun getFragment(): Fragment? {
            return mFragment
        }

        fun setUiConfig(uiConfig: FilePickerUIConfig): Builder {
            this.mUiConfig = uiConfig
            return this
        }

        var mSelectedList: MutableList<MediaEntity> = mutableListOf()
            private set

        fun setSelectedList(selectedList: MutableList<MediaEntity>): Builder {
            this.mSelectedList = selectedList
            return this
        }

        fun setSelectedPathList(selectedList: List<String>): Builder {
            this.mSelectedList = selectedList.map { it ->
                MediaEntity.fromPath(it)
            }.toMutableList()
            return this
        }

        var mSelectType: String = FilePickerFragment.SELECT_TYPE_ALL
            private set

        fun setSelectType(selectType: String): Builder {
            this.mSelectType = selectType
            return this
        }

        var mMaxSelectNumber = 0
            private set

        fun setMaxSelectNumber(maxSelectNumber: Int): Builder {
            this.mMaxSelectNumber = maxSelectNumber
            return this
        }

        var mRequestCode: Int = DEFAULT_REQUEST_CODE
            private set

        fun setRequestCode(requestCode: Int): Builder {
            this.mRequestCode = requestCode
            return this
        }


        var mMaxFileSize = 0L
            private set

        fun setMaxFileSize(maxFileSize: Long): Builder {
            this.mMaxFileSize = maxFileSize
            return this
        }

        var mMinFileSize = 1L
            private set

        fun setMinFileSize(minFileSize: Long): Builder {
            this.mMinFileSize = minFileSize
            return this
        }

        var mOnSelectCallback: OnSelectCallback? = null
            private set

        fun setOnSelectCallback(onSelectCallback: OnSelectCallback): Builder {
            this.mOnSelectCallback = onSelectCallback
            return this
        }

        fun start() {
            FilePicker.get().start()
        }
    }

    private var existingFragment: CallbackFragment? = null

    fun start() {
        builder?.let { bd ->
            if (bd.getFragment() != null) {
                bd.getFragment()?.context?.let { ctx ->
                    val fm = bd.getFragment()!!.childFragmentManager
                    val tag = "CallbackFragment"
                    existingFragment = fm.findFragmentByTag(tag) as? CallbackFragment
                    if (existingFragment == null) {
                        existingFragment = CallbackFragment()
                    }
                    existingFragment?.apply {
                        onResult = { resultCode: Int, data: Intent? ->
                            if (resultCode == RESULT_OK) {
                                val resultData = data?.getParcelableArrayListExtra<MediaEntity>("selectedDataList")?.toMutableList()
                                if (resultData != null) {
                                    builder?.mOnSelectCallback?.onCallback(resultData)
                                }
                            }
                        }
                        fm.beginTransaction().add(this, tag).commitNowAllowingStateLoss()

                        val intent = Intent(ctx, FilePickerActivity::class.java)
                        intent.putExtra("maxSelectNumber", bd.mMaxSelectNumber)
                        intent.putExtra("selectType", bd.mSelectType)
                        intent.putExtra("maxFileSize", bd.mMaxFileSize)
                        intent.putExtra("minFileSize", bd.mMinFileSize)
                        intent.putExtra("uiConfig", bd.mUiConfig)
                        intent.putParcelableArrayListExtra("selectedDataList", ArrayList(bd.mSelectedList))
                        launch(intent)
                    }
//                    bd.getFragment()?.startActivityForResult(intent, bd.mRequestCode)
                }
            } else {
                bd.getActivity()?.let { act ->
                    val fm = act.supportFragmentManager
                    val tag = "CallbackFragment"
                    existingFragment = fm.findFragmentByTag(tag) as? CallbackFragment
                    if (existingFragment == null) {
                        existingFragment = CallbackFragment()
                    }
                    existingFragment?.apply {
                        onResult = { resultCode: Int, data: Intent? ->
                            if (resultCode == RESULT_OK) {
                                val resultData = data?.getParcelableArrayListExtra<MediaEntity>("selectedDataList")?.toMutableList()
                                if (resultData != null) {
                                    builder?.mOnSelectCallback?.onCallback(resultData)
                                }
                            }
                        }

                        fm.beginTransaction().add(this, tag).commitNowAllowingStateLoss()
                        val intent = Intent(act, FilePickerActivity::class.java)
                        intent.putExtra("maxSelectNumber", bd.mMaxSelectNumber)
                        intent.putExtra("selectType", bd.mSelectType)
                        intent.putExtra("maxFileSize", bd.mMaxFileSize)
                        intent.putExtra("minFileSize", bd.mMinFileSize)
                        intent.putExtra("uiConfig", bd.mUiConfig)
                        intent.putParcelableArrayListExtra("selectedDataList", ArrayList(bd.mSelectedList))
                        launch(intent)
                    }
                }
            }
        }
    }

//    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        if (requestCode == builder?.mRequestCode && resultCode == Activity.RESULT_OK) {
//            val resultData = data?.getParcelableArrayListExtra<MediaEntity>("selectedDataList")?.toMutableList()
//            if (resultData != null) {
//                // Handle the result data
//                // For example, you can pass it to a callback or update the UI
//                builder?.mOnSelectCallback?.onCallback(resultData)
//            }
//        }
//    }

}