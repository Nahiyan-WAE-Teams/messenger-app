package com.nahiyan.miramessenger

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class LovelyTab(val title: String, val glyph: String) {
    Discover("Discover", "✦"), Likes("Likes", "♡"), Chats("Chats", "✉"), Profile("Profile", "◉")
}

@Composable
fun LovelyApp(viewModel: LovelyViewModel) {
    var selected by rememberSaveable { mutableStateOf(LovelyTab.Discover.name) }
    val discover by viewModel.discover.collectAsStateWithLifecycle()
    val matches by viewModel.matches.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                LovelyTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab.name,
                        onClick = { selected = tab.name },
                        icon = { Text(tab.glyph) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { insets ->
        AnimatedContent(
            targetState = LovelyTab.valueOf(selected),
            modifier = Modifier.fillMaxSize().padding(insets),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "native-navigation"
        ) { tab ->
            when (tab) {
                LovelyTab.Discover -> DiscoverScreen(discover, viewModel::like, viewModel::pass)
                LovelyTab.Likes -> SimpleListScreen("Your likes", matches, "No mutual matches yet", "A match appears only when a fictional profile explicitly has mutual interest.")
                LovelyTab.Chats -> PlaceholderScreen("Conversations", "Native chat is the next migration milestone. Real-person records will remain private notes, never simulated messages.")
                LovelyTab.Profile -> PlaceholderScreen("Your space", "Lovely is offline by design. Your local profiles and interactions stay on this device.")
            }
        }
    }
}

@Composable
private fun DiscoverScreen(profiles: List<ProfileEntity>, onLike: (ProfileEntity) -> Unit, onPass: (ProfileEntity) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("LOVELY", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("A little closer.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) { Text("✧", Modifier.padding(13.dp), color = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.height(18.dp))
        if (profiles.isEmpty()) {
            EmptyState("You've seen everyone for now", "Add more profiles to continue discovering.")
        } else {
            val profile = profiles.first()
            Card(Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF573247), Color(0xFF211B2A), Color(0xFF17131C))))) {
                    Column(Modifier.align(Alignment.Center).padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(116.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFFFA6B8), Color(0xFF9D6688)))), contentAlignment = Alignment.Center) {
                            Text(profile.name.take(1), color = Color(0xFF321323), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(22.dp))
                        Text("${profile.name}, ${profile.age}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(10.dp))
                        Text(profile.intent, color = Color(0xFFFFC1D1), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(18.dp))
                        Text(profile.bio, color = Color(0xFFF4EAF0), style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    Text("LOCAL PROFILE", Modifier.align(Alignment.BottomStart).padding(20.dp), color = Color(0xFFFFC1D1), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Interests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(profile.interests, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(.08f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onPass(profile) }, Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(18.dp)) { Text("Pass") }
                Button(onClick = { onLike(profile) }, Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(18.dp)) { Text("Like  ♡") }
            }
        }
    }
}

@Composable
private fun SimpleListScreen(title: String, profiles: List<ProfileEntity>, emptyTitle: String, emptyDetail: String) {
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        if (profiles.isEmpty()) EmptyState(emptyTitle, emptyDetail)
        profiles.forEach { profile ->
            Surface(Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .18f)) {
                        Box(contentAlignment = Alignment.Center) { Text(profile.name.take(1), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                    }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("${profile.name}, ${profile.age}", fontWeight = FontWeight.SemiBold)
                        Text("Mutual local match", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, detail: String) {
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        EmptyState("Built natively, one layer at a time", detail)
    }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("✧", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(detail, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
    }
}
