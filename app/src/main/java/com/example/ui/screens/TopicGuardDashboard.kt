package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.GeminiClient
import com.example.data.model.ContentFilterConfig
import com.example.data.model.FileAttachment
import com.example.data.model.LinkAnalysis
import com.example.ui.components.FileAttachmentCard
import com.example.ui.components.LinkPreviewCard
import com.example.ui.theme.TopicGreen
import com.example.ui.theme.TopicRed
import com.example.ui.theme.TopicYellow
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicGuardDashboard(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val filterConfig by viewModel.filterConfig.collectAsState()
    val scope = rememberCoroutineScope()

    var newKeyword by remember { mutableStateOf("") }

    // Standalone Scanner states
    var testUrl by remember { mutableStateOf("https://kotlinlang.org/docs/coroutines-overview.html") }
    var scannedLink by remember { mutableStateOf<LinkAnalysis?>(null) }
    var isScanningLink by remember { mutableStateOf(false) }

    // Standalone File states
    var analyzedFile by remember { mutableStateOf<FileAttachment?>(null) }
    var isAnalyzingFile by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Topic Shield",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Topic Guard & Shield",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Overview Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TopicPulse AI Protection",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = TopicGreen.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    color = TopicGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Powered by Gemini 3.5 Flash to automatically verify message relevance, audit shared links for safety, scan uploaded code/files for vulnerabilities, and enforce flexible content rules.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Metric badges grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricPill(label = "Topic Focus", value = "94%", color = TopicGreen)
                            MetricPill(label = "Scanned Links", value = "18 Safe", color = MaterialTheme.colorScheme.primary)
                            MetricPill(label = "Audited Files", value = "9 Clean", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }

            // Flexible Content Filtering Configuration
            item {
                Text(
                    text = "Flexible Content Filtering",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        FilterToggleRow(
                            title = "Block Toxicity & Harassment",
                            description = "Filters offensive or hostile messaging across channels.",
                            checked = filterConfig.blockToxicity,
                            onCheckedChange = {
                                viewModel.updateFilters(filterConfig.copy(blockToxicity = it))
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        FilterToggleRow(
                            title = "Mask Sensitive Data & PII",
                            description = "Detects credit card numbers, secret keys, and personal identifiers.",
                            checked = filterConfig.maskPii,
                            onCheckedChange = {
                                viewModel.updateFilters(filterConfig.copy(maskPii = it))
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        FilterToggleRow(
                            title = "Commercial Spam & Phishing Blocker",
                            description = "Prevents promotional spam, crypto pumps, and deceptive URLs.",
                            checked = filterConfig.filterSpam,
                            onCheckedChange = {
                                viewModel.updateFilters(filterConfig.copy(filterSpam = it))
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        FilterToggleRow(
                            title = "Strict Topic Relevance Gating",
                            description = "Requires messages to achieve >=60% relevance or require confirmation.",
                            checked = filterConfig.strictRelevanceGate,
                            onCheckedChange = {
                                viewModel.updateFilters(filterConfig.copy(strictRelevanceGate = it))
                            }
                        )
                    }
                }
            }

            // Custom Keyword Blocklist
            item {
                Text(
                    text = "Custom Forbidden Keywords",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newKeyword,
                                onValueChange = { newKeyword = it },
                                placeholder = { Text("Add blocked term (e.g. spoiler)") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newKeyword.isNotBlank()) {
                                        val updated = filterConfig.blockedKeywords + newKeyword.trim()
                                        viewModel.updateFilters(filterConfig.copy(blockedKeywords = updated))
                                        newKeyword = ""
                                    }
                                },
                                enabled = newKeyword.isNotBlank(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            filterConfig.blockedKeywords.forEach { kw ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = kw, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable {
                                                    val updated = filterConfig.blockedKeywords - kw
                                                    viewModel.updateFilters(filterConfig.copy(blockedKeywords = updated))
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Standalone Interactive Link Scanner Tool
            item {
                Text(
                    text = "Interactive Link Inspector Tool",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Test any URL with Gemini for trust score, malware risk, and topic match.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = testUrl,
                            onValueChange = { testUrl = it },
                            label = { Text("URL to Inspect") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    isScanningLink = true
                                    scannedLink = GeminiClient.scanLink(testUrl, "Kotlin & Jetpack Compose Development")
                                    isScanningLink = false
                                }
                            },
                            enabled = testUrl.isNotBlank() && !isScanningLink,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isScanningLink) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Auditing Link Security...")
                            } else {
                                Icon(imageVector = Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Inspect Link with Gemini")
                            }
                        }

                        if (scannedLink != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            LinkPreviewCard(link = scannedLink!!)
                        }
                    }
                }
            }

            // Standalone Interactive Deep File Analysis Tool
            item {
                Text(
                    text = "Interactive Deep File Analyzer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Gemini inspects source code, PDFs, or design specs for security integrity and topic fit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    isAnalyzingFile = true
                                    val sampleCode = """
                                        // Auditing security token generation
                                        fun generateSessionToken(userId: String): String {
                                            val secret = java.security.SecureRandom()
                                            val bytes = ByteArray(32)
                                            secret.nextBytes(bytes)
                                            return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                        }
                                    """.trimIndent()
                                    analyzedFile = GeminiClient.analyzeDeepFile(
                                        fileName = "TokenAuthService.kt",
                                        fileType = "code",
                                        contentSnippet = sampleCode,
                                        channelTopic = "Secure Authentication & Mobile Systems"
                                    )
                                    isAnalyzingFile = false
                                }
                            },
                            enabled = !isAnalyzingFile,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isAnalyzingFile) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyzing Code Semantics & Security...")
                            } else {
                                Icon(imageVector = Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Audit Sample Code (TokenAuthService.kt)")
                            }
                        }

                        if (analyzedFile != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            FileAttachmentCard(file = analyzedFile!!)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun MetricPill(
    label: String,
    value: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FilterToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
