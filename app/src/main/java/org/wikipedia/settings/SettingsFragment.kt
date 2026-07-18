package org.wikipedia.settings

class SettingsFragment : PreferenceLoaderFragment() {
    private lateinit var preferenceLoader: SettingsPreferenceLoader

    override fun loadPreferences() {
        preferenceLoader = SettingsPreferenceLoader(this)
        preferenceLoader.loadPreferences()
    }

    override fun onResume() {
        super.onResume()
        preferenceLoader.updateLanguagePrefSummary()
    }

    companion object {
        fun newInstance(): SettingsFragment {
            return SettingsFragment()
        }
    }
}
