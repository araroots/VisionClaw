package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.tr
import java.io.File

// Mirrors SharePhotoDialog.kt, but text-only -- there is no readily-available thumbnail for a
// video file the way there is a Bitmap for a captured photo.
@Composable
fun ShareVideoDialog(file: File, onDismiss: () -> Unit, onShare: (File) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = tr("Gravação salva", "Recording saved"))

                Button(onClick = { onShare(file) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Compartilhar", "Share"))
                }
            }
        }
    }
}
