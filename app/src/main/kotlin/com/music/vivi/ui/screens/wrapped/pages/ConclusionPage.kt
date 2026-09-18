package com.music.vivi.ui.screens.wrapped.pages
import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.vivi.R
import com.music.vivi.ui.screens.wrapped.components.AnimatedBackground
import com.music.vivi.ui.screens.wrapped.components.ShapeType

@Composable
fun ConclusionPage(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground(elementCount = 30, shapeTypes = listOf(ShapeType.Circle, ShapeType.Line))
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.icon),
                contentDescription = stringResource(R.string.wrapped_logo_content_description),
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.wrapped_thank_you),
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wrapped_special_thanks),
                style = TextStyle(
                    fontSize = 16.sp,
                    color = Color.Gray
                )
            )
            Spacer(modifier = Modifier.height(48.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onClose,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                ) {
                    Text(
                        text = stringResource(R.string.wrapped_close),
                        style = TextStyle(
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bitmap)
                                view.draw(canvas)
                                
                                val uri = com.music.vivi.utils.ComposeToImage.saveBitmapAsFile(context, bitmap, "Awen_Wrapped")
                                
                                val shareIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    type = "image/png"
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Wrapped"))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = "Share",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Share",
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}
