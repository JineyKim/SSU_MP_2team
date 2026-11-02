package com.ssu.mp.justsnapit.ui.camera

import com.ssu.mp.justsnapit.data.StickerStore
import com.ssu.mp.justsnapit.ui.components.RoundButton
import com.ssu.mp.justsnapit.ui.theme.Lime

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import java.io.File

@Composable
// 카메라 미리보기와 권한 요청, 촬영 및 전후면 전환을 처리합니다.
fun CameraScreen(onPhoto: (Bitmap) -> Unit, onGallery: () -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    val controller = remember { LifecycleCameraController(context) }
    var taking by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf(false) }
    DisposableEffect(permission, owner) {
        if (permission) runCatching { controller.bindToLifecycle(owner) }.onFailure { onError("카메라를 열 수 없어요. 갤러리에서 사진을 선택해주세요.") }
        onDispose { controller.unbind() }
    }
    // 둥근 카메라 영역 안에 미리보기와 촬영 버튼을 겹쳐 배치합니다.
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(30.dp)).background(Color(0xFF161616)).border(2.dp, Lime, RoundedCornerShape(30.dp))) {
        // 카메라 권한이 있으면 Android 미리보기 뷰를 Compose 화면에 표시합니다.
        if (permission) AndroidView(factory = { PreviewView(it).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            this.controller = controller
        } }, modifier = Modifier.fillMaxSize())
        // 권한이 없으면 안내 문구, 권한 요청, 갤러리 선택 버튼을 중앙에 세로로 배치합니다.
        else Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("일상의 순간을 담아보세요", color = Lime)
            TextButton(onClick = { request.launch(Manifest.permission.CAMERA) }) { Text("카메라 권한 허용") }
            TextButton(onClick = onGallery) { Text("갤러리에서 선택") }
        }
        // 미리보기 하단에 갤러리, 촬영, 전후면 전환 버튼을 가로로 배치합니다.
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            RoundButton("▧", "갤러리", 48, onClick = onGallery)
            RoundButton(if (taking) "…" else "●", "촬영", 76, true) {
                if (!permission) request.launch(Manifest.permission.CAMERA)
                else if (!taking) {
                    taking = true
                    val file = File.createTempFile("snap-", ".jpg", context.cacheDir)
                    try { controller.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                scope.launch {
                                    try { onPhoto(StickerStore(context).decode(Uri.fromFile(file))) }
                                    catch (e: Exception) { onError("촬영한 사진을 열지 못했어요.") }
                                    finally { file.delete(); taking = false }
                                }
                            }
                            override fun onError(exception: ImageCaptureException) {
                                file.delete(); taking = false; onError("촬영하지 못했어요. 다시 시도해주세요.")
                            }
                        })
                    } catch (e: Exception) { file.delete(); taking = false; onError("카메라가 준비되지 않았어요.") }
                }
            }
            RoundButton("↻", "카메라 전환", 48) {
                try {
                    val selector = if (front) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                    if (controller.hasCamera(selector)) { controller.cameraSelector = selector; front = !front }
                    else onError("전환할 카메라가 없어요.")
                } catch (e: Exception) { onError("카메라를 전환할 수 없어요.") }
            }
        }
    }
}
