package io.emqx.mqtt

import android.view.View

class HomeFragment : BaseFragment() {
    override val layoutResId: Int
        get() = R.layout.fragment_home

    override fun setUpView(view: View) {
    }

    companion object {
        fun newInstance(): HomeFragment {
            return HomeFragment()
        }
    }
}
