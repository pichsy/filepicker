# Paging3 + RecyclerView 分页加载演示

这是一个使用 Paging3 + RecyclerView 实现分页加载的完整演示项目。

## 功能特性

- ✅ 使用 Paging3 实现分页加载
- ✅ 支持下拉刷新
- ✅ 支持加载状态显示（加载中、错误、重试）
- ✅ 使用 RecyclerView 展示列表
- ✅ 模拟网络请求延迟
- ✅ 使用 Glide 加载图片
- ✅ Material Design 风格的 UI

## 项目结构

```
paging/
├── model/
│   └── User.kt                    # 用户数据模型
├── data/
│   └── UserPagingSource.kt        # 分页数据源
├── repository/
│   └── UserRepository.kt          # 数据仓库
├── viewmodel/
│   └── UserViewModel.kt           # ViewModel
├── adapter/
│   ├── UserPagingAdapter.kt       # 用户列表适配器
│   └── UserLoadStateAdapter.kt    # 加载状态适配器
└── PagingDemoActivity.kt          # 主界面
```

## 核心组件说明

### 1. UserPagingSource
- 继承自 `PagingSource<Int, User>`
- 实现分页数据加载逻辑
- 模拟网络请求，生成假数据
- 支持错误处理和重试

### 2. UserRepository
- 使用 `Pager` 配置分页参数
- 返回 `Flow<PagingData<User>>`

### 3. UserViewModel
- 使用 `cachedIn(viewModelScope)` 缓存分页数据
- 提供给 UI 层使用的数据流

### 4. UserPagingAdapter
- 继承自 `PagingDataAdapter`
- 使用 `DiffUtil` 优化列表更新
- 支持 ViewBinding

### 5. UserLoadStateAdapter
- 继承自 `LoadStateAdapter`
- 处理加载状态显示
- 支持错误重试

## 使用方法

1. 在 MainActivity 中点击 "Paging3 分页加载演示" 按钮
2. 进入演示页面，会自动加载第一页数据
3. 向下滚动触发分页加载
4. 下拉可以刷新数据
5. 如果加载失败，可以点击重试按钮

## 技术要点

- **分页配置**: 每页 20 条数据，总共 200 条数据
- **网络延迟**: 模拟 1 秒的网络请求延迟
- **图片加载**: 使用随机图片 API 展示头像
- **状态管理**: 完整的加载、错误、重试状态处理
- **UI 优化**: 使用 CardView 和 Material Design 组件

## 依赖库

```kotlin
// Paging3
implementation("androidx.paging:paging-runtime-ktx:3.3.2")

// SwipeRefreshLayout
implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

// Glide (图片加载)
implementation("com.github.bumptech.glide:glide:4.16.0")
```
