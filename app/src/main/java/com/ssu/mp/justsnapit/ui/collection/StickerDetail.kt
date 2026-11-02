package com.ssu.mp.justsnapit.ui.collection

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import com.ssu.mp.justsnapit.ui.theme.Lime
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import com.ssu.mp.justsnapit.data.Sticker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
// 선택한 스티커의 이미지와 정보를 표시하고 닫기 및 삭제 동작을 전달합니다.
internal fun StickerDetail(sticker: Sticker, onClose: () -> Unit, onDelete: () -> Unit) {
    // 저장 시각을 기기의 언어 및 시간대 설정에 맞춰 표시합니다.
    val localCreatedAt = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }.format(Date(sticker.created))
    // 선택한 스티커의 상세 화면을 전체 화면에 겹치고 바깥 영역을 누르면 닫습니다.
    Box(Modifier.fillMaxSize().background(Color(0xBB202020)).clickable { onClose() }.safeDrawingPadding().padding(28.dp)) {
        // 확대 이미지, 이름, 생성 시각을 화면 중앙에 세로로 배치합니다.
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(sticker.bitmap.asImageBitmap(), sticker.title, Modifier.fillMaxWidth().heightIn(max = 460.dp), contentScale = ContentScale.Fit)
            // 스티커 이미지와 상세 정보 사이에 여백을 둡니다.
            Spacer(Modifier.height(24.dp))
            Text(sticker.title, color = Lime, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(localCreatedAt, color = Color.LightGray)
        }
        // 상세 화면 아래에 닫기와 삭제 버튼을 가로로 배치합니다.
        Row(Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            TextButton(onClick = onClose) { Text("닫기") }
            TextButton(onClick = onDelete) { Text("삭제", color = Color(0xFFFF9B9B)) }
        }
    }
}
