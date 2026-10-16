package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.api.GeminiClient
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AgendaPoint
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
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "ChatRepository"

class ChatRepository(
    private val db: FirebaseFirestore,
    private val scope: CoroutineScope
) {
    // Secondary constructor resolving custom database ID from string resources
    constructor(context: Context, scope: CoroutineScope) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        scope
    )

    private val auth: FirebaseAuth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private val friendAlex = User("u_1", "Marcus Vance", "@marcusv", 0xFF0EA5E9, true, "Reviewing Kotlin 2.2")
    private val friendMaya = User("u_2", "Maya Lin", "@mayalin", 0xFFEC4899, true, "AI Research Lab")
    private val friendJordan = User("u_3", "Jordan Reed", "@jordanr", 0xFF10B981, true, "Audio DSP & Video")
    private val friendSophia = User("u_4", "Sophia Zhang", "@sophiaz", 0xFFF59E0B, true, "Design Systems Lead")
    private val friendLiam = User("u_5", "Liam Patel", "@liamp", 0xFF8B5CF6, false, "Offline")

    val friendsList = listOf(friendAlex, friendMaya, friendJordan, friendSophia, friendLiam)

    private val _currentUser = MutableStateFlow(buildCurrentUser())
    val currentUserState: StateFlow<User> = _currentUser.asStateFlow()

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _activeChannelId = MutableStateFlow<String>("")
    val activeChannelId: StateFlow<String> = _activeChannelId.asStateFlow()

    private val _messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    val messages: StateFlow<Map<String, List<Message>>> = _messages.asStateFlow()

    private val _filterConfig = MutableStateFlow(ContentFilterConfig())
    val filterConfig: StateFlow<ContentFilterConfig> = _filterConfig.asStateFlow()

    private val _callState = MutableStateFlow(CallState())
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private var channelsListenerRegistration: ListenerRegistration? = null
    private var messagesListenerRegistration: ListenerRegistration? = null

    init {
        syncUserProfile()
        listenToChannels()
    }

    private fun buildCurrentUser(): User {
        val fbUser = auth.currentUser
        return if (fbUser != null) {
            val emailPrefix = fbUser.email?.substringBefore("@") ?: "user"
            User(
                id = fbUser.uid,
                name = fbUser.displayName ?: "Google User",
                handle = "@$emailPrefix",
                avatarColor = 0xFF6366F1,
                isOnline = true,
                statusMessage = "Authenticated Google Account"
            )
        } else {
            User(
                id = "guest_pending",
                name = "Google User",
                handle = "@user",
                avatarColor = 0xFF6366F1,
                isOnline = true,
                statusMessage = "Authenticating..."
            )
        }
    }

    fun syncUserProfile() {
        val fbUser = auth.currentUser ?: return
        _currentUser.value = buildCurrentUser()

        val userDoc = db.collection("users").document(fbUser.uid)
        val data = mapOf(
            "id" to fbUser.uid,
            "email" to (fbUser.email ?: ""),
            "displayName" to (fbUser.displayName ?: "Google User"),
            "photoUrl" to (fbUser.photoUrl?.toString() ?: ""),
            "lastLoginAt" to FieldValue.serverTimestamp()
        )
        userDoc.set(data)
            .addOnFailureListener { e ->
                handleFirestoreError(e, OperationType.WRITE, userDoc.path)
            }
    }

    private fun listenToChannels() {
        val channelsRef = db.collection("channels")
        channelsListenerRegistration?.remove()
        channelsListenerRegistration = channelsRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, channelsRef.path)
                return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
                val list = snapshot.documents.mapNotNull { doc ->
                    try {
                        val id = doc.id
                        val name = doc.getString("name") ?: "general"
                        val isDm = doc.getBoolean("isDirectMessage") ?: false
                        val topic = doc.getString("topic") ?: "General Technical Discussion"
                        val guardStr = doc.getString("guardLevel") ?: "BALANCED"
                        val guard = try { GuardLevel.valueOf(guardStr) } catch (e: Exception) { GuardLevel.BALANCED }
                        val lastMsg = doc.getString("lastMessageText") ?: ""
                        val ts = doc.getTimestamp("lastMessageTimestamp")?.toDate()?.time ?: System.currentTimeMillis()

                        val keywords = (doc.get("topicKeywords") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

                        Channel(
                            id = id,
                            name = name,
                            isDirectMessage = isDm,
                            topic = topic,
                            topicKeywords = keywords,
                            participants = listOf(_currentUser.value, friendAlex, friendMaya),
                            guardLevel = guard,
                            unreadCount = 0,
                            lastMessageText = lastMsg,
                            lastMessageTimestamp = ts
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing channel document ${doc.id}", e)
                        null
                    }
                }
                _channels.value = list
                if (_activeChannelId.value.isEmpty() && list.isNotEmpty()) {
                    selectChannel(list.first().id)
                }
            } else {
                // Seed initial channels if database is freshly provisioned
                seedInitialChannels()
            }
        }
    }

    private fun seedInitialChannels() {
        val uid = auth.currentUser?.uid ?: return

        val seedList = listOf(
            Channel(
                id = "c_compose_ai",
                name = "android-dev-ai",
                isDirectMessage = false,
                topic = "Jetpack Compose architecture, state hoisting, animations & Gemini API integration",
                topicKeywords = listOf("compose", "kotlin", "gemini", "viewmodel", "state", "coroutine", "ui"),
                participants = listOf(_currentUser.value, friendAlex, friendMaya, friendJordan),
                guardLevel = GuardLevel.BALANCED,
                lastMessageText = "Verified the Compose recomposition benchmarks with the new compiler.",
                lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 12
            ),
            Channel(
                id = "c_quantum",
                name = "quantum-computing",
                isDirectMessage = false,
                topic = "Quantum algorithms, qubits, cryogenic hardware, and post-quantum encryption",
                topicKeywords = listOf("quantum", "qubit", "shor", "grover", "superconducting", "gate"),
                participants = listOf(_currentUser.value, friendMaya, friendLiam),
                guardLevel = GuardLevel.STRICT,
                lastMessageText = "Has anyone simulated error correction circuits using surface codes?",
                lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 45
            ),
            Channel(
                id = "c_cybersec",
                name = "cyber-defense",
                isDirectMessage = false,
                topic = "Zero-trust networks, application vulnerabilities, TLS, and automated link scanning",
                topicKeywords = listOf("security", "audit", "tls", "zero-trust", "vulnerability", "exploit", "cve"),
                participants = listOf(_currentUser.value, friendAlex, friendSophia),
                guardLevel = GuardLevel.STRICT,
                lastMessageText = "The automated link scanner caught a suspicious redirected payload.",
                lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 120
            ),
            Channel(
                id = "dm_maya",
                name = "Maya Lin",
                isDirectMessage = true,
                topic = "Generative AI multi-agent orchestration and multimodal prompt engineering",
                topicKeywords = listOf("gemini", "agents", "multimodal", "tokens", "reasoning"),
                participants = listOf(_currentUser.value, friendMaya),
                guardLevel = GuardLevel.BALANCED,
                lastMessageText = "I'm testing the new streaming responses for real-time speech.",
                lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 5
            )
        )

        for (ch in seedList) {
            val doc = db.collection("channels").document(ch.id)
            val data = mapOf(
                "id" to ch.id,
                "name" to ch.name,
                "isDirectMessage" to ch.isDirectMessage,
                "topic" to ch.topic,
                "topicKeywords" to ch.topicKeywords,
                "guardLevel" to ch.guardLevel.name,
                "creatorId" to uid,
                "memberIds" to listOf(uid, friendAlex.id, friendMaya.id),
                "lastMessageText" to ch.lastMessageText,
                "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp()
            )
            doc.set(data).addOnFailureListener { e ->
                handleFirestoreError(e, OperationType.CREATE, doc.path)
            }
        }
    }

    fun selectChannel(channelId: String) {
        _activeChannelId.value = channelId
        listenToMessages(channelId)
    }

    private fun listenToMessages(channelId: String) {
        val messagesRef = db.collection("channels").document(channelId).collection("messages")
        messagesListenerRegistration?.remove()
        messagesListenerRegistration = messagesRef
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, messagesRef.path)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val uid = auth.currentUser?.uid ?: ""
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            val senderId = doc.getString("senderId") ?: ""
                            val senderName = doc.getString("senderName") ?: "User"
                            val content = doc.getString("content") ?: ""
                            val ts = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()

                            val relScore = doc.getLong("relevanceScore")?.toInt()
                            val relStatus = doc.getString("relevanceStatus")
                            val relExpl = doc.getString("relevanceExplanation")

                            val relevance = if (relScore != null && relStatus != null) {
                                TopicRelevance(
                                    score = relScore,
                                    status = try { DriftStatus.valueOf(relStatus) } catch (e: Exception) { DriftStatus.ON_TOPIC },
                                    explanation = relExpl ?: "Evaluated by Gemini Topic Guard"
                                )
                            } else null

                            val linkUrl = doc.getString("linkUrl")
                            val linkAnalysis = if (linkUrl != null) {
                                LinkAnalysis(
                                    url = linkUrl,
                                    title = doc.getString("linkTitle") ?: "Shared Resource",
                                    domain = doc.getString("linkDomain") ?: "web",
                                    trustScore = doc.getLong("linkTrustScore")?.toInt() ?: 90,
                                    isSafe = doc.getBoolean("linkSafe") ?: true,
                                    summary = doc.getString("linkSummary") ?: "Scanned web reference.",
                                    topicMatchScore = doc.getLong("linkTopicScore")?.toInt() ?: 85
                                )
                            } else null

                            val fileName = doc.getString("fileName")
                            val fileAttachment = if (fileName != null) {
                                FileAttachment(
                                    id = doc.id,
                                    fileName = fileName,
                                    fileType = doc.getString("fileType") ?: "doc",
                                    fileSizeBytes = doc.getLong("fileSizeBytes") ?: 2048L,
                                    contentSummary = doc.getString("fileSummary") ?: "Scanned attachment.",
                                    safetyStatus = doc.getString("fileSafetyStatus") ?: "Verified Safe",
                                    topicRelevanceScore = doc.getLong("fileRelevanceScore")?.toInt() ?: 90
                                )
                            } else null

                            val senderUser = if (senderId == uid) {
                                _currentUser.value
                            } else {
                                friendsList.find { it.id == senderId }
                                    ?: User(senderId, senderName, "@$senderName", 0xFF0EA5E9)
                            }

                            Message(
                                id = doc.id,
                                channelId = channelId,
                                sender = senderUser,
                                content = content,
                                timestamp = ts,
                                relevance = relevance,
                                linkAnalysis = linkAnalysis,
                                fileAttachment = fileAttachment,
                                isFromMe = (senderId == uid)
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Error mapping message ${doc.id}", e)
                            null
                        }
                    }

                    val currentMap = _messages.value.toMutableMap()
                    currentMap[channelId] = list
                    _messages.value = currentMap
                }
            }
    }

    suspend fun verifyDraftMessage(channelId: String, text: String): TopicRelevance {
        val channel = _channels.value.find { it.id == channelId }
        val topic = channel?.topic ?: "General Discussion"
        return GeminiClient.verifyTopicRelevance(topic, text)
    }

    suspend fun scanUrl(url: String, channelId: String): LinkAnalysis {
        val channel = _channels.value.find { it.id == channelId }
        val topic = channel?.topic ?: "General Discussion"
        return GeminiClient.scanLink(url, topic)
    }

    fun sendMessage(
        channelId: String,
        content: String,
        linkAnalysis: LinkAnalysis? = null,
        fileAttachment: FileAttachment? = null,
        overrideRelevance: TopicRelevance? = null
    ) {
        val uid = requireUserId()
        val channel = _channels.value.find { it.id == channelId } ?: return
        val currentConfig = _filterConfig.value

        // Check content filter
        val (isBlocked, blockReason) = GeminiClient.checkContentFilters(content, currentConfig.blockedKeywords)
        if (isBlocked) {
            val blockedMsg = Message(
                id = UUID.randomUUID().toString(),
                channelId = channelId,
                sender = _currentUser.value,
                content = content,
                isFromMe = true,
                isFiltered = true,
                filterReason = blockReason
            )
            val currentMap = _messages.value.toMutableMap()
            val list = (currentMap[channelId] ?: emptyList()) + blockedMsg
            currentMap[channelId] = list
            _messages.value = currentMap
            return
        }

        scope.launch {
            val relevance = overrideRelevance ?: if (channel.guardLevel != GuardLevel.OFF) {
                GeminiClient.verifyTopicRelevance(channel.topic, content)
            } else null

            val link = linkAnalysis ?: extractAndScanLink(content, channel.topic)

            // Persist to Firestore
            val msgData = mutableMapOf<String, Any>(
                "channelId" to channelId,
                "senderId" to uid,
                "senderName" to _currentUser.value.name,
                "content" to content,
                "createdAt" to FieldValue.serverTimestamp()
            )

            if (relevance != null) {
                msgData["relevanceScore"] = relevance.score
                msgData["relevanceStatus"] = relevance.status.name
                msgData["relevanceExplanation"] = relevance.explanation
            }

            if (link != null) {
                msgData["linkUrl"] = link.url
                msgData["linkTitle"] = link.title
                msgData["linkDomain"] = link.domain
                msgData["linkTrustScore"] = link.trustScore
                msgData["linkSafe"] = link.isSafe
                msgData["linkSummary"] = link.summary
                msgData["linkTopicScore"] = link.topicMatchScore
            }

            if (fileAttachment != null) {
                msgData["fileName"] = fileAttachment.fileName
                msgData["fileType"] = fileAttachment.fileType
                msgData["fileSizeBytes"] = fileAttachment.fileSizeBytes
                msgData["fileSummary"] = fileAttachment.contentSummary
                msgData["fileSafetyStatus"] = fileAttachment.safetyStatus
                msgData["fileRelevanceScore"] = fileAttachment.topicRelevanceScore
            }

            val messagesCol = db.collection("channels").document(channelId).collection("messages")
            messagesCol.add(msgData)
                .addOnFailureListener { e ->
                    handleFirestoreError(e, OperationType.CREATE, messagesCol.path)
                }

            // Update channel last message in Firestore
            val channelDoc = db.collection("channels").document(channelId)
            channelDoc.update(
                mapOf(
                    "lastMessageText" to content,
                    "lastMessageTimestamp" to FieldValue.serverTimestamp()
                )
            ).addOnFailureListener { e ->
                handleFirestoreError(e, OperationType.UPDATE, channelDoc.path)
            }

            // Simulated friend interaction
            delay(1200)
            generateFriendResponse(channel, content)
        }
    }

    private suspend fun extractAndScanLink(content: String, topic: String): LinkAnalysis? {
        val urlRegex = Regex("https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\.&]+")
        val match = urlRegex.find(content)?.value
        return if (match != null) {
            GeminiClient.scanLink(match, topic)
        } else null
    }

    fun uploadAndAnalyzeFile(
        channelId: String,
        fileName: String,
        fileType: String,
        snippetContent: String
    ) {
        val channel = _channels.value.find { it.id == channelId } ?: return
        scope.launch {
            val attachment = GeminiClient.analyzeDeepFile(fileName, fileType, snippetContent, channel.topic)
            sendMessage(
                channelId = channelId,
                content = "Attached and audited file: $fileName (${attachment.safetyStatus})",
                fileAttachment = attachment,
                overrideRelevance = TopicRelevance(
                    score = attachment.topicRelevanceScore,
                    status = if (attachment.topicRelevanceScore >= 70) DriftStatus.ON_TOPIC else DriftStatus.DRIFT,
                    explanation = "File attachment validated: ${attachment.contentSummary}"
                )
            )
        }
    }

    private suspend fun generateFriendResponse(channel: Channel, triggerText: String) {
        val friend = channel.participants.firstOrNull { it.id != auth.currentUser?.uid } ?: friendAlex
        val triggerLower = triggerText.lowercase()

        val responseText = when {
            channel.name.contains("compose") || triggerLower.contains("compose") -> {
                "Great point on this. In Compose, maintaining unidirectional data flow and hoisting state prevents redundant recompositions."
            }
            channel.name.contains("quantum") || triggerLower.contains("quantum") -> {
                "Exactly! Surface code thresholds demand physical qubit fidelities above 99.5% for fault-tolerant logical qubits."
            }
            channel.name.contains("cyber") || triggerLower.contains("security") -> {
                "Agreed. Zero-trust verification at the proxy layer with link safety hashing guarantees untrusted payloads never touch client storage."
            }
            triggerLower.contains("?") -> {
                "Here is my take based on our ${channel.topic} specs: we should test the boundary conditions first."
            }
            else -> {
                "Solid update on ${channel.topic.take(30)}... Let's document this in the team notes."
            }
        }

        val relevance = GeminiClient.verifyTopicRelevance(channel.topic, responseText)

        val friendMsg = Message(
            id = UUID.randomUUID().toString(),
            channelId = channel.id,
            sender = friend,
            content = responseText,
            isFromMe = false,
            relevance = relevance
        )

        val currentMap = _messages.value.toMutableMap()
        val list = (currentMap[channel.id] ?: emptyList()) + friendMsg
        currentMap[channel.id] = list
        _messages.value = currentMap
    }

    fun createChannel(
        name: String,
        topic: String,
        keywords: List<String>,
        guardLevel: GuardLevel,
        isDirectMessage: Boolean,
        selectedFriendId: String? = null
    ) {
        val uid = requireUserId()
        val newId = "c_" + UUID.randomUUID().toString().take(8)

        val data = mapOf(
            "id" to newId,
            "name" to name,
            "isDirectMessage" to isDirectMessage,
            "topic" to topic,
            "topicKeywords" to keywords,
            "guardLevel" to guardLevel.name,
            "creatorId" to uid,
            "memberIds" to listOf(uid),
            "lastMessageText" to "Channel created with topic: $topic",
            "lastMessageTimestamp" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp()
        )

        val docRef = db.collection("channels").document(newId)
        docRef.set(data).addOnFailureListener { e ->
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
        }

        _activeChannelId.value = newId
    }

    fun updateFilterConfig(config: ContentFilterConfig) {
        _filterConfig.value = config
    }

    // Voice & Video Call Methods
    fun startCall(channelId: String, isVideo: Boolean) {
        val channel = _channels.value.find { it.id == channelId } ?: return
        val agenda = listOf(
            AgendaPoint("ag_1", "1. Review topic requirements for ${channel.name}"),
            AgendaPoint("ag_2", "2. Architecture & security audit"),
            AgendaPoint("ag_3", "3. Implementation milestones & QA")
        )

        _callState.value = CallState(
            isInCall = true,
            channelId = channelId,
            channelName = channel.name,
            isVideo = isVideo,
            participants = channel.participants,
            isMuted = false,
            isVideoEnabled = isVideo,
            isSpeakerOn = true,
            isFrontCamera = true,
            durationSeconds = 0,
            topicName = channel.topic,
            liveComplianceScore = 98,
            agendaPoints = agenda
        )

        scope.launch {
            val utterances = mutableListOf<String>()
            while (_callState.value.isInCall) {
                delay(1000)
                val current = _callState.value
                val newSeconds = current.durationSeconds + 1

                var newAlert = current.liveDriftAlert
                var newScore = current.liveComplianceScore
                if (newSeconds % 8 == 0) {
                    val sampleUtterance = if (newSeconds == 16) {
                        "Hey did anyone see the football match last night?"
                    } else if (newSeconds == 24) {
                        "Oh yeah, totally crazy play in the fourth quarter."
                    } else {
                        "Let's focus on our target topic: ${channel.topic}"
                    }
                    utterances.add(sampleUtterance)
                    val (score, alert) = GeminiClient.analyzeCallTopicAdherence(channel.topic, utterances)
                    newScore = score
                    newAlert = alert
                }

                _callState.value = current.copy(
                    durationSeconds = newSeconds,
                    liveComplianceScore = newScore,
                    liveDriftAlert = newAlert
                )
            }
        }
    }

    fun endCall() {
        _callState.value = CallState(isInCall = false)
    }

    fun toggleMute() {
        _callState.value = _callState.value.copy(isMuted = !_callState.value.isMuted)
    }

    fun toggleVideo() {
        _callState.value = _callState.value.copy(isVideoEnabled = !_callState.value.isVideoEnabled)
    }

    fun toggleCameraFacing() {
        _callState.value = _callState.value.copy(isFrontCamera = !_callState.value.isFrontCamera)
    }

    fun toggleSpeaker() {
        _callState.value = _callState.value.copy(isSpeakerOn = !_callState.value.isSpeakerOn)
    }

    fun markAgendaItem(pointId: String) {
        val current = _callState.value
        val updated = current.agendaPoints.map {
            if (it.id == pointId) it.copy(isCovered = !it.isCovered) else it
        }
        _callState.value = current.copy(agendaPoints = updated)
    }

    fun generateMeetingSummary() {
        val current = _callState.value
        val summary = """
            📋 Call Minutes (${current.channelName})
            • Topic Focus: ${current.topicName}
            • Overall Topic Adherence: ${current.liveComplianceScore}%
            • Covered: ${current.agendaPoints.filter { it.isCovered }.size} of ${current.agendaPoints.size} agenda items.
            • Gemini AI Assessment: Discussion remained structured with high technical precision.
        """.trimIndent()
        _callState.value = current.copy(inCallMinutesSummary = summary)
    }
}
