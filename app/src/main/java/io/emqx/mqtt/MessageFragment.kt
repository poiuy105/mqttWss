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
    private val mAllCapturedList: ArrayList<CapturedText> = ArrayList()
    private val mAppPackages: LinkedHashSet<String> = LinkedHashSet()
    private var mFilterContainer: LinearLayout? = null
    private var mExcludedCountText: TextView? = null

    private val captureListener: (String, String) -> Unit = { text, packageName ->
        activity?.runOnUiThread {
            addCapturedText(text, packageName)
        }
    }

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
        mAdapter = CapturedTextAdapter(mAllCapturedList)
        recyclerView.adapter = mAdapter

        mFilterContainer = view.findViewById(R.id.app_filter_container)
        mExcludedCountText = view.findViewById(R.id.excluded_count)
        val clearBtn = view.findViewById(R.id.btn_clear_log)

        CapturedTextManager.init(requireContext())
        CapturedTextManager.addListener(captureListener)

        clearBtn.setOnClickListener {
            CapturedTextManager.clearCaptured()
            mAllCapturedList.clear()
            mAdapter?.notifyDataSetChanged()
            Toast.makeText(fragmentActivity, "Log cleared", Toast.LENGTH_SHORT).show()
        }

        restoreSettings()
        rebuildFilterChips()
        filterList()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        CapturedTextManager.removeListener(captureListener)
    }

    private fun restoreSettings() {
        val whitelist = CapturedTextManager.getWhitelistApp()
        val excluded = CapturedTextManager.getExcludedApps()
        mAppPackages.addAll(excluded)
        if (whitelist != null) {
            mAppPackages.add(whitelist)
        }
        updateExcludedCount()
    }

    private fun addCapturedText(text: String, packageName: String) {
        val captured = CapturedText(text, packageName, System.currentTimeMillis())
        mAllCapturedList.add(0, captured)

        val isNew = mAppPackages.add(packageName)
        if (isNew) {
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
        val isWhitelist = CapturedTextManager.getWhitelistApp() == packageName
        val isExcluded = CapturedTextManager.getExcludedApps().contains(packageName)

        val chip = Button(requireContext()).apply {
            text = if (isWhitelist) "V ${getAppName(packageName)}" else getAppName(packageName)
            tag = packageName
            textSize = 12f

            updateChipStyle(this, isWhitelist, isExcluded)

            setOnClickListener {
                toggleExclude(packageName)
            }

            setOnLongClickListener {
                toggleWhitelist(packageName)
                true
            }
        }
        return chip
    }

    private fun toggleExclude(packageName: String) {
        val excluded = CapturedTextManager.getExcludedApps().toMutableSet()
        val whitelist = CapturedTextManager.getWhitelistApp()

        if (whitelist == packageName) {
            CapturedTextManager.setWhitelistApp(null)
        } else if (excluded.contains(packageName)) {
            excluded.remove(packageName)
            CapturedTextManager.setExcludedApps(excluded)
        } else {
            excluded.add(packageName)
            CapturedTextManager.setExcludedApps(excluded)
        }

        rebuildFilterChips()
        updateExcludedCount()
        filterList()
    }

    private fun toggleWhitelist(packageName: String) {
        val currentWhitelist = CapturedTextManager.getWhitelistApp()
        if (currentWhitelist == packageName) {
            CapturedTextManager.setWhitelistApp(null)
            Toast.makeText(fragmentActivity, "Whitelist cleared - all apps available", Toast.LENGTH_SHORT).show()
        } else {
            CapturedTextManager.setWhitelistApp(packageName)
            Toast.makeText(fragmentActivity, "V ${getAppName(packageName)} - only this app", Toast.LENGTH_LONG).show()
        }
        rebuildFilterChips()
        updateExcludedCount()
    }

    private fun updateChipStyle(chip: Button, isWhitelist: Boolean, isExcluded: Boolean) {
        when {
            isWhitelist -> {
                chip.setBackgroundColor(Color.parseColor("#4CAF50"))
                chip.setTextColor(Color.WHITE)
                chip.paintFlags = chip.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            isExcluded -> {
                chip.setBackgroundColor(Color.parseColor("#FFCCCC"))
                chip.setTextColor(Color.parseColor("#999999"))
                chip.paintFlags = chip.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            }
            else -> {
                chip.setBackgroundColor(Color.parseColor("#CCEECC"))
                chip.setTextColor(Color.parseColor("#333333"))
                chip.paintFlags = chip.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
        }
    }

    private fun updateExcludedCount() {
        val whitelist = CapturedTextManager.getWhitelistApp()
        val excluded = CapturedTextManager.getExcludedApps().size
        when {
            whitelist != null -> mExcludedCountText?.text = "Mode: Only V app"
            excluded > 0 -> mExcludedCountText?.text = "Excluded: $excluded apps"
            else -> mExcludedCountText?.text = "All apps available"
        }
    }

    private fun filterList() {
        val whitelist = CapturedTextManager.getWhitelistApp()
        val excluded = CapturedTextManager.getExcludedApps()

        val filtered = mAllCapturedList.filter { item ->
            when {
                whitelist != null -> item.packageName == whitelist
                excluded.isNotEmpty() -> !excluded.contains(item.packageName)
                else -> true
            }
        }

        mAllCapturedList.clear()
        mAllCapturedList.addAll(filtered)
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
