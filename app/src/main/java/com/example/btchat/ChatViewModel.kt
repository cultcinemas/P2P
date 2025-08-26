package com.example.btchat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.btchat.data.Message

class ChatViewModel: ViewModel() {
    private val _messages = MutableLiveData<List<Message>>(emptyList())
    val messages: LiveData<List<Message>> get() = _messages

    fun addMessage(m: Message) {
        val cur = _messages.value?.toMutableList() ?: mutableListOf()
        cur.add(m)
        _messages.postValue(cur)
    }
}
