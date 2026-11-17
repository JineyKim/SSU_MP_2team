package com.ssu.mp.justsnapit

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ssu.mp.justsnapit.data.StickerDatabase
import com.ssu.mp.justsnapit.data.StickerStore
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class StickerStorageTest {
    // 격리된 저장소에서 메모 이전, 개별 수정, 삭제 기록과 중복 가져오기 방지를 확인
    @Test fun importEditDeleteAndReload() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "storage-test-${UUID.randomUUID()}"
        val directory = File(base.cacheDir, name).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getFilesDir(): File = directory
            override fun getSharedPreferences(key: String, mode: Int) = base.getSharedPreferences(name, mode)
        }
        val db = Room.inMemoryDatabaseBuilder(base, StickerDatabase::class.java).build()
        val prefs = context.getSharedPreferences("collection", Context.MODE_PRIVATE)
        val id = "SnappedAt20261102053025-UTC-a8f31c92"
        try {
            val image = File(directory, "stickers/$id.png").apply { parentFile!!.mkdirs() }
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            prefs.edit().putString("items", JSONArray().put(JSONObject().put("id", id)
                .put("created", 123L).put("title", "테스트").put("memo", "원래 메모")).toString()).commit()
            val store = StickerStore(context, db)
            val imported = store.load()
            assertEquals("원래 메모", imported.single().memo)
            store.updateMemo(id, "수정한 메모\n둘째 줄", imported)
            assertEquals("수정한 메모\n둘째 줄", StickerStore(context, db).load().single().memo)
            assertTrue(db.stickers().load().single().pendingSync)
            assertTrue(db.stickers().load().single().updatedAt > 123L)
            store.delete(imported.single())
            assertTrue(store.load().isEmpty())
            assertFalse(image.exists())
            assertEquals(1, db.stickers().count(id))
            assertEquals(1, db.stickers().imported())
        } finally {
            db.close()
            prefs.edit().clear().commit()
            directory.deleteRecursively()
        }
    }
}
