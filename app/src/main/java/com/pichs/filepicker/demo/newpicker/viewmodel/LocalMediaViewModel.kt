package com.pichs.filepicker.demo.newpicker.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.pichs.filepicker.FilePickerSelectType
import com.pichs.filepicker.demo.newpicker.repository.LocalMediaRepository
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.entity.MediaFolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 本地媒体 ViewModel
 */
class LocalMediaViewModel : ViewModel() {
    
    private val repository = LocalMediaRepository()
    
    // 当前选择的媒体类型
    private val _currentSelectType = MutableStateFlow(FilePickerSelectType.IMAGE_VIDEO)
    val currentSelectType: StateFlow<String> = _currentSelectType.asStateFlow()
    
    // 已选择的媒体列表
    private val _selectedMediaList = MutableStateFlow<List<MediaEntity>>(emptyList())
    val selectedMediaList: StateFlow<List<MediaEntity>> = _selectedMediaList.asStateFlow()
    
    // 最大选择数量
    private val _maxSelectCount = MutableStateFlow(9)
    val maxSelectCount: StateFlow<Int> = _maxSelectCount.asStateFlow()

    // 当前目录文件总数
    private val _totalFileCount = MutableStateFlow(0)
    val totalFileCount: StateFlow<Int> = _totalFileCount.asStateFlow()

    // 当前选中的文件夹
    private val _currentFolder = MutableStateFlow<MediaFolder?>(null)
    val currentFolder: StateFlow<MediaFolder?> = _currentFolder.asStateFlow()

    // 所有文件夹列表
    private val _folderList = MutableStateFlow<List<MediaFolder>>(emptyList())
    val folderList: StateFlow<List<MediaFolder>> = _folderList.asStateFlow()
    
    // 当前分页数据流
    private var _currentPagingData: Flow<PagingData<MediaEntity>>? = null
    
    /**
     * 获取本地媒体分页数据流
     */
    fun getLocalMediaPagingData(context: Context): Flow<PagingData<MediaEntity>> {
        if (_currentPagingData == null) {
            _currentPagingData = repository.getLocalMediaStream(
                selectType = _currentSelectType.value,
                context = context,
                onTotalCountChanged = { count ->
                    _totalFileCount.value = count
                }
            ).cachedIn(viewModelScope)
        }
        return _currentPagingData!!
    }

    /**
     * 切换媒体类型
     */
    fun switchSelectType(selectType: String, context: Context): Flow<PagingData<MediaEntity>> {
        _currentSelectType.value = selectType
        // 清除缓存，确保切换类型时重新加载
        repository.clearCache()
        // 重置文件数量
        _totalFileCount.value = 0
        _currentPagingData = repository.getLocalMediaStream(
            selectType = selectType,
            context = context,
            onTotalCountChanged = { count ->
                _totalFileCount.value = count
            }
        ).cachedIn(viewModelScope)
        return _currentPagingData!!
    }

    /**
     * 刷新数据
     */
    fun refreshData(context: Context): Flow<PagingData<MediaEntity>> {
        repository.clearCache()
        // 重置文件数量
        _totalFileCount.value = 0
        _currentPagingData = repository.getLocalMediaStream(
            selectType = _currentSelectType.value,
            context = context,
            onTotalCountChanged = { count ->
                _totalFileCount.value = count
            }
        ).cachedIn(viewModelScope)
        return _currentPagingData!!
    }
    
    /**
     * 添加选中的媒体
     */
    fun addSelectedMedia(mediaEntity: MediaEntity): Boolean {
        val currentList = _selectedMediaList.value.toMutableList()
        
        // 检查是否已经选中
        if (currentList.any { it.path == mediaEntity.path }) {
            return false
        }
        
        // 检查是否超过最大选择数量
        if (currentList.size >= _maxSelectCount.value) {
            return false
        }
        
        // 添加到选中列表
        currentList.add(mediaEntity.copy())
        _selectedMediaList.value = currentList
        return true
    }
    
    /**
     * 移除选中的媒体
     */
    fun removeSelectedMedia(mediaEntity: MediaEntity) {
        val currentList = _selectedMediaList.value.toMutableList()
        val index = currentList.indexOfFirst { it.path == mediaEntity.path }
        
        if (index != -1) {
            currentList.removeAt(index)
            _selectedMediaList.value = currentList
        }
    }
    
    /**
     * 检查媒体是否已选中
     */
    fun isMediaSelected(mediaEntity: MediaEntity): Boolean {
        return _selectedMediaList.value.any { it.path == mediaEntity.path }
    }
    
    /**
     * 获取媒体的选中序号
     */
    fun getMediaSelectedIndex(mediaEntity: MediaEntity): Int {
//        val selectedMedia = _selectedMediaList.value.find { it.path == mediaEntity.path }
//        return selectedMedia?.selectedCount ?: 0
        return _selectedMediaList.value.indexOfFirst { it.path == mediaEntity.path } + 1
    }
    
    /**
     * 清空选中列表
     */
    fun clearSelectedMedia() {
        _selectedMediaList.value = emptyList()
    }
    
    /**
     * 设置最大选择数量
     */
    fun setMaxSelectCount(count: Int) {
        _maxSelectCount.value = count
    }
    
    /**
     * 获取当前选中数量
     */
    fun getSelectedCount(): Int {
        return _selectedMediaList.value.size
    }
    
    /**
     * 是否可以继续选择
     */
    fun canSelectMore(): Boolean {
        return _selectedMediaList.value.size < _maxSelectCount.value
    }

    /**
     * 设置文件夹列表
     */
    fun setFolderList(folders: List<MediaFolder>) {
        _folderList.value = folders
    }

    /**
     * 选择文件夹
     */
    fun selectFolder(folder: MediaFolder?) {
        _currentFolder.value = folder
    }

    /**
     * 获取当前文件夹名称
     */
    fun getCurrentFolderName(): String {
        return _currentFolder.value?.name ?: "全部"
    }
}
