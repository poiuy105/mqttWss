package io.emqx.mqtt

import android.content.pm.PackageManager
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView

class MessageFragment : BaseFragment() {
    private var mAdapter: CapturedTextAdapter? = null
    private val mCapturedList: ArrayList<CapturedText> = ArrayList()
    private val mAllCapturedList: ArrayList<CapturedText> = ArrayList()
    private val mAppPackages: LinkedHashSet<String> = LinkedHashSet()
    private val mExcludedApps: HashSet<String> = HashSet()
    private var mFilterContainer: LinearLayout? = null
    private var mExcludedCountText: TextView? = null

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

        mFilterContainer = view.findViewById(R.id.app_filter_container)
        mExcludedCountText = view.findViewById(R.id.excluded_count)
        val clearBtn = view.findViewById<Button>(R.id.btn_clear_log)

        clearBtn.setOnClickListener {
            mCapturedList.clear()
            mAllCapturedList.clear()
            mAppPackages.clear()
            mExcludedApps.clear()
            mFilterContainer?.removeAllViews()
            updateExcludedCount()
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

        if (mFilterContainer?.childCount != mAppPackages.size) {
            rebuildFilterChips()
        }

        filterList()
    }

    private fun rebuildFilterChips() {
        mFilterContainer?.removeAllViews()
        mAppPackages.forEach { packageName ->
            val chip = createChip(packageName)
            mFilterContainer?.addView(chip)
        }
    }

    private fun createChip(packageName: String): Button {
        val chip = Button(requireContext()).apply {
            text = getAppName(packageName)
            tag = packageName
            textSize = 12f

            val isExcluded = mExcludedApps.contains(packageName)
            updateChipStyle(this, isExcluded)

            setOnClickListener {
                toggleExclude(packageName)
            }
        }
        return chip
    }

    private fun toggleExclude(packageName: String) {
        if (mExcludedApps.contains(packageName)) {
            mExcludedApps.remove(packageName)
        } else {
            mExcludedApps.add(packageName)
        }

        for (i in 0 until (mFilterContainer?.childCount ?: 0)) {
            val chip = mFilterContainer?.getChildAt(i) as? Button
            if (chip?.tag == packageName) {
                updateChipStyle(chip, mExcludedApps.contains(packageName))
                break
            }
        }

        updateExcludedCount()
        filterList()
    }

    private fun updateChipStyle(chip: Button, isExcluded: Boolean) {
        if (isExcluded) {
            chip.setBackgroundColor(Color.parseColor("#FFCCCC"))
            chip.setTextColor(Color.parseColor("#999999"))
            chip.paintFlags = chip.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            chip.setBackgroundColor(Color.parseColor("#CCEECC"))
            chip.setTextColor(Color.parseColor("#333333"))
            chip.paintFlags = chip.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }

    private fun updateExcludedCount() {
        mExcludedCountText?.text = "Excluded: ${mExcludedApps.size} / ${mAppPackages.size}"
    }

    private fun filterList() {
        mCapturedList.clear()
        if (mExcludedApps.isEmpty()) {
            mCapturedList.addAll(mAllCapturedList)
        } else {
            mAllCapturedList.filterTo(mCapturedList) { !mExcludedApps.contains(it.packageName) }
        }
        mAdapter?.notifyDataSetChanged()
    }

    private fun getAppName(packageName: String): String {
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