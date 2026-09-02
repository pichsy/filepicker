package com.pichs.filepicker

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.Multimaps.index
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.entity.MediaFolder
import com.pichs.filepicker.query.FileQueryHelper
import com.pichs.filepicker.query.QueryType
import com.pichs.filepicker.utils.FilePickerFileUtils
import com.pichs.filepicker.utils.FilePickerLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.collections.mutableListOf

class FilePickerViewModel : ViewModel() {

    companion object {

        fun clearAll() {
            userUseSelectDataList.clear()
            finalSelectedDataList.clear()
        }

        /**
         * 用户使用的选择数据列表，临时一次性的。
         */
        val userUseSelectDataList = mutableListOf<MediaEntity>()

        val finalSelectedDataList = mutableListOf<MediaEntity>()
    }

    var uiConfig: FilePickerUIConfig = FilePickerUIConfig()

    val activityLifecycleChannel = Channel<Lifecycle.Event>()

    fun sendActivityLifecycleEvent(event: Lifecycle.Event) {
        viewModelScope.launch {
            activityLifecycleChannel.send(event)
        }
    }

    /**
     * 原图是否勾选，默认不勾选
     */
    val originalCheckedFlow = MutableStateFlow(false)

    val maxSelectNumber = MutableStateFlow(0)
    val singleClickEnable = MutableStateFlow(false)
    val slideChooseEnable = MutableStateFlow(true)
    val selectType = MutableStateFlow(FilePickerSelectType.IMAGE_VIDEO)
    val maxFileSize = MutableStateFlow(0L)
    val minFileSize = MutableStateFlow(0L)

    fun isCanShowBottomSelectRecyclerView(): Boolean {
        return uiConfig.isShowHomePageSelectedBottomListWidget && (selectType.value == FilePickerSelectType.IMAGE_VIDEO || selectType.value == FilePickerSelectType.IMAGE || selectType.value == FilePickerSelectType.VIDEO || selectType.value == FilePickerSelectType.IMAGE_VIDEO_GIF || selectType.value == FilePickerSelectType.GIF)
    }


    val isAllDataLoaded = MutableStateFlow(false)

    private val _allFolderDataList = MutableStateFlow<MutableList<MediaFolder>>(mutableListOf())
    val allFolderDataList = _allFolderDataList.asStateFlow()

    private val _currentFolderDataList = MutableStateFlow<MutableList<MediaEntity>>(mutableListOf())
    val currentFolderDataList = _currentFolderDataList.asStateFlow()

    private var _currentFolder = MutableStateFlow<MediaFolder?>(null)
    val currentFolder = _currentFolder.asStateFlow()

    fun isCanSingleClickSelect(): Boolean {
        return maxSelectNumber.value == 1 && singleClickEnable.value
    }

    /**
     * 是否 启用 滑动选择模式
     */
    fun isCanSlideChoose(): Boolean {
        return slideChooseEnable.value && !isCanSingleClickSelect()
    }

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
        _allFolderDataList.value = dataList.toMutableList()
    }

    fun getAllDataList(): MutableList<MediaFolder> {
        return _allFolderDataList.value
    }

    fun getAllDataEntityList(): MutableList<MediaEntity> {
        return _allFolderDataList.value.flatMap { it.mediaEntityList }.toMutableList()
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

    /**
     * path -> 在 selectedData 中的下标。与 selectedData 同生命周期，任何改动选中集合/顺序的地方都必须同步。
     * 只在主线程读写（与 selectedData 一致），用于把 indexOfSelected 从每次 onBind O(n) 降到 O(1)。
     */
    private val selectedIndexMap = HashMap<String, Int>()

    /**
     * path -> 在 tempSelectData 中的下标。综合角标 = selectedData.size + 该下标。
     * 单独维护是为了滑动连选时只需重建很小的 temp 部分，而不动已选部分。
     */
    private val tempIndexMap = HashMap<String, Int>()

    private var snapshotSelectedMap: HashMap<String, Int>? = null
    private var snapshotTempMap: HashMap<String, Int>? = null
    private var snapshotSelectedSize: Int = 0

    fun indexOfSelected(item: MediaEntity?): Int {
        if (item == null) return -1
        if (selectedData.isEmpty() && tempSelectData.isEmpty()) return -1
        val path = item.path
        if (path == null) {
            // 与原实现保持一致：path 为 null 时按 null 匹配，退回线性扫描
            return (selectedData + tempSelectData).indexOfFirst { it.path == null }
        }
        // selectedData 在前，与 (selectedData + tempSelectData).indexOfFirst 的“取最先出现”语义一致
        selectedIndexMap[path]?.let { return it }
        tempIndexMap[path]?.let { return selectedData.size + it }
        return -1
    }

    fun addSelectedData(mediaEntity: MediaEntity) {
        if (selectedData.contains(mediaEntity)) {
            return
        }
        selectedData.add(mediaEntity)
        mediaEntity.path?.let { path ->
            if (!selectedIndexMap.containsKey(path)) {
                selectedIndexMap[path] = selectedData.size - 1
            }
        }
    }

    fun addSelectedDataList(list: MutableList<MediaEntity>) {
        if (list.isEmpty()) return
        selectedData.addAll(list)
        rebuildSelectedIndexMap()
    }

    fun removeSelectedData(mediaEntity: MediaEntity) {
        if (selectedData.remove(mediaEntity)) {
            rebuildSelectedIndexMap()
        }
    }

    fun removeSelectedDataAll(list: List<MediaEntity>?) {
        if (list.isNullOrEmpty()) return
        if (selectedData.removeAll { it in list }) {
            rebuildSelectedIndexMap()
        }
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

    // ---------------- path -> 角标 索引维护 ----------------

    private fun rebuildSelectedIndexMap() {
        selectedIndexMap.clear()
        for (i in selectedData.indices) {
            val path = selectedData[i].path ?: continue
            if (!selectedIndexMap.containsKey(path)) {
                // 与 indexOfFirst 语义一致：重复 path 保留最先出现的下标
                selectedIndexMap[path] = i
            }
        }
    }

    private fun rebuildTempIndexMap() {
        tempIndexMap.clear()
        for (i in tempSelectData.indices) {
            val path = tempSelectData[i].path ?: continue
            if (!tempIndexMap.containsKey(path)) {
                tempIndexMap[path] = i
            }
        }
    }

    /**
     * 拖拽排序后同步索引：from/to 两个位置的角标互换。
     */
    fun onSelectedDataSwapped(from: Int, to: Int) {
        if (from == to) return
        if (from !in selectedData.indices || to !in selectedData.indices) return
        val fromPath = selectedData[from].path
        val toPath = selectedData[to].path
        if (fromPath == null || toPath == null) {
            // 有 null path 的实体无法进入以 path 为键的索引：全量重建兜底，
            // 保证非 null 参与方的角标与 swap 后的真实位置一致；
            // null path 项自身由 indexOfSelected 的线性退化路径保证正确
            rebuildSelectedIndexMap()
            return
        }
        val fromIndex = selectedIndexMap[fromPath]
        val toIndex = selectedIndexMap[toPath]
        if (fromIndex == null || toIndex == null) {
            rebuildSelectedIndexMap()
            return
        }
        selectedIndexMap[fromPath] = toIndex
        selectedIndexMap[toPath] = fromIndex
    }

    fun clearTempSelectData() {
        tempSelectData.clear()
        tempIndexMap.clear()
    }

    fun addAllTempSelectData(list: Collection<MediaEntity>) {
        if (list.isEmpty()) return
        tempSelectData.addAll(list)
        rebuildTempIndexMap()
    }

    fun removeAllTempSelectData(list: List<MediaEntity>) {
        if (list.isEmpty()) return
        if (tempSelectData.removeAll { it in list }) {
            rebuildTempIndexMap()
        }
    }

    /**
     * 把滑动选择的临时数据合并进已选列表。
     * 合并前后 combined(selectedData + tempSelectData) 的下标不变，所以角标显示不需要刷新。
     */
    fun mergeTempSelectDataIntoSelected() {
        if (tempSelectData.isEmpty()) return
        selectedData.addAll(tempSelectData)
        clearTempSelectData()
        rebuildSelectedIndexMap()
    }

    /**
     * 记录当前角标索引快照，配合 [getSelectionChangedPaths] 计算角标发生变化的条目，用于局部刷新。
     */
    fun takeSelectionSnapshot() {
        snapshotSelectedMap = HashMap(selectedIndexMap)
        snapshotTempMap = HashMap(tempIndexMap)
        snapshotSelectedSize = selectedData.size
    }

    /**
     * 与最近一次快照对比，返回角标（选中序号）发生变化的 path 集合。
     */
    fun getSelectionChangedPaths(): Set<String> {
        val oldSelected = snapshotSelectedMap ?: return emptySet()
        val oldTemp = snapshotTempMap ?: return emptySet()
        if (oldSelected.isEmpty() && oldTemp.isEmpty() && selectedIndexMap.isEmpty() && tempIndexMap.isEmpty()) {
            return emptySet()
        }
        val changed = HashSet<String>()
        val paths = HashSet<String>()
        paths.addAll(oldSelected.keys)
        paths.addAll(oldTemp.keys)
        paths.addAll(selectedIndexMap.keys)
        paths.addAll(tempIndexMap.keys)
        for (path in paths) {
            val oldIndex = oldSelected[path] ?: (oldTemp[path]?.let { snapshotSelectedSize + it } ?: -1)
            val newIndex = selectedIndexMap[path] ?: (tempIndexMap[path]?.let { selectedData.size + it } ?: -1)
            if (oldIndex != newIndex) {
                changed.add(path)
            }
        }
        return changed
    }

    /**
     * 当前所有带角标（已选中或临时选中）的 path 集合。
     */
    fun getBadgedPathSet(): Set<String> {
        if (selectedIndexMap.isEmpty() && tempIndexMap.isEmpty()) return emptySet()
        val paths = HashSet<String>(selectedIndexMap.size + tempIndexMap.size)
        paths.addAll(selectedIndexMap.keys)
        paths.addAll(tempIndexMap.keys)
        return paths
    }

    fun initUserSelectDataList(folders: List<MediaFolder>) {
        if (userUseSelectDataList.isEmpty()) {
            return
        }
        val allData = folders.flatMap { it.mediaEntityList }.toMutableList()
        userUseSelectDataList.forEach { item ->
            val entity = allData.find { item.path == it.path }
            if (entity != null) {
                // addSelectedData 内部有 contains 去重，并同步 selectedIndexMap
                addSelectedData(entity)
            }
        }
        if (isAllDataLoaded.value) {
            userUseSelectDataList.clear()
        }
    }


    fun filterAllData(folders: List<MediaFolder>): MutableList<MediaFolder> {
        // 将所有的 不合格的都剔除出去
        return folders.map { folder ->
            val filteredMediaList = folder.mediaEntityList.filter { it.size in minFileSize.value..maxFileSize.value }.toMutableList()
            MediaFolder(
                folderPath = folder.folderPath, name = folder.name, mediaEntityList = filteredMediaList
            )
        }.filter { it.mediaEntityList.isNotEmpty() }.toMutableList()
    }


    /**
     * 记录 并方便
     * 停止请求。
     */
    private var loadJob: Job? = null

    fun loadData(context: Context) {
        loadJob?.cancel()
        loadJob = null
        loadJob = viewModelScope.launch {
            isAllDataLoaded.value = false
            val queryType = getQueryType(selectType.value)
            val startTime = System.currentTimeMillis()
            val result = FileQueryHelper.queryAlbums(
                context = context,
                queryTypes = queryType,
                minSize = minFileSize.value,
                maxSize = maxFileSize.value,
                queryBuilder = { builder ->
                    builder.sizeGreaterThan(minFileSize.value)
                    if (!(maxFileSize.value <= 0L || maxFileSize.value == Long.MAX_VALUE)) {
                        // 如果没有设置最大文件大小，则不添加此条件
                        builder.and().sizeLessThanEqualTo(maxFileSize.value).and().filePathNotContains("/.")
                    }
                    if (queryType.size == 1 && queryType.contains(QueryType.NONE)) {
                        builder.and().leftBracket()
                        if (selectType.value == FilePickerSelectType.DOCUMENT) {
                            builder.fileNameEndWith(".doc").or().fileNameEndWith(".docx").or().fileNameEndWith(".pdf").or().fileNameEndWith(".ppt").or()
                                .fileNameEndWith(".pptx").or().fileNameEndWith(".xls").or().fileNameEndWith(".xlsx").or().fileNameEndWith(".txt")
                        } else if (selectType.value == FilePickerSelectType.ZIP_ALL) {
                            builder.fileNameEndWith(".zip").or().fileNameEndWith(".rar").or().fileNameEndWith(".7z").or().fileNameEndWith(".tar").or()
                                .fileNameEndWith(".gz").or().fileNameEndWith(".bz2").or().fileNameEndWith(".iso")
                        } else if (selectType.value == FilePickerSelectType.APK) {
                            builder.mimeTypeEquals(FilePickerMimeType.APK)
                        } else if (selectType.value == FilePickerSelectType.PDF) {
                            builder.mimeTypeEquals(FilePickerMimeType.PDF)
                        } else if (selectType.value == FilePickerSelectType.TXT) {
                            builder.mimeTypeEquals(FilePickerMimeType.TXT)
                        } else if (selectType.value == FilePickerSelectType.DOC) {
                            builder.fileNameEndWith(".doc").or().fileNameEndWith(".docx")
                        } else if (selectType.value == FilePickerSelectType.EXCEL) {
                            builder.fileNameEndWith(".xls").or().fileNameEndWith(".xlsx")
                        } else if (selectType.value == FilePickerSelectType.PPT) {
                            builder.fileNameEndWith(".ppt").or().fileNameEndWith(".pptx")
                        } else if (selectType.value == FilePickerSelectType.ZIP) {
                            builder.mimeTypeEquals(FilePickerMimeType.ZIP)
                        } else if (selectType.value == FilePickerSelectType.RAR) {
                            builder.mimeTypeEquals(FilePickerMimeType.RAR)
                                .or().mimeTypeEquals(FilePickerMimeType.RAR_VND)
                        } else if (selectType.value == FilePickerSelectType.SEVEN_Z) {
                            builder.mimeTypeEquals(FilePickerMimeType.SEVEN_Z)
                        } else if (selectType.value == FilePickerSelectType.TAR) {
                            builder.mimeTypeEquals(FilePickerMimeType.TAR)
                        } else if (selectType.value == FilePickerSelectType.GZ) {
                            builder.mimeTypeEquals(FilePickerMimeType.GZ)
                        } else if (selectType.value == FilePickerSelectType.BZ2) {
                            builder.mimeTypeEquals(FilePickerMimeType.BZ2)
                        } else if (selectType.value == FilePickerSelectType.ISO) {
                            builder.mimeTypeEquals(FilePickerMimeType.ISO)
                        } else {
                            builder.oneEqualsOne()
                        }
                        builder.rightBracket()
                    }
                },
                fastNumber = 40,
                onFastCallBack = { fastList ->
                    val fastLoadTime = System.currentTimeMillis() - startTime
                    FilePickerLog.d(
                        """FilePickerViewModel222
                        onFastCallBack=======
                        耗时：${fastLoadTime}
                        loadData: ${fastList.size} 个文件夹, 
                        ${fastList.sumOf { it.mediaEntityList.size }} 个文件
                        """.trimIndent()
                    )
                    val nameCountMap = mutableMapOf<String, Int>()

                    for (folder in fastList) {
                        val nickName = FilePickerFileUtils.getNickName(folder.name ?: "", uiConfig)
                        val count = nameCountMap.getOrDefault(nickName, 0)
                        if (count > 0) {
                            // 添加后缀，从 1 开始
                            folder.nickName = "$nickName $count"
                        } else {
                            folder.nickName = nickName
                        }
                        // 更新出现次数
                        nameCountMap[nickName] = count + 1
                    }

                    updateAllDataList(fastList)
                })

            FilePickerLog.d(
                """
                FilePickerViewModel222
                耗时 总共：${System.currentTimeMillis() - startTime}
                loadData: ${result.mediaFolders.size} 个文件夹, 
                ${result.mediaFolders.sumOf { it.mediaEntityList.size }} 个文件
            """.trimIndent()
            )
            isAllDataLoaded.value = true

            val nameCountMap = mutableMapOf<String, Int>()

            for (folder in result.mediaFolders) {
                val nickName = FilePickerFileUtils.getNickName(folder.name ?: "", uiConfig)
                val count = nameCountMap.getOrDefault(nickName, 0)
                if (count > 0) {
                    // 添加后缀，从 1 开始
                    folder.nickName = "$nickName $count"
                } else {
                    folder.nickName = nickName
                }
                // 更新出现次数
                nameCountMap[nickName] = count + 1
            }

            updateAllDataList(result.mediaFolders)
        }
    }


    private fun getQueryType(selectType: String): MutableSet<QueryType> {
        return when (selectType) {
            FilePickerSelectType.IMAGE_VIDEO -> mutableSetOf(QueryType.IMAGE, QueryType.VIDEO)
            FilePickerSelectType.IMAGE -> mutableSetOf(QueryType.IMAGE)
            FilePickerSelectType.VIDEO -> mutableSetOf(QueryType.VIDEO)
            FilePickerSelectType.IMAGE_VIDEO_GIF -> mutableSetOf(QueryType.IMAGE, QueryType.VIDEO, QueryType.GIF)
            FilePickerSelectType.GIF -> mutableSetOf(QueryType.GIF)
            FilePickerSelectType.AUDIO -> mutableSetOf(QueryType.AUDIO)
            else -> mutableSetOf(QueryType.NONE)
        }
    }

    fun onDestroy() {
        loadJob?.cancel()
        loadJob = null
    }


//    val refreshIndexChannel = Channel<Int>()

    /**
     * 更新当前选中数据的索引
     * 主要用于在选择数据后，更新所有文件夹中的数据的选中索引
     * 用户刷新数据。
     * 失败了，这样刷新效率并不高，触摸后刷新延迟更加严重。除非有更高级的算法。 效率不如 notifyItemChanged(position)
     */
    fun updateCurrentSelectIndex() {
        viewModelScope.launch {
            FilePickerLog.d(
                "FilePickerFragment8848",
                "updateCurrentSelectIndex: selectedData size = ${selectedData.size}, tempSelectData size = ${tempSelectData.size}"
            )
            val combineDataList = (selectedData + tempSelectData).distinctBy { it.path }
            FilePickerLog.d("FilePickerFragment8848", "combineDataList=${combineDataList.size}")

            val allDataList = _allFolderDataList.value.toMutableList()

            FilePickerLog.d("FilePickerFragment8848", "allDataList=${allDataList.size}")

            allDataList.forEach { folder ->
                folder.mediaEntityList.forEach { entity ->
                    val index = combineDataList.indexOfFirst { entity.path.equals(it.path, true) }
                    entity.selectedIndex = index
                }
            }

            FilePickerLog.d(
                "FilePickerFragment8848",
                "666666updateCurrentSelectIndex: allFolderDataList updated=${getAllDataEntityList().filter { it.selectedIndex != -1 }.size}"
            )

            _allFolderDataList.update { allDataList }
//        updateAllDataList(allDataList)
//            refreshIndexChannel.send(combineDataList.size)
        }
    }
}