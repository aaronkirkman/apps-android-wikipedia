package org.wikipedia.settings

import android.content.Intent
import android.os.Build
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.wikipedia.BuildConfig
import org.wikipedia.Constants
import org.wikipedia.R
import org.wikipedia.WikipediaApp
import org.wikipedia.auth.AccountUtil
import org.wikipedia.settings.homefeed.HomeFeedSettingsActivity
import org.wikipedia.settings.languages.WikipediaLanguagesActivity
import org.wikipedia.theme.ThemeFittingRoomActivity
import org.wikipedia.util.FeedbackUtil

internal class SettingsPreferenceLoader(fragment: PreferenceFragmentCompat) : BasePreferenceLoader(fragment) {
    override fun loadPreferences() {
        loadPreferences(R.xml.preferences)
        loadPreferences(R.xml.preferences_about)
        updateLanguagePrefSummary()
        findPreference(R.string.preference_key_language).onPreferenceClickListener = Preference.OnPreferenceClickListener {
            activity.startActivityForResult(WikipediaLanguagesActivity.newIntent(activity, Constants.InvokeSource.SETTINGS),
                    Constants.ACTIVITY_REQUEST_ADD_A_LANGUAGE)
            true
        }
        findPreference(R.string.preference_key_customize_home_feed).onPreferenceClickListener = Preference.OnPreferenceClickListener {
             activity.startActivityForResult(
                 HomeFeedSettingsActivity.newIntent(activity),
                    Constants.ACTIVITY_REQUEST_FEED_CONFIGURE)
            true
        }
        findPreference(R.string.preference_key_color_theme).let {
            it.setSummary(WikipediaApp.instance.currentTheme.nameId)
            it.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                activity.startActivity(ThemeFittingRoomActivity.newIntent(activity))
                true
            }
        }

        findPreference(R.string.preference_key_selected_app_icon).isVisible = false

        findPreference(R.string.preference_key_about_wikipedia_app).onPreferenceClickListener = Preference.OnPreferenceClickListener {
            activity.startActivity(Intent(activity, AboutActivity::class.java))
            true
        }
        findPreference(R.string.preference_key_send_feedback).onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                FeedbackUtil.composeEmail(
                    activity,
                    subject = "Android App ${BuildConfig.VERSION_NAME} Feedback",
                    body = deviceInformation()
                )
                true
        }
        if (AccountUtil.isLoggedIn) {
            loadPreferences(R.xml.preferences_account)
            (findPreference(R.string.preference_key_logout) as LogoutPreference).activity = activity
        }

    }

    private fun deviceInformation(): String {
        return "\n\nVersion: ${BuildConfig.VERSION_NAME} \nDevice: ${Build.BRAND} ${Build.MODEL} (SDK: ${Build.VERSION.SDK_INT})\n"
    }

    fun updateLanguagePrefSummary() {
        // TODO: resolve RTL vs LTR with multiple languages (e.g. list contains English and Hebrew)
        findPreference(R.string.preference_key_language).summary = WikipediaApp.instance.languageState.appLanguageLocalizedNames
    }

}
