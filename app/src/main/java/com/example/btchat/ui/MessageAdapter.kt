package com.example.btchat.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.btchat.R
import com.example.btchat.data.Message

class MessageAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val data = mutableListOf<Message>()

    fun submitList(list: List<Message>) {
        data.clear(); data.addAll(list); notifyDataSetChanged()
    }
    fun add(msg: Message) { data.add(msg); notifyItemInserted(data.size-1) }

    override fun getItemViewType(position: Int): Int = if (data[position].isSelf) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if (viewType == 1) R.layout.item_message_self else R.layout.item_message_other
        val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val h = holder as Holder
        val m = data[position]
        h.text.text = m.text ?: (m.fileName ?: "<file>")
        h.meta.text = "${m.timestamp} • ${if (m.delivered) "delivered" else "pending"}"
    }

    override fun getItemCount(): Int = data.size

    class Holder(v: View): RecyclerView.ViewHolder(v) {
        val text: TextView = v.findViewById(R.id.tvText)
        val meta: TextView = v.findViewById(R.id.tvMeta)
    }
}
