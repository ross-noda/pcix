package com.example.pix.ui

import android.graphics.BitmapFactory
import android.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.pix.R
import com.example.pix.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TaskImages(images: List<TaskImage>, model: TasksViewModel) {
    var expanded by remember { mutableStateOf<TaskImage?>(null) }
    var deleting by remember { mutableStateOf<TaskImage?>(null) }
    images
        .sortedBy { it.createdAt }
        .forEach { item ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                ImagePreview(
                    item,
                    Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 280.dp).clickable {
                        expanded = item
                    },
                )
                TextButton(onClick = { deleting = item }) {
                    Text(stringResource(R.string.remove_image))
                }
            }
        }
    if (expanded != null)
        Dialog(
            onDismissRequest = { expanded = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize()) {
                Column {
                    IconButton(onClick = { expanded = null }) {
                        PixIcon(PixSymbol.CLOSE, stringResource(R.string.back))
                    }
                    ImagePreview(expanded!!, Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    deleting?.let { item ->
        ConfirmDialog(
            stringResource(R.string.remove_image),
            stringResource(R.string.remove_image_body),
            { deleting = null },
        ) {
            deleting = null
            model.removeImage(item)
        }
    }
}

@Composable
private fun ImagePreview(item: TaskImage, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by
        produceState<android.graphics.Bitmap?>(null, item.fileName) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                            val file = ImageStore(context).file(item.fileName)
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(file.path, bounds)
                            var sample = 1
                            while (
                                bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600
                            ) sample *= 2
                            val decoded =
                                BitmapFactory.decodeFile(
                                    file.path,
                                    BitmapFactory.Options().apply { inSampleSize = sample },
                                ) ?: return@runCatching null
                            val exif = ExifInterface(file.path)
                            val matrix = android.graphics.Matrix()
                            when (
                                exif.getAttributeInt(
                                    ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_NORMAL,
                                )
                            ) {
                                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                                ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
                                    matrix.postScale(-1f, 1f)
                                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                                ExifInterface.ORIENTATION_TRANSPOSE -> {
                                    matrix.postRotate(90f)
                                    matrix.postScale(-1f, 1f)
                                }
                                ExifInterface.ORIENTATION_TRANSVERSE -> {
                                    matrix.postRotate(270f)
                                    matrix.postScale(-1f, 1f)
                                }
                            }
                            if (matrix.isIdentity) decoded
                            else
                                android.graphics.Bitmap.createBitmap(
                                    decoded,
                                    0,
                                    0,
                                    decoded.width,
                                    decoded.height,
                                    matrix,
                                    true,
                                )
                        }
                        .getOrNull()
                }
        }
    if (bitmap != null)
        Image(
            bitmap!!.asImageBitmap(),
            stringResource(R.string.task_image),
            modifier,
            contentScale = ContentScale.Fit,
        )
    else
        Box(modifier) {
            Text(
                stringResource(R.string.image_loading),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
}
