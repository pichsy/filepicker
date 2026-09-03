# 图库选择器

### 最新版本 ![](https://img.shields.io/maven-metadata/v.svg?label=maven-central&metadataUrl=https%3A%2F%2Frepo1.maven.org%2Fmaven2%2Fcom%2Fgitee%2Fpichs%2Ffilepicker%2Fmaven-metadata.xml)

- 使用最新版的 filepicker 库，必须使用最新版的xwidget库。都是 5.8.1 以上确保。
- 用了很多，都没有丝滑的选择效果的开源库，滑动选择丝滑。闲来没事，随手写一个吧。
- 图库选择，文件选择
- 仿华为相册滑动选择手势逻辑，
- 微信选择库样式风格
- 支持多选，限制数量，不限制数量。
- 使用场景，多用于 视频剪辑选择库，相册管理选择库。那去玩吧。

### 好不好用，直接安装。

- 扫码：![filepicker](https://www.pgyer.com/app/qrcode/CdA0TDQB)
- 或者：点击下载 [app-debug.apk](app/release/app-debug.apk)

# 依赖库，都是常用库，强烈建议 项目使用。

- 下面的这个三方库，本maven仓库中的aar都过滤掉了。建议自己从下面引用，防止库冲突。

## 最新版本

1. **filepicker
   ** ![](https://img.shields.io/maven-metadata/v.svg?label=maven-central&metadataUrl=https%3A%2F%2Frepo1.maven.org%2Fmaven2%2Fcom%2Fgitee%2Fpichs%2Ffilepicker%2Fmaven-metadata.xml)
2. **[xwidget](https://github.com/pichsy/xwidget)
   **  ![](https://img.shields.io/maven-metadata/v.svg?label=maven-central&metadataUrl=https%3A%2F%2Frepo1.maven.org%2Fmaven2%2Fcom%2Fgitee%2Fpichs%2Fxwidget%2Fmaven-metadata.xml)
3. **BRV
   **  ![](https://camo.githubusercontent.com/a94b501a064dd623ccd416f3c8262e8309b5a5ac74373d41980797e9ba286522/68747470733a2f2f6a69747061636b2e696f2f762f6c69616e676a696e676b616e6a692f4252562e737667)
4. **BasePopup
   ** ![](https://camo.githubusercontent.com/a3d2af2f4eff2d27ff650b3ae97271ec783ebc4e12db2fd6213402d57929ed9b/68747470733a2f2f696d672e736869656c64732e696f2f6d6176656e2d63656e7472616c2f762f696f2e6769746875622e72617a657264702f42617365506f707570)
5. **androidx.media3** 这里建议使用***1.11.0***
   稳定版 ![](https://img.shields.io/maven-metadata/v.svg?label=google-maven&metadataUrl=https%3A%2F%2Fdl.google.com%2Fandroid%2Fmaven2%2Fandroidx%2Fmedia3%2Fmedia3-exoplayer%2Fmaven-metadata.xml)
6. **glide** ![](https://img.shields.io/badge/glide-4.16.0-brightgreen.svg)

```kotlin
dependencies {
    // 基础组件库 （必须）
    implementation("com.gitee.pichs:filepicker:7.0.0")

    // 基础组件库 （必须）
    implementation("com.gitee.pichs:xwidget:5.8.1")

    // glide 图片加载 （必须）
    implementation("com.github.bumptech.glide:glide:4.16.0")

    //基础库（必须）
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.annotation)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // brv （必须）
    implementation("com.github.liangjingkanji:BRV:1.6.1")
    // 弹窗 （必须）
    implementation("io.github.razerdp:BasePopup:3.2.1")
    // 视频播放库 （必须）采用exoplayer
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-ui:1.11.0")
}


使用libs.version.toml用户引入方式
dependencies {
    api(libs.filepicker)
    api(libs.xwidget)
    api(libs.glide)
    api(libs.brv)
    api(libs.basepopup)
    api(libs.media3.exoplayer)
    api(libs.media3.ui)
}

# libs.version.toml中写法
[versions]
xwidget = "5.8.1"
filepicker = "7.0.0"
brv = "1.6.1"
basepopup = "3.2.1"
glide = "4.16.0"
activityKtx = "1.9.0"
fragmentKtx = "1.6.2"
recyclerview = "1.4.0"
kotlinxCoroutinesAndroid = "1.7.3"
kotlinxCoroutinesCore = "1.7.3"
media3Exoplayer = "1.11.0"

[libraries]
androidx-media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3Exoplayer" }
androidx-media3-ui = { module = "androidx.media3:media3-ui", version.ref = "media3Exoplayer" }
androidx-activity-ktx = { group = "androidx.activity", name = "activity-ktx", version.ref = "activityKtx" }
androidx-fragment-ktx = { group = "androidx.fragment", name = "fragment-ktx", version.ref = "fragmentKtx" }
androidx-recyclerview = { group = "androidx.recyclerview", name = "recyclerview", version.ref = "recyclerview" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutinesAndroid" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "kotlinxCoroutinesCore" }
xwidget = { group = "com.gitee.pichs", name = "xwidget", version.ref = "xwidget" }
filepicker = { group = "com.gitee.pichs", name = "filepicker", version.ref = "filepicker" }
basepopup = { group = "io.github.razerdp", name = "BasePopup", version.ref = "basepopup" }
brv = { group = "com.github.liangjingkanji", name = "BRV", version.ref = "brv" }
glide = { group = "com.github.bumptech.glide", name = "glide", version.ref = "glide" }

```


# 动图效果

![img2.gif](pics/img2.gif)

# FilePicker 文件选择器 - 使用文档

`FilePicker` 是一个支持图片、视频、文件选择的轻量级文件选择器，支持自定义配置、选中列表管理、回调返回选中数据。

---

## 快速入口

* **支持 Activity 和 Fragment 调用**
* **支持多选、最大数量控制、文件大小限制**
* **支持返回原图选项**
* **支持 UI 文案与界面定制**
* **支持滑动选择**
* **支持单选立即返回**
* **支持预选列表**

---

## 基本用法

### 1. 在 Activity 中启动

```kotlin
FilePicker.with(this) // this: FragmentActivity
    .setSelectType(FilePickerSelectType.ofImage()) // 设置选择类型
    .setMaxSelectNumber(9) // 设置最大选择数量
    .setSlideChooseEnable(true) // 开启滑动选择
    .setOnSelectCallback(object : OnSelectCallback {
        override fun onSelectedCallback(isUseOriginal: Boolean, list: MutableList<MediaEntity>) {
            // 选择完成回调
            println("是否使用原图: $isUseOriginal")
            list.forEach {
                println("文件路径: ${it.path}")
            }
        }
        override fun onCancel() {
            // 取消选择
        }
    })
    .setUiConfig(
        FilePickerUIConfig(
            isHideSelectTab = false,
            allAlbumName = "全部",
            confirmBtnText = "发送",
            isShowOriginal = false,
            isPreviewPageIndexMode = true,
            isShowSelectedListDeleteIcon = true,
            folderNickNameMap = hashMapOf(
                "DCIM" to "相册"
            )
        )
    ).
    .start()
```

### 2. 在 Fragment 中启动

```kotlin
FilePicker.with(this) // this: Fragment
    .setSelectType(FilePickerSelectType.ofImage())
    .setMaxSelectNumber(5)
    .setMaxFileSize(50 * 1024 * 1024) // 50MB
    .setMinFileSize(1 * 1024) // 1KB
    .setOnSelectCallback { isUseOriginal, list ->
        // 选择完成回调
    }
    .setUiConfig(
        FilePickerUIConfig(
            isHideSelectTab = true,
            allAlbumName = "全部",
            confirmBtnText = "下一步",
            isShowOriginal = false,
            isPreviewPageIndexMode = true,
            isShowSelectedListDeleteIcon = true,
            folderNickNameMap = hashMapOf(
                "DICM" to "相机",
                "Download" to "下载"
            )
        )
    ).
    .start()
```

### 3. 选择文件（音频 / 文档 / 压缩包）

```kotlin
FilePicker.with(this)
    .setSelectType(FilePickerSelectType.ofAudio()) // 文件类型：ofAudio/ofDocument/ofPdf/ofApk/ofZipAll...
    .setMaxSelectNumber(1)
    .setSingleClickEnable(true) // 单击直接返回，文件选择场景常用
    .setOnSelectCallback { isUseOriginal, list ->
        // 返回 MediaEntity（含 path/name/mimeType/size）
    }
    .start()
```

### 4. 自定义后缀过滤

任意后缀组合，不需要为每种后缀单独加类型：

```kotlin
FilePicker.with(this)
    .setSelectType(FilePickerSelectType.ofExtensions("xz", "tar", "bak"))
    .start()
```

> 有专属类型入口的后缀（图片、音频、文档、核心压缩包等）优先用 `ofXxx()`，
> 两者在 UI 模式与预览上的差异见下方 [类型入口怎么选](#类型入口怎么选)。

详见下方 [自定义后缀过滤](#自定义后缀过滤)。

---

## API 文档

### FilePicker

| 方法名                         | 描述                        |
|-----------------------------|---------------------------|
| `with(activity)`            | 使用 `FragmentActivity` 初始化 |
| `with(fragment)`            | 使用 `Fragment` 初始化         |
| `convertToPathList(list)`   | 将 `MediaEntity` 列表转换为路径列表 |
| `convertToEntityList(list)` | 将路径列表转换为 `MediaEntity` 列表 |

> 7.0.0 起类型入口方法统一收敛到 `FilePickerSelectType`（语义更贴合）。
> `FilePicker.ofImage()` 等老方法已标注 `@Deprecated`，**仍可正常使用**（内部转发），
> IDE 会提示迁移，建议尽快替换为 `FilePickerSelectType.ofXxx()`。完整对照见
> [已废弃 API](#已废弃-api-deprecated)。

### 已废弃 API（@Deprecated）

以下方法自 7.0.0 起标注 `@Deprecated`（源码级兼容，**调用不报错、行为不变**，内部直接转发到新入口）。
新代码请一律使用右列写法：

| 废弃（不要再用）                  | 最新写法                                  |
|-----------------------------|---------------------------------------|
| ~~`FilePicker.ofAll()`~~       | `FilePickerSelectType.ofAll()`        |
| ~~`FilePicker.ofAllWithGif()`~~ | `FilePickerSelectType.ofAllWithGif()` |
| ~~`FilePicker.ofImage()`~~     | `FilePickerSelectType.ofImage()`      |
| ~~`FilePicker.ofVideo()`~~     | `FilePickerSelectType.ofVideo()`      |
| ~~`FilePicker.ofGif()`~~       | `FilePickerSelectType.ofGif()`        |
| ~~`FilePicker.ofAudio()`~~     | `FilePickerSelectType.ofAudio()`      |
| ~~`FilePicker.ofDocument()`~~  | `FilePickerSelectType.ofDocument()`   |
| ~~`FilePicker.ofPdf()`~~       | `FilePickerSelectType.ofPdf()`        |
| ~~`FilePicker.ofDoc()`~~       | `FilePickerSelectType.ofDoc()`        |
| ~~`FilePicker.ofPpt()`~~       | `FilePickerSelectType.ofPpt()`        |
| ~~`FilePicker.ofExcel()`~~     | `FilePickerSelectType.ofExcel()`      |
| ~~`FilePicker.ofTxt()`~~       | `FilePickerSelectType.ofTxt()`        |
| ~~`FilePicker.ofApk()`~~       | `FilePickerSelectType.ofApk()`        |
| ~~`FilePicker.ofZipAll()`~~    | `FilePickerSelectType.ofZipAll()`     |
| ~~`FilePicker.ofZip()`~~       | `FilePickerSelectType.ofZip()`        |
| ~~`FilePicker.ofRar()`~~       | `FilePickerSelectType.ofRar()`        |
| ~~`FilePicker.of7Z()`~~        | `FilePickerSelectType.of7Z()`         |

> 6.x 已删除的 `ofTar()` / `ofGz()` / `ofBz2()` / `ofIso()` / `ofBr()` / `ofLz4()` / `ofZstd()` / `ofXz()`
> 不在废弃之列（已移除），替代写法见下方 [7.0.0 旧 API 迁移对照表](#700-旧-api-迁移对照表)。

### FilePickerSelectType 类型入口

| 方法名                | 描述                        |
|--------------------|---------------------------|
| `ofAll()`          | 选择图片和视频                   |
| `ofAllWithGif()`   | 选择图片、视频和GIF               |
| `ofImage()`        | 只选择图片                     |
| `ofVideo()`        | 只选择视频                     |
| `ofGif()`          | 只选择GIF                    |
| `ofAudio()`        | 只选择音频                     |
| `ofDocument()`     | 选择所有文档类型                  |
| `ofPdf()`          | 只选择PDF                    |
| `ofDoc()`          | 只选择DOC和DOCX               |
| `ofPpt()`          | 只选择PPT和PPTX               |
| `ofExcel()`        | 只选择XLS和XLSX               |
| `ofTxt()`          | 只选择TXT                    |
| `ofApk()`          | 只选择APK                    |
| `ofZipAll()`       | 选择所有压缩包类型                 |
| `ofZip()`          | 只选择ZIP                    |
| `ofRar()`          | 只选择RAR                    |
| `of7Z()`           | 只选择7Z                     |
| `ofExtensions(...)` | 按任意后缀组合自定义过滤（见下节）         |

### 类型入口怎么选

**有专属类型入口的后缀，优先用类型入口；没有的才用 `ofExtensions()`。**
两者能力上都能查到文件，但语义与体验有差别：

| 场景 | 推荐入口 | 原因 |
|------|---------|------|
| 选图片/视频 | `ofImage()` / `ofVideo()` / `ofAll()` | 相册网格模式、大图预览、原图选项齐全 |
| 选音频/文档/APK | `ofAudio()` / `ofDocument()` / `ofApk()` | 专属查询条件与图标 |
| 选压缩包 | `ofZipAll()` / `ofZip()` / `ofRar()` / `of7Z()` | mime + 后缀双轨查询，图标专属 |
| 选其他任意后缀 | `ofExtensions("xz", "bak", ...)` | 无对应类型时的通用方案 |

`ofExtensions()` 与类型入口的差异（按后缀捞文件的语义带来的）：

- UI 呈现为文件列表模式（按时间排序 + 文件夹分组），不是相册网格模式
- 无大图预览手势（`isCanPreview()` 不放行 `ext:` 类型）
- 查询按文件名后缀匹配，**会包含隐藏目录（`.` 开头路径）下的文件**——若需排除，
  设置 `setMaxFileSize()`（走 `filePathNotContains("/.")` 过滤）或自行过滤结果

### 自定义后缀过滤

tar/gz/bz2/iso/br/lz4/zstd/xz 等其他压缩格式不再单独提供 API，统一用
`FilePickerSelectType.ofExtensions()` 按任意后缀组合：

```kotlin
// 按任意后缀过滤，直接作为 selectType 传入
FilePicker.with(this)
    .setSelectType(FilePickerSelectType.ofExtensions("xz", "tar", "bak"))
    .start()
```

- 后缀自动去掉前导点、转小写、去重，并过滤 SQL 通配符（`%` `_`）
- 任意后缀都能查（真机已验证：编造后缀 `.qqqxx7`、大写 `.ABCxyz`、双重点 `.tar.gz` 均命中），
  前提是文件位于 MediaStore 索引的公共存储；应用私有目录、`.nomedia` 目录内的文件查不到
- 图标识别不受影响：内置的 tar/gz/bz2/iso/br/lz4/zstd/xz 类型常量与图标映射仍然生效，
  老代码直接传 `FilePickerSelectType.TAR` 等字符串常量依旧可用
- 查询、识别由内部「压缩包登记处」统一驱动：`ZIP_ALL` 由登记处派生，
  单个类型（如 `ZIP`）只取登记处中自己那一行，互不影响

**7.0.0 旧 API 迁移对照表：**

| 6.x（已删除）                | 7.0.0 等价写法                                      |
|--------------------------|-------------------------------------------------|
| `ofTar()`                | `FilePickerSelectType.ofExtensions("tar")`      |
| `ofGz()`                 | `FilePickerSelectType.ofExtensions("gz", "tgz")` |
| `ofBz2()`                | `FilePickerSelectType.ofExtensions("bz2")`      |
| `ofIso()`                | `FilePickerSelectType.ofExtensions("iso")`      |
| `ofBr()`                 | `FilePickerSelectType.ofExtensions("br")`       |
| `ofLz4()`                | `FilePickerSelectType.ofExtensions("lz4")`      |
| `ofZstd()`               | `FilePickerSelectType.ofExtensions("zstd")`     |
| `ofXz()`                 | `FilePickerSelectType.ofExtensions("xz")`       |
| `ofTar()` + `ofIso()` 组合 | `FilePickerSelectType.ofExtensions("tar", "iso")` |

> 也可以不做任何改动：把 selectType 直接传 `FilePickerSelectType.TAR` 等字符串常量，查询、过滤、图标展示行为与旧版一致。

### Builder

| 方法名                             | 描述                                               |
|---------------------------------|--------------------------------------------------|
| `setSelectType(type)`           | 设置选择的文件类型                                        |
| `setMaxSelectNumber(num)`       | 设置最大选择数量                                         |
| `setMaxFileSize(size)`          | 设置最大文件大小（字节）                                     |
| `setMinFileSize(size)`          | 设置最小文件大小（字节）                                     |
| `setSlideChooseEnable(enable)`  | 是否启用滑动选择，默认 `true`                               |
| `setSingleClickEnable(enable)`  | 是否启用单选立即返回，默认 `false`。仅在 `maxSelectNumber` 为1时生效 |
| `setSelectedList(list)`         | 设置已选中的 `MediaEntity` 列表                          |
| `setSelectedPathList(list)`     | 设置已选中的文件路径列表                                     |
| `setUiConfig(config)`           | 设置自定义UI配置                                        |
| `setOnSelectCallback(callback)` | 设置选择结果回调                                         |
| `start()`                       | 启动选择器                                            |

### UI 自定义配置 (FilePickerUIConfig)

通过 `FilePicker.with(this).setUiConfig(uiConfig)` 进行设置。

```kotlin
val uiConfig = FilePickerUIConfig().apply {
    isHideSelectTab = false
    confirmBtnText = "完成"
    allAlbumName = "所有文件"
    // ... 更多配置
}
```

| 属性                                       | 类型                        | 描述                | 默认值                 |
|------------------------------------------|---------------------------|-------------------|---------------------|
| `isHideSelectTab`                        | `Boolean`                 | 是否隐藏顶部分类Tab       | `false`             |
| `confirmBtnText`                         | `String`                  | 确定按钮的文本           | `"确定"`              |
| `isPreviewPageIndexMode`                 | `Boolean`                 | 预览页面是否显示页码        | `true`              |
| `allAlbumName`                           | `String`                  | “全部”文件夹的显示名称      | `"全部"`              |
| `previewText`                            | `String`                  | 预览页面的标题           | `"预览"`              |
| `isShowBottomPreviewText`                | `Boolean`                 | 是否显示底部预览按钮        | `true`              |
| `previewSelectText`                      | `String`                  | 预览页面底部“选择”按钮文本    | `"选择"`              |
| `originalText`                           | `String`                  | “原图”选项的文本         | `"原图"`              |
| `isShowOriginal`                         | `Boolean`                 | 是否显示“原图”选项        | `true`              |
| `isOriginalChecked`                      | `Boolean`                 | “原图”选项是否默认选中      | `false`             |
| `isShowHomePageSelectedBottomListWidget` | `Boolean`                 | 是否显示主页底部已选列表      | `true`              |
| `isShowSelectedListDeleteIcon`           | `Boolean`                 | 是否显示已选列表项的删除按钮    | `false`             |
| `selectedListDeleteIconResId`            | `Int`                     | 已选列表项删除按钮的图标资源ID  | `0`                 |
| `selectedListDeleteIconBackgroundColor`  | `Int`                     | 已选列表项删除按钮的背景色     | `Color.TRANSPARENT` |
| `atLeastSelectOneToastContent`           | `String`                  | 未选择任何文件时的提示       | `"至少选择一个"`          |
| `selectMaxNumberOverToastContent`        | `String`                  | 超出最大选择数量时的提示      | `"已达到最大选择数量"`       |
| `folderNickNameMap`                      | `HashMap<String, String>` | 文件夹名称映射，用于自定义显示名称 | `emptyHashMap()`    |

---

</br>
## !!!温馨提示： 如果UI效果与你的需求差距较大，强烈建议下载源码，自己修改一下。fork代码，自己改。!!!
</br>

---

## 结束语

`FilePicker` 旨在提供简洁、灵活、可定制的文件选择功能，广泛适用于相册、文件管理、视频选择等场景。

如需深入定制或遇到问题，欢迎补充需求，我可以帮您生成对应的开发指导。

## 特别鸣谢

本项目在开发过程中参考和使用了以下优秀的开源项目，特此致谢：

- [xwidget](https://github.com/pichsy/xwidget) - 提供了超级方便的基础组件。
- [BRV](https://github.com/liangjingkanji/BRV) —— 便捷的 RecyclerView 适配器库
- [BasePopup](https://github.com/razerdp/BasePopup) —— 强大的弹窗库
- [Glide](https://github.com/bumptech/glide) —— 高效的图片加载库
- [androidx.media3](https://developer.android.com/jetpack/androidx/releases/media3) —— 官方视频播放组件

感谢你们的无私奉献，让开发变得更加高效和有趣！

## 升级日志

### 7.0.0

- **类型入口 API 收敛到 `FilePickerSelectType`**：`ofAll()` / `ofImage()` / `ofVideo()` / `ofZipAll()` 等
  全部类型方法现在挂在 `FilePickerSelectType` 上（语义更贴合）。`FilePicker.ofXxx()` 系列标注
  `@Deprecated` 但**保留且可用**（内部转发，行为不变），IDE 会提示迁移。
- **API 精简（破坏性变更）**：压缩包相关入口只保留核心四个 `ofZipAll()` / `ofZip()` / `ofRar()` / `of7Z()`，
  删除 `ofTar()` / `ofGz()` / `ofBz2()` / `ofIso()` / `ofBr()` / `ofLz4()` / `ofZstd()` / `ofXz()`。
- **新增自定义后缀过滤**：任意后缀组合不再需要为每种类型加 API，一行搞定：

  ```kotlin
  FilePicker.with(this)
      .setSelectType(FilePickerSelectType.ofExtensions("xz", "tar", "bak"))
      .start()
  ```

  - 后缀自动归一化：去前导点、转小写、去重、过滤 SQL 通配符（`%` `_`）
  - 过滤按文件名后缀匹配（不区分大小写），mime 轨不参与（认不出的后缀在 MediaStore 里多为 `application/octet-stream`）
  - `SelectTypeUtil.isValidType()` 已放行 `ext:` 前缀的自定义类型，UI 自动按"文件列表"模式呈现
- **兼容性说明**：`FilePickerSelectType.TAR` / `GZ` / `BZ2` / `ISO` / `BR` / `LZ4` / `ZSTD` / `XZ` 等类型常量
  全部保留，老代码直接传字符串常量依旧可用，行为不变；图标识别逻辑不受本次精简影响。
- **压缩包逻辑收敛到唯一登记处**：类型 → 后缀（`ARCHIVE_EXTS`）与类型 → mime（`ARCHIVE_MIMES`）
  两张登记表是唯一数据源，`ZIP_ALL`、MediaStore 查询条件、`MediaEntity` 类型识别（`isArchive()`/`isZip()` 等）
  全部由其派生。新增压缩格式只需在两张表里各加一行，查询/识别/图标自动跟上，
  不会再出现某处手抄漏掉新格式的问题。
- **新增接入建议**：有专属类型入口的后缀（图片/音频/文档/APK/核心压缩包）优先用 `ofXxx()` 入口，
  `ofExtensions()` 用于无对应类型的任意后缀。两者差异见「类型入口怎么选」一节。
- **修复：RAR 文件识别不到图标/查询不到**。
  不同设备的 MimeTypeMap 会把 `.rar` 映射成三种 mime 之一（`application/x-rar-compressed`、
  `application/vnd.rar`、`application/rar`），现在三种全部兼容；查询与图标识别同时增加文件名后缀兜底。
- **修复：MediaStore 通用类型（`application/octet-stream`）文件识别不到**。
  mime 为空或为 octet-stream 时，按文件名后缀兜底识别（rar/7z/tar/gz/bz2/iso/br/lz4/zstd/xz、
  图片/视频/音频/文档等全部受益），正确标注了 mime 的文件行为不变。
- **修复：`pptx` / `xlsx` / `docx` 图标映射错误**（原来落到错误图标），`rar` 使用专属图标，
  新增 zip/tar/gz/iso/rar/7z 等压缩格式独立图标；`isArchive()` 补齐 `bz2` 覆盖。
- **去除硬编码**：媒体类型判断中的 MIME 字符串全部收敛到 `FilePickerMimeType` 常量。

### 6.0.0

- **性能大幅优化**：图片数量很多的场景下，打开选择器的加载速度显著提升。
    - 媒体库扫描去掉逐行 `File.stat()` 与逐行日志，列索引一次性提取。
    - 选择状态由「遍历查找」改为「路径 → 下标」哈希表，拖拽/滑动多选在千张级别也保持 O(1)。
    - 首屏聚合、已选列表比对等重计算移出主线程，避免掉帧。
    - 再次进入选择器时，数据已加载则不再全量重扫。
- **修复**：设置最大选择数量后点击开始选择时偶发崩溃（`ConcurrentModificationException`）。
- **修复**：在部分华为等机型上启动报 `ClassNotFoundException: FilePickerUIConfig` 的问题（UI 配置改为纯基础类型 Bundle 传递，旧方式保持兼容）。
- **修复**：本地 GIF 不再写入磁盘缓存的问题，滑动加载更省流量/解码开销。
- 拖拽排序、滑动多选、单选/取消、预览等交互行为与 5.x 完全一致。

### 5.9.0

- 解决选择图片过多，intent超过1M的问题。随便选无上限
- 优化index数字过大展示不全的问题。目前支持到 99999 张
- 增加文件夹别名设置setUiConfig中folderNickNameMap里自行添加，默认不添加。也可做翻译文件夹用。仅文件夹哦。
- 其他的UI上的文字都在FilePickerUIConfig中有对应属性，请仔细查看文档。


### 5.8.0 stable版本

- 修复最后一行选中图片被遮挡的问题
- 优化放大图标，更好看了。

### 5.7.0

- 优化过多图片首屏展示慢的问题。

### 5.6.3

- 修复音频播放放到后台还在继续的问题。

### 5.6.2

- 适配xwidget的工具类，适配底部导航栏。xwidget库务必使用最新 5.6.2 版本。
- 为了让版本 跟[xwidget](https://github.com/pichsy/xwidget)关联，方便使用和记忆，版本号跟[xwidget](https://github.com/pichsy/xwidget)统一了。

### 4.6.1

- 剔除日志影响

### 4.6.0

- 新版UI 点击更丝滑

### 4.5.1

- 适配底部小横导航栏
- 优化UI选择样式

### 4.3.0 (预计)

- **新增**：支持更多文件类型选择，如文档（audio, pdf, doc, ppt, excel, txt）、APK、各类压缩包等。
- **新增**：`setSingleClickEnable(boolean)` API，支持单选模式下单击立即返回。
- **新增**：`setSlideChooseEnable(boolean)` API，可禁用滑动选择手势。
- **新增**：`FilePickerUIConfig` 中增加 `isHideSelectTab`、`allAlbumName`、`isShowBottomPreviewText` 等UI配置项。

### 4.2.0

- 修复maxFileSize默认值:目前改为Long.MAX_VALUE .

### 4.1.0

- 适配手机底部导航栏，为屏幕内虚拟三键的情况。
