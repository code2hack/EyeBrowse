package com.code2hack.eyebrowse.projector.hostprobe

import android.app.Activity
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView

/** Only owned text/media; no browser, network, storage, microphone or background service. */
class FixtureActivity : Activity() {
    private lateinit var video: VideoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(16 + bars.left, 16 + bars.top, 16 + bars.right, 16 + bars.bottom)
                insets
            }
        }
        val status = TextView(this).apply {
            text = "PAV66 — owned fixture"
            setTextColor(Color.GREEN)
        }
        root.addView(status)
        root.addView(EditText(this).apply {
            id = R.id.owned_text
            hint = "Owned text only"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
            maxLines = 2
            setSelectAllOnFocus(false)
        })
        video = VideoView(this).apply {
            id = R.id.owned_video
            setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) status.text = "PLAYING owned clip"
                false
            }
            setOnCompletionListener { status.text = "Owned clip finished" }
            setOnErrorListener { _, what, extra ->
                status.text = "Owned clip error $what/$extra"
                true
            }
        }
        root.addView(Button(this).apply {
            id = R.id.play_owned_clip
            text = "Play owned flash/beep"
            setOnClickListener {
                video.setVideoURI(Uri.parse("android.resource://$packageName/${R.raw.pav66_flash_beep}"))
                video.start()
            }
        })
        root.addView(Button(this).apply {
            id = R.id.stop_owned_clip
            text = "Stop owned clip"
            setOnClickListener {
                video.stopPlayback()
                status.text = "Owned clip stopped"
            }
        })
        root.addView(video, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onStop() {
        video.stopPlayback()
        super.onStop()
    }
}
