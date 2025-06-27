package com.pichs.filepicker

import android.util.Log
import androidx.lifecycle.ViewModel
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.entity.MediaFolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.CopyOnWriteArrayList

class FilePickerViewModel : ViewModel() {

    val maxSelectNumber = MutableStateFlow(0)
    val selectType = MutableStateFlow("all")
    val maxFileSize = MutableStateFlow(0L)
    val minFileSize = MutableStateFlow(1L)

    val userUseSelectDataList = MutableStateFlow<MutableList<MediaEntity>>(mutableListOf())

    private val allDataList = MutableStateFlow<MutableList<MediaFolder>>(mutableListOf())

    private val _currentFolderDataList = MutableStateFlow<MutableList<MediaEntity>>(mutableListOf())
    val currentFolderDataList = _currentFolderDataList

    private var _currentFolder = MutableStateFlow<MediaFolder?>(null)
    val currentFolder = _currentFolder

    fun isOverMaxSelectNumber(listSize: Int): Boolean {
        return maxSelectNumber.value > 0 && listSize >= maxSelectNumber.value
    }

    fun isGreaterThanMaxSelectNumber(listSize: Int): Boolean {
        return maxSelectNumber.value > 0 && listSize > maxSelectNumber.value
    }

    fun updateCurrentFolder(folder: MediaFolder?) {
        _currentFolder.value = folder
    }

    fun updateAllDataList(dataList: List<MediaFolder>) {
        allDataList.value = dataList.toMutableList()
    }

    fun getAllDataList(): MutableList<MediaFolder> {
        return allDataList.value
    }

    fun getAllDataEntityList(): MutableList<MediaEntity> {
        return allDataList.value.flatMap { it.mediaEntityList }.toMutableList()
    }

    fun updateCurrentFolderDataList(dataList: List<MediaEntity>) {
        _currentFolderDataList.update { dataList.toMutableList() }
    }

    fun getCurrentFolderDataList(): MutableList<MediaEntity> {
        return _currentFolderDataList.value
    }

    fun getCurrentFolderDataByPosition(position: Int): MediaEntity? {
        return _currentFolderDataList.value.getOrNull(position)
    }


    var selectedData = CopyOnWriteArrayList<MediaEntity>()
    var tempSelectData = CopyOnWriteArrayList<MediaEntity>()

    fun indexOfSelected(item: MediaEntity): Int {
        return (selectedData + tempSelectData).indexOfFirst { it.path == item.path }
    }

    fun addSelectedData(mediaEntity: MediaEntity) {
        if (selectedData.contains(mediaEntity)) {
            return
        }
        selectedData.add(mediaEntity)
    }

    fun addSelectedDataList(list: MutableList<MediaEntity>) {
        selectedData.addAll(list)
    }

    fun removeSelectedData(mediaEntity: MediaEntity) {
        selectedData.remove(mediaEntity)
    }

    fun removeSelectedDataAll(list: List<MediaEntity>?) {
        if (list.isNullOrEmpty()) return
        selectedData.removeAll { it in list }
    }

    fun getSelectedDataList(): MutableList<MediaEntity> {
        return selectedData
    }

    fun containsSelectedData(mediaEntity: MediaEntity): Boolean {
        return selectedData.contains(mediaEntity)
    }

    fun getSelectedDataByPosition(position: Int): MediaEntity? {
        return selectedData.getOrNull(position)
    }

    fun isSelected(mediaEntity: MediaEntity): Boolean {
        return selectedData.contains(mediaEntity)
    }

    fun getSelectedCount(): Int {
        return selectedData.size
    }

    fun initUserSelectDataList(folders: List<MediaFolder>) {
        if (userUseSelectDataList.value.isEmpty()) {
            return
        }
        val allData = folders.flatMap { it.mediaEntityList }.toMutableList()
        userUseSelectDataList.value.forEach { item ->
            val entity = allData.find { item.path == it.path }
            if (entity != null) {
                selectedData.add(entity)
            }
        }
        userUseSelectDataList.value.clear()
    }


    fun filterAllData(folders: List<MediaFolder>): MutableList<MediaFolder> {
        Log.d("FilePickerViewModel", "filterAllData: maxSize=${maxFileSize.value}, minSize=${minFileSize.value}, folders.size=${folders.size}")
        // 将所有的 不合格的都剔除出去
        return folders.map { folder ->
            val filteredMediaList = folder.mediaEntityList.filter { it.size in minFileSize.value..maxFileSize.value }.toMutableList()
            MediaFolder(
                folderPath = folder.folderPath,
                name = folder.name,
                coverImagePath = folder.coverImagePath,
                coverImageUri = folder.coverImageUri,
                mediaEntityList = filteredMediaList
            )
        }.filter { it.mediaEntityList.isNotEmpty() }.toMutableList()
    }

}