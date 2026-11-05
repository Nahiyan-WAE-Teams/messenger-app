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
    @Query("SELECT id FROM profiles") suspend fun profileIds(): List<String>
    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1") suspend fun profileById(id: String): ProfileEntity?
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
        val existingIds = dao.profileIds().toSet()
        val starterProfiles = listOf(
            ProfileEntity("ava-morgan", "Ava Morgan", 25, "Architect by day, ceramicist by weekend. I collect little places with good light.", interests="Architecture, Pottery, Bookstores, Hiking", values="Curiosity, Kindness", personalityTags="Thoughtful, Playful", lifestyle="Creative, Outdoors", likesUser=true, city="Portland, OR", palette="rose"),
            ProfileEntity("nina-park", "Nina Park", 25, "Bookshop wanderer, film photographer, and enthusiastic rainy-day person. Slow conversations are my favorite.", interests="Books, Photography, Movies, Nature", values="Creativity, Patience", personalityTags="Reflective, Witty", lifestyle="Quiet, Creative", likesUser=true, city="Seattle, WA", palette="blue"),
            ProfileEntity("zoe-carter", "Zoe Carter", 28, "Live gigs, spontaneous city walks, and making friends with every cat in the neighborhood.", interests="Concerts, Cats, Fashion, Food", values="Freedom, Humor", personalityTags="Spontaneous, Bold", lifestyle="Social, Active", city="Chicago, IL", palette="plum"),
            ProfileEntity("maya-ellis", "Maya Ellis", 24, "Plant parent, amateur ceramicist, and believer that breakfast belongs at every hour.", interests="Plants, Art, Brunch, Yoga", values="Family, Patience", personalityTags="Warm, Reflective", lifestyle="Homebody, Creative", likesUser=true, city="Portland, OR", palette="sage"),
            ProfileEntity("sophie-reed", "Sophie Reed", 29, "Mountain air, indie films, and finding the best dumplings in every city I visit.", interests="Travel, Movies, Food, Hiking", values="Adventure, Honesty", personalityTags="Curious, Calm", lifestyle="Outdoors, Active", city="Denver, CO", palette="gold"),
            ProfileEntity("ella-brooks", "Ella Brooks", 26, "Designer by day, amateur stargazer by night. I collect little moments and very large sweaters.", interests="Design, Space, Coffee, Museums", values="Creativity, Kindness", personalityTags="Dreamy, Thoughtful", lifestyle="Creative, Quiet", city="Boston, MA", palette="lavender"),
            ProfileEntity("ruby-james", "Ruby James", 27, "Beach sunsets, old-school R&B, and making dinner for people I care about.", interests="Music, Cooking, Beach, Fitness", values="Loyalty, Family", personalityTags="Affectionate, Direct", lifestyle="Social, Active", city="Los Angeles, CA", palette="coral"),
            ProfileEntity("jules-bennett", "Jules Bennett", 26, "Long conversations, small concerts, and people who mean what they say.", interests="Music, Concerts, Coffee, Cycling", values="Honesty, Loyalty", personalityTags="Direct, Funny", lifestyle="Social, Active", city="Austin, TX", palette="sunset"),
            ProfileEntity("harper-lane", "Harper Lane", 27, "I restore old furniture, make ambitious Sunday breakfasts, and will absolutely stop to pet your dog.", interests="Design, Cooking, Dogs, Markets", values="Patience, Warmth", personalityTags="Grounded, Funny", lifestyle="Homebody, Creative", city="Brooklyn, NY", palette="rose"),
            ProfileEntity("olivia-chen", "Olivia Chen", 29, "Museum afternoons, terrible puns, and a camera roll full of tiny architectural details.", interests="Museums, Photography, Architecture, Coffee", values="Curiosity, Honesty", personalityTags="Witty, Observant", lifestyle="Quiet, Creative", city="San Francisco, CA", palette="blue"),
            ProfileEntity("tessa-brooks", "Tessa Brooks", 24, "Weekend trail runner with a soft spot for used bookstores and very loud playlists.", interests="Running, Hiking, Books, Music", values="Adventure, Discipline", personalityTags="Energetic, Sincere", lifestyle="Outdoors, Active", city="Boulder, CO", palette="sage"),
            ProfileEntity("naomi-ellis", "Naomi Ellis", 30, "I host dinner parties for six and somehow cook for twelve. Big believer in showing up.", interests="Cooking, Gardening, Jazz, Food", values="Community, Loyalty", personalityTags="Warm, Decisive", lifestyle="Social, Homebody", city="Philadelphia, PA", palette="coral"),
            ProfileEntity("claire-bennett", "Claire Bennett", 28, "A science writer who likes astronomy, midnight walks, and asking one question too many.", interests="Science, Space, Podcasts, Hiking", values="Curiosity, Independence", personalityTags="Analytical, Playful", lifestyle="Quiet, Outdoors", city="Washington, DC", palette="lavender"),
            ProfileEntity("dani-rivera", "Dani Rivera", 26, "Dance classes, street food, and turning an ordinary Tuesday into a small adventure.", interests="Dance, Food, Travel, Fitness", values="Joy, Freedom", personalityTags="Expressive, Bold", lifestyle="Social, Active", city="Miami, FL", palette="sunset"),
            ProfileEntity("morgan-price", "Morgan Price", 31, "I make playlists for road trips and remember the small things people tell me.", interests="Music, Road Trips, Coffee, Photography", values="Loyalty, Empathy", personalityTags="Thoughtful, Dry Humor", lifestyle="Balanced, Creative", city="Nashville, TN", palette="gold"),
            ProfileEntity("avery-cole", "Avery Cole", 25, "Plant shop regular, amateur baker, and enthusiastic supporter of staying in on Fridays.", interests="Plants, Baking, Cats, Art", values="Kindness, Simplicity", personalityTags="Gentle, Quirky", lifestyle="Homebody, Creative", city="Minneapolis, MN", palette="sage"),
            ProfileEntity("leah-foster", "Leah Foster", 28, "Climbing gym after work, farmers market on Saturday, and always planning the next long weekend.", interests="Climbing, Outdoors, Food, Travel", values="Growth, Honesty", personalityTags="Driven, Easygoing", lifestyle="Active, Outdoors", city="Salt Lake City, UT", palette="blue"),
            ProfileEntity("riley-james", "Riley James", 27, "Film photography, thrift-store treasure hunts, and a weakness for old soul records.", interests="Photography, Vintage, Music, Movies", values="Creativity, Authenticity", personalityTags="Independent, Witty", lifestyle="Creative, Social", city="Los Angeles, CA", palette="plum"),
            ProfileEntity("eden-walsh", "Eden Walsh", 32, "A public librarian who believes every good friendship starts with a recommendation.", interests="Books, Poetry, Gardens, Tea", values="Community, Patience", personalityTags="Reflective, Kind", lifestyle="Quiet, Homebody", city="Portland, ME", palette="sage"),
            ProfileEntity("paige-turner", "Paige Turner", 24, "Graphic designer, amateur skateboarder, and collector of oddly specific facts.", interests="Design, Skateboarding, Comics, Music", values="Curiosity, Courage", personalityTags="Playful, Adventurous", lifestyle="Active, Creative", city="Austin, TX", palette="coral"),
            ProfileEntity("quinn-hayes", "Quinn Hayes", 30, "I work in public radio, love a long train ride, and think a little awkwardness is charming.", interests="Radio, Travel, Podcasts, Museums", values="Empathy, Curiosity", personalityTags="Observant, Dry Humor", lifestyle="Balanced, Quiet", city="Chicago, IL", palette="lavender"),
            ProfileEntity("stella-monroe", "Stella Monroe", 26, "Yoga before breakfast, live music after dinner, and a very competitive board-game streak.", interests="Yoga, Music, Board Games, Food", values="Balance, Humor", personalityTags="Warm, Competitive", lifestyle="Social, Active", city="Atlanta, GA", palette="gold"),
            ProfileEntity("wren-parker", "Wren Parker", 29, "Wildflower walks, ceramics that lean slightly to the left, and thoughtful silences.", interests="Nature, Pottery, Poetry, Hiking", values="Patience, Authenticity", personalityTags="Calm, Creative", lifestyle="Outdoors, Quiet", city="Asheville, NC", palette="sage"),
            ProfileEntity("june-kim", "June Kim", 27, "Product designer who loves a clever interface, a perfect bowl of noodles, and low-key Sundays.", interests="Design, Technology, Food, Movies", values="Craft, Kindness", personalityTags="Curious, Direct", lifestyle="Balanced, Creative", city="Seattle, WA", palette="plum")
        )
        val scripted = starterProfiles.map(StarterDialogue::apply)
        dao.upsertProfiles(scripted.filter { it.id !in existingIds })
        scripted.filter { it.id in existingIds }.forEach { seed ->
            val current = dao.profileById(seed.id) ?: return@forEach
            if (current.kind == "fictional" && current.dialogueRules == DialogueEngine.DEFAULT_RULES && current.fallback == DialogueEngine.DEFAULT_FALLBACK) {
                dao.upsertProfile(current.copy(dialogueRules = seed.dialogueRules, fallback = seed.fallback))
            } else if (current.kind == "fictional" && current.dialogueRules.lines().none { line ->
                    line.substringBefore("|").split(",").any { it.trim().lowercase() in listOf("hello", "hi", "hey") }
                }) {
                // Add a greeting rule without replacing any existing character-authored rules.
                dao.upsertProfile(current.copy(
                    dialogueRules = "hello, hi, hey | Hey! It's nice to hear from you.\n${current.dialogueRules}"
                ))
            }
        }
        if (dao.getPreferences() == null) dao.upsertPreferences(PreferencesEntity())
        if (dao.getOwnProfile() == null) dao.upsertOwnProfile(OwnProfileEntity())
    }

    suspend fun act(profile: ProfileEntity, action: String) = dao.act(profile, action, System.currentTimeMillis())
    suspend fun saveProfile(profile: ProfileEntity) = dao.upsertProfile(profile)
    suspend fun deleteProfile(id: String) = dao.removeProfile(id)
    suspend fun removeConversation(id: String) = dao.deleteMessages(id)
    suspend fun savePreferences(p: PreferencesEntity) = dao.upsertPreferences(p)
    suspend fun saveOwn(p: OwnProfileEntity) = dao.upsertOwnProfile(p)
    suspend fun send(profile: ProfileEntity, text: String, aiConfig: AiRuntimeConfig? = null) {
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
            val matchedRule = DialogueEngine.matchingReply(text, profile.dialogueRules, stage, recentReplies, priorUser)
            val config = aiConfig
            val useAi = config != null && AiConversationRouter.shouldCallAi(config.preferences.mode, matchedRule, config.apiKey.isNotBlank())
            val reply = when {
                AiConversationRouter.shouldUseAuthoredRule(config?.preferences?.mode ?: ConversationMode.LOCAL, matchedRule) -> matchedRule!!
                useAi -> runCatching { AiChatClient.generate(config!!, profile, history) }
                    .getOrElse { OfflineConversationEngine.reply(profile, text, stage, recentReplies, priorUser, previous.exchangeCount) }
                else -> OfflineConversationEngine.reply(profile, text, stage, recentReplies, priorUser, previous.exchangeCount)
            }
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

object StarterDialogue {
    private val scripts = mapOf(
        "ava-morgan" to ("architecture, buildings | Old buildings have such good stories. I always notice the details." to "I was thinking about a building I want to visit. What's up?"),
        "nina-park" to ("books, reading | What are you reading lately? I love seeing what people pick for themselves." to "I'm letting that thought develop. Tell me a little more."),
        "zoe-carter" to ("music, concert | I always want to know which bands are good live." to "I got distracted by something happening outside. Continue."),
        "maya-ellis" to ("plants, garden | One new leaf can make my whole day. Plant people get it." to "I was checking on my plants. What were you saying?"),
        "sophie-reed" to ("travel, hiking | I keep a list of places I'd like to get lost in." to "I'm deciding between a mountain story and a food story."),
        "ella-brooks" to ("design, stars | I notice typography everywhere. It's a professional hazard." to "I was redesigning that thought in my head. Try me again."),
        "ruby-james" to ("music, cooking | A good bass line makes cooking twice as fun." to "I'm in the kitchen. What's on your mind?"),
        "jules-bennett" to ("honest, truth | I'd rather hear an awkward truth than a polished half-answer." to "I don't have a clever line for that. Say more?"),
        "harper-lane" to ("furniture, vintage | Old furniture has personality, and usually a few questionable repairs." to "I got flour on my phone again. What's up?"),
        "olivia-chen" to ("museum, photography | I can spend an afternoon following one tiny detail around a museum." to "I have an observation forming. Give me a second."),
        "tessa-brooks" to ("running, hiking | The first ten minutes of a run are mostly negotiations with myself." to "I may still be catching my breath. What's happening?"),
        "naomi-ellis" to ("dinner, cooking | I always cook for more people than are coming. Leftovers are a love language." to "I'm planning a dinner menu. Give me a moment."),
        "claire-bennett" to ("science, space | The universe is absurdly interesting. I have too many questions." to "I want to answer that properly, not guess."),
        "dani-rivera" to ("dance, adventure | A dance class is a reset button with better music." to "I started planning three things at once. Pick one thread."),
        "morgan-price" to ("playlist, music | I make playlists for people. It's how I pay attention." to "I'm choosing the right song for this conversation."),
        "avery-cole" to ("plants, baking | My apartment is becoming a greenhouse with a rent payment." to "I was waiting for something to come out of the oven."),
        "leah-foster" to ("climbing, outdoors | Climbing is problem-solving with chalk everywhere. I love it." to "Just got back from the climbing gym. My arms are negotiating."),
        "riley-james" to ("vintage, records | Thrift stores are treasure hunts where intuition is the map." to "I found something interesting and I'm working out how to explain it."),
        "eden-walsh" to ("books, poetry | Tell me a book you wish you could read again for the first time." to "I'm turning that over in my head. No rush."),
        "paige-turner" to ("skate, comics | I'm better at trying new tricks than landing them. Yet." to "I have an oddly specific fact for this moment."),
        "quinn-hayes" to ("radio, train | Radio is magic: a voice makes a stranger feel like company." to "I'm editing my answer like it's going on air."),
        "stella-monroe" to ("yoga, board game | I'm friendly until the board game starts. Then I focus." to "I'm deciding whether to be gracious or competitive."),
        "wren-parker" to ("nature, pottery | I like noticing what's growing in the cracks and along the edges." to "I'm taking a quiet minute with that thought."),
        "june-kim" to ("design, noodles | I have strong opinions about noodles and moderate evidence." to "I'm mentally rearranging that into a better answer.")
    )
    fun apply(profile: ProfileEntity): ProfileEntity = scripts[profile.id]?.let {
        profile.copy(dialogueRules = "hello, hi, hey | Hey! It's nice to hear from you.\n${it.first}", fallback = it.second)
    } ?: profile
}

object DialogueEngine {
    const val DEFAULT_RULES = "hello, hi, hey | Hey! Nice to hear from you."
    const val DEFAULT_FALLBACK = "I haven't written a reply for that yet. What else is on your mind?"

    private fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFKC)
        .replace(Regex("[^\\p{L}\\p{N}\\s']"), " ").replace(Regex("\\s+"), " ").trim()
    fun matchingReply(message: String, rulesText: String, stage: String, recentReplies: List<String>, previousMessage: String): String? {
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
        val winner = candidates.firstOrNull() ?: return null
        return winner.replies.firstOrNull { it !in recentReplies.takeLast(3) } ?: winner.replies.first()
    }

    fun reply(message: String, rulesText: String, fallback: String, stage: String, recentReplies: List<String>, previousMessage: String): String =
        matchingReply(message, rulesText, stage, recentReplies, previousMessage) ?: fallback

}
