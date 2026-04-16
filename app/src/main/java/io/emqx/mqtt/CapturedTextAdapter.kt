package io.emqx.mqtt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CapturedTextAdapter(
    private val list: ArrayList<CapturedText>,
    private val onLongClick: (CapturedText) -> Unit
) : RecyclerView.Adapter<CapturedTextAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val timeText: TextView = view.findViewById(R.id.captured_time)
        val depthText: TextView = view.findViewById(R.id.captured_depth)
        val boundsText: TextView = view.findViewById(R.id.captured_bounds)
        val appText: TextView = view.findViewById(R.id.captured_app)
        val classText: TextView = view.findViewById(R.id.captured_class)
        val textContent: TextView = view.findViewById(R.id.captured_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_captured_text, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.timeText.text = item.getFormattedTime()
        holder.depthText.text = item.getDepthString()
        holder.boundsText.text = item.getBoundsString()
        holder.appText.text = item.packageName
        holder.classText.text = item.viewClass
        holder.textContent.text = item.text

        holder.itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }
    }

    override fun getItemCount(): Int = list.size
}