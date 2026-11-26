package com.ssu.mp.justsnapit.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

const val HOME_BOARD_ID = "home"
const val HOME_BOARD_NAME = "home"

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

@Entity(tableName = "boards")
data class BoardEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDefault: Boolean = false,
    val pendingSync: Boolean = true,
    val deleted: Boolean = false
)

@Entity(
    tableName = "board_stickers",
    primaryKeys = ["boardId", "stickerId"]
)
data class BoardStickerEntity(
    val boardId: String,
    val stickerId: String,
    val addedAt: Long
)

@Dao
interface StickerDao {
    // 삭제되지 않은 스티커를 생성 순서대로 조회
    @Query("SELECT * FROM stickers WHERE deleted = 0 ORDER BY createdAt, id")
    suspend fun load(): List<StickerEntity>

    // 삭제되지 않은 전체 스티커 개수를 조회
    @Query("SELECT COUNT(*) FROM stickers WHERE deleted = 0")
    suspend fun countActive(): Int

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

@Dao
interface BoardDao {
    // 삭제되지 않은 보드를 기본 보드 우선, 생성 순서대로 조회
    @Query("SELECT * FROM boards WHERE deleted = 0 ORDER BY isDefault DESC, createdAt ASC, id ASC")
    suspend fun loadBoards(): List<BoardEntity>

    // 식별자가 같은 보드의 존재 여부를 확인
    @Query("SELECT COUNT(*) FROM boards WHERE id = :id")
    suspend fun countBoard(id: String): Int

    // 새 보드를 추가하고 중복 식별자는 오류로 처리
    @Insert
    suspend fun insertBoard(board: BoardEntity)

    // 기본 보드가 없을 때만 추가
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensureBoard(board: BoardEntity)

    // 보드 이름과 수정 시각을 변경
    @Query("UPDATE boards SET name = :name, updatedAt = :now, pendingSync = 1 WHERE id = :id AND deleted = 0")
    suspend fun renameBoard(id: String, name: String, now: Long): Int

    // 기본 보드를 제외한 보드를 삭제 상태로 표시
    @Query("UPDATE boards SET deleted = 1, updatedAt = :now, pendingSync = 1 WHERE id = :id AND isDefault = 0 AND deleted = 0")
    suspend fun markBoardDeleted(id: String, now: Long): Int

    // 보드와 스티커 연결을 저장
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBoardSticker(link: BoardStickerEntity)

    // 여러 보드와 스티커 연결을 일괄 저장
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBoardStickers(links: List<BoardStickerEntity>)

    // 특정 보드의 스티커 연결을 모두 제거
    @Query("DELETE FROM board_stickers WHERE boardId = :boardId")
    suspend fun deleteBoardStickers(boardId: String)

    // 특정 스티커의 보드 연결을 모두 제거
    @Query("DELETE FROM board_stickers WHERE stickerId = :stickerId")
    suspend fun deleteStickerFromBoards(stickerId: String)

    // 지정한 보드에 속한 삭제되지 않은 스티커를 조회
    @Query(
        """
        SELECT s.* FROM stickers s
        INNER JOIN board_stickers bs ON s.id = bs.stickerId
        WHERE bs.boardId = :boardId AND s.deleted = 0
        ORDER BY bs.addedAt, s.createdAt, s.id
        """
    )
    suspend fun loadStickersForBoard(boardId: String): List<StickerEntity>
}

@Database(
    entities = [StickerEntity::class, StorageState::class, BoardEntity::class, BoardStickerEntity::class],
    version = 2,
    exportSchema = true
)
abstract class StickerDatabase : RoomDatabase() {
    // 스티커 저장 작업을 수행할 데이터 접근 객체를 반환
    abstract fun stickers(): StickerDao

    // 보드 저장 작업을 수행할 데이터 접근 객체를 반환
    abstract fun boards(): BoardDao

    companion object {
        @Volatile private var instance: StickerDatabase? = null

        // 버전 1의 스티커 데이터를 유지하면서 기본 home 보드와 연결 테이블 추가
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `boards` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `isDefault` INTEGER NOT NULL,
                        `pendingSync` INTEGER NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `board_stickers` (
                        `boardId` TEXT NOT NULL,
                        `stickerId` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`boardId`, `stickerId`)
                    )
                    """.trimIndent()
                )
                val now = System.currentTimeMillis()
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `boards` (`id`, `name`, `createdAt`, `updatedAt`, `isDefault`, `pendingSync`, `deleted`)
                    VALUES ('$HOME_BOARD_ID', '$HOME_BOARD_NAME', $now, $now, 1, 1, 0)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `board_stickers` (`boardId`, `stickerId`, `addedAt`)
                    SELECT '$HOME_BOARD_ID', `id`, `createdAt` FROM `stickers` WHERE `deleted` = 0
                    """.trimIndent()
                )
            }
        }

        // 앱 전체에서 하나의 데이터베이스 인스턴스를 사용
        fun get(context: Context): StickerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                StickerDatabase::class.java,
                "stickers.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
