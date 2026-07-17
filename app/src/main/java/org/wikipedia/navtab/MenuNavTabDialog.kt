package org.wikipedia.navtab

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.wikipedia.activity.FragmentUtil
import org.wikipedia.analytics.eventplatform.BreadCrumbLogEvent
import org.wikipedia.databinding.ViewMainDrawerBinding
import org.wikipedia.page.ExtendedBottomSheetDialogFragment
import org.wikipedia.util.DimenUtil

class MenuNavTabDialog : ExtendedBottomSheetDialogFragment() {
    interface Callback {
        fun settingsClick()
    }

    private var _binding: ViewMainDrawerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ViewMainDrawerBinding.inflate(inflater, container, false)

        binding.mainDrawerSettingsContainer.setOnClickListener {
            BreadCrumbLogEvent.logClick(requireActivity(), binding.mainDrawerSettingsContainer)
            callback()?.settingsClick()
            dismiss()
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        BottomSheetBehavior.from(binding.root.parent as View).peekHeight = DimenUtil.displayHeightPx
    }

    private fun callback(): Callback? {
        return FragmentUtil.getCallback(this, Callback::class.java)
    }

    companion object {
        fun newInstance(): MenuNavTabDialog {
            return MenuNavTabDialog()
        }
    }
}
