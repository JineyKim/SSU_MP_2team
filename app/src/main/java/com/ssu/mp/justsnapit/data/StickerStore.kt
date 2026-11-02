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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.security.SecureRandom
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class Sticker(val id: String, val bitmap: Bitmap, val created: Long, val title: String)

class StickerStore(private val context: Context) {
    private val directory = File(context.filesDir, "stickers").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("collection", Context.MODE_PRIVATE)
    private val filenamePattern = Regex("SnappedAt[0-9]{14}-UTC-[0-9a-f]{8}")
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT).withZone(ZoneOffset.UTC)
    private val random = SecureRandom()

    // 저장된 목록과 이미지 파일을 읽어 스티커 목록을 복원합니다.
    suspend fun load(): List<Sticker> = withContext(Dispatchers.IO) {
        val list = runCatching { JSONArray(prefs.getString("items", "[]")) }.getOrDefault(JSONArray())
        buildList {
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                val id = item.optString("id")
                // SnappedAt + 14자리 시각 + -UTC- + 8자리 16진수 형식의 식별자만 불러옵니다.
                if (!filenamePattern.matches(id)) continue
                // 저장 목록과 실제 파일명이 다르면 불러오지 않습니다. 폴더에서 파일을 임의로 재검색하지 않습니다.
                val file = File(directory, "$id.png")
                if (!file.isFile) continue
                val bitmap = BitmapFactory.decodeFile(file.path)
                if (bitmap != null) add(Sticker(id, bitmap,
                    item.getLong("created"), item.optString("title", "My snap")))
            }
        }
    }

    // 중복되지 않는 파일명으로 이미지와 정보를 저장하고 스티커를 반환합니다.
    suspend fun add(bitmap: Bitmap, current: List<Sticker>): Sticker = withContext(Dispatchers.IO) {
        val created = System.currentTimeMillis()
        val timestamp = timestampFormat.format(Instant.ofEpochMilli(created))
        var id: String
        var file: File
        // UTC(협정 세계시) 저장 시각과 8자리 난수로 이름을 만들고, 중복되면 난수를 다시 생성합니다.
        while (true) {
            currentCoroutineContext().ensureActive()
            val suffix = String.format(Locale.ROOT, "%08x", random.nextInt())
            id = "SnappedAt$timestamp-UTC-$suffix"
            file = File(directory, "$id.png")
            // 파일 생성과 존재 확인을 한 번에 수행하여 이미 있는 파일을 덮어쓰지 않습니다.
            if (current.none { it.id == id } && file.createNewFile()) break
        }
        val sticker = Sticker(id, bitmap, created, "Snap ${current.size + 1}")
        try {
            file.outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "이미지를 저장하지 못했어요." }
            }
            persist(current + sticker)
            sticker
        } catch (error: Exception) {
            // 이번 저장에 실패한 경우 새로 만든 파일만 정리합니다.
            file.delete()
            throw error
        }
    }

    // 대상 스티커를 저장 목록에서 제외하고 이미지 파일을 삭제합니다.
    suspend fun delete(sticker: Sticker, current: List<Sticker>) = withContext(Dispatchers.IO) {
        persist(current.filterNot { it.id == sticker.id })
        File(directory, "${sticker.id}.png").delete()
        Unit
    }

    // 스티커의 식별자, 생성 시각, 이름을 앱 내부 설정에 저장합니다.
    private fun persist(items: List<Sticker>) {
        val json = JSONArray()
        items.forEach { json.put(JSONObject().put("id", it.id).put("created", it.created).put("title", it.title)) }
        check(prefs.edit().putString("items", json.toString()).commit()) { "저장하지 못했어요." }
    }

    // 사진을 읽고 크기를 제한하여 비트맵으로 반환합니다.
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

// 사진에서 피사체를 분리하여 배경이 제거된 이미지를 반환합니다.
suspend fun cutOut(bitmap: Bitmap): Bitmap {
    val client = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
    return try { requireNotNull(client.process(InputImage.fromBitmap(bitmap, 0)).await().foregroundBitmap) }
    finally { client.close() }
}

// 투명 여백을 자르고 이미지 크기를 조정한 뒤 흰색 테두리를 추가합니다.
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
    val scaled = Bitmap.createScaledBitmap(crop, max(1, (crop.width * ratio).toInt()), max(1, (crop.height * ratio).toInt()), true)
    val border = 14
    val result = Bitmap.createBitmap(scaled.width + border * 2, scaled.height + border * 2, Bitmap.Config.ARGB_8888)
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

// 이미지 복사본에서 지정한 위치의 원형 영역을 투명하게 지웁니다.
fun erase(source: Bitmap, x: Float, y: Float, radius: Float): Bitmap = source.copy(Bitmap.Config.ARGB_8888, true).also {
    Canvas(it).drawCircle(x, y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    })
}
