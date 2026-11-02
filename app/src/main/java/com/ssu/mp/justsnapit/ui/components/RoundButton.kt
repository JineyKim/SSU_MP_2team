package com.ssu.mp.justsnapit.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import com.ssu.mp.justsnapit.ui.theme.Lime
import com.ssu.mp.justsnapit.ui.theme.Charcoal

@Composable
// 아이콘과 클릭 동작을 지정할 수 있는 공통 원형 버튼을 표시합니다.
fun RoundButton(symbol: String, label: String, size: Int = 44, accent: Boolean = false, onClick: () -> Unit) {
    // 공통 원형 버튼의 중앙에 아이콘을 놓고 강조 여부에 따라 색상을 지정합니다.
    Box(Modifier.size(size.dp).clip(CircleShape).background(if (accent) Lime else Charcoal)
        .border(1.dp, Color.White.copy(alpha = .12f), CircleShape).clickable(onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        SnapIcon(symbol, if (accent) Color.Black else Color.White, (size * .5f).toInt())
    }
}
