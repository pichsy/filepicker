package com.pichs.filepicker.video

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
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
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.TimeBar
import com.pichs.filepicker.databinding.FilepickerVideoPlayerBinding
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.loader.MediaLoader
import com.pichs.filepicker.utils.FilePickerClickHelper
import com.pichs.filepicker.widget.OnItemSelectionChangedListener

@SuppressLint("ClickableViewAccessibility")
@UnstableApi
class VideoPlayerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding: FilepickerVideoPlayerBinding = FilepickerVideoPlayerBinding.inflate(LayoutInflater.from(context), this, true)
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
    fun loadCover(entity: MediaEntity?) {
        this.mediaEntity = entity
        resetProgress()
        if (mediaEntity?.path.isNullOrEmpty()) {
            binding.ivCover.visibility = View.GONE
            return
        }
        val uri = mediaEntity?.path ?: return
        Log.d("VideoPlayerView", "loadCover: uri=${mediaEntity?.uri}, path=${mediaEntity?.path}, uri=${uri}")
        // 在这里使用您项目中的图片加载库（如 Glide, Coil 等）
        MediaLoader.loadVideoCover(uri, binding.ivCover)
        binding.ivCover.visibility = View.VISIBLE
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
                    val videoWidth = videoSize.width
                    val videoHeight = videoSize.height
                    if (videoWidth > 0 && videoHeight > 0) {
                        val aspectRatio = videoWidth.toFloat() / videoHeight
                        val layoutParams = binding.playerView.layoutParams
                        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT
                        layoutParams.height = (binding.playerView.width / aspectRatio).toInt()
                        binding.playerView.layoutParams = layoutParams
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