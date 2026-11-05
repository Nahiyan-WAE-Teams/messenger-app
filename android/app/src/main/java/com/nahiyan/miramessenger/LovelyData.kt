package com.nahiyan.miramessenger

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val age: Int,
    val bio: String,
    val kind: String = "fictional",
    val intent: String = "Long-term",
    val interests: String = "",
    val values: String = "",
    val lifestyle: String = "",
    val personalityTags: String = "",
    val likesUser: Boolean = false,
    val active: Boolean = true
)

@Entity(tableName = "profile_actions")
data class ProfileActionEntity(
    @PrimaryKey val profileId: String,
    val action: String,
    val createdAt: Long
)

@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey val profileId: String,
    val createdAt: Long,
    val unread: Boolean = true
)

@Dao
interface LovelyDao {
    @Query("SELECT * FROM profiles WHERE active = 1 AND id NOT IN (SELECT profileId FROM profile_actions) ORDER BY name")
    fun observeDiscoverProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT profiles.* FROM profiles INNER JOIN matches ON profiles.id = matches.profileId ORDER BY matches.createdAt DESC")
    fun observeMatches(): Flow<List<ProfileEntity>>

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun profileCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<ProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: ProfileActionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(match: MatchEntity)

    @Transaction
    suspend fun recordLike(profile: ProfileEntity, timestamp: Long) {
        insertAction(ProfileActionEntity(profile.id, "like", timestamp))
        if (profile.kind == "fictional" && profile.likesUser) insertMatch(MatchEntity(profile.id, timestamp))
    }

    @Transaction
    suspend fun recordPass(profileId: String, timestamp: Long) {
        insertAction(ProfileActionEntity(profileId, "pass", timestamp))
    }
}

@Database(entities = [ProfileEntity::class, ProfileActionEntity::class, MatchEntity::class], version = 1, exportSchema = false)
abstract class LovelyDatabase : RoomDatabase() {
    abstract fun lovelyDao(): LovelyDao
    companion object {
        @Volatile private var instance: LovelyDatabase? = null
        fun get(context: Context): LovelyDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, LovelyDatabase::class.java, "lovely.db")
                .build().also { instance = it }
        }
    }
}

class LovelyRepository(private val dao: LovelyDao) {
    val discover = dao.observeDiscoverProfiles()
    val matches = dao.observeMatches()

    suspend fun initialize() {
        if (dao.profileCount() != 0) return
        dao.insertProfiles(listOf(
            ProfileEntity("ava-morgan", "Ava", 25, "Architect by day, ceramicist by weekend. I collect little places with good light.", interests = "architecture, pottery, bookstores, hiking", values = "curiosity, kindness", personalityTags = "thoughtful, playful", likesUser = true),
            ProfileEntity("maya-chen", "Maya", 27, "Photographer, night-walk enthusiast, and unapologetic noodle loyalist.", interests = "photography, food, films, travel", values = "honesty, creativity", personalityTags = "witty, spontaneous"),
            ProfileEntity("sofia-reed", "Sofia", 24, "Plant parent with a questionable number of plants. Looking for something sincere.", interests = "gardening, books, cooking, art", values = "family, patience", personalityTags = "warm, reflective"),
            ProfileEntity("jules-bennett", "Jules", 26, "Long conversations, small concerts, and people who mean what they say.", interests = "music, concerts, coffee, cycling", values = "honesty, loyalty", personalityTags = "direct, funny")
        ))
    }
    suspend fun like(profile: ProfileEntity) = dao.recordLike(profile, System.currentTimeMillis())
    suspend fun pass(profile: ProfileEntity) = dao.recordPass(profile.id, System.currentTimeMillis())
}
