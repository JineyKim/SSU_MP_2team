package com.ssu.mp.justsnapit.ui.collection

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import com.ssu.mp.justsnapit.ui.theme.Lime
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.ssu.mp.justsnapit.data.Sticker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
// 선택한 스티커의 이미지와 메모를 표시하고 메모 편집, 닫기 및 삭제 동작을 처리
internal fun StickerDetail(sticker: Sticker, onClose: () -> Unit, onDelete: () -> Unit, onSaveMemo: suspend (String) -> Unit) {
    var editingMemo by rememberSaveable(sticker.id) { mutableStateOf(false) }
    var draft by rememberSaveable(sticker.id) { mutableStateOf(sticker.memo) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // 저장 시각을 기기의 언어 및 시간대 설정에 맞춰 표시
    val localCreatedAt = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }.format(Date(sticker.created))
    // 선택한 스티커의 상세 화면을 전체 화면에 겹치고 바깥 영역을 누르면 닫기
    Box(Modifier.fillMaxSize().background(Color(0xBB202020)).clickable { onClose() }.safeDrawingPadding().padding(28.dp)) {
        // 제목, 생성 시각, 이미지, 메모 순서로 세로 배치
        Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(bottom = 56.dp)
            .verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(sticker.title, color = Lime, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(localCreatedAt, color = Color.LightGray)
            // 상세 정보와 스티커 이미지 사이에 여백 추가
            Spacer(Modifier.height(24.dp))
            Image(sticker.bitmap.asImageBitmap(), sticker.title, Modifier.fillMaxWidth().heightIn(max = 340.dp), contentScale = ContentScale.Fit)
            if (sticker.memo.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(sticker.memo, color = Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable {
                        draft = sticker.memo; saveError = null; editingMemo = true
                    }.padding(12.dp))
            }
        }
        // 상세 화면 아래에 닫기, 메모 편집과 삭제 버튼을 가로로 배치
        Row(Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            TextButton(onClick = onClose) { Text("닫기") }
            TextButton(onClick = { draft = sticker.memo; saveError = null; editingMemo = true }) { Text("메모") }
            TextButton(onClick = onDelete) { Text("삭제", color = Color(0xFFFF9B9B)) }
        }
    }
    // 선택한 스티커의 메모를 편집하고 저장 결과를 안내
    if (editingMemo) AlertDialog(
        onDismissRequest = { if (!saving) editingMemo = false },
        title = { Text("스티커 메모") },
        text = {
            // 입력란과 저장 오류 안내를 세로로 배치
            Column {
                OutlinedTextField(value = draft, onValueChange = { draft = it }, enabled = !saving,
                    modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 8,
                    placeholder = { Text("이 스티커에 대한 기억을 남겨보세요") })
                saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                val text = draft
                saving = true
                saveError = null
                scope.launch {
                    try {
                        onSaveMemo(text)
                        editingMemo = false
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        saveError = "메모를 저장하지 못했어요. 다시 시도해주세요."
                    } finally { saving = false }
                }
            }) { Text(if (saving) "저장 중…" else "저장") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = { editingMemo = false }) { Text("취소") } }
    )
}
