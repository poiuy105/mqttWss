package io.emqx.mqtt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CapturedTextAdapter(private val list: ArrayList<CapturedText>) :
    RecyclerView.Adapter<CapturedTextAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val timeText: TextView = view.findViewById(R.id.captured_time)
        val appText: TextView = view.findViewById(R.id.captured_app)
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
        holder.appText.text = item.packageName
        holder.textContent.text = item.text
    }

    override fun getItemCount(): Int = list.size
}
