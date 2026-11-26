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

data class Board(val id: String, val name: String, val created: Long, val isDefault: Boolean = false)

data class BoardSnapshot(val boards: List<Board>, val activeBoardId: String, val stickers: List<Sticker>)

class StickerStore(private val context: Context, private val database: StickerDatabase = StickerDatabase.get(context)) {
    private val directory = File(context.filesDir, "stickers").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("collection", Context.MODE_PRIVATE)
    private val dao = database.stickers()
    private val boardDao = database.boards()
    private val filenamePattern = Regex("SnappedAt[0-9]{14}-UTC-[0-9a-f]{8}")
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT).withZone(ZoneOffset.UTC)
    private val random = SecureRandom()
    private val lastBoardKey = "last_board_id"

    // 기본 보드와 최근 방문 보드, 해당 보드의 스티커 목록을 복원
    suspend fun loadWorkspace(): BoardSnapshot = withContext(Dispatchers.IO) {
        prepareStorage()
        val boards = loadBoardsInternal()
        val savedId = prefs.getString(lastBoardKey, HOME_BOARD_ID) ?: HOME_BOARD_ID
        val activeId = if (boards.any { it.id == savedId }) savedId else HOME_BOARD_ID
        saveLastBoardId(activeId)
        BoardSnapshot(boards, activeId, loadBoardStickersInternal(activeId))
    }

    // 지정한 보드의 스티커 목록을 복원하고 최근 방문 보드로 기록
    suspend fun openBoard(boardId: String): List<Sticker> = withContext(Dispatchers.IO) {
        prepareStorage()
        saveLastBoardId(boardId)
        loadBoardStickersInternal(boardId)
    }

    // 새 보드를 생성하고 최근 방문 보드로 전환한 뒤 전체 보드 목록을 반환
    suspend fun createBoard(name: String): Pair<List<Board>, Board> = withContext(Dispatchers.IO) {
        prepareStorage()
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "보드 이름을 입력해주세요." }
        val now = System.currentTimeMillis()
        val timestamp = timestampFormat.format(Instant.ofEpochMilli(now))
        var id: String
        while (true) {
            currentCoroutineContext().ensureActive()
            val suffix = String.format(Locale.ROOT, "%08x", random.nextInt())
            id = "Board$timestamp-UTC-$suffix"
            if (boardDao.countBoard(id) == 0) break
        }
        val entity = BoardEntity(id = id, name = trimmed, createdAt = now, updatedAt = now, isDefault = false)
        boardDao.insertBoard(entity)
        saveLastBoardId(id)
        loadBoardsInternal() to entity.toBoard()
    }

    // 지정한 보드의 이름을 변경하고 갱신된 보드 목록을 반환
    suspend fun renameBoard(boardId: String, name: String): List<Board> = withContext(Dispatchers.IO) {
        prepareStorage()
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "보드 이름을 입력해주세요." }
        check(boardDao.renameBoard(boardId, trimmed, System.currentTimeMillis()) == 1) { "보드를 찾을 수 없어요." }
        loadBoardsInternal()
    }

    // 기본 보드가 아닌 보드를 삭제하고 필요 시 기본 보드로 전환
    suspend fun deleteBoard(boardId: String, currentBoardId: String): BoardSnapshot = withContext(Dispatchers.IO) {
        prepareStorage()
        require(boardId != HOME_BOARD_ID) { "기본 home 보드는 삭제할 수 없어요." }
        database.withTransaction {
            check(boardDao.markBoardDeleted(boardId, System.currentTimeMillis()) == 1) { "보드를 삭제할 수 없어요." }
            boardDao.deleteBoardStickers(boardId)
        }
        val boards = loadBoardsInternal()
        val nextBoardId = if (currentBoardId == boardId || boards.none { it.id == currentBoardId }) HOME_BOARD_ID else currentBoardId
        saveLastBoardId(nextBoardId)
        BoardSnapshot(boards, nextBoardId, loadBoardStickersInternal(nextBoardId))
    }

    // 저장된 목록과 이미지 파일을 읽어 전체 스티커 목록을 복원
    suspend fun load(): List<Sticker> = withContext(Dispatchers.IO) {
        prepareStorage()
        decodeStickers(dao.load())
    }

    // 기본 보드 생성과 설정 파일 데이터 이전을 하나의 트랜잭션으로 준비
    private suspend fun prepareStorage() {
        database.withTransaction {
            val now = System.currentTimeMillis()
            boardDao.ensureBoard(
                BoardEntity(
                    id = HOME_BOARD_ID,
                    name = HOME_BOARD_NAME,
                    createdAt = now,
                    updatedAt = now,
                    isDefault = true
                )
            )
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
            boardDao.insertBoardStickers(items.map { BoardStickerEntity(HOME_BOARD_ID, it.id, it.createdAt) })
            dao.setState(StorageState("preferences_imported"))
        }
    }

    // 저장된 보드 목록을 화면 모델로 변환하여 반환
    private suspend fun loadBoardsInternal(): List<Board> = boardDao.loadBoards().map { it.toBoard() }

    // 지정한 보드에 연결된 스티커 이미지를 디코딩하여 반환
    private suspend fun loadBoardStickersInternal(boardId: String): List<Sticker> =
        decodeStickers(boardDao.loadStickersForBoard(boardId))

    // 최근 방문한 보드 식별자를 설정 파일에 기록
    private fun saveLastBoardId(boardId: String) {
        prefs.edit().putString(lastBoardKey, boardId).apply()
    }

    // 데이터베이스 스티커 목록에서 유효한 이미지 파일만 읽어 화면 모델로 변환
    private fun decodeStickers(entities: List<StickerEntity>): List<Sticker> = buildList {
        for (item in entities) {
            val id = item.id
            // SnappedAt + 14자리 시각 + -UTC- + 8자리 16진수 형식의 식별자만 불러오기
            if (!filenamePattern.matches(id)) continue
            // 저장 목록과 실제 파일명이 다르면 불러오기 생략, 폴더 내 파일 재검색 생략
            if (item.imageName != "$id.png") continue
            val file = File(directory, item.imageName)
            if (!file.isFile) continue
            val bitmap = BitmapFactory.decodeFile(file.path)
            if (bitmap != null) add(Sticker(id, bitmap, item.createdAt, item.title, item.memo))
        }
    }

    // 중복되지 않는 파일명으로 이미지와 정보를 저장하고 선택한 보드에 연결
    suspend fun add(bitmap: Bitmap, current: List<Sticker>, boardId: String = HOME_BOARD_ID): Sticker = withContext(Dispatchers.IO) {
        prepareStorage()
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
        val nextNumber = max(dao.countActive(), current.size) + 1
        val sticker = Sticker(id, bitmap, created, "Snap $nextNumber")
        try {
            file.outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "이미지를 저장하지 못했어요." }
            }
            database.withTransaction {
                dao.insert(StickerEntity(id, file.name, created, created, sticker.title))
                boardDao.insertBoardSticker(BoardStickerEntity(boardId, id, created))
            }
            sticker
        } catch (error: Exception) {
            // 이번 저장에 실패한 경우 새로 만든 파일만 정리
            file.delete()
            throw error
        }
    }

    // 대상 스티커를 저장 목록과 보드 연결에서 제외하고 이미지 파일을 삭제
    suspend fun delete(sticker: Sticker) = withContext(Dispatchers.IO) {
        prepareStorage()
        database.withTransaction {
            dao.markDeleted(sticker.id, System.currentTimeMillis())
            boardDao.deleteStickerFromBoards(sticker.id)
        }
        File(directory, "${sticker.id}.png").delete()
        Unit
    }

    // 선택한 스티커의 메모를 저장하고 갱신된 목록을 반환
    suspend fun updateMemo(id: String, memo: String, current: List<Sticker>): List<Sticker> = withContext(Dispatchers.IO) {
        prepareStorage()
        check(dao.updateMemo(id, memo, System.currentTimeMillis()) == 1) { "스티커를 찾을 수 없어요." }
        val updated = current.map { if (it.id == id) it.copy(memo = memo) else it }
        updated
    }

    // 보드 엔티티를 화면 모델로 변환
    private fun BoardEntity.toBoard(): Board = Board(id = id, name = name, created = createdAt, isDefault = isDefault)

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
