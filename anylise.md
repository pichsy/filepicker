## FilePicker 库架构分析与优化建议

### 一、项目概览
FilePicker 是一个可配置的本地文件选择库，支持图片、视频、音频及常见文档、压缩包等类型，提供单选/多选、滑动批量选择、预览弹窗、底部已选列表等能力。核心模块集中于 `filepicker/` Android Library 模块，`app/` 为 Demo。

主要职责分层：
- 入口与路由：`FilePicker`（Builder API）、`CallbackFragment`、`FilePickerActivity`、`FilePickerFragment`
- 状态与数据：`FilePickerViewModel`（基于 StateFlow）、实体模型 `MediaEntity`/`MediaFolder`
- 查询与加载：`FileQueryHelper`（MediaStore 查询、快速回调）、旧版 `scanner/MediaScanner`（已标记过时）
- UI 与交互：RecyclerView（BRV 框架）、`FilePickerRecyclerView`（滑动选择）、预览弹窗与活动
- 媒体加载：`loader/MediaLoader`（基于 Glide 统一加载缩略图/封面）

### 二、核心架构与数据流
1) 使用方通过 `FilePicker.with(...)` 构建 `Builder`，设置 UI 与选择参数（类型、数量、大小、单击/滑动选择、初始已选列表等），并调用 `start()`。
2) `FilePicker` 内部创建/复用 `CallbackFragment` 以接收 `Activity` 结果，启动 `FilePickerActivity` 并通过 Intent 传参。
3) `FilePickerActivity` 注入 `FilePickerViewModel`，设置 `uiConfig` 与各参数后，加载 `FilePickerFragment`。
4) `FilePickerFragment` 启动生命周期后：
   - 初始化 Tab、网格/列表 RecyclerView、底部已选列表与监听
   - 订阅 `FilePickerViewModel` 的 StateFlow：`allFolderDataList`、`currentFolderDataList`、`originalCheckedFlow`
   - 调用 `viewModel.loadData(context)` 触发数据加载
5) `FilePickerViewModel.loadData`（IO 线程）调用 `FileQueryHelper.queryAlbums(...)`：
   - 构建查询条件（类型、大小、隐藏目录过滤等），按 `DATE_ADDED DESC` 读取 Cursor
   - 遍历 Cursor 生成 `MediaEntity` 并归入 `MediaFolder`
   - 达到 `fastNumber`（默认 40）时进行一次“快速回调”，先行更新 UI 的 “全部数据列表”
   - 完整遍历后更新最终数据并置 `isAllDataLoaded=true`
6) UI 层将 `currentFolderDataList` 绑定给 RecyclerView：
   - 网格模式：图片/视频/GIF，缩略图由 `MediaLoader`（Glide）加载
   - 列表模式：音频/文档等，使用图标 + 文本信息
7) 选择逻辑：
   - 单击/滑动选择，状态存于 ViewModel 的 `selectedData`/`tempSelectData`（`CopyOnWriteArrayList`）
   - 选中角标、底部已选列表与按钮状态随 StateFlow 变化而更新
8) 预览：
   - 弹窗 `FilePickerPreviewDialog`/`FilePickerFinalPreviewDialog` 支持预览、删除、拖拽排序、确认
9) 结果回调：
   - `callbackToChooser` 将最终选择结果写入 `FilePickerViewModel.finalSelectedDataList` 并 `setResult(RESULT_OK)` 返回给 `CallbackFragment`，回调业务方 `OnSelectCallback`

### 三、设计优点
- 架构清晰：入口 Builder API -> Activity/Fragment -> ViewModel（StateFlow）-> 查询/加载 -> UI/预览 -> 回调
- 查询能力强：`QueryWhere.Builder` 封装了 SQL 条件拼装，支持类型组合、MIME 过滤、大小范围、隐藏目录过滤等
- 首屏优化思路明确：`fastNumber` 快速回调提供“先可见”的首屏数据，提升感知
- UI 交互完善：Tab 切换、单选/多选、滑动批量选择、角标、底部已选列表、预览弹窗、拖拽排序
- 媒体加载统一：`MediaLoader` 对 GIF/IMAGE/VIDEO 缩略图/封面做了统一封装，默认降动画、使用占位图
- 配置灵活：`FilePickerUIConfig` 控制文案、显隐、图标资源与颜色等，便于主题化

### 四、设计问题与潜在风险
- 数据加载层：
  - 在 Cursor 循环内执行多次文件系统检查（`isFileExists`、`isFile`、`getFileSize` 等）与大量日志打印，造成 IO 压力与主线程被抢占（日志本身也会引发卡顿）
  - 一次性构建所有 `MediaFolder` 和全量 `MediaEntity`，缺少分页/增量机制，内存与排序成本高
  - 多处对列表进行 `sortedByDescending { addTime }`，在大数据量下耗时明显
- UI 渲染层：
  - `binding.recyclerView.models = list` 直接替换整表数据，虽配置了 `ItemDifferCallback`，但仍伴随较大范围的 `notifyItemRangeChanged`（如角标刷新处），导致主线程频繁测量/绘制
  - 选中状态的 UI 刷新多处使用整段刷新（全量 or 最后一行范围），缺少精细 payload 刷新
  - GIF 默认以动图加载（`.asGif()`），对首屏可能带来额外解码与内存压力
- 生命周期与刷新：
  - `onResume` 中再次 `loadData()`（尽管首帧跳过），在某些场景会造成不必要的 IO 冲击与 UI 抖动
- 细节缺陷：
  - `MediaFolder.equals/hashCode` 逻辑异常（同名/同路径时返回 false 的实现看起来不符合预期），可能影响集合行为与去重
  - 选中集合使用 `CopyOnWriteArrayList`，在频繁写入（滑动批量选择）场景不是最佳结构

### 五、首屏渲染卡顿原因分析（现状）
- 数据层：
  - Cursor 遍历中执行文件系统调用与大量日志，阻塞 IO 线程并延迟 `fastNumber` 回调；同时日志写入可能影响主线程（Logcat I/O）
  - 首次回调后 UI 立即绑定 40 个缩略图，Glide 同时发起多路解码与磁盘访问，造成 CPU/IO 争抢
- UI 层：
  - RecyclerView 首次填充进行大量 measure/layout，若同时触发多次 `notify`（角标、最后一行、底部列表）会加重主线程压力
  - GIF/大图缩略图即刻解码，未做采样/格式优化，GPU 上传开销高
- 排序与数据整理：
  - 多次 `sortedByDescending { addTime }` 在主线程触发（由 Flow 收到后立即处理并赋值）会卡 UI
- 生命周期：
  - `onResume` 的重复加载可能与首屏初始化重叠，增加不确定卡顿

### 六、优化方案
以下按“首屏数据加载优化”和“渲染速度优化”拆分，并提供实施优先级。

#### 1) 首屏数据加载优化
- 减少 Cursor 循环中的文件系统调用与日志：
  - 依赖 MediaStore 的 `SIZE` 字段，不再调用 `isFileExists/isFile/getFileSize`；必要的异常文件过滤通过 SQL 条件预先完成
  - 将隐藏目录过滤下推到 SQL（`DATA NOT LIKE '%/.%'` 或更严格的 path 段匹配），避免逐条路径判断
  - 降低日志级别，移除 while-循环中的高频日志；保留关键阶段的摘要日志
- 采用更强的“快速可见”策略：
  - 将 `fastNumber` 调整为 60~100（因设备而异），以保证首屏 2~3 屏内容可见，减少二次抖动
  - 快速回调仅构建“全部”聚合列表，不立即完成分相册构建；完整数据构建在后台继续
- 引入分页/增量：
  - 基于 Paging 3 封装一个 `MediaStorePagingSource`（可参考 app 模块的 Paging Demo），首屏仅加载第一页（例如 60 条）
  - 仅在进入“相册选择”弹窗或滑到底部时再加载更多
- 异步排序与缓存：
  - 在 IO 线程完成排序后再入流；对“全部”列表与当前相册列表各自缓存已排好序的数据，减少重复排序
- 内容变化监听替代主动 reload：
  - 通过 `ContentObserver` 监听 MediaStore 变化，做去抖（500~1000ms）后刷新，移除 `onResume` 的强制 `loadData()`

#### 2) 渲染速度优化
- RecyclerView 优化：
  - 启用 `setHasFixedSize(true)`，合理的 `setItemViewCacheSize`（如 30~60），为主列表与底部列表设置共享 `RecycledViewPool`
  - 使用异步 Diff（若 BRV 支持）或迁移到 `ListAdapter`，避免 `models = list` 带来的整表抖动；精确使用 payload 刷新选中角标
  - 移除/合并同一帧内的多次 `notify`；将“最后一行刷新”合并到 payload 里或用 `ItemDecoration` 处理底部间距
- Glide 加载策略：
  - 对缩略图统一 `override(160~200)`，`format(PREFER_RGB_565)`，`thumbnail(0.25f)`，必要时 `downsample(AtMost)`；控制并发（`GlideBuilder.setMaxRequestsPerHost` 类似）
  - GIF 首屏以静态首帧展示（`asBitmap()`）或延迟到点击预览时再动图加载
  - 对屏外项做预取：监听滚动方向，对即将出现的 1~2 行调用 `preload`
- 选择状态刷新策略：
  - 仅对变化的项调用 `notifyItemChanged(position, payload)`；维护 path->position 的索引（或使用 stableIds）来定位最小刷新集
  - 底部已选列表改为 Diff 刷新，避免整表重设 models
- 排序与过滤在后台：
  - `resetListDataWithSelectData` 产生的新列表排序应在 IO 线程完成后一次性提交到主线程

#### 3) 工程与可维护性优化
- 修复 `MediaFolder.equals/hashCode` 的逻辑错误，保证同名同路径的相等性语义
- 将 `CopyOnWriteArrayList` 替换为线程安全且写入友好的结构（如主线程访问场景直接用 `MutableList` + 单线程约束，或使用 `MutableStateFlow` 管理选中集合）
- 将 `onResume` 的二次加载改为内容观察与显式刷新入口（下拉刷新/按钮）
- 为“类型 Tab 切换”建立缓存（图片/视频/GIF 列表各自缓存），避免每次切换全表筛选与排序
- 建立性能日志开关（如 `BuildConfig.DEBUG && isPerformanceLogEnabled`），并统一到少量关键埋点

### 七、分阶段落地建议（优先级）
- 第一阶段（快速收益）：
  - 关闭/精简 Cursor 循环内日志；移除 per-row 文件系统检查；SQL 下推过滤
  - Glide 参数优化（缩略图尺寸/色彩格式/thumbnail）；首帧 GIF 静态化
  - 精细化角标刷新，去除整表 `notifyItemRangeChanged`
  - 禁止 `onResume` 自动 reload，避免叠加加载
- 第二阶段（结构升级）：
  - 引入 Paging 3，首页只加载第一页；顶部/底部列表共用 RecycledViewPool
  - 缓存不同类型筛选结果与排序结果；后台线程完成排序
  - 稳定 ID 与 path->position 索引，全面 payload 刷新
- 第三阶段（体验完善）：
  - 引入 MediaStore `ContentObserver` + 去抖刷新
  - 预取策略与滚动性能微调；预览弹窗首屏资源预载
  - 统一日志与性能指标，建立卡顿监控（如帧率、主线程耗时）

### 八、首屏卡顿的定位要点（供排查）
- 打开 Systrace/Perfetto，观察首次绑定列表时的主线程长任务（绘制/布局/图像解码）
- 打开 `StrictMode` 的 `detectLongRunningOperations`，检查主线程是否有意外阻塞
- 打开 Glide 日志/过载统计，查看同时在飞的请求数与解码队列
- 打点 `fastNumber` 首回调时延与“绑定列表 -> 首帧图片可见”的耗时

### 九、结语
该库整体结构清晰、可配置性强，功能覆盖完整。当前首屏卡顿主要来自数据加载阶段的过度校验与日志、UI 刷新范围偏大、以及图片解码同时触发。按本方案渐进优化后，可显著提升首屏可见时延（目标 < 300ms 列表出现，< 800ms 首屏缩略图稳定）、滑动流畅度（> 55fps），并降低功耗与内存峰值。



### 附录：选择角标刷新优化（详细方案）

#### 现状与问题
- 角标与选中遮罩依赖 `indexOfSelected(item)` 在 onBind 时实时计算，导致每次绑定都要遍历已选集合。
- 变更后多用整段 `notifyItemRangeChanged` 或较大范围刷新，造成主线程过度测量/绘制，首屏与滑动过程中易卡顿。
- 批量滑动选择在合入临时集合时，未计算最小受影响集，容易触发成片刷新。

#### 目标
- 将刷新粒度降至“最小受影响项”，选中/取消仅刷必要条目；取消选中时仅对“序号大于被删除项”的已选条目递减角标并局部刷新。
- onBind 降为 O(1) 复杂度，不再做 contains 与序号计算。

#### 数据结构与状态管理
- 选择集合：使用 `MutableSet<String>`（path 集合）进行 O(1) 选中判断。
- 角标映射：维护 `MutableMap<String, Int>` 的 `path -> selectedIndex`，用于 O(1) 获取角标。
- 位置映射：维护 `MutableMap<String, Int>` 的 `path -> adapterPosition`，在设置/变更 `currentFolderDataList` 后重建一次；Tab/相册切换时同步重建。
- UI 模型：
  - 方案 A：在现有 `MediaEntity` 上真实维护 `selectedIndex`（选中为 >=0，未选为 -1），并在 Diff 的 `areContentsTheSame` 中纳入该字段。
  - 方案 B：创建 UI 包装模型承载 `isSelected/selectedIndex`，避免污染实体；Adapter 绑定读包装字段。

#### 刷新策略与 payload 协议
- 定义 payload：`SELECTION_CHANGED`（选中态/遮罩变化）、`INDEX_CHANGED`（角标数字变化）、`MARGIN_CHANGED`（最后一行间距变化，可选）。
- 单项选中：
  - 更新 Set/Map + `selectedIndex`；定位 position 并 `notifyItemChanged(position, SELECTION_CHANGED)`。
- 单项取消（被删除序号 = K）：
  - 从 Set/Map 移除目标 path，并将“序号 > K 的已选项”的 `selectedIndex` 全部减一；对这些项的 position 批量 `notifyItemChanged(position, INDEX_CHANGED)`；对被取消项 `notifyItemChanged(position, SELECTION_CHANGED)`。
- 批量滑选（onTouchSelectEnd 合并提交）：
  - 计算新增集与删除集；对新增集逐项设置 index 并 `notifyItemChanged(position, SELECTION_CHANGED)`；对删除集同“单项取消”策略，并合并去重后批量刷新。
- 底部已选列表：使用 Diff（或 ListAdapter）基于 path 计算差异，避免整表 `models = list` 重设。
- 最后一行底部间距：用 `ItemDecoration` 根据“是否有选中”统一绘制额外 inset，移除“最后一行范围刷新”。

#### Adapter 与 Diff 配置
- 启用稳定 ID：`setHasStableIds(true)`，ID 可使用 path 的稳定哈希；BRV 不便时可迁移到 `ListAdapter`/`AsyncListDiffer`。
- Diff 关键字段：纳入 `selectedIndex` 与（可选）`isSelected`；`onPayload` 中仅根据 payload 更新角标文本/选中样式，不重绑图片。
- onBind 优化：只做 O(1) 的字段读取与视图赋值，严禁 contains 与 `indexOfSelected` 之类线性扫描。

#### ViewModel 侧维护点
- 在 `updateCurrentFolderDataList` 或数据切换后：重建 `path -> position` 映射。
- 选择/取消 API：
  - 写 Set/Map 与实体 `selectedIndex`（或包装模型），并返回需要刷新的 position 列表给 UI 层执行 payload 刷新。
  - 提供“计算取消序号 K 后受影响项”的辅助方法，返回 positions 以便批量 `notify`。

#### 生命周期与加载配合
- 移除 `onResume()` 的 `loadData()` 重复加载，避免角标状态被整表覆盖与再次测量。
- 列表数据更新时，先计算差异后提交（Diff），不要直接整表替换。

#### 预期收益
- 单选/取消：通常仅 1~数项 payload 刷新，摆脱整表 `notifyItemRangeChanged`。
- 批量滑选：刷新项等于新增/删除集合大小；滑动过程 UI 流畅，结束时一次性提交。
- onBind 降为 O(1)，滚动掉帧显著减少。

#### 建议落地步骤（不涉及具体代码，仅任务列表）
1) 在 ViewModel 中增加 `selectedPathSet`、`pathToIndexMap`、`pathToPositionMap`，以及相关维护方法。
2) 在列表数据设置点位（含 Tab/相册切换）重建 `pathToPositionMap`。
3) 重写选中/取消/批量合并 API，返回需局刷的 positions 与对应 payload 类型。
4) Adapter 启用稳定 ID 与 payload 分发；onBind 移除线性计算；onPayload 精准更新角标与遮罩。
5) 底部已选列表改用 Diff；最后一行间距交给 `ItemDecoration`。
6) 关闭 `onResume()` 的自动 reload，改为 ContentObserver + 去抖主动刷新。
