package com.pichs.filepicker

import android.app.Activity
import android.content.Intent
import androidx.fragment.app.Fragment
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

        fun with(activity: Activity): Builder {
            return Builder(activity).apply {
                get().setBuilder(this)
            }
        }

        fun with(fragment: Fragment): Builder {
            return Builder(fragment).apply {
                get().setBuilder(this)
            }
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
        private var mActivity: Activity? = null
        private var mFragment: Fragment? = null

        constructor(activity: Activity) {
            this.mActivity = activity
        }

        constructor(fragment: Fragment) {
            this.mFragment = fragment
        }

        fun getActivity(): Activity? {
            return mActivity
        }

        fun getFragment(): Fragment? {
            return mFragment
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

        fun selectAll(): Builder {
            this.mSelectType = FilePickerFragment.SELECT_TYPE_ALL
            return this
        }

        fun selectImage(): Builder {
            this.mSelectType = FilePickerFragment.SELECT_TYPE_IMAGE
            return this
        }

        fun selectVideo(): Builder {
            this.mSelectType = FilePickerFragment.SELECT_TYPE_VIDEO
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

        fun build(): FilePicker {
            return FilePicker._instance
        }
    }

    fun start() {
        builder?.let { bd ->
            if (bd.getFragment() != null) {
                bd.getFragment()?.context?.let { ctx ->
                    val intent = Intent(ctx, FilePickerActivity::class.java)
                    intent.putExtra("maxSelectNumber", bd.mMaxSelectNumber)
                    intent.putExtra("selectType", bd.mSelectType)
                    intent.putExtra("maxFileSize", bd.mMaxFileSize)
                    intent.putExtra("minFileSize", bd.mMinFileSize)
                    intent.putParcelableArrayListExtra("selectedDataList", ArrayList(bd.mSelectedList))
                    bd.getFragment()?.startActivityForResult(intent, bd.mRequestCode)
                }
            } else {
                bd.getActivity()?.let { act ->
                    val intent = Intent(act, FilePickerActivity::class.java)
                    intent.putExtra("maxSelectNumber", bd.mMaxSelectNumber)
                    intent.putExtra("selectType", bd.mSelectType)
                    intent.putExtra("maxFileSize", bd.mMaxFileSize)
                    intent.putExtra("minFileSize", bd.mMinFileSize)
                    intent.putParcelableArrayListExtra("selectedDataList", ArrayList(bd.mSelectedList))
                    act.startActivityForResult(intent, bd.mRequestCode)
                }
            }
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == builder?.mRequestCode && resultCode == Activity.RESULT_OK) {
            val resultData = data?.getParcelableArrayListExtra<MediaEntity>("selectedDataList")?.toMutableList()
            if (resultData != null) {
                // Handle the result data
                // For example, you can pass it to a callback or update the UI
                builder?.mOnSelectCallback?.onCallback(resultData)
            }
        }
    }

}