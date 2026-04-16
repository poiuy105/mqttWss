package io.emqx.mqtt

import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import java.util.ArrayList

class PublishFragment : BaseFragment() {
    private var mAdapter: PublishRecyclerViewAdapter? = null
    private val mPublishList: ArrayList<Publish> = ArrayList()
    private lateinit var mConfigManager: ConfigManager

    override val layoutResId: Int
        get() = R.layout.fragment_publish_list

    private fun appendLog(message: String) {
        (fragmentActivity as? MainActivity)?.let { main ->
            main.appendLog("[Publish] $message")
        }
    }

    override fun setUpView(view: View) {
        mConfigManager = ConfigManager.getInstance(fragmentActivity!!)

        val recyclerView = view.findViewById<RecyclerView>(R.id.publish_list)
        mAdapter = PublishRecyclerViewAdapter(
            mPublishList,
            onDeleteClick = { publish -> deletePublish(publish) },
            onRepublishClick = { publish -> republish(publish) }
        )
        recyclerView.adapter = mAdapter
        recyclerView.addItemDecoration(
            DividerItemDecoration(
                fragmentActivity,
                DividerItemDecoration.VERTICAL
            )
        )

        view.findViewById<Button>(R.id.btn_add_publish).setOnClickListener {
            showAddPublishDialog()
        }

        loadPublishHistory()
    }

    private fun showAddPublishDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_publish, null)
        val topicInput = dialogView.findViewById<EditText>(R.id.dialog_topic)
        val payloadInput = dialogView.findViewById<EditText>(R.id.dialog_payload)
        val qosGroup = dialogView.findViewById<RadioGroup>(R.id.dialog_qos)
        val retainedGroup = dialogView.findViewById<RadioGroup>(R.id.dialog_retained)

        AlertDialog.Builder(requireContext())
            .setTitle("Add Publish")
            .setView(dialogView)
            .setPositiveButton("Publish") { _, _ ->
                val topic = topicInput.text.toString()
                val payload = payloadInput.text.toString()
                if (topic.isEmpty()) {
                    Toast.makeText(fragmentActivity, "Please enter topic", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val qos = when (qosGroup.checkedRadioButtonId) {
                    R.id.dialog_qos1 -> 1
                    R.id.dialog_qos2 -> 2
                    else -> 0
                }
                val retained = retainedGroup.checkedRadioButtonId == R.id.dialog_retained_true
                val publish = Publish(topic, payload, qos, retained)
                publishToTopic(publish)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun publishToTopic(publish: Publish) {
        appendLog("Publishing to: ${publish.topic}, payload: ${publish.payload}")
        (fragmentActivity as MainActivity).publish(
            publish,
            object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    appendLog("Publish SUCCESS: ${publish.topic}")
                    activity?.runOnUiThread {
                        mPublishList.add(0, publish)
                        mAdapter?.notifyItemInserted(0)
                        savePublishHistory()
                        Toast.makeText(fragmentActivity, "Published: ${publish.topic}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    appendLog("Publish FAILED: $exception")
                    activity?.runOnUiThread {
                        Toast.makeText(fragmentActivity, "Failed to publish: $exception", Toast.LENGTH_SHORT).show()
                    }
                }
            })
    }

    private fun deletePublish(publish: Publish) {
        val index = mPublishList.indexOf(publish)
        if (index != -1) {
            mPublishList.removeAt(index)
            mAdapter?.notifyItemRemoved(index)
            savePublishHistory()
            appendLog("Deleted publish: ${publish.topic}")
            Toast.makeText(fragmentActivity, "Deleted: ${publish.topic}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun republish(publish: Publish) {
        publishToTopic(publish)
    }

    private fun savePublishHistory() {
        val history = mPublishList.joinToString(";") { "${it.topic},${it.payload},${it.qos},${it.retained}" }
        mConfigManager.publishHistory = history
    }

    private fun loadPublishHistory() {
        val saved = mConfigManager.publishHistory
        if (saved.isNotEmpty()) {
            mPublishList.clear()
            saved.split(";").forEach { item ->
                val parts = item.split(",")
                if (parts.size >= 4) {
                    val topic = parts[0]
                    val payload = parts[1]
                    val qos = parts[2].toIntOrNull() ?: 0
                    val retained = parts[3].toBoolean()
                    mPublishList.add(Publish(topic, payload, qos, retained))
                }
            }
            mAdapter?.notifyDataSetChanged()
        }
    }

    companion object {
        fun newInstance(): PublishFragment {
            return PublishFragment()
        }
    }
}