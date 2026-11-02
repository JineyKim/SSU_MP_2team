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

@Composable
// 카메라와 수집함 모드를 전환하는 선택형 버튼을 표시합니다.
internal fun ModeButton(symbol: String, label: String, active: Boolean, click: () -> Unit) {
    // 모드 전환 버튼의 둥근 터치 영역을 만들고 선택 상태에 따라 배경색을 바꿉니다.
    Box(Modifier.size(70.dp, 52.dp).clip(CircleShape).background(if (active) Lime else Color.Transparent)
        .clickable(onClick = click).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        SnapIcon(symbol, if (active) Color.Black else Lime, 29)
    }
}
