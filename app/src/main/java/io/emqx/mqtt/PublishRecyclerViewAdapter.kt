package io.emqx.mqtt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PublishRecyclerViewAdapter(
    private val mValues: List<Publish>,
    private val onDeleteClick: (Publish) -> Unit,
    private val onRepublishClick: (Publish) -> Unit
) : RecyclerView.Adapter<PublishRecyclerViewAdapter.ViewHolder>() {

    private var expandedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_publish, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mValues[position]
        holder.mTopicView.text = item.topic
        holder.mPayloadView.text = item.payload
        holder.mQosView.text = "QoS: ${item.qos}"
        holder.mRetainedView.text = "Retained: ${item.isRetained}"

        val isExpanded = position == expandedPosition
        holder.mActionButtons.visibility = if (isExpanded) View.VISIBLE else View.GONE

        holder.mDeleteButton.setOnClickListener {
            onDeleteClick(item)
        }

        holder.mRepublishButton.setOnClickListener {
            onRepublishClick(item)
        }

        holder.itemView.setOnClickListener {
            val previousExpanded = expandedPosition
            expandedPosition = if (isExpanded) -1 else holder.adapterPosition
            notifyItemChanged(previousExpanded)
            if (expandedPosition != -1) {
                notifyItemChanged(expandedPosition)
            }
        }
    }

    override fun getItemCount(): Int {
        return mValues.size
    }

    inner class ViewHolder(mView: View) : RecyclerView.ViewHolder(mView) {
        val mTopicView: TextView = mView.findViewById(R.id.tv_topic)
        val mPayloadView: TextView = mView.findViewById(R.id.tv_payload)
        val mQosView: TextView = mView.findViewById(R.id.tv_qos)
        val mRetainedView: TextView = mView.findViewById(R.id.tv_retained)
        val mActionButtons: LinearLayout = mView.findViewById(R.id.action_buttons)
        val mDeleteButton: Button = mView.findViewById(R.id.btn_delete)
        val mRepublishButton: Button = mView.findViewById(R.id.btn_republish)
    }
}