package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DriftStatus
import com.example.data.model.Message
import com.example.data.model.TopicRelevance
import com.example.ui.components.FileAttachmentCard
import com.example.ui.components.GuardLevelBadge
import com.example.ui.components.LinkPreviewCard
import com.example.ui.components.TopicRelevanceChip
import com.example.ui.components.UserAvatar
import com.example.ui.theme.TopicGreen
import com.example.ui.theme.TopicRed
import com.example.ui.theme.TopicYellow
import com.example.ui.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val activeChannel by viewModel.activeChannel.collectAsState()
    val messages by viewModel.activeMessages.collectAsState()
    val inputText by viewModel.messageInput.collectAsState()
    val draftRelevance by viewModel.draftRelevance.collectAsState()
    val isCheckingDraft by viewModel.isCheckingDraftRelevance.collectAsState()
    val showStrictWarning by viewModel.showStrictWarning.collectAsState()
    val pendingRewrite by viewModel.pendingRewriteSuggestion.collectAsState()

    val showAttachmentSheet by viewModel.showAttachmentSheet.collectAsState()
    val showLinkScannerSheet by viewModel.showLinkScannerSheet.collectAsState()

    var showTopicBannerExpanded by remember { mutableStateOf(false) }
    var selectedExplanationMessage by remember { mutableStateOf<Message?>(null) }

    val listState = rememberLazyListState()

    // Auto scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Android back handler
    BackHandler {
        viewModel.closeChat()
    }

    val channel = activeChannel ?: return

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.closeChat() },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Chats"
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showTopicBannerExpanded = !showTopicBannerExpanded }
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (channel.isDirectMessage) channel.name else "#${channel.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                GuardLevelBadge(guardLevel = channel.guardLevel)
                            }
                            Text(
                                text = "Topic: ${channel.topic}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    // Voice Call Button
                    IconButton(
                        onClick = { viewModel.startVoiceCall() },
                        modifier = Modifier.testTag("voice_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Start Voice Call",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    // Video Call Button
                    IconButton(
                        onClick = { viewModel.startVideoCall() },
                        modifier = Modifier.testTag("video_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Start Video Call",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Pinned Topic Guard Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clickable { showTopicBannerExpanded = !showTopicBannerExpanded }
                    .testTag("pinned_topic_banner"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Channel Topic Target",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Topic Info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = channel.topic,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (showTopicBannerExpanded) 5 else 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    AnimatedVisibility(visible = showTopicBannerExpanded) {
                        Column(modifier = Modifier.padding(top = 6.dp)) {
                            Text(
                                text = "Enforcement: ${channel.guardLevel.name} • Off-topic messages receive live guidance and AI rewrites to maintain channel focus.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (channel.topicKeywords.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    channel.topicKeywords.forEach { kw ->
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surface
                                        ) {
                                            Text(
                                                text = "#$kw",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Message Bubble List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubbleItem(
                        message = msg,
                        onRelevanceClick = { selectedExplanationMessage = msg }
                    )
                }
            }

            // Live Pre-Send Topic Meter Banner
            AnimatedVisibility(visible = inputText.length >= 6 || isCheckingDraft) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isCheckingDraft -> MaterialTheme.colorScheme.surfaceVariant
                        draftRelevance?.status == DriftStatus.ON_TOPIC -> TopicGreen.copy(alpha = 0.15f)
                        draftRelevance?.status == DriftStatus.DRIFT -> TopicYellow.copy(alpha = 0.15f)
                        else -> TopicRed.copy(alpha = 0.15f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isCheckingDraft) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Gemini checking topic alignment...",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (draftRelevance != null) {
                                val rel = draftRelevance!!
                                val color = when (rel.status) {
                                    DriftStatus.ON_TOPIC -> TopicGreen
                                    DriftStatus.DRIFT -> TopicYellow
                                    DriftStatus.OFF_TOPIC -> TopicRed
                                }
                                Icon(
                                    imageVector = if (rel.status == DriftStatus.ON_TOPIC) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${rel.score}% On-Topic: ${rel.explanation}",
                                    fontSize = 11.sp,
                                    color = color,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (!isCheckingDraft && draftRelevance?.suggestedRewrite != null) {
                            Text(
                                text = "AI Rewrite",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        viewModel.messageInput.value = draftRelevance!!.suggestedRewrite!!
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Message Composer Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Upload / Deep File attachment button
                    IconButton(
                        onClick = { viewModel.showAttachmentSheet.value = true },
                        modifier = Modifier.testTag("attach_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach & Analyze File",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Scan Link button
                    IconButton(
                        onClick = { viewModel.showLinkScannerSheet.value = true },
                        modifier = Modifier.testTag("scan_link_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Scan Link Safety & Topic",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }

                    // Input Text Field
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { viewModel.onMessageInputChange(it) },
                        placeholder = { Text("Discuss #${channel.name}...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("message_input_field"),
                        shape = RoundedCornerShape(20.dp),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Send Button
                    IconButton(
                        onClick = { viewModel.sendMessage() },
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                            .testTag("send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Message",
                            tint = if (inputText.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Modal Sheet: Deep File Upload & Inspection
    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.showAttachmentSheet.value = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Secure File Upload & Deep Gemini Scan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Files are audited with Gemini 3.5 Flash for code vulnerabilities, topic alignment, and executive summaries.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                UploadOptionCard(
                    icon = Icons.Default.Code,
                    title = "Kotlin Source Code (StateBuffer.kt)",
                    subtitle = "Audits coroutines, memory leaks, and Compose state hoisting.",
                    onClick = { viewModel.uploadSampleFile("kotlin_code") }
                )

                Spacer(modifier = Modifier.height(8.dp))

                UploadOptionCard(
                    icon = Icons.Default.Description,
                    title = "System Architecture Spec (.md)",
                    subtitle = "Zero-trust tokens, edge AI latency SLA, and TLS rules.",
                    onClick = { viewModel.uploadSampleFile("tech_spec") }
                )

                Spacer(modifier = Modifier.height(8.dp))

                UploadOptionCard(
                    icon = Icons.Default.Image,
                    title = "UI Wireframe Mockup (PNG)",
                    subtitle = "Visual design tokens, contrast ratios, and layout fit.",
                    onClick = { viewModel.uploadSampleFile("image_mockup") }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Modal Sheet: Link Scanner & Topic Match
    if (showLinkScannerSheet) {
        var inputUrl by remember { mutableStateOf("https://developer.android.com/reference/kotlin/androidx/compose/runtime/package-summary") }
        val isScanning by viewModel.isScanningUrl.collectAsState()
        val scannedResult by viewModel.linkScanResult.collectAsState()

        ModalBottomSheet(
            onDismissRequest = { viewModel.showLinkScannerSheet.value = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Gemini Link Safety & Topic Scanner",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Validates destination domain trust, detects malicious redirects, and verifies topic match with #${channel.name}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    label = { Text("URL to Scan") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { viewModel.scanUrl(inputUrl) },
                    enabled = inputUrl.isNotBlank() && !isScanning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Auditing Domain & Content...")
                    } else {
                        Icon(imageVector = Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan Link with Gemini")
                    }
                }

                if (scannedResult != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinkPreviewCard(link = scannedResult!!)
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.shareScannedLinkIntoChat() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = TopicGreen)
                    ) {
                        Text("Post Verified Link to Channel")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Strict Mode Off-Topic Warning Dialog
    if (showStrictWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissStrictWarning() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = TopicRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Strict Topic Guard Active")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This channel enforces strict relevance to: \"${channel.topic}\".",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!pendingRewrite.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Gemini Suggested Re-anchor:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = pendingRewrite!!,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.applyRewriteAndSend() },
                    enabled = !pendingRewrite.isNullOrBlank()
                ) {
                    Text("Use AI Rewrite")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.dismissStrictWarning() }) {
                        Text("Edit")
                    }
                    TextButton(onClick = { viewModel.bypassStrictWarningAndSend() }) {
                        Text("Send Anyway", color = TopicRed)
                    }
                }
            }
        )
    }

    // Detail Dialog for Topic Relevance Explanation
    if (selectedExplanationMessage != null) {
        val rel = selectedExplanationMessage?.relevance
        AlertDialog(
            onDismissRequest = { selectedExplanationMessage = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gemini Topic Audit")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (rel != null) {
                        Text(
                            text = "Score: ${rel.score}% • Status: ${rel.status.name}",
                            fontWeight = FontWeight.Bold,
                            color = when (rel.status) {
                                DriftStatus.ON_TOPIC -> TopicGreen
                                DriftStatus.DRIFT -> TopicYellow
                                DriftStatus.OFF_TOPIC -> TopicRed
                            }
                        )
                        Text(
                            text = rel.explanation,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (rel.keyConcepts.isNotEmpty()) {
                            Text(
                                text = "Identified Key Concepts: ${rel.keyConcepts.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedExplanationMessage = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun MessageBubbleItem(
    message: Message,
    onRelevanceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMe = message.isFromMe

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("message_bubble_${message.id}"),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        if (!isMe) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                UserAvatar(user = message.sender, size = 20, showOnlineDot = false)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = message.sender.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (message.isFiltered) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = TopicRed.copy(alpha = 0.15f)),
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = TopicRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = message.filterReason ?: "Message filtered by content policy",
                        color = TopicRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            Card(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMe) 16.dp else 4.dp,
                    bottomEnd = if (isMe) 4.dp else 16.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.content,
                        color = if (isMe) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // Link Preview if attached
                    if (message.linkAnalysis != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinkPreviewCard(link = message.linkAnalysis)
                    }

                    // File Card if attached
                    if (message.fileAttachment != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FileAttachmentCard(file = message.fileAttachment)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Topic Relevance Badge
                        if (message.relevance != null) {
                            TopicRelevanceChip(
                                relevance = message.relevance,
                                onClick = onRelevanceClick
                            )
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        if (isMe) {
                            Text(
                                text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UploadOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
