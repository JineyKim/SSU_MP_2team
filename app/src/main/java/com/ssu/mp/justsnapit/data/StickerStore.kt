package com.ssu.mp.justsnapit.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.room.withTransaction
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.security.SecureRandom
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap

data class Sticker(val id: String, val bitmap: Bitmap, val created: Long, val title: String, val memo: String = "")

class StickerStore(private val context: Context, private val database: StickerDatabase = StickerDatabase.get(context)) {
    private val directory = File(context.filesDir, "stickers").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("collection", Context.MODE_PRIVATE)
    private val dao = database.stickers()
    private val filenamePattern = Regex("SnappedAt[0-9]{14}-UTC-[0-9a-f]{8}")
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT).withZone(ZoneOffset.UTC)
    private val random = SecureRandom()

    // 저장된 목록과 이미지 파일을 읽어 스티커 목록을 복원
    suspend fun load(): List<Sticker> = withContext(Dispatchers.IO) {
        importPreferences()
        buildList {
            for (item in dao.load()) {
                val id = item.id
                // SnappedAt + 14자리 시각 + -UTC- + 8자리 16진수 형식의 식별자만 불러오기
                if (!filenamePattern.matches(id)) continue
                // 저장 목록과 실제 파일명이 다르면 불러오기 생략, 폴더 내 파일 재검색 생략
                if (item.imageName != "$id.png") continue
                val file = File(directory, item.imageName)
                if (!file.isFile) continue
                val bitmap = BitmapFactory.decodeFile(file.path)
                if (bitmap != null) add(Sticker(id, bitmap,
                    item.createdAt, item.title, item.memo))
            }
        }
    }

    // 설정 파일의 스티커 정보와 가져오기 완료 상태를 하나의 트랜잭션으로 저장
    private suspend fun importPreferences() {
        database.withTransaction {
            if (dao.imported() != 0) return@withTransaction
            val list = JSONArray(prefs.getString("items", "[]"))
            val items = buildList {
                for (i in 0 until list.length()) {
                    val item = list.getJSONObject(i)
                    val id = item.getString("id")
                    if (!filenamePattern.matches(id)) continue
                    val created = item.getLong("created")
                    add(StickerEntity(id, "$id.png", created, created,
                        item.optString("title", "My snap"), item.optString("memo", "")))
                }
            }
            dao.importItems(items)
            dao.setState(StorageState("preferences_imported"))
        }
    }

    // 중복되지 않는 파일명으로 이미지와 정보를 저장하고 스티커를 반환
    suspend fun add(bitmap: Bitmap, current: List<Sticker>): Sticker = withContext(Dispatchers.IO) {
        importPreferences()
        val created = System.currentTimeMillis()
        val timestamp = timestampFormat.format(Instant.ofEpochMilli(created))
        var id: String
        var file: File
        // UTC(협정 세계시) 저장 시각과 8자리 난수로 이름을 만들고, 중복되면 난수를 다시 생성
        while (true) {
            currentCoroutineContext().ensureActive()
            val suffix = String.format(Locale.ROOT, "%08x", random.nextInt())
            id = "SnappedAt$timestamp-UTC-$suffix"
            file = File(directory, "$id.png")
            // 파일 생성과 존재 확인을 한 번에 수행하여 이미 있는 파일을 덮어쓰기 방지
            if (dao.count(id) == 0 && file.createNewFile()) break
        }
        val sticker = Sticker(id, bitmap, created, "Snap ${current.size + 1}")
        try {
            file.outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "이미지를 저장하지 못했어요." }
            }
            dao.insert(StickerEntity(id, file.name, created, created, sticker.title))
            sticker
        } catch (error: Exception) {
            // 이번 저장에 실패한 경우 새로 만든 파일만 정리
            file.delete()
            throw error
        }
    }

    // 대상 스티커를 저장 목록에서 제외하고 이미지 파일을 삭제
    suspend fun delete(sticker: Sticker) = withContext(Dispatchers.IO) {
        importPreferences()
        dao.markDeleted(sticker.id, System.currentTimeMillis())
        File(directory, "${sticker.id}.png").delete()
        Unit
    }

    // 선택한 스티커의 메모를 저장하고 갱신된 목록을 반환
    suspend fun updateMemo(id: String, memo: String, current: List<Sticker>): List<Sticker> = withContext(Dispatchers.IO) {
        importPreferences()
        check(dao.updateMemo(id, memo, System.currentTimeMillis()) == 1) { "스티커를 찾을 수 없어요." }
        val updated = current.map { if (it.id == id) it.copy(memo = memo) else it }
        updated
    }

    // 사진을 읽고 크기를 제한하여 비트맵으로 반환
    suspend fun decode(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val ratio = min(1f, 1400f / max(info.size.width, info.size.height))
                decoder.setTargetSize(max(1, (info.size.width * ratio).toInt()), max(1, (info.size.height * ratio).toInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            val options = BitmapFactory.Options()
            while (max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 1600) options.inSampleSize *= 2
            context.contentResolver.openInputStream(uri).use { requireNotNull(BitmapFactory.decodeStream(it, null, options)) }
        }
    }
}

// 사진에서 피사체를 분리하여 배경이 제거된 이미지를 반환
suspend fun cutOut(bitmap: Bitmap): Bitmap {
    val client = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
    return client.use { client ->
        requireNotNull(client.process(InputImage.fromBitmap(bitmap, 0)).await().foregroundBitmap)
    }
}

// 투명 여백을 자르고 이미지 크기를 조정한 뒤 흰색 테두리를 추가
fun outlined(source: Bitmap): Bitmap {
    val pixels = IntArray(source.width * source.height)
    source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    var left = source.width; var top = source.height; var right = -1; var bottom = -1
    pixels.forEachIndexed { i, pixel ->
        if ((pixel ushr 24) > 30) {
            val x = i % source.width; val y = i / source.width
            left = min(left, x); right = max(right, x); top = min(top, y); bottom = max(bottom, y)
        }
    }
    require(right >= left) { "남아 있는 피사체가 없어요." }
    val crop = Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1)
    val ratio = min(1f, 700f / max(crop.width, crop.height))
    val scaled =
        crop.scale(max(1, (crop.width * ratio).toInt()), max(1, (crop.height * ratio).toInt()))
    val border = 14
    val result = createBitmap(scaled.width + border * 2, scaled.height + border * 2)
    val canvas = Canvas(result)
    val mask = scaled.extractAlpha()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    for (degree in 0 until 360 step 10) {
        val angle = Math.toRadians(degree.toDouble())
        canvas.drawBitmap(mask, border + (kotlin.math.cos(angle) * border).toFloat(), border + (kotlin.math.sin(angle) * border).toFloat(), paint)
    }
    canvas.drawBitmap(scaled, border.toFloat(), border.toFloat(), null)
    mask.recycle()
    return result
}

// 이미지 복사본에서 지정한 위치의 원형 영역을 투명하게 지우기
fun erase(source: Bitmap, x: Float, y: Float, radius: Float): Bitmap = source.copy(Bitmap.Config.ARGB_8888, true).also {
    Canvas(it).drawCircle(x, y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    })
}
