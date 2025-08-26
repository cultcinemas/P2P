package com.example.btchat.data

enum class MessageType { TEXT, FILE }

data class Message(
    val id: String,
    val fromDevice: String,
    val toDevice: String?, // null for broadcast
    val type: MessageType,
    val text: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
    var delivered: Boolean = false,
    var isSelf: Boolean = false
)
