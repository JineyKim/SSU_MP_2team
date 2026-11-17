package com.ssu.mp.justsnapit.data

import android.content.Context
import androidx.room.*

@Entity(tableName = "stickers")
data class StickerEntity(
    @PrimaryKey val id: String,
    val imageName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val title: String,
    val memo: String = "",
    val pendingSync: Boolean = true,
    val deleted: Boolean = false
)

@Entity(tableName = "storage_state")
data class StorageState(@PrimaryKey val key: String)

@Dao
interface StickerDao {
    // 삭제되지 않은 스티커를 생성 순서대로 조회
    @Query("SELECT * FROM stickers WHERE deleted = 0 ORDER BY createdAt, id")
    suspend fun load(): List<StickerEntity>

    // 식별자가 같은 스티커의 존재 여부를 확인
    @Query("SELECT COUNT(*) FROM stickers WHERE id = :id")
    suspend fun count(id: String): Int

    // 스티커 정보를 추가하고 중복 식별자는 오류로 처리
    @Insert
    suspend fun insert(sticker: StickerEntity)

    // 저장된 스티커를 덮어쓰지 않고 가져오기
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun importItems(stickers: List<StickerEntity>)

    // 메모와 수정 시각을 변경하고 서버 반영이 필요한 상태로 표시
    @Query("UPDATE stickers SET memo = :memo, updatedAt = :now, pendingSync = 1 WHERE id = :id AND deleted = 0")
    suspend fun updateMemo(id: String, memo: String, now: Long): Int

    // 삭제 사실을 서버에 전달할 수 있도록 기록 보관
    @Query("UPDATE stickers SET deleted = 1, updatedAt = :now, pendingSync = 1 WHERE id = :id AND deleted = 0")
    suspend fun markDeleted(id: String, now: Long)

    // 설정 파일에서 데이터를 가져왔는지 확인
    @Query("SELECT COUNT(*) FROM storage_state WHERE `key` = 'preferences_imported'")
    suspend fun imported(): Int

    // 데이터 가져오기 완료 상태를 저장
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun setState(state: StorageState)
}

@Database(entities = [StickerEntity::class, StorageState::class], version = 1, exportSchema = true)
abstract class StickerDatabase : RoomDatabase() {
    // 스티커 저장 작업을 수행할 데이터 접근 객체를 반환
    abstract fun stickers(): StickerDao

    companion object {
        @Volatile private var instance: StickerDatabase? = null

        // 앱 전체에서 하나의 데이터베이스 인스턴스를 사용
        fun get(context: Context): StickerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext,
                StickerDatabase::class.java, "stickers.db").build().also { instance = it }
        }
    }
}
