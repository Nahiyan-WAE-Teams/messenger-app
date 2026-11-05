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
    val city: String = "",
    val interests: String = "",
    val values: String = "",
    val lifestyle: String = "",
    val personalityTags: String = "",
    val likesUser: Boolean = false,
    val active: Boolean = true,
    val photo: String = "",
    val dialogueRules: String = "hello, hi, hey | Hey! Nice to hear from you.",
    val fallback: String = "I haven't written a reply for that yet. What else is on your mind?",
    val palette: String = "rose"
)

@Entity(tableName = "profile_actions", primaryKeys = ["profileId"])
data class ProfileActionEntity(val profileId: String, val action: String, val createdAt: Long)

@Entity(tableName = "matches")
data class MatchEntity(@PrimaryKey val profileId: String, val createdAt: Long, val unread: Boolean = true)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val sender: String,
    val message: String,
    val createdAt: Long
)

@Entity(tableName = "relationships")
data class RelationshipEntity(
    @PrimaryKey val profileId: String,
    val exchangeCount: Int = 0,
    val activeDays: String = "",
    val lastInteractionAt: Long = 0
)

@Entity(tableName = "preferences")
data class PreferencesEntity(
    @PrimaryKey val id: Int = 1,
    val minAge: Int = 21,
    val maxAge: Int = 35,
    val intent: String = "Any",
    val interests: String = "",
    val values: String = "",
    val lifestyle: String = "",
    val personality: String = ""
)

@Entity(tableName = "own_profile")
data class OwnProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "Your name",
    val bio: String = "A little about me...",
    val interests: String = "Coffee, Travel, Music"
)

@Dao
interface LovelyDao {
    @Query("SELECT * FROM profiles WHERE active = 1 ORDER BY name") fun observeProfiles(): Flow<List<ProfileEntity>>
    @Query("SELECT * FROM profiles WHERE active = 1 AND id NOT IN (SELECT profileId FROM profile_actions) ORDER BY name") fun observeUnseen(): Flow<List<ProfileEntity>>
    @Query("SELECT profiles.* FROM profiles INNER JOIN profile_actions ON profiles.id = profile_actions.profileId WHERE profile_actions.action = 'like' ORDER BY profile_actions.createdAt DESC") fun observeLikes(): Flow<List<ProfileEntity>>
    @Query("SELECT profiles.* FROM profiles INNER JOIN matches ON profiles.id = matches.profileId ORDER BY matches.createdAt DESC") fun observeMatches(): Flow<List<ProfileEntity>>
    @Query("SELECT * FROM chat_messages ORDER BY createdAt") fun observeMessages(): Flow<List<ChatMessageEntity>>
    @Query("SELECT * FROM preferences WHERE id = 1") fun observePreferences(): Flow<PreferencesEntity?>
    @Query("SELECT * FROM own_profile WHERE id = 1") fun observeOwnProfile(): Flow<OwnProfileEntity?>
    @Query("SELECT * FROM relationships") fun observeRelationships(): Flow<List<RelationshipEntity>>
    @Query("SELECT COUNT(*) FROM profiles") suspend fun profileCount(): Int
    @Query("SELECT * FROM preferences WHERE id = 1 LIMIT 1") suspend fun getPreferences(): PreferencesEntity?
    @Query("SELECT * FROM own_profile WHERE id = 1 LIMIT 1") suspend fun getOwnProfile(): OwnProfileEntity?
    @Query("SELECT * FROM chat_messages WHERE profileId = :profileId ORDER BY createdAt") fun observeConversation(profileId: String): Flow<List<ChatMessageEntity>>
    @Query("SELECT * FROM chat_messages WHERE profileId = :profileId ORDER BY createdAt DESC LIMIT 3") suspend fun recentMessages(profileId: String): List<ChatMessageEntity>
    @Query("SELECT * FROM relationships WHERE profileId = :profileId LIMIT 1") suspend fun relationship(profileId: String): RelationshipEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertProfiles(profiles: List<ProfileEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertProfile(profile: ProfileEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAction(action: ProfileActionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMatch(match: MatchEntity)
    @Insert suspend fun insertMessage(message: ChatMessageEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertRelationship(relationship: RelationshipEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPreferences(preferences: PreferencesEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertOwnProfile(profile: OwnProfileEntity)
    @Query("DELETE FROM profiles WHERE id = :id") suspend fun deleteProfile(id: String)
    @Query("DELETE FROM profile_actions WHERE profileId = :id") suspend fun deleteAction(id: String)
    @Query("DELETE FROM matches WHERE profileId = :id") suspend fun deleteMatch(id: String)
    @Query("DELETE FROM chat_messages WHERE profileId = :id") suspend fun deleteMessages(id: String)
    @Query("DELETE FROM relationships WHERE profileId = :id") suspend fun deleteRelationship(id: String)
    @Query("DELETE FROM profiles") suspend fun clearProfiles()
    @Query("DELETE FROM profile_actions") suspend fun clearActions()
    @Query("DELETE FROM matches") suspend fun clearMatches()
    @Query("DELETE FROM chat_messages") suspend fun clearMessages()
    @Query("DELETE FROM relationships") suspend fun clearRelationships()
    @Query("DELETE FROM preferences") suspend fun clearPreferences()
    @Query("DELETE FROM own_profile") suspend fun clearOwnProfile()

    @Transaction
    suspend fun act(profile: ProfileEntity, action: String, timestamp: Long) {
        upsertAction(ProfileActionEntity(profile.id, action, timestamp))
        if (action == "like" && profile.kind == "fictional" && profile.likesUser) upsertMatch(MatchEntity(profile.id, timestamp))
    }
    @Transaction
    suspend fun removeProfile(id: String) {
        deleteProfile(id); deleteAction(id); deleteMatch(id); deleteMessages(id); deleteRelationship(id)
    }
}

@Database(entities = [ProfileEntity::class, ProfileActionEntity::class, MatchEntity::class, ChatMessageEntity::class, RelationshipEntity::class, PreferencesEntity::class, OwnProfileEntity::class], version = 2, exportSchema = false)
abstract class LovelyDatabase : RoomDatabase() {
    abstract fun lovelyDao(): LovelyDao
    companion object {
        @Volatile private var instance: LovelyDatabase? = null
        fun get(context: Context): LovelyDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, LovelyDatabase::class.java, "lovely.db")
                .fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}

class LovelyRepository(private val dao: LovelyDao) {
    val profiles = dao.observeProfiles()
    val unseen = dao.observeUnseen()
    val likes = dao.observeLikes()
    val matches = dao.observeMatches()
    val messages = dao.observeMessages()
    val preferences = dao.observePreferences()
    val ownProfile = dao.observeOwnProfile()
    val relationships = dao.observeRelationships()

    suspend fun initialize() {
        if (dao.profileCount() == 0) dao.upsertProfiles(listOf(
            ProfileEntity("ava-morgan", "Ava Morgan", 25, "Architect by day, ceramicist by weekend. I collect little places with good light.", interests="Architecture, Pottery, Bookstores, Hiking", values="Curiosity, Kindness", personalityTags="Thoughtful, Playful", likesUser=true, city="Portland, OR", palette="rose"),
            ProfileEntity("nina-park", "Nina Park", 25, "Bookshop wanderer, film photographer, and enthusiastic rainy-day person. Slow conversations are my favorite.", interests="Books, Photography, Movies, Nature", values="Creativity, Patience", personalityTags="Reflective, Witty", likesUser=true, city="Seattle, WA", palette="blue"),
            ProfileEntity("zoe-carter", "Zoe Carter", 28, "Live gigs, spontaneous city walks, and making friends with every cat in the neighborhood.", interests="Concerts, Cats, Fashion, Food", values="Freedom, Humor", personalityTags="Spontaneous, Bold", city="Chicago, IL", palette="plum"),
            ProfileEntity("maya-ellis", "Maya Ellis", 24, "Plant parent, amateur ceramicist, and believer that breakfast belongs at every hour.", interests="Plants, Art, Brunch, Yoga", values="Family, Patience", personalityTags="Warm, Reflective", likesUser=true, city="Portland, OR", palette="sage"),
            ProfileEntity("sophie-reed", "Sophie Reed", 29, "Mountain air, indie films, and finding the best dumplings in every city I visit.", interests="Travel, Movies, Food, Hiking", values="Adventure, Honesty", personalityTags="Curious, Calm", city="Denver, CO", palette="gold"),
            ProfileEntity("ella-brooks", "Ella Brooks", 26, "Designer by day, amateur stargazer by night. I collect little moments and very large sweaters.", interests="Design, Space, Coffee, Museums", values="Creativity, Kindness", personalityTags="Dreamy, Thoughtful", city="Boston, MA", palette="lavender"),
            ProfileEntity("ruby-james", "Ruby James", 27, "Beach sunsets, old-school R&B, and making dinner for people I care about.", interests="Music, Cooking, Beach, Fitness", values="Loyalty, Family", personalityTags="Affectionate, Direct", city="Los Angeles, CA", palette="coral"),
            ProfileEntity("jules-bennett", "Jules Bennett", 26, "Long conversations, small concerts, and people who mean what they say.", interests="Music, Concerts, Coffee, Cycling", values="Honesty, Loyalty", personalityTags="Direct, Funny", city="Austin, TX", palette="sunset")
        ))
        if (dao.getPreferences() == null) dao.upsertPreferences(PreferencesEntity())
        if (dao.getOwnProfile() == null) dao.upsertOwnProfile(OwnProfileEntity())
    }

    suspend fun act(profile: ProfileEntity, action: String) = dao.act(profile, action, System.currentTimeMillis())
    suspend fun saveProfile(profile: ProfileEntity) = dao.upsertProfile(profile)
    suspend fun deleteProfile(id: String) = dao.removeProfile(id)
    suspend fun savePreferences(p: PreferencesEntity) = dao.upsertPreferences(p)
    suspend fun saveOwn(p: OwnProfileEntity) = dao.upsertOwnProfile(p)
    suspend fun send(profile: ProfileEntity, text: String) {
        val now = System.currentTimeMillis()
        dao.insertMessage(ChatMessageEntity(profileId=profile.id, sender="me", message=text, createdAt=now))
        if (profile.kind == "fictional") {
            val history = dao.recentMessages(profile.id).reversed()
            val priorUser = history.dropLast(1).lastOrNull { it.sender == "me" }?.message.orEmpty()
            val recentReplies = history.filter { it.sender == "them" }.map { it.message }
            val previous = dao.relationship(profile.id) ?: RelationshipEntity(profile.id)
            val stage = when {
                previous.exchangeCount >= 10 && previous.activeDays.split(",").filter { it.isNotBlank() }.distinct().size >= 3 -> "close"
                previous.exchangeCount >= 5 && previous.activeDays.split(",").filter { it.isNotBlank() }.distinct().size >= 2 -> "familiar"
                previous.exchangeCount >= 1 -> "getting-to-know"
                else -> "first-meeting"
            }
            val reply = DialogueEngine.reply(text, profile.dialogueRules, profile.fallback, stage, recentReplies, priorUser)
            dao.insertMessage(ChatMessageEntity(profileId=profile.id, sender="them", message=reply, createdAt=System.currentTimeMillis()))
            val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now))
            val days = (previous.activeDays.split(",").filter { it.isNotBlank() } + day).distinct().takeLast(30).joinToString(",")
            dao.upsertRelationship(RelationshipEntity(profile.id, previous.exchangeCount + 1, days, now))
        }
    }
    suspend fun reset() {
        dao.clearProfiles(); dao.clearActions(); dao.clearMatches(); dao.clearMessages(); dao.clearRelationships(); dao.clearPreferences(); dao.clearOwnProfile()
        initialize()
    }
}

object DialogueEngine {
    private fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFKC)
        .replace(Regex("[^\\p{L}\\p{N}\\s']"), " ").replace(Regex("\\s+"), " ").trim()
    fun reply(message: String, rulesText: String, fallback: String, stage: String, recentReplies: List<String>, previousMessage: String): String {
        val padded = " ${norm(message)} "
        val previous = " ${norm(previousMessage)} "
        data class Candidate(val score: Int, val priority: Int, val replies: List<String>)
        val candidates = rulesText.lines().mapNotNull { line ->
            val parts = line.split("|", limit=2)
            if (parts.size != 2) return@mapNotNull null
            var triggerText = parts[0].trim()
            var priority = 0
            val stageMatch = Regex("^\\[(first-meeting|getting-to-know|familiar|close)]\\s*", RegexOption.IGNORE_CASE).find(triggerText)
            if (stageMatch != null) {
                if (stageMatch.groupValues[1] != stage) return@mapNotNull null
                priority = 2
                triggerText = triggerText.removeRange(stageMatch.range)
            }
            val context = Regex("^\\[after:([^]]+)]\\s*", RegexOption.IGNORE_CASE).find(triggerText)
            if (context != null) {
                val terms = context.groupValues[1].split(",").map { norm(it) }.filter { it.isNotBlank() }
                if (terms.none { previous.contains(" $it ") }) return@mapNotNull null
                priority += 1
                triggerText = triggerText.removeRange(context.range)
            }
            val triggers = triggerText.split(",").map { norm(it) }.filter { it.isNotBlank() }
            val score = triggers.filter { padded.contains(" $it ") }.sumOf { it.length + 10 }
            val replies = parts[1].split(";").map { it.trim() }.filter { it.isNotBlank() }
            if (score == 0 || replies.isEmpty()) null else Candidate(score, priority, replies)
        }.sortedWith(compareByDescending<Candidate> { it.priority }.thenByDescending { it.score })
        val winner = candidates.firstOrNull() ?: return fallback
        return winner.replies.firstOrNull { it !in recentReplies.takeLast(3) } ?: winner.replies.first()
    }
}
