package com.ssu.mp.justsnapit.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import com.ssu.mp.justsnapit.ui.theme.Lime
import android.graphics.Bitmap
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import com.ssu.mp.justsnapit.data.erase
import com.ssu.mp.justsnapit.ui.components.RoundButton

@Composable
// 스티커 이미지와 지우개, 원본 복구, 저장 도구를 표시합니다.
internal fun ColumnScope.StickerEditor(bitmap: Bitmap, onEdit: (Bitmap) -> Unit, onReset: () -> Unit, onCancel: () -> Unit, onSave: () -> Unit) {
    var eraser by remember { mutableStateOf(false) }
    var brush by remember { mutableFloatStateOf(30f) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val current by rememberUpdatedState(bitmap)
    // 편집 화면 상단에 취소, 제목, 저장 버튼을 좌우로 나누어 배치합니다.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onCancel) { Text("취소") }
        Text("Make it yours", color = Lime, fontWeight = FontWeight.Bold)
        TextButton(onClick = onSave) { Text("담기") }
    }
    // 상단 메뉴와 하단 도구 사이의 남은 공간 중앙에 편집할 이미지를 표시합니다.
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Image(bitmap.asImageBitmap(), "스티커 편집", Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)
            .onSizeChanged { area = it }.pointerInput(eraser, brush) {
                if (eraser) detectDragGestures { change, _ ->
                    change.consume()
                    if (area.width > 0) {
                        val ratio = current.width.toFloat() / area.width
                        onEdit(erase(current, change.position.x * ratio, change.position.y * ratio, brush * ratio))
                    }
                }
            }, contentScale = ContentScale.FillBounds)
    }
    if (eraser) {
        Text("지우개 크기", color = Color.Gray, fontSize = 12.sp)
        Slider(brush, { brush = it }, valueRange = 5f..60f)
    }
    // 원본 복구, 지우개, 저장 버튼을 하단에 같은 간격으로 배치합니다.
    Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        RoundButton("↶", "원본으로 되돌리기", 60, onClick = onReset)
        RoundButton("◇", "지우개", 60, eraser, onClick = { eraser = !eraser })
        RoundButton("✓", "스티커 저장", 72, true, onClick = onSave)
    }
}
