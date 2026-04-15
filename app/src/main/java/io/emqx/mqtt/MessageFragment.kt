package io.emqx.mqtt

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView

class MessageFragment : BaseFragment() {
    private var mAdapter: CapturedTextAdapter? = null
    private val mCapturedList: ArrayList<CapturedText> = ArrayList()
    private val mAllCapturedList: ArrayList<CapturedText> = ArrayList()
    private val mAppPackages: LinkedHashSet<String> = LinkedHashSet()
    private val mAppList: ArrayList<String> = ArrayList()
    private var mSpinner: Spinner? = null
    private var mSelectedApp: String? = null

    override val layoutResId: Int
        get() = R.layout.fragment_message_list

    override fun setUpView(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.message_list)
        recyclerView.addItemDecoration(
            DividerItemDecoration(
                fragmentActivity,
                DividerItemDecoration.VERTICAL
            )
        )
        mAdapter = CapturedTextAdapter(mCapturedList)
        recyclerView.adapter = mAdapter

        mSpinner = view.findViewById(R.id.app_filter_spinner)
        val clearBtn = view.findViewById<Button>(R.id.btn_clear_log)

        mAppList.add("All Apps")
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            mAppList
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mSpinner?.adapter = spinnerAdapter

        mSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                mSelectedApp = if (position == 0) null else mAppList[position]
                filterList()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        clearBtn.setOnClickListener {
            mCapturedList.clear()
            mAllCapturedList.clear()
            mAppPackages.clear()
            mAppList.clear()
            mAppList.add("All Apps")
            spinnerAdapter.notifyDataSetChanged()
            mAdapter?.notifyDataSetChanged()
            Toast.makeText(fragmentActivity, "Log cleared", Toast.LENGTH_SHORT).show()
        }

        VoiceAccessibilityService.setOnTextCapturedListener { text, packageName ->
            activity?.runOnUiThread {
                addCapturedText(text, packageName)
            }
        }
    }

    private fun addCapturedText(text: String, packageName: String) {
        val captured = CapturedText(text, packageName, System.currentTimeMillis())
        mAllCapturedList.add(0, captured)
        mAppPackages.add(packageName)

        mAppList.clear()
        mAppList.add("All Apps")
        mAppPackages.forEach { mAppList.add(it) }
        (mSpinner?.adapter as? ArrayAdapter<String>)?.notifyDataSetChanged()

        filterList()
    }

    private fun filterList() {
        mCapturedList.clear()
        if (mSelectedApp == null) {
            mCapturedList.addAll(mAllCapturedList)
        } else {
            mAllCapturedList.filterTo(mCapturedList) { it.packageName == mSelectedApp }
        }
        mAdapter?.notifyDataSetChanged()
    }

    fun getAppName(packageName: String): String {
        return try {
            val pm = fragmentActivity?.packageManager
            val appInfo = pm?.getApplicationInfo(packageName, 0)
            if (appInfo != null) {
                pm?.getApplicationLabel(appInfo)?.toString() ?: packageName
            } else {
                packageName
            }
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    companion object {
        fun newInstance(): MessageFragment {
            return MessageFragment()
        }
    }
}
