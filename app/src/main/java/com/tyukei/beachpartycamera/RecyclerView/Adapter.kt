package com.tyukei.beachpartycamera

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SidebarAdapter(
    private val results: List<ImageResult>,
    private val itemClickListener: (Int) -> Unit,
    private val deleteClickListener: (Int) -> Unit
) : RecyclerView.Adapter<SidebarAdapter.SidebarViewHolder>() {

    class SidebarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val resultTextView: TextView = itemView.findViewById(R.id.resultTextView)
        val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SidebarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.sidebar_item, parent, false)
        return SidebarViewHolder(view)
    }

    override fun onBindViewHolder(holder: SidebarViewHolder, position: Int) {
        val result = results[position]
        holder.resultTextView.text = result.text.take(5) + "..."
        holder.itemView.setOnClickListener {
            itemClickListener(position)
        }
        holder.deleteButton.setOnClickListener {
            deleteClickListener(position)
        }
    }

    override fun getItemCount() = results.size
}