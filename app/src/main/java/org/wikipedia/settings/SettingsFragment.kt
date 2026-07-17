package org.wikipedia.settings

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import org.wikipedia.R
import org.wikipedia.settings.dev.DeveloperSettingsActivity.Companion.newIntent

class SettingsFragment : PreferenceLoaderFragment(), MenuProvider {
    private lateinit var preferenceLoader: SettingsPreferenceLoader

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun loadPreferences() {
        preferenceLoader = SettingsPreferenceLoader(this)
        preferenceLoader.loadPreferences()
    }

    override fun onResume() {
        super.onResume()
        preferenceLoader.updateLanguagePrefSummary()
        preferenceLoader.updateRecommendedReadingListSummary()
        requireActivity().invalidateOptionsMenu()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_settings, menu)
    }

    override fun onPrepareMenu(menu: Menu) {
        prepareDeveloperSettingsMenuItem(menu)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.developer_settings -> {
                launchDeveloperSettingsActivity()
                true
            }
            else -> false
        }
    }

    private fun launchDeveloperSettingsActivity() {
        startActivity(newIntent(requireActivity()))
    }

    private fun prepareDeveloperSettingsMenuItem(menu: Menu) {
        menu.findItem(R.id.developer_settings).isVisible = Prefs.isShowDeveloperSettingsEnabled
    }

    companion object {
        fun newInstance(): SettingsFragment {
            return SettingsFragment()
        }
    }
}
