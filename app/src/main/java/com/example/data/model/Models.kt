package com.example.data.model

enum class GuardLevel {
    STRICT,    // Requires >=70% relevance or warns and provides AI rewrite before sending
    BALANCED,  // Flags drift and highlights off-topic content with badges
    ADVISORY,  // Shows subtle relevance tags
    OFF        // No topic checking
}

enum class DriftStatus {
    ON_TOPIC,
    DRIFT,
    OFF_TOPIC
}

data class User(
    val id: String,
    val name: String,
    val handle: String,
    val avatarColor: Long,
    val isOnline: Boolean = true,
    val statusMessage: String = "Online"
)

data class TopicRelevance(
    val score: Int, // 0 - 100
    val status: DriftStatus,
    val explanation: String,
    val suggestedRewrite: String? = null,
    val keyConcepts: List<String> = emptyList(),
    val analyzedAt: Long = System.currentTimeMillis()
)

data class LinkAnalysis(
    val url: String,
    val title: String,
    val domain: String,
    val trustScore: Int, // 0 - 100
    val isSafe: Boolean,
    val summary: String,
    val topicMatchScore: Int,
    val tags: List<String> = emptyList()
)

data class FileAttachment(
    val id: String,
    val fileName: String,
    val fileType: String, // "code", "pdf", "image", "doc"
    val fileSizeBytes: Long,
    val contentSummary: String,
    val safetyStatus: String, // "Verified Safe", "Suspicious", "Warning"
    val topicRelevanceScore: Int, // 0 - 100
    val keyTakeaways: List<String> = emptyList(),
    val securityNotes: String = "No known vulnerabilities found",
    val previewSnippet: String? = null,
    val storedFileId: String? = null, // id under channels/{channelId}/files when the file itself was uploaded
    val mimeType: String? = null
)

data class Message(
    val id: String,
    val channelId: String,
    val sender: User,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val relevance: TopicRelevance? = null,
    val linkAnalysis: LinkAnalysis? = null,
    val fileAttachment: FileAttachment? = null,
    val isFromMe: Boolean = false,
    val isFiltered: Boolean = false,
    val filterReason: String? = null,
    val callUrl: String? = null,
    val isVideoCall: Boolean = true
)

data class Channel(
    val id: String,
    val name: String,
    val isDirectMessage: Boolean,
    val topic: String,
    val topicKeywords: List<String>,
    val participants: List<User>,
    val guardLevel: GuardLevel = GuardLevel.BALANCED,
    val unreadCount: Int = 0,
    val lastMessageText: String = "",
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val activeCallId: String? = null
)

data class ContentFilterConfig(
    val blockToxicity: Boolean = true,
    val maskPii: Boolean = true,
    val filterSpam: Boolean = true,
    val strictRelevanceGate: Boolean = false,
    val blockedKeywords: List<String> = listOf("crypto pump", "free nitro", "phishing", "malware"),
    val minRelevanceScore: Int = 60
)
