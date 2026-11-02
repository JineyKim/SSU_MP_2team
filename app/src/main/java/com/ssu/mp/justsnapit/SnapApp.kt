package com.ssu.mp.justsnapit

import com.ssu.mp.justsnapit.data.Sticker
import com.ssu.mp.justsnapit.data.StickerStore
import com.ssu.mp.justsnapit.data.cutOut
import com.ssu.mp.justsnapit.data.outlined

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ssu.mp.justsnapit.ui.camera.CameraScreen
import com.ssu.mp.justsnapit.ui.collection.CollectionScreen
import com.ssu.mp.justsnapit.ui.collection.StickerDetail
import com.ssu.mp.justsnapit.ui.components.ModeButton
import com.ssu.mp.justsnapit.ui.editor.StickerEditor
import com.ssu.mp.justsnapit.ui.theme.Lime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
// 앱 상태를 관리하고 카메라, 수집함, 편집 및 상세 화면을 표시합니다.
fun SnapApp() {
    val context = LocalContext.current
    val store = remember { StickerStore(context) }
    val scope = rememberCoroutineScope()
    var stickers by remember { mutableStateOf(emptyList<Sticker>()) }
    var camera by rememberSaveable { mutableStateOf(false) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Sticker?>(null) }
    var editing by remember { mutableStateOf<Bitmap?>(null) }
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { store.load() }.onSuccess { stickers = it }.onFailure { error = "수집함을 읽지 못했어요." }
        loaded = true
    }
    // 사진의 배경을 제거하고 결과를 편집 화면에 전달합니다.
    fun process(bitmap: Bitmap) {
        scope.launch {
            busy = true
            try {
                val result = cutOut(bitmap)
                original = result; editing = result
            } catch (e: Exception) {
                error = "배경을 제거하지 못했어요. 처음 사용 시 모델 다운로드를 위해 인터넷 연결이 필요합니다. 잠시 후 다시 시도해주세요."
            } finally { busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try { process(store.decode(uri)) } catch (e: Exception) { busy = false; error = "사진을 열지 못했어요." }
        }
    }
    // 처리 중이 아닐 때 시스템 사진 선택 화면을 엽니다.
    fun pick() { if (!busy) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    BackHandler(selected != null || editing != null || camera || grid) {
        when { selected != null -> selected = null; editing != null -> { editing = null; original = null }
            grid -> grid = false; else -> camera = false }
    }
    // 전체 화면의 검정 배경과 연두색 테두리를 그리고, 상세 화면과 로딩 화면을 위에 겹칩니다.
    Box(Modifier.fillMaxSize().background(Color.Black).border(3.dp, Lime, RoundedCornerShape(48.dp))) {
        // 상단 메뉴, 본문, 하단 전환 버튼을 세로로 배치하고 상세 보기가 열리면 배경을 흐리게 합니다.
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).then(if (selected != null) Modifier.blur(20.dp) else Modifier)) {
            if (editing == null) {
                // 하단 전환 버튼 위의 남은 공간에 현재 선택한 화면을 표시합니다.
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (camera) {
                        CameraScreen(onPhoto = ::process, onGallery = ::pick, onError = { error = it })
                    } else {
                        CollectionScreen(
                            stickers = stickers,
                            grid = grid,
                            loaded = loaded,
                            onAdd = ::pick,
                            onToggleGrid = { grid = !grid },
                            onSelect = { selected = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                // 화면 아래 중앙에 카메라와 수집함 전환 버튼을 나란히 배치합니다.
                Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp, bottom = 14.dp)
                    .border(2.dp, Lime, CircleShape).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ModeButton("▣", "카메라", camera) { camera = true }
                    ModeButton("☺", "수집함", !camera) { camera = false }
                }
            } else {
                StickerEditor(requireNotNull(editing), onEdit = { editing = it }, onReset = { editing = original },
                    onCancel = { editing = null; original = null }, onSave = {
                        if (!busy) scope.launch {
                            busy = true
                            try {
                                val image = withContext(Dispatchers.Default) { outlined(requireNotNull(editing)) }
                                val sticker = store.add(image, stickers)
                                stickers = stickers + sticker; editing = null; original = null; camera = false; grid = false
                            } catch (e: Exception) { error = e.message ?: "저장하지 못했어요." }
                            finally { busy = false }
                        }
                    })
            }
        }
        selected?.let { item ->
            StickerDetail(
                sticker = item,
                onClose = { selected = null },
                onDelete = {
                    scope.launch {
                        try {
                            store.delete(item, stickers)
                            stickers = stickers.filterNot { it.id == item.id }
                            selected = null
                        } catch (e: Exception) {
                            error = "삭제하지 못했어요."
                        }
                    }
                }
            )
        }
        // 처리 중에는 어두운 덮개를 표시하고 아래 화면의 터치 입력을 막습니다.
        if (busy || !loaded) Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
            // 로딩 표시와 대기 안내 문구를 화면 중앙에 세로로 배치합니다.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Lime)
                // 로딩 표시와 안내 문구 사이의 간격입니다.
                Spacer(Modifier.height(16.dp))
                Text("잠시만 기다려주세요…", color = Lime)
            }
        }
        error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("안내") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("확인") } }) }
    }
}
