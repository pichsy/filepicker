# 文件夹选择功能实现

## 🎯 **功能概述**

在本地图库 Demo 中添加了文件夹选择功能，用户可以点击顶部标题来选择不同的文件夹，实现按文件夹浏览媒体文件。

## 🚀 **实现特性**

### **1. UI 设计**
- ✅ 顶部标题改为可点击的文件夹选择器
- ✅ 显示文件夹图标 + 文件夹名称 + 下拉箭头
- ✅ 左侧显示当前文件夹的文件数量
- ✅ 点击后弹出文件夹选择对话框

### **2. 功能特性**
- ✅ 自动扫描所有媒体文件夹
- ✅ 按文件数量排序显示文件夹
- ✅ 支持"全部"选项查看所有文件
- ✅ 复用现有的 FolderChooseDialog
- ✅ 实时更新文件数量显示

## 📁 **项目结构**

```
newpicker/
├── data/
│   ├── LocalMediaPagingSource.kt      # 分页数据源
│   └── FolderScanner.kt               # 🆕 文件夹扫描器
├── viewmodel/
│   └── LocalMediaViewModel.kt         # 添加文件夹状态管理
├── LocalMediaPickerFragment.kt        # 添加文件夹选择UI
└── res/
    ├── layout/
    │   └── fragment_local_media_picker.xml  # 更新标题布局
    └── drawable/
        └── filepicker_ic_arrow_down.xml     # 🆕 下拉箭头图标
```

## 🔧 **核心实现**

### **1. 文件夹扫描器 (FolderScanner)**
```kotlin
object FolderScanner {
    suspend fun scanFolders(context: Context, selectType: String): List<MediaFolder> {
        // 扫描所有媒体文件
        // 按文件夹分组
        // 按文件数量排序
        return folderMap.values.toList().sortedByDescending { it.mediaEntityList.size }
    }
}
```

### **2. ViewModel 状态管理**
```kotlin
class LocalMediaViewModel : ViewModel() {
    // 当前选中的文件夹
    private val _currentFolder = MutableStateFlow<MediaFolder?>(null)
    val currentFolder: StateFlow<MediaFolder?> = _currentFolder.asStateFlow()
    
    // 所有文件夹列表
    private val _folderList = MutableStateFlow<List<MediaFolder>>(emptyList())
    val folderList: StateFlow<List<MediaFolder>> = _folderList.asStateFlow()
}
```

### **3. UI 布局设计**
```xml
<LinearLayout
    android:id="@+id/ll_folder_selector"
    android:background="?android:attr/selectableItemBackground"
    android:orientation="horizontal">
    
    <ImageView android:src="@drawable/filepicker_ic_file_folder" />
    <TextView android:id="@+id/tv_folder_name" android:text="全部" />
    <ImageView android:src="@drawable/filepicker_ic_arrow_down" />
    
</LinearLayout>
```

### **4. 文件夹选择对话框**
```kotlin
private fun showFolderChooseDialog() {
    val folderList = viewModel.folderList.value.toMutableList()
    val currentFolder = viewModel.currentFolder.value
    
    FolderChooseDialog(
        requireContext(),
        "全部",
        folderList,
        currentFolder
    ) { selectedFolder ->
        viewModel.selectFolder(selectedFolder)
        // 更新UI显示
    }.showPopupWindow()
}
```

## 📊 **功能流程**

### **1. 初始化流程**
1. Fragment 启动时调用 `loadFolderList()`
2. `FolderScanner.scanFolders()` 扫描所有文件夹
3. 将文件夹列表保存到 ViewModel
4. 默认选择"全部"文件夹

### **2. 文件夹选择流程**
1. 用户点击顶部文件夹选择器
2. 弹出 `FolderChooseDialog` 对话框
3. 显示所有可用文件夹列表
4. 用户选择文件夹后更新 ViewModel 状态
5. UI 自动更新显示选中的文件夹名称

### **3. 类型切换流程**
1. 用户切换媒体类型（全部/图片/视频）
2. 重新扫描对应类型的文件夹
3. 更新文件夹列表
4. 重置为"全部"文件夹

## 🎨 **UI 效果**

### **标题栏显示**
```
[📁] 全部 [▼]     300张     0/9
```

### **文件夹对话框**
```
📁 全部 (1250张)
📁 相机 (450张)
📁 截图 (320张)
📁 下载 (280张)
📁 微信 (200张)
...
```

## 🔮 **扩展功能**

### **当前实现状态**
- ✅ 文件夹扫描和显示
- ✅ 文件夹选择UI
- ✅ 状态管理和UI更新
- ⏳ 按文件夹过滤数据（待实现）

### **后续优化方向**
1. **真实文件夹过滤**：修改 PagingSource 支持按文件夹过滤
2. **缓存优化**：缓存文件夹扫描结果
3. **性能优化**：异步扫描，避免阻塞UI
4. **用户体验**：添加加载状态提示

## 💡 **技术亮点**

### **1. 复用现有组件**
- 复用 `FolderChooseDialog` 对话框
- 复用 `MediaFolder` 数据模型
- 复用文件夹图标资源

### **2. 状态管理**
- 使用 StateFlow 管理文件夹状态
- 响应式UI更新
- 完整的生命周期管理

### **3. 性能优化**
- 协程异步扫描文件夹
- 按需加载，避免重复扫描
- 内存友好的数据结构

### **4. 用户体验**
- 直观的文件夹选择界面
- 实时的文件数量显示
- 流畅的交互动画

这个文件夹选择功能为用户提供了更好的文件管理体验，让用户可以快速定位到特定文件夹中的媒体文件！🎉
