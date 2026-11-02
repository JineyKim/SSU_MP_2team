package com.ssu.mp.justsnapit.ui.collection

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.ssu.mp.justsnapit.data.Sticker
import com.ssu.mp.justsnapit.physics.StickerPhysics
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
// 수집함에 스티커를 표시하고 낙하, 드래그 및 클릭 동작을 처리합니다.
internal fun StickerJar(stickers: List<Sticker>, onSelect: (Sticker) -> Unit) {
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val size = with(density) { 112.dp.toPx() }
    // 스티커를 좌표에 따라 자유롭게 배치하고 영역 크기를 낙하·드래그 경계 계산에 사용합니다.
    Box(Modifier.fillMaxSize().onSizeChanged { bounds = it }) {
        stickers.forEachIndexed { index, sticker ->
            key(sticker.id) {
                val body = remember { StickerPhysics() }
                LaunchedEffect(bounds) {
                    if (bounds.width == 0) return@LaunchedEffect
                    body.place(bounds.width.toFloat(), size, index)
                    var last = 0L
                    while (true) {
                        if (body.dragging || body.isResting(bounds.height.toFloat(), size)) {
                            last = 0L
                            delay(50.milliseconds)
                            continue
                        }
                        withFrameNanos { now ->
                            val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(.032f)
                            last = now
                            body.update(dt, bounds.width.toFloat(), bounds.height.toFloat(), size)
                        }
                    }
                }
                Image(sticker.bitmap.asImageBitmap(), sticker.title,
                    Modifier.size(112.dp).graphicsLayer { translationX = body.x; translationY = body.y; rotationZ = body.angle }
                        .pointerInput(sticker.id) { detectTapGestures(onTap = { onSelect(sticker) }) }
                        .pointerInput(sticker.id, bounds) {
                            detectDragGestures(onDragStart = { body.startDrag() },
                                onDragEnd = { body.endDrag() }, onDragCancel = { body.endDrag() }) { change, delta ->
                                change.consume()
                                body.dragBy(delta.x, delta.y, bounds.width.toFloat(), bounds.height.toFloat(), size)
                            }
                        }, contentScale = ContentScale.Fit)
            }
        }
    }
}
