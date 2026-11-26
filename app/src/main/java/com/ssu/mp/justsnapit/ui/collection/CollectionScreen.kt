package com.ssu.mp.justsnapit.ui.collection

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import com.ssu.mp.justsnapit.ui.theme.Lime
import com.ssu.mp.justsnapit.ui.theme.Charcoal
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ssu.mp.justsnapit.data.Board
import com.ssu.mp.justsnapit.data.Sticker
import com.ssu.mp.justsnapit.ui.components.RoundButton

@Composable
// 현재 보드 정보, 상단 관리 버튼 및 스티커 목록을 표시
internal fun CollectionScreen(
    board: Board?,
    stickers: List<Sticker>,
    grid: Boolean,
    loaded: Boolean,
    onManageBoards: () -> Unit,
    onAdd: () -> Unit,
    onToggleGrid: () -> Unit,
    onSelect: (Sticker) -> Unit,
    modifier: Modifier = Modifier
) {
    // 상단 메뉴와 보드 스티커 영역을 세로로 배치
    Column(modifier) {
        // 상단에 앱 이름·현재 보드 이름과 보드 관리·보기 전환·추가 버튼을 가로로 배치
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // 앱 이름과 현재 보드 이름을 세로로 표시하고 버튼을 제외한 가로 공간을 차지
            Column(Modifier.weight(1f).clickable { onManageBoards() }) {
                Text("Just", color = Lime, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("Snap It!", color = Lime, fontSize = 29.sp, fontWeight = FontWeight.Black)
                board?.let {
                    Text(
                        it.name,
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            RoundButton("▤", "보드 관리", onClick = onManageBoards)
            // 상단 원형 버튼 사이의 간격을 확보
            Spacer(Modifier.width(7.dp))
            RoundButton(if (grid) "◉" else "⊞", "보기 전환", onClick = { onToggleGrid() })
            // 보기 전환 버튼과 사진 추가 버튼 사이에 간격 추가
            Spacer(Modifier.width(7.dp))
            RoundButton("+", "사진 추가", onClick = { onAdd() })
        }
        // 상단과 하단 메뉴 사이의 남은 공간에 본문을 표시하고 빈 보드 안내를 겹쳐 표시
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                // 스티커를 한 줄에 세 개씩 배치하고 세로로 스크롤 지원
                grid -> LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(8.dp)) {
                    items(stickers, key = { it.id }) { item ->
                        Image(item.bitmap.asImageBitmap(), item.title, Modifier.padding(8.dp).aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp)).background(Charcoal).clickable { onSelect(item) }.padding(8.dp))
                    }
                }
                else -> key(board?.id) { StickerJar(stickers, onSelect) }
            }
            // 보드가 비어 있으면 안내 문구와 첫 스티커 추가 버튼을 중앙에 세로로 배치
            if (stickers.isEmpty() && loaded) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Your little collection", color = Lime, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                // 제목과 설명 사이에 세로 여백 추가
                Spacer(Modifier.height(10.dp))
                Text("좋아하는 순간을 쏙 담아보세요", color = Color.Gray, fontSize = 13.sp)
                TextButton(onClick = { onAdd() }) { Text("첫 스티커 만들기 +") }
            }
        }
    }
}
