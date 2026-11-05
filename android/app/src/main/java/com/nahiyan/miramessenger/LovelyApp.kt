package com.nahiyan.miramessenger

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*

private enum class Tab(val label:String,val glyph:String) { Discover("Discover","✦"), Likes("Likes","♡"), Matches("Matches","♥"), Chats("Chats","▤"), Profile("Profile","◉") }
private val intents = listOf("Any","Long-term","See where it goes","Friendship first")
private val interests = listOf("Art","Books","Coffee","Cooking","Concerts","Dogs","Fashion","Fitness","Food","Hiking","Music","Movies","Nature","Photography","Plants","Space","Travel","Yoga","Architecture","Cats","Museums")

@Composable
fun LovelyApp(vm: LovelyViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.Discover.name) }
    var editor by remember { mutableStateOf<ProfileEntity?>(null) }
    var add by remember { mutableStateOf(false) }
    var filters by rememberSaveable { mutableStateOf(false) }
    var activeId by rememberSaveable { mutableStateOf("") }
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val unseen by vm.unseen.collectAsStateWithLifecycle()
    val likes by vm.likes.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val own by vm.ownProfile.collectAsStateWithLifecycle()
    val aiPreferences by vm.aiPreferences.collectAsStateWithLifecycle()
    val pref = preferences ?: PreferencesEntity()
    val mine = own ?: OwnProfileEntity()
    val discover = remember(unseen,pref,mine) {
        DiscoveryRanker.rank(unseen,
            interests=(mine.interests+","+pref.interests).split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet(),
            values=pref.values.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet(),
            lifestyle=pref.lifestyle.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet(),
            personality=pref.personality.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet(),
            intent=pref.intent,minAge=pref.minAge,maxAge=pref.maxAge,seed=likes.size*31+matches.size*17)
    }
    val chatProfiles = messages.map { it.profileId }.distinct().mapNotNull { id -> profiles.find { it.id==id } }
    val active = profiles.find { it.id==activeId }
    val activeMessages = messages.filter { it.profileId==activeId }

    Scaffold(containerColor=MaterialTheme.colorScheme.background,bottomBar={
        NavigationBar(
            modifier=Modifier.padding(horizontal=12.dp, vertical=8.dp).clip(RoundedCornerShape(26.dp))
                .border(1.dp,Color.White.copy(alpha=.07f),RoundedCornerShape(26.dp)),
            containerColor=Color(0xFF19151E), tonalElevation=0.dp
        ) {
            Tab.entries.forEach { item ->
                NavigationBarItem(selected=tab==item.name,onClick={tab=item.name},
                    icon={
                        AnimatedContent(tab==item.name,label="nav-icon") { selected ->
                            Text(item.glyph,fontSize=if(selected) 22.sp else 20.sp,
                                color=if(selected) MaterialTheme.colorScheme.primary else Color(0xFF8F8794))
                        }
                    },
                    label={Text(item.label,fontSize=10.sp,fontWeight=if(tab==item.name) FontWeight.Bold else FontWeight.Medium)},
                    colors=NavigationBarItemDefaults.colors(
                        selectedIconColor=MaterialTheme.colorScheme.primary,
                        selectedTextColor=MaterialTheme.colorScheme.primary,
                        indicatorColor=Color(0xFF382331),
                        unselectedIconColor=Color(0xFF8F8794),
                        unselectedTextColor=Color(0xFF8F8794)
                    )
                )
            }
        }
    }) { padding ->
        AnimatedContent(Tab.valueOf(tab),Modifier.fillMaxSize().padding(padding),
            transitionSpec={fadeIn()+slideInHorizontally { it/20 } togetherWith fadeOut()+slideOutHorizontally { -it/20 }},label="tab") { page ->
            when(page) {
                Tab.Discover -> Discover(discover,pref,mine.interests,filters,{filters=!filters},vm::savePreferences,
                    {vm.act(it,"like")},{vm.act(it,"pass")},{add=true})
                Tab.Likes -> Collection("Your likes","Profiles you chose to keep close.",likes,
                    "No likes yet","When someone catches your eye, tap the heart in Discover.",
                    {activeId=it.id;tab=Tab.Chats.name},{editor=it})
                Tab.Matches -> Collection("Mutual stories","Matches exist only for fictional profiles with explicit local mutual interest.",matches,
                    "No mutual matches yet","Edit a fictional profile and enable mutual interest to test a local match.",
                    {activeId=it.id;tab=Tab.Chats.name},{editor=it})
                Tab.Chats -> Chats(chatProfiles,active,activeMessages,{activeId=it.id},
                    {p,msg->vm.send(p,msg)},{vm.removeConversation(it.id);activeId=""},{activeId=""},{tab=Tab.Discover.name},
                    {editor=it})
                Tab.Profile -> ProfileSettings(profiles,mine,pref,aiPreferences,vm::saveOwn,vm::savePreferences,vm::saveAiSettings,
                    {add=true},{editor=it},vm::delete,vm::reset)
            }
        }
    }
    if(add || editor!=null) Editor(editor,{add=false;editor=null},{vm.save(it);add=false;editor=null})
}

@Composable private fun Header(kicker:String,title:String,subtitle:String) {
    Column(Modifier.padding(bottom=18.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Text(kicker.uppercase(),color=MaterialTheme.colorScheme.primary,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.8.sp)
        }
        Text(title,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,letterSpacing=(-.7).sp)
        Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium,lineHeight=21.sp)
    }
}

@Composable private fun Discover(items:List<ProfileEntity>,p:PreferencesEntity,myInterests:String,show:Boolean,toggle:()->Unit,save:(PreferencesEntity)->Unit,like:(ProfileEntity)->Unit,pass:(ProfileEntity)->Unit,add:()->Unit) {
    var index by remember(items) { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp, vertical=18.dp)) {
        Row(verticalAlignment=Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("lovely",fontSize=25.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-1.1).sp,color=MaterialTheme.colorScheme.onBackground)
                    Text("✦",fontSize=17.sp,color=MaterialTheme.colorScheme.primary)
                }
                Text("A little closer to a good story.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(18.dp))
                Header("Curated for your kind of connection","Meet someone new.","A thoughtful collection, shaped by what matters to you.")
            }
            FilledTonalIconButton(onClick=toggle,modifier=Modifier.padding(top=4.dp)) { Text("☷",fontSize=21.sp) }
        }
        AnimatedVisibility(show,enter=expandVertically()+fadeIn(),exit=shrinkVertically()+fadeOut()) {
            Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().padding(bottom=14.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Discovery preferences",fontWeight=FontWeight.Bold)
                    Text("Minimum age: "+p.minAge)
                    Slider(p.minAge.toFloat(),{save(p.copy(minAge=it.toInt().coerceAtMost(p.maxAge)))},valueRange=18f..60f)
                    Text("Maximum age: "+p.maxAge)
                    Slider(p.maxAge.toFloat(),{save(p.copy(maxAge=it.toInt().coerceAtLeast(p.minAge)))},valueRange=18f..60f)
                    Text("Relationship intention")
                    IntentRow(p.intent) { save(p.copy(intent=it)) }
                    OutlinedTextField(p.interests,{save(p.copy(interests=it))},Modifier.fillMaxWidth(),label={Text("Interests you enjoy")},placeholder={Text("Art, books, travel…")})
                    OutlinedTextField(p.values,{save(p.copy(values=it))},Modifier.fillMaxWidth(),label={Text("Values that matter")},placeholder={Text("Honesty, curiosity, kindness…")})
                    OutlinedTextField(p.lifestyle,{save(p.copy(lifestyle=it))},Modifier.fillMaxWidth(),label={Text("Lifestyle")},placeholder={Text("Active, homebody, outdoors…")})
                    OutlinedTextField(p.personality,{save(p.copy(personality=it))},Modifier.fillMaxWidth(),label={Text("Personality you connect with")},placeholder={Text("Thoughtful, playful, calm…")})
                }
            }
        }
        val profile=items.getOrNull(index)
        if(profile==null) Empty("That's everyone for now.","Widen your filters or add a profile to your local collection.","＋ Add a profile",add)
        else {
            Hero(profile,Modifier.fillMaxWidth().height(560.dp),DiscoveryRanker.explain(profile, (p.interests+","+myInterests).split(",").map{it.trim().lowercase()}.filter{it.isNotBlank()}.toSet(), p.values.split(",").map{it.trim().lowercase()}.filter{it.isNotBlank()}.toSet(), p.lifestyle.split(",").map{it.trim().lowercase()}.filter{it.isNotBlank()}.toSet(), p.personality.split(",").map{it.trim().lowercase()}.filter{it.isNotBlank()}.toSet()), onSwipeLeft={pass(profile);index=0}, onSwipeRight={like(profile);index=0})
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
                OutlinedButton(onClick={pass(profile);index=0},Modifier.weight(.42f).height(60.dp),
                    shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=.12f))) {
                    Text("×",fontSize=23.sp); Spacer(Modifier.width(7.dp)); Text("Pass",fontWeight=FontWeight.SemiBold)
                }
                Button(onClick={like(profile);index=0},Modifier.weight(.58f).height(60.dp),
                    shape=RoundedCornerShape(20.dp),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primary)) {
                    Text("♥",fontSize=20.sp); Spacer(Modifier.width(8.dp)); Text("Send a like",fontWeight=FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Discovery is ranked privately using your preferences and profile diversity. No compatibility percentage is shown.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun Hero(p:ProfileEntity,modifier:Modifier=Modifier,reasons:List<String> = emptyList(),onSwipeLeft:()->Unit={},onSwipeRight:()->Unit={}) {
    var dragX by remember(p.id) { mutableFloatStateOf(0f) }
    val animatedX by animateFloatAsState(dragX,animationSpec=spring(dampingRatio=.72f,stiffness=420f),label="profile-swipe")
    val haptic=LocalHapticFeedback.current
    val swipeInput=Modifier.pointerInput(p.id) {
        detectHorizontalDragGestures(
            onDragEnd={
                when {
                    dragX > 125f -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSwipeRight() }
                    dragX < -125f -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSwipeLeft() }
                }
                dragX=0f
            },
            onDragCancel={dragX=0f},
            onHorizontalDrag={change,amount -> change.consume(); dragX=(dragX+amount).coerceIn(-260f,260f)}
        )
    }

    Card(modifier.then(swipeInput).offset { IntOffset(animatedX.roundToInt(),0) }.graphicsLayer { rotationZ=animatedX/42f; alpha=1f-(abs(animatedX)/1500f).coerceIn(0f,.16f) },shape=RoundedCornerShape(34.dp),elevation=CardDefaults.cardElevation(defaultElevation=10.dp),
        border=BorderStroke(1.dp,Color.White.copy(alpha=.11f))) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(palette(p.palette)))) {
            if(p.photo.isNotBlank()) ProfileImage(p.photo,Modifier.fillMaxSize())
            else {
                Box(Modifier.align(Alignment.TopEnd).padding(top=52.dp,end=22.dp).size(190.dp)
                    .clip(CircleShape).background(Brush.radialGradient(listOf(Color.White.copy(alpha=.16f),Color.Transparent))))
                Text("L",Modifier.align(Alignment.TopEnd).padding(top=36.dp,end=24.dp),
                    fontSize=190.sp,fontWeight=FontWeight.Black,color=Color.White.copy(alpha=.045f),lineHeight=180.sp)
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                0f to Color.Black.copy(alpha=if(p.photo.isNotBlank()) .08f else .04f),
                .34f to Color.Transparent,
                .58f to Color(0xFF130D17).copy(alpha=.52f),
                1f to Color(0xFF100B13).copy(alpha=.98f)
            )))
            Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                Surface(shape=CircleShape,color=Color.Black.copy(alpha=.28f),border=BorderStroke(1.dp,Color.White.copy(alpha=.17f))) {
                    Text(if(p.kind=="fictional") "✦  FICTIONAL PROFILE" else "◉  PRIVATE RECORD",
                        Modifier.padding(horizontal=13.dp,vertical=9.dp),color=Color.White,fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                }
                Spacer(Modifier.weight(1f))
                Surface(shape=CircleShape,color=Color.Black.copy(alpha=.25f)) {
                    Text("LOCAL",Modifier.padding(horizontal=12.dp,vertical=8.dp),color=Color.White.copy(alpha=.85f),fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                }
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal=23.dp, vertical=25.dp),
                verticalArrangement=Arrangement.spacedBy(10.dp)) {
                if(p.city.isNotBlank()) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("⌖",fontSize=14.sp,color=Color.White.copy(alpha=.8f))
                    Text(p.city,color=Color.White.copy(alpha=.82f),fontSize=12.sp,fontWeight=FontWeight.Medium)
                }
                Text(p.name+", "+p.age,style=MaterialTheme.typography.headlineLarge,color=Color.White,
                    fontWeight=FontWeight.ExtraBold,letterSpacing=(-.8).sp,lineHeight=39.sp)
                Surface(shape=CircleShape,color=Color.White.copy(alpha=.13f),border=BorderStroke(1.dp,Color.White.copy(alpha=.16f))) {
                    Text(p.intent.ifBlank{"Open to a connection"},Modifier.padding(horizontal=12.dp,vertical=7.dp),
                        color=Color(0xFFFFD9E4),fontSize=11.sp,fontWeight=FontWeight.SemiBold)
                }
                if(reasons.isNotEmpty()) {
                    Text("WHY THIS PROFILE",fontSize=9.sp,letterSpacing=1.5.sp,color=Color.White.copy(alpha=.62f),fontWeight=FontWeight.Bold)
                    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        reasons.forEach { reason ->
                            Surface(shape=CircleShape,color=Color(0xFFFF789A).copy(alpha=.20f),border=BorderStroke(.7.dp,Color(0xFFFFB1C5).copy(alpha=.35f))) {
                                Text(reason,Modifier.padding(horizontal=9.dp,vertical=6.dp),fontSize=9.sp,color=Color.White,fontWeight=FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Text(p.bio.ifBlank{"A little more about this profile is waiting to be added."},
                    color=Color.White.copy(alpha=.94f),style=MaterialTheme.typography.bodyLarge,lineHeight=23.sp)
                val tags=p.interests.split(",").map{it.trim()}.filter{it.isNotBlank()}.take(4)
                if(tags.isNotEmpty()) Row(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.CenterVertically) {
                    tags.forEach { tag ->
                        Surface(shape=CircleShape,color=Color.White.copy(alpha=.12f),border=BorderStroke(.7.dp,Color.White.copy(alpha=.13f))) {
                            Text(tag,Modifier.padding(horizontal=10.dp,vertical=7.dp),fontSize=10.sp,color=Color.White.copy(alpha=.92f),fontWeight=FontWeight.Medium)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text("A STORY THAT STARTS WITH A HELLO",fontSize=9.sp,letterSpacing=1.7.sp,
                    color=Color.White.copy(alpha=.55f),fontWeight=FontWeight.Bold)
            }
        }
    }
}

@Composable private fun Collection(title:String,sub:String,items:List<ProfileEntity>,emptyTitle:String,emptyBody:String,chat:(ProfileEntity)->Unit,edit:(ProfileEntity)->Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Header("Your connections",title,sub)
        if(items.isEmpty()) Empty(emptyTitle,emptyBody)
        else LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            items(items,key={it.id}) { p ->
                Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically) {
                        Avatar(p,50)
                        Column(Modifier.weight(1f).padding(start=12.dp)) {
                            Text(p.name+", "+p.age,fontWeight=FontWeight.Bold)
                            Text(p.city.ifBlank{if(p.kind=="real") "Private record" else "Fictional profile"},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick={edit(p)}) { Text("Edit") }
                        FilledTonalButton(onClick={chat(p)},shape=RoundedCornerShape(13.dp)) { Text(if(p.kind=="real") "Notes" else "Chat") }
                    }
                }
            }
        }
    }
}

@Composable private fun Chats(items:List<ProfileEntity>,active:ProfileEntity?,messages:List<ChatMessageEntity>,select:(ProfileEntity)->Unit,send:(ProfileEntity,String)->Unit,remove:(ProfileEntity)->Unit,back:()->Unit,discover:()->Unit,edit:(ProfileEntity)->Unit) {
    var draft by remember(active?.id) { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Header("Local conversations","Messages","Fictional dialogue or private notes. Nothing is sent.")
        if(active==null) {
            if(items.isEmpty()) Empty("Your conversations live here.","Open a profile from Likes or Matches to start a local conversation.","Explore profiles",discover)
            else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(items,key={it.id}) { p ->
                    Surface(onClick={select(p)},shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(13.dp),verticalAlignment=Alignment.CenterVertically) {
                            Avatar(p,46)
                            Column(Modifier.weight(1f).padding(start=12.dp)) {
                                Text(p.name,fontWeight=FontWeight.Bold)
                                Text(if(p.kind=="real") "Private notes only" else "Fictional · local written replies",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("›",fontSize=24.sp,color=MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick=back) { Text("‹",fontSize=24.sp) }
                Avatar(active,42)
                Column(Modifier.weight(1f).padding(start=10.dp)) {
                    Text(active.name,fontWeight=FontWeight.Bold)
                    Text(if(active.kind=="real") "Private notes · not connected" else "Fictional · dialogue rules",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick={edit(active)}) { Text("Edit",color=MaterialTheme.colorScheme.primary) }
                TextButton(onClick={remove(active)}) { Text("Clear",color=MaterialTheme.colorScheme.error) }
            }
            HorizontalDivider(Modifier.padding(vertical=9.dp))
            if(active.kind=="real") Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.tertiaryContainer) {
                Text("Private notes only. This person is not connected to Lovely. No replies or activity are simulated.",Modifier.padding(10.dp),style=MaterialTheme.typography.bodySmall)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                if(messages.isEmpty()) item { Empty(if(active.kind=="real") "A private page for your notes." else "Start the conversation.",if(active.kind=="real") "Anything you write stays on this device." else "Replies come only from the dialogue rules saved for this fictional profile.") }
                items(messages,key={it.id}) { m ->
                    val mine=m.sender=="me"
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=if(mine) Arrangement.End else Arrangement.Start) {
                        Surface(shape=RoundedCornerShape(17.dp),color=if(mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.widthIn(max=300.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(m.message,color=if(mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(SimpleDateFormat("h:mm a",Locale.getDefault()).format(Date(m.createdAt)),Modifier.align(Alignment.End),fontSize=9.sp,color=if(mine) MaterialTheme.colorScheme.onPrimary.copy(alpha=.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                OutlinedTextField(draft,{draft=it},Modifier.weight(1f),placeholder={Text(if(active.kind=="real") "Write a private note…" else "Write a message…")},shape=RoundedCornerShape(22.dp),maxLines=4)
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick={if(draft.isNotBlank()){send(active,draft.trim());draft=""}},enabled=draft.isNotBlank()) { Text("↑",fontSize=22.sp) }
            }
        }
    }
}

@Composable private fun ProfileSettings(profiles:List<ProfileEntity>,own:OwnProfileEntity,p:PreferencesEntity,ai:AiPreferences,saveOwn:(OwnProfileEntity)->Unit,savePrefs:(PreferencesEntity)->Unit,saveAi:(AiProvider,String,ConversationMode,String?,Boolean)->Unit,add:()->Unit,edit:(ProfileEntity)->Unit,delete:(ProfileEntity)->Unit,reset:()->Unit) {
    var name by remember(own.name) { mutableStateOf(own.name) }
    var bio by remember(own.bio) { mutableStateOf(own.bio) }
    var mine by remember(own.interests) { mutableStateOf(own.interests) }
    var confirm by remember { mutableStateOf(false) }
    var provider by remember(ai.provider) { mutableStateOf(ai.provider) }
    var model by remember(ai.provider,ai.model) { mutableStateOf(ai.model) }
    var mode by remember(ai.mode) { mutableStateOf(ai.mode) }
    var apiKey by remember { mutableStateOf("") }
    var clearKey by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Header("Your private space","Your profile.","Manage your profile library and preferences.")
        Surface(shape=RoundedCornerShape(23.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text("Display name")})
                OutlinedTextField(bio,{bio=it},Modifier.fillMaxWidth(),label={Text("About me")},minLines=2)
                OutlinedTextField(mine,{mine=it},Modifier.fillMaxWidth(),label={Text("My interests, comma separated")})
                Button(onClick={saveOwn(OwnProfileEntity(name=name,bio=bio,interests=mine))},Modifier.fillMaxWidth()) { Text("Save my profile") }
            }
        }
        Surface(shape=RoundedCornerShape(23.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("AI conversations",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Text("Use your own provider key for flexible character conversations. Local dialogue remains available and works offline.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Conversation mode",fontWeight=FontWeight.SemiBold)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    FilterChip(mode==ConversationMode.LOCAL,{mode=ConversationMode.LOCAL},label={Text("Local")})
                    FilterChip(mode==ConversationMode.HYBRID,{mode=ConversationMode.HYBRID},label={Text("Hybrid")})
                    FilterChip(mode==ConversationMode.AI_ONLY,{mode=ConversationMode.AI_ONLY},label={Text("AI only")})
                }
                Text(when(mode) {
                    ConversationMode.LOCAL -> "Authored rules and the offline character engine. No API calls."
                    ConversationMode.HYBRID -> "Your authored triggers win first. AI handles messages without a matching rule."
                    ConversationMode.AI_ONLY -> "Send fictional-character messages to the selected AI provider."
                },fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Provider",fontWeight=FontWeight.SemiBold)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    FilterChip(provider==AiProvider.GEMINI,{provider=AiProvider.GEMINI;model="gemini-2.5-flash"},label={Text("Gemini")})
                    FilterChip(provider==AiProvider.OPENROUTER,{provider=AiProvider.OPENROUTER;model="openrouter/auto"},label={Text("OpenRouter")})
                }
                OutlinedTextField(model,{model=it},Modifier.fillMaxWidth(),label={Text("Model ID")},placeholder={Text(if(provider==AiProvider.GEMINI) "gemini-2.5-flash" else "openrouter/auto")},singleLine=true)
                OutlinedTextField(apiKey,{apiKey=it;clearKey=false},Modifier.fillMaxWidth(),label={Text(if(ai.hasKey) "Replace API key (leave blank to keep saved key)" else "API key")},placeholder={Text("Paste your provider key")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(if(ai.hasKey && !clearKey) "A key is saved securely on this device." else "No API key saved.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
                    if(ai.hasKey) TextButton(onClick={clearKey=true;apiKey=""}) { Text("Remove key",color=MaterialTheme.colorScheme.error) }
                }
                Button(onClick={saveAi(provider,model,mode,apiKey.takeIf{it.isNotBlank()},clearKey);apiKey="";clearKey=false},Modifier.fillMaxWidth()) { Text("Save AI settings") }
                Text("Keys are encrypted with Android Keystore and stay on this device. AI mode sends conversation context to your chosen provider. Provider charges and privacy policies apply.",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(shape=RoundedCornerShape(23.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Profile library",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold); Text(profiles.size.toString()+" local profiles",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    FilledTonalButton(onClick=add) { Text("＋ Add") }
                }
                profiles.forEach { profile ->
                    Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                        Avatar(profile,40)
                        Column(Modifier.weight(1f).padding(start=9.dp)) {
                            Text(profile.name,fontWeight=FontWeight.SemiBold)
                            Text(if(profile.kind=="real") "Private record" else "Fictional character",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick={edit(profile)}) { Text("Edit") }
                        TextButton(onClick={delete(profile)}) { Text("Delete",color=MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        Surface(shape=RoundedCornerShape(23.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Discovery preferences",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Text("Age range: "+p.minAge+"–"+p.maxAge)
                Slider(p.minAge.toFloat(),{savePrefs(p.copy(minAge=it.toInt().coerceAtMost(p.maxAge)))},valueRange=18f..60f)
                Slider(p.maxAge.toFloat(),{savePrefs(p.copy(maxAge=it.toInt().coerceAtLeast(p.minAge)))},valueRange=18f..60f)
                IntentRow(p.intent) { savePrefs(p.copy(intent=it)) }
                OutlinedTextField(p.values,{savePrefs(p.copy(values=it))},Modifier.fillMaxWidth(),label={Text("Values you care about")})
                OutlinedTextField(p.lifestyle,{savePrefs(p.copy(lifestyle=it))},Modifier.fillMaxWidth(),label={Text("Lifestyle preferences")})
                OutlinedTextField(p.personality,{savePrefs(p.copy(personality=it))},Modifier.fillMaxWidth(),label={Text("Personality preferences")})
            }
        }
        Surface(shape=RoundedCornerShape(23.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Text("Privacy, by design",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Privacy("Storage","This device"); Privacy("AI services",if(ai.mode==ConversationMode.LOCAL) "Off" else ai.provider.name); Privacy("Backend","Not required"); Privacy("Internet permission",if(ai.mode==ConversationMode.LOCAL) "Not used" else "Used only for AI"); Privacy("Real-person records","Private notes only")
                Text("Profiles and chats stay local. API keys are encrypted on-device. When AI mode is used, selected conversation context is sent to your chosen provider. Only store information you have permission to keep.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick={confirm=true},Modifier.fillMaxWidth()) { Text("Reset all local data",color=MaterialTheme.colorScheme.error) }
            }
        }
        if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Reset Lovely?")},text={Text("This deletes local profiles, likes, matches, conversations and preferences, then restores starter profiles.")},confirmButton={TextButton(onClick={confirm=false;reset()}){Text("Reset",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}})
    }
}

@Composable private fun Editor(initial:ProfileEntity?,dismiss:()->Unit,save:(ProfileEntity)->Unit) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var age by remember(initial?.id) { mutableStateOf((initial?.age?:25).toString()) }
    var city by remember(initial?.id) { mutableStateOf(initial?.city.orEmpty()) }
    var bio by remember(initial?.id) { mutableStateOf(initial?.bio.orEmpty()) }
    var kind by remember(initial?.id) { mutableStateOf(initial?.kind?:"fictional") }
    var intent by remember(initial?.id) { mutableStateOf(initial?.intent?:"Long-term") }
    var tags by remember(initial?.id) { mutableStateOf(initial?.interests.orEmpty()) }
    var values by remember(initial?.id) { mutableStateOf(initial?.values.orEmpty()) }
    var life by remember(initial?.id) { mutableStateOf(initial?.lifestyle.orEmpty()) }
    var traits by remember(initial?.id) { mutableStateOf(initial?.personalityTags.orEmpty()) }
    var mutual by remember(initial?.id) { mutableStateOf(initial?.likesUser?:false) }
    var ruleEntries by remember(initial?.id) { mutableStateOf(DialogueRuleCodec.parse(initial?.dialogueRules ?: DialogueEngine.DEFAULT_RULES)) }
    var fallback by remember(initial?.id) { mutableStateOf(initial?.fallback?:"I haven't written a reply for that yet.") }

    var error by remember { mutableStateOf("") }
    var photo by remember(initial?.id) { mutableStateOf(initial?.photo.orEmpty()) }
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes != null && bytes.size <= 1_500_000) photo = Base64.encodeToString(bytes, Base64.NO_WRAP)
            else error = "Choose a photo smaller than 1.5 MB."
        }
    }
    AlertDialog(onDismissRequest=dismiss,title={Text(if(initial==null) "Add a profile" else "Edit profile",fontWeight=FontWeight.Bold)},
        text={Column(Modifier.heightIn(max=550.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row { FilterChip(kind=="fictional",{kind="fictional"},label={Text("Fictional")}); Spacer(Modifier.width(6.dp)); FilterChip(kind=="real",{kind="real";mutual=false},label={Text("Real-person record")}) }
            OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text("Name")})
            OutlinedTextField(age,{age=it},Modifier.fillMaxWidth(),label={Text("Age · 18 to 99")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            OutlinedTextField(city,{city=it},Modifier.fillMaxWidth(),label={Text("City / location")})
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                if(photo.isNotBlank()) ProfileImage(photo,Modifier.size(76.dp).clip(CircleShape))
                else Surface(Modifier.size(76.dp),shape=CircleShape,color=palette(initial?.palette ?: "rose").first()) {
                    Box(contentAlignment=Alignment.Center) { Text(name.take(1).ifBlank{"?"}.uppercase(),color=Color.White,fontSize=28.sp) }
                }
                Column(Modifier.weight(1f)) {
                    Text(if(photo.isBlank()) "Portrait not set" else "Portrait ready",fontWeight=FontWeight.SemiBold)
                    Text("Stored on this device only.",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        TextButton(onClick={photoPicker.launch("image/*")}) { Text(if(photo.isBlank()) "Add photo" else "Change") }
                        if(photo.isNotBlank()) TextButton(onClick={photo=""}) { Text("Remove") }
                    }
                }
            }
            OutlinedTextField(bio,{bio=it},Modifier.fillMaxWidth(),label={Text("About")},minLines=2)
            Text("Relationship intention"); IntentRow(intent){intent=it}
            OutlinedTextField(tags,{tags=it},Modifier.fillMaxWidth(),label={Text("Interests · comma separated")})
            OutlinedTextField(values,{values=it},Modifier.fillMaxWidth(),label={Text("Values")})
            OutlinedTextField(life,{life=it},Modifier.fillMaxWidth(),label={Text("Lifestyle")})
            OutlinedTextField(traits,{traits=it},Modifier.fillMaxWidth(),label={Text("Personality traits")})
            if(kind=="fictional") {
                Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(mutual,{mutual=it}); Text("Mutual interest in this fictional story") }
                Text("Conversation builder",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Text("Choose exactly what she says when a message matches a trigger. Triggers can be words or short phrases, separated by commas.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                ruleEntries.forEachIndexed { index, rule ->
                    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f),modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(11.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text("RULE ${index+1}",fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp,color=MaterialTheme.colorScheme.primary,modifier=Modifier.weight(1f))
                                TextButton(onClick={ruleEntries=ruleEntries.toMutableList().also{it.removeAt(index)}}) { Text("Remove",color=MaterialTheme.colorScheme.error) }
                            }
                            OutlinedTextField(rule.trigger,{value->ruleEntries=ruleEntries.toMutableList().also{it[index]=it[index].copy(trigger=value)}},Modifier.fillMaxWidth(),label={Text("When the user says…")},placeholder={Text("hi, hello, hey")},singleLine=true)
                            OutlinedTextField(rule.reply,{value->ruleEntries=ruleEntries.toMutableList().also{it[index]=it[index].copy(reply=value)}},Modifier.fillMaxWidth(),label={Text("She replies…")},placeholder={Text("Hey! Good to hear from you.")},minLines=2)
                        }
                    }
                }
                OutlinedButton(onClick={ruleEntries=ruleEntries+DialogueRuleEditorEntry("","")},Modifier.fillMaxWidth()) { Text("＋ Add conversation rule") }
                OutlinedTextField(fallback,{fallback=it},Modifier.fillMaxWidth(),label={Text("If nothing matches, she says…")})
            } else Text("Private notes only. No simulated interest, messages, activity or matches.",fontSize=12.sp,color=MaterialTheme.colorScheme.tertiary)
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        }},
        confirmButton={Button(onClick={
            val years=age.toIntOrNull()
            if(name.isBlank()||years==null||years !in 18..99){error="Enter a name and valid age (18–99).";return@Button}
            val colors=listOf("rose","sage","blue","plum","gold","coral","lavender","sunset")
            val color=initial?.palette?:colors[(name.hashCode().toUInt().toLong()%8).toInt()]
            save(ProfileEntity(initial?.id?:"local-"+System.currentTimeMillis(),name.trim(),years,bio,kind,intent,city,tags,values,life,traits,kind=="fictional"&&mutual,true,photo,DialogueRuleCodec.serialize(ruleEntries),fallback,color))
        }) { Text("Save profile") }},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}

@Composable private fun IntentRow(value:String,onChange:(String)->Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        intents.forEach { FilterChip(value==it,{onChange(it)},label={Text(it,fontSize=11.sp)}) }
    }
}
@Composable private fun ProfileImage(data:String,modifier:Modifier=Modifier) {
    val bitmap = remember(data) {
        runCatching {
            val bytes = Base64.decode(data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) Image(bitmap, contentDescription="Profile photo", modifier=modifier, contentScale=ContentScale.Crop)
}
@Composable private fun Avatar(p:ProfileEntity,size:Int) {
    Surface(Modifier.size(size.dp),shape=CircleShape,color=palette(p.palette).first()) {
        Box(contentAlignment=Alignment.Center) { if(p.photo.isNotBlank()) ProfileImage(p.photo,Modifier.fillMaxSize().clip(CircleShape)) else Text(p.name.take(1).uppercase(),color=Color.White,fontSize=(size*.42).sp) }
    }
}
private fun palette(name:String):List<Color> = when(name) {
    "sage"->listOf(Color(0xFF78917C),Color(0xFF314B43),Color(0xFF19131D))
    "blue"->listOf(Color(0xFF6687A5),Color(0xFF273A55),Color(0xFF19131D))
    "plum"->listOf(Color(0xFF9B6D91),Color(0xFF432C4D),Color(0xFF19131D))
    "gold"->listOf(Color(0xFFB58C5B),Color(0xFF604631),Color(0xFF19131D))
    "coral"->listOf(Color(0xFFC77C72),Color(0xFF603A46),Color(0xFF19131D))
    "lavender"->listOf(Color(0xFF9584B8),Color(0xFF43385F),Color(0xFF19131D))
    "sunset"->listOf(Color(0xFFC07A64),Color(0xFF4C354B),Color(0xFF19131D))
    else->listOf(Color(0xFFC17A93),Color(0xFF60384F),Color(0xFF19131D))
}
@Composable private fun Privacy(a:String,b:String) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(a,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(b,fontWeight=FontWeight.SemiBold)} }
@Composable private fun Empty(title:String,body:String,action:String?=null,onAction:(()->Unit)?=null) {
    Column(Modifier.fillMaxWidth().padding(vertical=32.dp,horizontal=16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Text("♡",fontSize=46.sp,color=MaterialTheme.colorScheme.primary)
        Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Text(body,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(action!=null&&onAction!=null){Spacer(Modifier.height(12.dp));Button(onClick=onAction){Text(action)}}
    }
}
