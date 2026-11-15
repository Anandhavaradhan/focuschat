package com.example.data.repository

import android.util.Base64
import android.util.Log
import com.example.data.api.GeminiClient
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.Channel
import com.example.data.model.ContentFilterConfig
import com.example.data.model.DriftStatus
import com.example.data.model.FileAttachment
import com.example.data.model.GuardLevel
import com.example.data.model.LinkAnalysis
import com.example.data.model.Message
import com.example.data.model.TopicRelevance
import com.example.data.model.User
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "ChatRepository"

/** Largest file that can be attached. Files are kept in Firestore in base64 chunks. */
const val MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024
private const val CHUNK_CHARS = 700_000

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

/**
 * Everything is real Firestore data:
 *  - users/{uid}: everyone who has signed in (the people you can chat with)
 *  - channels/{id}: channels and DMs, visible only to their memberIds
 *  - channels/{id}/messages/{id}
 *  - channels/{id}/files/{id} (+ /chunks/{n}): uploaded attachments
 */
class ChatRepository(
    private val db: FirebaseFirestore,
    private val scope: CoroutineScope
) {
    private val auth: FirebaseAuth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private val _currentUser = MutableStateFlow(buildCurrentUser())
    val currentUserState: StateFlow<User> = _currentUser.asStateFlow()

    // Everyone who has signed in, by id
    private val _users = MutableStateFlow<Map<String, User>>(emptyMap())

    // Everyone except me: the people you can start a DM with or add to a channel
    private val _people = MutableStateFlow<List<User>>(emptyList())
    val people: StateFlow<List<User>> = _people.asStateFlow()

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _activeChannelId = MutableStateFlow("")
    val activeChannelId: StateFlow<String> = _activeChannelId.asStateFlow()

    private val _messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    val messages: StateFlow<Map<String, List<Message>>> = _messages.asStateFlow()

    private val _filterConfig = MutableStateFlow(ContentFilterConfig())
    val filterConfig: StateFlow<ContentFilterConfig> = _filterConfig.asStateFlow()

    // A short message for the user (upload failed, file too big, ...)
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    fun clearNotice() {
        _notice.value = null
    }

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private var usersListener: ListenerRegistration? = null
    private var channelsListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null
    private var channelDocs: List<DocumentSnapshot> = emptyList()

    init {
        syncUserProfile()
        listenToUsers()
        listenToChannels()
    }

    private fun handleOf(email: String?): String =
        "@" + (email?.substringBefore("@")?.lowercase()?.ifBlank { null } ?: "user")

    private fun colorFor(id: String): Long {
        val palette = listOf(0xFF0EA5E9, 0xFFEC4899, 0xFF10B981, 0xFFF59E0B, 0xFF8B5CF6, 0xFF6366F1, 0xFFEF4444, 0xFF14B8A6)
        return palette[(id.hashCode() and 0x7fffffff) % palette.size]
    }

    private fun buildCurrentUser(): User {
        val fbUser = auth.currentUser
        return if (fbUser != null) {
            User(
                id = fbUser.uid,
                name = fbUser.displayName ?: "Google User",
                handle = handleOf(fbUser.email),
                avatarColor = colorFor(fbUser.uid),
                isOnline = true,
                statusMessage = fbUser.email ?: ""
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

    private fun unknownUser(id: String) = User(id, "Former member", "@unknown", 0xFF94A3B8, false, "")

    fun syncUserProfile() {
        val fbUser = auth.currentUser ?: return
        _currentUser.value = buildCurrentUser()

        val userDoc = db.collection("users").document(fbUser.uid)
        val data = mapOf(
            "id" to fbUser.uid,
            "email" to (fbUser.email ?: ""),
            "displayName" to (fbUser.displayName ?: "Google User"),
            "handle" to handleOf(fbUser.email),
            "photoUrl" to (fbUser.photoUrl?.toString() ?: ""),
            "lastLoginAt" to FieldValue.serverTimestamp()
        )
        userDoc.set(data)
            .addOnFailureListener { e ->
                handleFirestoreError(e, OperationType.WRITE, userDoc.path)
            }
    }

    private fun listenToUsers() {
        val ref = db.collection("users")
        usersListener?.remove()
        usersListener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, ref.path)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            val me = auth.currentUser?.uid
            val map = snapshot.documents.associate { doc ->
                val email = doc.getString("email")
                doc.id to User(
                    id = doc.id,
                    name = doc.getString("displayName")?.ifBlank { null } ?: email ?: "User",
                    handle = doc.getString("handle") ?: handleOf(email),
                    avatarColor = colorFor(doc.id),
                    isOnline = false,
                    statusMessage = email ?: ""
                )
            }
            _users.value = map
            _people.value = map.values.filter { it.id != me }.sortedBy { it.name.lowercase() }
            rebuildChannels()
        }
    }

    private fun listenToChannels() {
        val uid = auth.currentUser?.uid ?: return
        val query = db.collection("channels").whereArrayContains("memberIds", uid)
        channelsListener?.remove()
        channelsListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "channels")
                return@addSnapshotListener
            }
            channelDocs = snapshot?.documents ?: emptyList()
            rebuildChannels()
        }
    }

    // Channels need both the channel documents and the people in them; rebuild when either changes.
    private fun rebuildChannels() {
        val me = auth.currentUser?.uid ?: ""
        val users = _users.value
        val list = channelDocs.mapNotNull { doc ->
            try {
                val memberIds = (doc.get("memberIds") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val participants = memberIds.map { id ->
                    if (id == me) _currentUser.value else users[id] ?: unknownUser(id)
                }
                val isDm = doc.getBoolean("isDirectMessage") ?: false
                val other = participants.firstOrNull { it.id != me }
                val guard = try {
                    GuardLevel.valueOf(doc.getString("guardLevel") ?: "BALANCED")
                } catch (e: Exception) {
                    GuardLevel.BALANCED
                }
                Channel(
                    id = doc.id,
                    name = if (isDm) other?.name ?: (doc.getString("name") ?: "Chat") else doc.getString("name") ?: "general",
                    isDirectMessage = isDm,
                    topic = doc.getString("topic") ?: "General Discussion",
                    topicKeywords = (doc.get("topicKeywords") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                    participants = participants,
                    guardLevel = guard,
                    unreadCount = 0,
                    lastMessageText = doc.getString("lastMessageText") ?: "",
                    lastMessageTimestamp = doc.getTimestamp("lastMessageTimestamp")?.toDate()?.time ?: System.currentTimeMillis()
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing channel document ${doc.id}", e)
                null
            }
        }.sortedByDescending { it.lastMessageTimestamp }
        _channels.value = list
        if (_activeChannelId.value.isEmpty() && list.isNotEmpty()) {
            _activeChannelId.value = list.first().id
        }
    }

    fun selectChannel(channelId: String) {
        _activeChannelId.value = channelId
        listenToMessages(channelId)
    }

    private fun listenToMessages(channelId: String) {
        val messagesRef = db.collection("channels").document(channelId).collection("messages")
        messagesListener?.remove()
        messagesListener = messagesRef
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, messagesRef.path)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val uid = auth.currentUser?.uid ?: ""
                val list = snapshot.documents.mapNotNull { doc -> parseMessage(doc, channelId, uid) }
                val currentMap = _messages.value.toMutableMap()
                currentMap[channelId] = list
                _messages.value = currentMap
            }
    }

    private fun parseMessage(doc: DocumentSnapshot, channelId: String, uid: String): Message? = try {
        val senderId = doc.getString("senderId") ?: ""
        val senderName = doc.getString("senderName") ?: "User"
        val relScore = doc.getLong("relevanceScore")?.toInt()
        val relStatus = doc.getString("relevanceStatus")
        val relevance = if (relScore != null && relStatus != null) {
            TopicRelevance(
                score = relScore,
                status = try { DriftStatus.valueOf(relStatus) } catch (e: Exception) { DriftStatus.ON_TOPIC },
                explanation = doc.getString("relevanceExplanation") ?: "Evaluated by Gemini Topic Guard"
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
                fileSizeBytes = doc.getLong("fileSizeBytes") ?: 0L,
                contentSummary = doc.getString("fileSummary") ?: "Scanned attachment.",
                safetyStatus = doc.getString("fileSafetyStatus") ?: "Verified Safe",
                topicRelevanceScore = doc.getLong("fileRelevanceScore")?.toInt() ?: 90,
                storedFileId = doc.getString("fileId"),
                mimeType = doc.getString("fileMime")
            )
        } else null

        val sender = if (senderId == uid) _currentUser.value
        else _users.value[senderId] ?: User(senderId, senderName, "@user", colorFor(senderId), false, "")

        Message(
            id = doc.id,
            channelId = channelId,
            sender = sender,
            content = doc.getString("content") ?: "",
            timestamp = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
            relevance = relevance,
            linkAnalysis = linkAnalysis,
            fileAttachment = fileAttachment,
            isFromMe = senderId == uid,
            callUrl = doc.getString("callUrl"),
            isVideoCall = doc.getBoolean("isVideoCall") ?: true
        )
    } catch (e: Exception) {
        Log.w(TAG, "Error mapping message ${doc.id}", e)
        null
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
        overrideRelevance: TopicRelevance? = null,
        callUrl: String? = null,
        isVideoCall: Boolean = true
    ) {
        val uid = requireUserId()
        val channel = _channels.value.find { it.id == channelId } ?: return
        val currentConfig = _filterConfig.value

        // Content filter (blocked keywords, spam, PII): blocked messages are never sent
        if (callUrl == null) {
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
                currentMap[channelId] = (currentMap[channelId] ?: emptyList()) + blockedMsg
                _messages.value = currentMap
                return
            }
        }

        scope.launch {
            val relevance = when {
                callUrl != null -> null
                overrideRelevance != null -> overrideRelevance
                channel.guardLevel != GuardLevel.OFF -> GeminiClient.verifyTopicRelevance(channel.topic, content)
                else -> null
            }
            val link = linkAnalysis ?: if (callUrl == null) extractAndScanLink(content, channel.topic) else null

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
                fileAttachment.storedFileId?.let { msgData["fileId"] = it }
                fileAttachment.mimeType?.let { msgData["fileMime"] = it }
            }
            if (callUrl != null) {
                msgData["callUrl"] = callUrl
                msgData["isVideoCall"] = isVideoCall
            }

            val messagesCol = db.collection("channels").document(channelId).collection("messages")
            messagesCol.add(msgData)
                .addOnFailureListener { e ->
                    _notice.value = "Your message could not be sent. Check your connection and try again."
                    handleFirestoreError(e, OperationType.CREATE, messagesCol.path)
                }

            val channelDoc = db.collection("channels").document(channelId)
            channelDoc.update(
                mapOf(
                    "lastMessageText" to content.take(200),
                    "lastMessageTimestamp" to FieldValue.serverTimestamp()
                )
            ).addOnFailureListener { e ->
                handleFirestoreError(e, OperationType.UPDATE, channelDoc.path)
            }
        }
    }

    private suspend fun extractAndScanLink(content: String, topic: String): LinkAnalysis? {
        val urlRegex = Regex("https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\.&]+")
        val match = urlRegex.find(content)?.value
        return if (match != null) GeminiClient.scanLink(match, topic) else null
    }

    private fun kindOf(fileName: String, mime: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") -> "image"
            mime == "application/pdf" || ext == "pdf" -> "pdf"
            ext in setOf("kt", "java", "py", "js", "ts", "c", "cpp", "h", "swift", "go", "rs", "rb", "php", "cs", "sql", "sh", "json", "xml", "html", "css") -> "code"
            else -> "doc"
        }
    }

    private fun isTextual(fileName: String, mime: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return mime.startsWith("text/") || mime == "application/json" || mime == "application/xml" ||
            ext in setOf("txt", "md", "csv", "kt", "java", "py", "js", "ts", "c", "cpp", "h", "swift", "go", "rs", "rb", "php", "cs", "sql", "sh", "json", "xml", "html", "css", "yaml", "yml")
    }

    /**
     * Uploads a real file the user picked: Gemini scans it for the channel's topic, the file is stored in
     * Firestore (channels/{id}/files/{fileId}, in base64 chunks) and a message with the file card is sent.
     */
    fun uploadFile(channelId: String, fileName: String, mimeType: String?, bytes: ByteArray) {
        val uid = requireUserId()
        val channel = _channels.value.find { it.id == channelId } ?: return
        if (bytes.isEmpty()) {
            _notice.value = "That file is empty."
            return
        }
        if (bytes.size > MAX_ATTACHMENT_BYTES) {
            _notice.value = "Files must be 5 MB or smaller."
            return
        }
        val mime = mimeType ?: "application/octet-stream"
        val kind = kindOf(fileName, mime)
        _isUploading.value = true
        scope.launch {
            try {
                val snippet = if (isTextual(fileName, mime)) {
                    String(bytes, Charsets.UTF_8).take(8000)
                } else {
                    "[$mime file named $fileName, ${bytes.size} bytes. The contents are not text.]"
                }
                val analysis = GeminiClient.analyzeDeepFile(fileName, kind, snippet, channel.topic)

                val fileId = UUID.randomUUID().toString().replace("-", "")
                val filesCol = db.collection("channels").document(channelId).collection("files")
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val chunks = b64.chunked(CHUNK_CHARS)
                chunks.forEachIndexed { i, part ->
                    filesCol.document(fileId).collection("chunks").document(i.toString())
                        .set(mapOf("data" to part, "uploaderId" to uid)).awaitTask()
                }
                filesCol.document(fileId).set(
                    mapOf(
                        "name" to fileName,
                        "mime" to mime,
                        "size" to bytes.size.toLong(),
                        "chunkCount" to chunks.size.toLong(),
                        "uploaderId" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).awaitTask()

                val attachment = analysis.copy(
                    fileName = fileName,
                    fileType = kind,
                    fileSizeBytes = bytes.size.toLong(),
                    storedFileId = fileId,
                    mimeType = mime
                )
                sendMessage(
                    channelId = channelId,
                    content = "Shared a file: $fileName",
                    fileAttachment = attachment,
                    overrideRelevance = TopicRelevance(
                        score = attachment.topicRelevanceScore,
                        status = if (attachment.topicRelevanceScore >= 70) DriftStatus.ON_TOPIC else DriftStatus.DRIFT,
                        explanation = "File checked against the topic: ${attachment.contentSummary}"
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Upload failed", e)
                _notice.value = "The file could not be uploaded. Please try again."
            } finally {
                _isUploading.value = false
            }
        }
    }

    /** Downloads an uploaded attachment. Returns null if it is missing. */
    suspend fun downloadFile(channelId: String, fileId: String): ByteArray? {
        val fileDoc = db.collection("channels").document(channelId).collection("files").document(fileId)
        val meta = fileDoc.get().awaitTask()
        if (!meta.exists()) return null
        val count = (meta.getLong("chunkCount") ?: 0L).toInt()
        val sb = StringBuilder()
        for (i in 0 until count) {
            val chunk = fileDoc.collection("chunks").document(i.toString()).get().awaitTask()
            sb.append(chunk.getString("data") ?: return null)
        }
        return Base64.decode(sb.toString(), Base64.NO_WRAP)
    }

    /**
     * Starts a real call in a new Jitsi Meet room and posts a "join" message to the chat.
     * Returns the room link to open.
     */
    fun startCall(channelId: String, isVideo: Boolean): String? {
        _channels.value.find { it.id == channelId } ?: return null
        val room = "FocusChat-" + UUID.randomUUID().toString().replace("-", "")
        val url = "https://meet.jit.si/$room" + if (isVideo) "" else "#config.startWithVideoMuted=true"
        sendMessage(
            channelId = channelId,
            content = if (isVideo) "Started a video call. Tap Join to come in." else "Started a voice call. Tap Join to come in.",
            callUrl = url,
            isVideoCall = isVideo
        )
        return url
    }

    /**
     * Creates a channel (or opens the existing DM). memberIds are the other people to include.
     */
    fun createChannel(
        name: String,
        topic: String,
        keywords: List<String>,
        guardLevel: GuardLevel,
        isDirectMessage: Boolean,
        memberIds: List<String>
    ) {
        val uid = requireUserId()
        val members = (listOf(uid) + memberIds).distinct()

        if (isDirectMessage) {
            val existing = _channels.value.firstOrNull { ch ->
                ch.isDirectMessage && ch.participants.map { it.id }.toSet() == members.toSet()
            }
            if (existing != null) {
                selectChannel(existing.id)
                return
            }
        }

        val newId = "c_" + UUID.randomUUID().toString().take(8)
        val data = mapOf(
            "id" to newId,
            "name" to name,
            "isDirectMessage" to isDirectMessage,
            "topic" to topic,
            "topicKeywords" to keywords,
            "guardLevel" to guardLevel.name,
            "creatorId" to uid,
            "memberIds" to members,
            "lastMessageText" to "Created with topic: $topic",
            "lastMessageTimestamp" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        val docRef = db.collection("channels").document(newId)
        docRef.set(data).addOnFailureListener { e ->
            _notice.value = "The chat could not be created. Please try again."
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
        }
        selectChannel(newId)
    }

    fun updateFilterConfig(config: ContentFilterConfig) {
        _filterConfig.value = config
    }
}
