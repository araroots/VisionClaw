package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import android.net.Uri
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R

// Real AI-generated 360-degree turntable spin of the glasses (see res/raw/oculos_3d.mp4) --
// replaced the earlier fake-3D cross-fade trick (RotatingGlasses.kt), which looked flat/paper-like
// with only 3 source photos to work with.
@Composable
fun RotatingGlassesVideo(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            VideoView(context).apply {
                setVideoURI(Uri.parse("android.resource://${context.packageName}/${R.raw.oculos_3d}"))
                setOnPreparedListener { mediaPlayer ->
                    mediaPlayer.isLooping = true
                    mediaPlayer.setVolume(0f, 0f)
                }
                start()
            }
        },
    )
}
