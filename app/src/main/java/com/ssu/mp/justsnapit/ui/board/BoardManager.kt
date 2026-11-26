package com.ssu.mp.justsnapit.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssu.mp.justsnapit.data.Board
import com.ssu.mp.justsnapit.ui.theme.Charcoal
import com.ssu.mp.justsnapit.ui.theme.Lime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
// 보드 목록 조회, 전환, 생성, 이름 수정 및 삭제 화면 표시
internal fun BoardManager(
    boards: List<Board>,
    activeBoardId: String,
    onSelect: (Board) -> Unit,
    onCreate: suspend (String) -> Unit,
    onRename: suspend (Board, String) -> Unit,
    onDelete: suspend (Board) -> Unit,
    onClose: () -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    var editingBoard by remember { mutableStateOf<Board?>(null) }
    var deletingBoard by remember { mutableStateOf<Board?>(null) }
    var draftName by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 보드 관리 화면을 전체 배경 위에 겹치고 바깥 영역 클릭 시 닫기
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(enabled = !submitting) { onClose() }
            .safeDrawingPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        // 보드 목록과 관리 버튼을 담은 카드 컨테이너 배치
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 540.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF161616))
                .border(1.5.dp, Lime.copy(alpha = 0.6f), RoundedCornerShape(28.dp))
                .clickable(enabled = false) {}
                .padding(20.dp)
        ) {
            // 상단 제목과 새 보드 생성 버튼 가로 배치
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("보드 관리", color = Lime, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        draftName = ""
                        actionError = null
                        creating = true
                    }
                ) {
                    Text("+ 새 보드", color = Lime, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(12.dp))
            // 등록된 보드 목록을 세로로 스크롤 표시
            LazyColumn(
                Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(boards, key = { it.id }) { board ->
                    val selected = board.id == activeBoardId
                    // 개별 보드 선택 영역과 편집·삭제 버튼 가로 배치
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (selected) Color(0xFF232919) else Charcoal)
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) Lime else Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable(enabled = !submitting) { onSelect(board) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 보드 이름과 기본 보드 표시 세로 배치
                        Column(Modifier.weight(1f)) {
                            // 보드 이름과 기본 배지 가로 배치
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    board.name,
                                    color = if (selected) Lime else Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (board.isDefault) {
                                    Text(
                                        "기본",
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Lime)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (selected) {
                                Text("현재 보드", color = Color.LightGray, fontSize = 12.sp)
                            }
                        }
                        TextButton(
                            enabled = !submitting,
                            onClick = {
                                draftName = board.name
                                actionError = null
                                editingBoard = board
                            }
                        ) {
                            Text("편집", color = Color.White, fontSize = 13.sp)
                        }
                        if (!board.isDefault) {
                            TextButton(
                                enabled = !submitting,
                                onClick = {
                                    actionError = null
                                    deletingBoard = board
                                }
                            ) {
                                Text("삭제", color = Color(0xFFFF9B9B), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // 하단 닫기 버튼 우측 정렬 배치
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(enabled = !submitting, onClick = onClose) {
                    Text("닫기", color = Color.LightGray)
                }
            }
        }
    }

    if (creating) {
        BoardNameDialog(
            title = "새 보드 만들기",
            confirmLabel = "생성",
            name = draftName,
            onNameChange = { draftName = it; actionError = null },
            saving = submitting,
            error = actionError,
            onConfirm = {
                val input = draftName.trim()
                if (input.isEmpty()) {
                    actionError = "보드 이름을 입력해주세요."
                    return@BoardNameDialog
                }
                submitting = true
                actionError = null
                scope.launch {
                    try {
                        onCreate(input)
                        creating = false
                        onClose()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        actionError = e.message ?: "보드를 만들지 못했어요."
                    } finally {
                        submitting = false
                    }
                }
            },
            onDismiss = { if (!submitting) creating = false }
        )
    }

    editingBoard?.let { target ->
        BoardNameDialog(
            title = "보드 이름 편집",
            confirmLabel = "저장",
            name = draftName,
            onNameChange = { draftName = it; actionError = null },
            saving = submitting,
            error = actionError,
            onConfirm = {
                val input = draftName.trim()
                if (input.isEmpty()) {
                    actionError = "보드 이름을 입력해주세요."
                    return@BoardNameDialog
                }
                submitting = true
                actionError = null
                scope.launch {
                    try {
                        onRename(target, input)
                        editingBoard = null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        actionError = e.message ?: "보드 이름을 수정하지 못했어요."
                    } finally {
                        submitting = false
                    }
                }
            },
            onDismiss = { if (!submitting) editingBoard = null }
        )
    }

    deletingBoard?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!submitting) deletingBoard = null },
            title = { Text("보드 삭제") },
            text = {
                // 삭제 확인 안내와 오류 메시지 세로 배치
                Column {
                    Text("'${target.name}' 보드를 삭제할까요?")
                    actionError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        submitting = true
                        actionError = null
                        scope.launch {
                            try {
                                onDelete(target)
                                deletingBoard = null
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                actionError = e.message ?: "보드를 삭제하지 못했어요."
                            } finally {
                                submitting = false
                            }
                        }
                    }
                ) {
                    Text(if (submitting) "삭제 중…" else "삭제", color = Color(0xFFFF9B9B))
                }
            },
            dismissButton = {
                TextButton(enabled = !submitting, onClick = { deletingBoard = null }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
// 보드 이름 입력과 저장 확인을 처리하는 공통 대화상자 표시
private fun BoardNameDialog(
    title: String,
    confirmLabel: String,
    name: String,
    onNameChange: (String) -> Unit,
    saving: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // 보드 이름 입력란과 오류 안내 세로 배치
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    enabled = !saving,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("보드 이름 입력") }
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = onConfirm) {
                Text(if (saving) "저장 중…" else confirmLabel)
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
