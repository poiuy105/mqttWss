package io.emqx.mqtt

import android.content.Context
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter

class SectionsPagerAdapter(
    fm: FragmentManager,
    private val mContext: Context,
    private val mFragmentList: List<Fragment>
) : FragmentPagerAdapter(fm) {
    override fun getItem(position: Int): Fragment {
        return mFragmentList[position]
    }

    @StringRes
    override fun getPageTitle(position: Int): Int {
        return TAB_TITLES[position]
    }

    @DrawableRes
    fun getPageIcon(position: Int): Int {
        return TAB_ICONS[position]
    }

    override fun getCount(): Int {
        return mFragmentList.size
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {}

    companion object {
        @StringRes
        val TAB_TITLES = intArrayOf(
            R.string.home,
            R.string.connection,
            R.string.subscription,
            R.string.publish,
            R.string.message
        )

        @DrawableRes
        val TAB_ICONS = intArrayOf(
            R.drawable.ic_home,
            R.drawable.ic_connection,
            R.drawable.ic_subscription,
            R.drawable.ic_publish,
            R.drawable.ic_message
        )
    }
}
