package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CallState
import com.example.data.model.Channel
import com.example.data.model.ContentFilterConfig
import com.example.data.model.DriftStatus
import com.example.data.model.FileAttachment
import com.example.data.model.GuardLevel
import com.example.data.model.LinkAnalysis
import com.example.data.model.Message
import com.example.data.model.TopicRelevance
import com.example.data.model.User
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore

enum class AppTab {
    CHATS,
    SHIELD, // Topic Guard & Content Filter Dashboard
}

class ChatViewModel(private val db: FirebaseFirestore) : ViewModel() {
    val repository = ChatRepository(db, viewModelScope)

    val channels: StateFlow<List<Channel>> = repository.channels
    val activeChannelId: StateFlow<String> = repository.activeChannelId
    val filterConfig: StateFlow<ContentFilterConfig> = repository.filterConfig
    val callState: StateFlow<CallState> = repository.callState
    val currentUserState: StateFlow<User> = repository.currentUserState
    val friendsList: List<User> = repository.friendsList

    fun refreshUserSession() {
        repository.syncUserProfile()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = FirebaseFirestore.getInstance(
                context.applicationContext.getString(R.string.firestore_database_id)
            )
            return ChatViewModel(db) as T
        }
    }

    private val _selectedTab = MutableStateFlow(AppTab.CHATS)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _isInsideChat = MutableStateFlow(false)
    val isInsideChat: StateFlow<Boolean> = _isInsideChat.asStateFlow()

    // Active Channel derived state
    val activeChannel: StateFlow<Channel?> = combine(channels, activeChannelId) { list, id ->
        list.find { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Active Messages derived state
    val activeMessages: StateFlow<List<Message>> = combine(repository.messages, activeChannelId) { map, id ->
        map[id] ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Composer Input State
    val messageInput = MutableStateFlow("")
    val draftRelevance = MutableStateFlow<TopicRelevance?>(null)
    val isCheckingDraftRelevance = MutableStateFlow(false)

    // Pre-send Warning Dialog
    val pendingOffTopicDraft = MutableStateFlow<String?>(null)
    val pendingRewriteSuggestion = MutableStateFlow<String?>(null)
    val showStrictWarning = MutableStateFlow(false)

    // Attachment & Link Scanner Sheets
    val showAttachmentSheet = MutableStateFlow(false)
    val showLinkScannerSheet = MutableStateFlow(false)
    val showCreateChannelDialog = MutableStateFlow(false)

    // Link Scanner standalone state
    val linkScannerUrl = MutableStateFlow("")
    val isScanningUrl = MutableStateFlow(false)
    val linkScanResult = MutableStateFlow<LinkAnalysis?>(null)

    // File Inspector standalone state
    val isAnalyzingFile = MutableStateFlow(false)
    val fileAnalysisResult = MutableStateFlow<FileAttachment?>(null)

    private var draftDebounceJob: Job? = null

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    fun openChat(channelId: String) {
        repository.selectChannel(channelId)
        _isInsideChat.value = true
        messageInput.value = ""
        draftRelevance.value = null
    }

    fun closeChat() {
        _isInsideChat.value = false
    }

    fun onMessageInputChange(newText: String) {
        messageInput.value = newText
        draftDebounceJob?.cancel()

        if (newText.length < 6) {
            draftRelevance.value = null
            isCheckingDraftRelevance.value = false
            return
        }

        val channel = activeChannel.value ?: return
        if (channel.guardLevel == GuardLevel.OFF) {
            draftRelevance.value = null
            return
        }

        draftDebounceJob = viewModelScope.launch {
            delay(650) // Debounce typing
            isCheckingDraftRelevance.value = true
            val relevance = repository.verifyDraftMessage(channel.id, newText)
            draftRelevance.value = relevance
            isCheckingDraftRelevance.value = false
        }
    }

    fun sendMessage() {
        val text = messageInput.value.trim()
        if (text.isEmpty()) return

        val channel = activeChannel.value ?: return
        val currentRelevance = draftRelevance.value

        // Check if strict guard warns against sending
        if (channel.guardLevel == GuardLevel.STRICT && currentRelevance != null && currentRelevance.status == DriftStatus.OFF_TOPIC) {
            pendingOffTopicDraft.value = text
            pendingRewriteSuggestion.value = currentRelevance.suggestedRewrite
            showStrictWarning.value = true
            return
        }

        repository.sendMessage(channel.id, text, overrideRelevance = currentRelevance)
        messageInput.value = ""
        draftRelevance.value = null
    }

    fun bypassStrictWarningAndSend() {
        val channel = activeChannel.value ?: return
        val text = pendingOffTopicDraft.value ?: return
        repository.sendMessage(channel.id, text)
        showStrictWarning.value = false
        pendingOffTopicDraft.value = null
        messageInput.value = ""
        draftRelevance.value = null
    }

    fun applyRewriteAndSend() {
        val channel = activeChannel.value ?: return
        val rewrite = pendingRewriteSuggestion.value ?: return
        repository.sendMessage(channel.id, rewrite)
        showStrictWarning.value = false
        pendingOffTopicDraft.value = null
        pendingRewriteSuggestion.value = null
        messageInput.value = ""
        draftRelevance.value = null
    }

    fun dismissStrictWarning() {
        showStrictWarning.value = false
        pendingOffTopicDraft.value = null
    }

    fun scanUrl(url: String) {
        val channel = activeChannel.value ?: return
        if (url.isBlank()) return
        viewModelScope.launch {
            isScanningUrl.value = true
            val result = repository.scanUrl(url, channel.id)
            linkScanResult.value = result
            isScanningUrl.value = false
        }
    }

    fun shareScannedLinkIntoChat() {
        val channel = activeChannel.value ?: return
        val link = linkScanResult.value ?: return
        repository.sendMessage(
            channelId = channel.id,
            content = "Sharing verified resource: ${link.title} - ${link.url}",
            linkAnalysis = link
        )
        linkScanResult.value = null
        showLinkScannerSheet.value = false
    }

    fun uploadSampleFile(type: String) {
        val channel = activeChannel.value ?: return
        showAttachmentSheet.value = false

        when (type) {
            "kotlin_code" -> {
                val code = """
                    package com.example.performance
                    // Optimizes memory allocation for high throughput state streams
                    class StateBuffer(private val capacity: Int = 1024) {
                        private val queue = java.util.ArrayDeque<ByteArray>(capacity)
                        fun push(data: ByteArray) {
                            if (queue.size >= capacity) queue.poll()
                            queue.offer(data)
                        }
                    }
                """.trimIndent()
                repository.uploadAndAnalyzeFile(channel.id, "StateBuffer.kt", "code", code)
            }
            "tech_spec" -> {
                val spec = """
                    # Architecture Specification v2.4
                    Target SLA: 99.99% availability with edge AI inference under 150ms.
                    Protocols: TLS 1.3, Encrypted gRPC over HTTP/2, Mutual Authentication.
                    Security: Static bytecode verification, zero-trust token signing.
                """.trimIndent()
                repository.uploadAndAnalyzeFile(channel.id, "Architecture_v2.4_Spec.md", "doc", spec)
            }
            "image_mockup" -> {
                val imageNote = """
                    [IMAGE ATTACHMENT: UI Wireframe]
                    Resolution: 1080x2400
                    Components: Real-time topic badge indicator, floating call banner, audio waveform visualizer.
                    Target Contrast: APCA Bronze standard.
                """.trimIndent()
                repository.uploadAndAnalyzeFile(channel.id, "TopicChat_Wireframe_v3.png", "image", imageNote)
            }
        }
    }

    fun createNewChannel(
        name: String,
        topic: String,
        keywords: List<String>,
        guardLevel: GuardLevel,
        isDm: Boolean,
        friendId: String?
    ) {
        repository.createChannel(name, topic, keywords, guardLevel, isDm, friendId)
        showCreateChannelDialog.value = false
    }

    fun updateFilters(config: ContentFilterConfig) {
        repository.updateFilterConfig(config)
    }

    // Call Actions
    fun startVoiceCall() {
        val channel = activeChannel.value ?: return
        repository.startCall(channel.id, isVideo = false)
    }

    fun startVideoCall() {
        val channel = activeChannel.value ?: return
        repository.startCall(channel.id, isVideo = true)
    }

    fun endCall() {
        repository.endCall()
    }

    fun toggleMute() = repository.toggleMute()
    fun toggleVideo() = repository.toggleVideo()
    fun toggleCameraFacing() = repository.toggleCameraFacing()
    fun toggleSpeaker() = repository.toggleSpeaker()
    fun markAgendaItem(id: String) = repository.markAgendaItem(id)
    fun generateMeetingSummary() = repository.generateMeetingSummary()
}
