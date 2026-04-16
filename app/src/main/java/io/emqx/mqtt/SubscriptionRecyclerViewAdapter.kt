package io.emqx.mqtt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SubscriptionRecyclerViewAdapter(
    private val mValues: List<Subscription>,
    private val onDeleteClick: (Subscription) -> Unit,
    private val onSpeakClick: (Subscription) -> Unit
) : RecyclerView.Adapter<SubscriptionRecyclerViewAdapter.ViewHolder>() {

    private var expandedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subscription, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mValues[position]
        holder.mTopicView.text = item.topic
        holder.mQosView.text = "QoS: ${item.qos}"
        holder.mLastMessageView.text = if (item.lastMessage.isNotEmpty()) "Last: ${item.lastMessage}" else "No messages yet"

        val isExpanded = position == expandedPosition
        holder.mActionButtons.visibility = if (isExpanded) View.VISIBLE else View.GONE

        holder.mDeleteButton.setOnClickListener {
            onDeleteClick(item)
        }

        holder.mSpeakButton.setOnClickListener {
            onSpeakClick(item)
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
        val mQosView: TextView = mView.findViewById(R.id.tv_qos)
        val mLastMessageView: TextView = mView.findViewById(R.id.tv_last_message)
        val mActionButtons: LinearLayout = mView.findViewById(R.id.action_buttons)
        val mDeleteButton: Button = mView.findViewById(R.id.btn_delete)
        val mSpeakButton: Button = mView.findViewById(R.id.btn_speak)
    }
}