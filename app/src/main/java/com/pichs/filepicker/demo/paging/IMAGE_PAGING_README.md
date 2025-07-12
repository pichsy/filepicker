# 图片分页加载演示 (无上限)

这是一个使用 Paging3 + RecyclerView 实现的无上限图片分页加载演示项目。

## 功能特性

- ✅ **无上限分页加载** - 可以无限滚动加载图片
- ✅ **网格布局** - 使用 2 列网格展示图片
- ✅ **下拉刷新** - 支持下拉刷新重新加载
- ✅ **无底部加载器** - 静默加载，不显示底部 loading
- ✅ **图片预加载** - 提前 5 个位置开始预加载
- ✅ **圆角卡片** - Material Design 风格的卡片布局
- ✅ **多样化图片源** - 使用多个图片 API 获得丰富内容
- ✅ **点击交互** - 支持图片点击事件
- ✅ **错误处理** - 优雅处理加载错误

## 项目结构

```
paging/
├── model/
│   └── ImageItem.kt               # 图片数据模型
├── data/
│   └── ImagePagingSource.kt       # 无上限分页数据源
├── repository/
│   └── ImageRepository.kt         # 图片数据仓库
├── viewmodel/
│   └── ImageViewModel.kt          # ViewModel
├── adapter/
│   └── ImagePagingAdapter.kt      # 图片网格适配器
└── ImagePagingDemoActivity.kt     # 主界面
```

## 核心特性说明

### 1. 无上限加载
- `nextKey = page + 1` - 总是返回下一页
- 没有总数限制，可以无限滚动
- 自动生成新的图片内容

### 2. 静默加载
- 不使用 `withLoadStateFooter()`
- 通过 `LoadState` 监听处理错误
- 使用 Toast 提示错误信息

### 3. 预加载优化
- `prefetchDistance = 5` - 提前 5 个位置开始加载
- 提升用户滚动体验
- 减少等待时间

### 4. 多样化图片源
```kotlin
// 使用多个图片 API 轮换
when (imageId % 4) {
    0 -> "https://picsum.photos/400/300?random=$imageId"
    1 -> "https://source.unsplash.com/400x300/?$category&sig=$imageId"
    2 -> "https://picsum.photos/400/300?random=${imageId + 1000}"
    else -> "https://source.unsplash.com/400x300/?nature&sig=${imageId + 2000}"
}
```

## 配置参数

```kotlin
PagingConfig(
    pageSize = 20,              // 每页 20 张图片
    enablePlaceholders = false, // 不使用占位符
    initialLoadSize = 20,       // 初始加载 20 张
    prefetchDistance = 5        // 提前 5 个位置预加载
)
```

## 使用方法

1. 在 MainActivity 中点击 "图片分页加载演示(无上限)" 按钮
2. 进入演示页面，自动加载第一页图片
3. 向下滚动自动触发无限分页加载
4. 下拉可以刷新重新开始
5. 点击图片可以触发点击事件

## 技术亮点

- **性能优化**: 使用 DiffUtil 优化列表更新
- **内存管理**: Glide 自动管理图片缓存
- **用户体验**: 圆角图片 + 卡片阴影效果
- **错误恢复**: 网络错误时显示占位图
- **响应式设计**: 适配不同屏幕尺寸

## 图片分类

自动生成的图片包含以下分类：
- 自然风景 (nature)
- 城市建筑 (city)  
- 美食 (food)
- 动物 (animals)
- 科技 (technology)
- 艺术 (art)
- 人物 (people)
- 旅行 (travel)

每张图片都有对应的标题、描述和标签信息。
