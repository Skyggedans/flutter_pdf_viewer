package com.pycampers.flutterpdfviewer

import android.content.Context
import android.content.Intent
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener
import com.github.barteksc.pdfviewer.listener.OnTapListener
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.ui.PlayerView
import java.util.HashMap

class PlayerController(
val pdfId: String,
    val pageTranslator: PageTranslator,
    val context: Context,
    val videoPages: VideoPages,
    val autoPlay: Boolean,
    val pdfView: PDFView,
    val playerView: PlayerView,
    val closeButton: ImageButton
) : OnPageChangeListener, OnTapListener {
    var exoPlayer: ExoPlayer? = null
    var isPlaying = false
    var wasFakeJump = false
    var lastVideoPage: Int? = null
    var isVideoPage = false
    var currentVideo: HashMap<String, String>? = null

    val broadcaster: LocalBroadcastManager
        get() = LocalBroadcastManager.getInstance(context)

    init {
        //
        // setup gestures to hide/show the close button
        //

        class GestureListener : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent?): Boolean {
                if (playerView.isControllerVisible) {
                    playerView.hideController()
                    closeButton.visibility = View.GONE
                } else {
                    playerView.showController()
                    closeButton.visibility = View.VISIBLE
                }
                return super.onSingleTapUp(e)
            }
        }

        val gestureDetector = GestureDetector(context, GestureListener())
        playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        //
        // define close button behavior
        //

        closeButton.setOnClickListener {
            hidePlayer()
            stopPlayer()

            lastVideoPage?.let { page ->
                wasFakeJump = true
                pdfView.stopFling()
                pdfView.jumpTo(page)
            }
        }
    }

    fun hidePlayer() {
        pdfView.visibility = View.VISIBLE
        playerView.visibility = View.GONE
        closeButton.visibility = View.GONE
    }

    fun showPlayer() {
        pdfView.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        closeButton.visibility = View.VISIBLE
    }

    fun startPlayer(mediaSource: MediaSource) {
        exoPlayer = ExoPlayerFactory.newSimpleInstance(context)
        playerView.player = exoPlayer
        exoPlayer?.prepare(mediaSource)
        exoPlayer?.playWhenReady = true
        isPlaying = true
    }

    fun stopPlayer() {
        exoPlayer?.playWhenReady = false
        exoPlayer?.release()
        isPlaying = false
    }

    override fun onPageChanged(page: Int, pageCount: Int) {
        isVideoPage = false

        var actualPage = page
        pageTranslator?.let {
            actualPage = pageTranslator[page]!!
        }

        broadcaster.sendBroadcast(
            Intent(ANALYTICS_BROADCAST_ACTION).run {
                putExtra("name", "onPageChanged")
                putExtra("pdfId", pdfId)
                putExtra("pageIndex", actualPage)
            }
        )

        val video = videoPages[actualPage]
        if (video != null) {
            isVideoPage = true
            currentVideo = video
            stopPlayer()
            if (autoPlay && !wasFakeJump) {
                playVideo(video)
            }
        } else if (isPlaying) {
            hidePlayer()
            stopPlayer()
        }

        if (wasFakeJump) {
            wasFakeJump = false
        }
    }

    fun playVideo(video: HashMap<*, *>) {
        showPlayer()
        startPlayer(getMediaSourceForVideo(context, video))
        lastVideoPage = pdfView.currentPage
    }

    override fun onTap(e: MotionEvent?): Boolean {
        if (isVideoPage) {
            playVideo(currentVideo ?: return false)
            return true
        }
        return false
    }
}
