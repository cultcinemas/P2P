package com.example.btchat.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class EmojiPickerFragment(private val onPick: (String) -> Unit): DialogFragment() {
    private val emojis = listOf("😀","😁","😂","🤣","😊","😍","😘","😎","🤩","🤔","😴","😇","😭","👍","🙏","🔥","💯","🎉")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(android.R.layout.simple_list_item_1, null)
        val grid = GridLayout(requireContext()).apply { columnCount = 6; rowCount = 3 }
        emojis.forEach { e ->
            val tv = TextView(requireContext()).apply { text = e; textSize = 24f; setPadding(16,16,16,16) }
            tv.setOnClickListener { onPick(e); dismiss() }
            grid.addView(tv)
        }
        return AlertDialog.Builder(requireContext()).setTitle("Pick emoji").setView(grid).create()
    }
}
