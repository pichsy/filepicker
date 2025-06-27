# filepicker

- 图库选择，
- 仿华为相册滑动选择手势逻辑，
- 微信选择库样式风格
- 支持多选、单选
- 支持拍照、拍视频

# 依赖库，都是常用库，强烈建议 项目使用。
- 下面的这个三方库，本maven仓库中的aar都过滤掉了。建议自己从下面引用，防止库冲突。

```kotlin
dependencies {
    // 基础组件库 （必须）
    implementation("com.gitee.pichs:filepicker:1.0.0")
    
    // 基础组件库 （必须）
    implementation("com.gitee.pichs:xwidget:5.3.0")

    // glide 图片加载 （必须）
    implementation("com.github.bumptech.glide:glide:4.15.0")

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
    implementation("com.github.liangjingkanji:BRV:1.5.8")
    // 弹窗 （必须）
    implementation("io.github.razerdp:BasePopup:3.2.1")
    // 视频播放库 （必须）
    implementation("com.github.CarGuo.GSYVideoPlayer:gsyvideoplayer:v10.1.0")
}
```


# 先看效果

# 使用方式

# 自定义UI
- 请自行下载源码，修改UI。 api搞得：越复杂越难用，现在没有人有耐心去研究api了。