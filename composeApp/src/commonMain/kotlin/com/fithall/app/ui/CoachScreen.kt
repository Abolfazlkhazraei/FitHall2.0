package com.fithall.app.ui

import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fithall.app.AppState
import com.fithall.app.AppViewModel
import com.fithall.app.model.ChatMessage
import com.fithall.app.model.ChatRole

private val suggestions = listOf(
    "What should I eat for dinner to hit my protein?",
    "I only have 30 minutes today. What should I do?",
    "Make my workout plan a bit harder",
    "How do I know if I'm progressing?",
    "Give me 3 high-protein snack ideas",
)

@Composable
fun CoachScreen(state: AppState, viewModel: AppViewModel, onBack: () -> Unit = {}) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.chat.size, state.coachTyping) {
        val count = state.chat.size + if (state.coachTyping) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    fun send(text: String) {
        viewModel.sendCoachMessage(text)
        input = ""
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        HeroHeader(
            title = "Coach",
            subtitle = "Knows your meals and plans",
            trailing = {
                if (state.chat.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearChat) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear chat", tint = Brand.onGradient)
                    }
                }
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.Close, contentDescription = "Back to Today", tint = Brand.onGradient)
                }
            },
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.chat.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.AutoAwesome,
                        "Hi ${state.profile?.name?.substringBefore(' ') ?: "there"}!",
                        "Ask me anything about your training or nutrition, or ask me to adjust your plan.",
                    )
                }
            }
            items(state.chat) { Bubble(it) }
            if (state.coachTyping) item { Bubble(ChatMessage(ChatRole.ASSISTANT, "Thinking…")) }
            state.coachFailure?.takeIf { !state.coachTyping }?.let { failure ->
                item {
                    ErrorCard(
                        failure.copy(message = "Your coach couldn't reply. ${failure.message}"),
                        onRetry = viewModel::retryCoachReply,
                        onDismiss = viewModel::dismissFailures,
                    )
                }
            }
        }

        if (state.chat.isEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(suggestions) { s ->
                    SuggestionChip(
                        onClick = { send(s) },
                        label = { Text(s) },
                        shape = RoundedCornerShape(50),
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = null,
                    )
                }
            }
        }

        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Message your coach") },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            Spacer(Modifier.width(8.dp))
            val canSend = input.isNotBlank() && !state.coachTyping
            Box(
                Modifier.size(52.dp).clip(CircleShape)
                    .background(if (canSend) Palette.lime else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = canSend) { send(input) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (canSend) Palette.onLime else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    val mine = message.role == ChatRole.USER
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(12.dp)).background(Palette.blue), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Palette.onLime, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        val shape = if (mine) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
        Text(
            message.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (mine) Palette.onLime else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(shape)
                .then(
                    if (mine) Modifier.background(Palette.lime)
                    else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}
