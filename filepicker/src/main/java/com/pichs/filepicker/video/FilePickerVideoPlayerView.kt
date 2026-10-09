package com.pichs.filepicker.video

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.pichs.filepicker.databinding.FilePickerVideoPlayerBinding
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.loader.MediaLoader
import com.pichs.filepicker.utils.FilePickerClickHelper

@SuppressLint("ClickableViewAccessibility")
@UnstableApi
class FilePickerVideoPlayerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding: FilePickerVideoPlayerBinding = FilePickerVideoPlayerBinding.inflate(LayoutInflater.from(context), this, true)
    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    private var mediaEntity: MediaEntity? = null

    private var onSingleClick: (() -> Unit)? = null

    fun setOnSingleClickListener(listener: () -> Unit) {
        onSingleClick = listener
    }

    init {
        FilePickerClickHelper.clicks(binding.ivPlayBtn) {
            togglePlayPause()
        }

        binding.controller.exoPlay.setOnClickListener {
            togglePlayPause()
        }

        binding.controller.exoPlayPause.setOnClickListener {
            togglePlayPause()
        }
        updateProgress()
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                togglePlayPause()
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // 切换控制器的可见性
//                if (binding.controller.root.isVisible) {
//                    binding.controller.root.visibility = View.GONE
//                } else {
//                    binding.controller.root.visibility = View.VISIBLE
//                }
                onSingleClick?.invoke()
                return true
            }
        })

        binding.videoRoot.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            // 返回 true 表示事件已被处理
            true
        }

        binding.controller.exoProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.controller.exoPosition.text = formatTime(progress.toLong())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                handler.removeCallbacks(updateProgressRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (player == null) {
                    loadVideoAndPlay(mediaEntity, binding.controller.exoProgress.progress.toLong())
                    return
                }
                player?.seekTo(binding.controller.exoProgress.progress.toLong())
                handler.post(updateProgressRunnable)
            }

        })

//        binding.controller.exoProgress.addListener(object : TimeBar.OnScrubListener {
//
//            override fun onScrubStart(timeBar: TimeBar, position: Long) {
//                handler.removeCallbacks(updateProgressRunnable)
//            }
//
//            override fun onScrubMove(timeBar: TimeBar, position: Long) {
////                player?.seekTo(position)
//                binding.controller.exoPosition.text = formatTime(position)
//            }
//
//            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
//                if (player == null) {
//                    loadVideoAndPlay(mediaEntity, position)
//                    return
//                }
//                player?.seekTo(position)
//                handler.post(updateProgressRunnable)
//            }
//        })
    }

    /**
     * 设置视频封面
     * @param url 图片的URL
     */
    fun loadCover(entity: MediaEntity?, coverUrl: String? = null) {
        this.mediaEntity = entity
        resetProgress()
        if (mediaEntity?.path.isNullOrEmpty()) {
            binding.ivCover.visibility = View.GONE
            return
        }
        val uri = coverUrl ?: mediaEntity?.path ?: return
        // 在这里使用您项目中的图片加载库（如 Glide, Coil 等）
        if (coverUrl.isNullOrEmpty()) {
            // 传 entity：带缓存签名，且优先用 content uri 命中 MediaStore 视频缩略图分支拿真实帧
            MediaLoader.loadVideoCover(mediaEntity, binding.ivCover)
        } else {
            MediaLoader.loadVideoCover(uri, binding.ivCover)
        }
        binding.ivCover.visibility = View.VISIBLE

        // 播放前 onVideoSizeChanged 还没触发，先拿 MediaStore 里的宽高把封面撑起来。
        // 不撑的话封面会用缩略图的固有尺寸（loadVideoCover 固定 override(512,384)，是个 4:3 的扁盒子）
        // 居中显示，而视频面是全宽 —— 两者对不上，表现就是"视频铺满、封面缩在中间"。
        val videoWidth = mediaEntity?.width ?: 0
        val videoHeight = mediaEntity?.height ?: 0
        val rotation = mediaEntity?.orientation ?: 0
        if (videoWidth > 0 && videoHeight > 0) {
            applyAspectRatioToViews(displayAspectRatio(videoWidth, videoHeight, rotation))
        }
    }

    /**
     * 由"存储宽高 + 旋转角"算**显示**宽高比。
     *
     * 相机录出来的文件里 `width/height` 是传感器尺寸（例如 3840×2160，横的），
     * 真正的显示方向在 `orientation` / `unappliedRotationDegrees` 里（竖着录的通常是 90）。
     * 不把旋转算进去就会得到横屏比例，而封面缩略图是**已经旋转好的竖图** ——
     * 两者对不上，`fitCenter` 就会把封面按高度压小，表现正是"视频铺满宽度、封面缩在中间"。
     *
     * 实测数据（华为 BRE-AL80）：
     *   VID_xx.mp4  width=3840 height=2160 orientation=90  → 显示比例应为 0.5625（竖），
     *   按 width/height 直接算会得到 1.78（横），封面就被压到约 1/3 宽。
     */
    private fun displayAspectRatio(width: Int, height: Int, rotationDegrees: Int): Float {
        if (width <= 0 || height <= 0) return 0f
        return if (rotationDegrees == 90 || rotationDegrees == 270) {
            height.toFloat() / width
        } else {
            width.toFloat() / height
        }
    }

    /**
     * 让**视频面和封面用同一个盒子**：宽度撑满，高度按视频宽高比算。
     *
     * 原来只有 playerView 在 onVideoSizeChanged 里被撑开，iv_cover 一直是
     * wrap_content + fitCenter，它的高度取决于缩略图自己的固有尺寸，
     * 图像再在盒子里 fitCenter 一次，就变成"视频铺满宽度、封面缩在中间"。
     */
    private fun applyAspectRatioToViews(aspectRatio: Float) {
        if (aspectRatio <= 0f) return
        val apply = Runnable {
            val containerWidth = binding.videoRoot.width.takeIf { it > 0 } ?: return@Runnable
            val targetHeight = (containerWidth / aspectRatio).toInt()
            if (targetHeight <= 0) return@Runnable
            listOf(binding.playerView, binding.ivCover).forEach { view ->
                val params = view.layoutParams
                if (params.width != FrameLayout.LayoutParams.MATCH_PARENT || params.height != targetHeight) {
                    params.width = FrameLayout.LayoutParams.MATCH_PARENT
                    params.height = targetHeight
                    view.layoutParams = params
                }
            }
        }
        // loadCover 可能发生在布局之前，那时量不到宽度，等一帧再来
        if (binding.videoRoot.width > 0) apply.run() else binding.videoRoot.post(apply)
    }

    fun loadVideoAndPlay(entiity: MediaEntity?, startPosition: Long = -1) {
        this.mediaEntity = entiity
        if (mediaEntity?.uri == null && mediaEntity?.path.isNullOrEmpty()) {
            return
        }
        releasePlayer()
        var spotion = startPosition
        player = ExoPlayer.Builder(context).build().also { exoPlayer ->
            val uri = mediaEntity?.uri ?: mediaEntity?.path?.toUri() ?: return
            binding.playerView.player = exoPlayer
            exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
//            exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlayPauseButtons(isPlaying)
                    if (isPlaying) {
                        binding.ivCover.visibility = View.GONE
                        if (spotion > 0) {
                            exoPlayer.seekTo(spotion)
                            spotion = -1
                        }
                    }
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        // 除了视频面，封面也要一起撑成同一个盒子 —— 否则两者尺度不一致。
                        // 注意用 unappliedRotationDegrees 还原显示方向（见 displayAspectRatio）
                        applyAspectRatioToViews(
                            displayAspectRatio(
                                videoSize.width, videoSize.height, videoSize.unappliedRotationDegrees
                            )
                        )
                    }
                }
            })
            exoPlayer.prepare()
            exoPlayer.play()
        }

        handler.post(updateProgressRunnable)
    }

    @UnstableApi
    private fun updateProgress() {
        val duration = player?.duration ?: 0
        val position = player?.currentPosition ?: 0

        // 仅当获取到有效时长时才更新总时长显示
        if (duration > 0) {
            binding.controller.exoProgress.max = duration.toInt()
            binding.controller.exoDuration.text = formatTime(duration)
        }

        binding.controller.exoProgress.progress = position.toInt()
        binding.controller.exoPosition.text = formatTime(position)

        updatePlayPauseButtons(player?.isPlaying == true)
    }

    fun resetProgress() {
        binding.controller.exoProgress.max = mediaEntity?.duration?.toInt() ?: 0
        binding.controller.exoProgress.progress = 0
        binding.controller.exoPosition.text = formatTime(0L)
        binding.controller.exoDuration.text = formatTime(mediaEntity?.duration ?: 0L)
        updatePlayPauseButtons(false)
    }

    private fun updatePlayPauseButtons(isPlaying: Boolean) {
        if (isPlaying) {
            binding.ivPlayBtn.isVisible = false
            binding.controller.exoPlay.visibility = View.GONE
            binding.controller.exoPlayPause.visibility = View.VISIBLE
        } else {
            binding.ivPlayBtn.isVisible = true
            binding.controller.exoPlay.visibility = View.VISIBLE
            binding.controller.exoPlayPause.visibility = View.GONE
        }
    }

    private fun togglePlayPause() {
        if (player == null || player?.isReleased == true) {
            loadVideoAndPlay(mediaEntity)
            return
        }
        player?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                // 如果播放已结束，则从头开始
                if (it.playbackState == Player.STATE_ENDED) {
                    it.seekTo(0)
                }
                it.play()
            }
        }
    }

    fun releasePlayer() {
        player?.stop()
        handler.removeCallbacks(updateProgressRunnable)
        player?.release()
        player = null
    }

    @SuppressLint("DefaultLocale")
    private fun formatTime(ms: Long): String {
        if (ms < 0) return "00:00"
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}