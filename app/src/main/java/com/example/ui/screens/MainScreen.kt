package com.example.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.ChatViewModel

@Composable
fun MainScreen(
    viewModel: ChatViewModel,
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isInsideChat by viewModel.isInsideChat.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    // 2. If viewing a specific Channel or 1-on-1 Chat, display ChatDetailScreen
    if (isInsideChat) {
        ChatDetailScreen(viewModel = viewModel, modifier = modifier)
        return
    }

    // 3. Otherwise show Main App with Bottom Navigation
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(modifier = Modifier.testTag("main_bottom_nav")) {
                NavigationBarItem(
                    selected = selectedTab == AppTab.CHATS,
                    onClick = { viewModel.selectTab(AppTab.CHATS) },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == AppTab.CHATS) Icons.Default.ChatBubble else Icons.Default.ChatBubbleOutline,
                            contentDescription = "Chats"
                        )
                    },
                    label = { Text("Topic Chats") },
                    modifier = Modifier.testTag("nav_tab_chats")
                )

                NavigationBarItem(
                    selected = selectedTab == AppTab.SHIELD,
                    onClick = { viewModel.selectTab(AppTab.SHIELD) },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == AppTab.SHIELD) Icons.Default.Shield else Icons.Default.Security,
                            contentDescription = "Shield"
                        )
                    },
                    label = { Text("AI Shield") },
                    modifier = Modifier.testTag("nav_tab_shield")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(targetState = selectedTab, label = "tab_crossfade") { tab ->
                when (tab) {
                    AppTab.CHATS -> ChatListScreen(viewModel = viewModel, onSignOut = onSignOut)
                    AppTab.SHIELD -> TopicGuardDashboard(viewModel = viewModel)
                }
            }
        }
    }
}
