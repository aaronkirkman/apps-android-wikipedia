package org.wikipedia.activitytab

import kotlinx.serialization.Serializable
import org.wikipedia.R

@Serializable
data class ActivityTabModules(
    val isTimeSpentEnabled: Boolean = true,
    val isReadingInsightsEnabled: Boolean = true,
    val isEditingInsightsEnabled: Boolean = true,
    val isImpactEnabled: Boolean = true,
    val isTimelineEnabled: Boolean = true,
) {
    fun isModuleEnabled(moduleType: ModuleType): Boolean = when (moduleType) {
        ModuleType.TIME_SPENT -> isTimeSpentEnabled
        ModuleType.READING_INSIGHTS -> isReadingInsightsEnabled
        ModuleType.EDITING_INSIGHTS -> isEditingInsightsEnabled
        ModuleType.IMPACT -> isImpactEnabled
        ModuleType.TIMELINE -> isTimelineEnabled
    }

    fun setModuleEnabled(moduleType: ModuleType, enabled: Boolean) = when (moduleType) {
        ModuleType.TIME_SPENT -> copy(isTimeSpentEnabled = enabled)
        ModuleType.READING_INSIGHTS -> copy(isReadingInsightsEnabled = enabled)
        ModuleType.EDITING_INSIGHTS -> copy(isEditingInsightsEnabled = enabled)
        ModuleType.IMPACT -> copy(isImpactEnabled = enabled)
        ModuleType.TIMELINE -> copy(isTimelineEnabled = enabled)
    }

    fun isModuleVisible(moduleType: ModuleType): Boolean = isModuleEnabled(moduleType)

    fun noModulesVisible() = ModuleType.entries.all {
        !isModuleVisible(it)
    }

    fun areAllModulesEnabled(): Boolean {
        return ModuleType.entries.all { this.isModuleEnabled(it) }
    }
}

enum class ModuleType(val displayName: Int) {
    TIME_SPENT(R.string.activity_tab_customize_screen_time_spent_switch_title),
    READING_INSIGHTS(R.string.activity_tab_customize_screen_reading_insights_switch_title),
    EDITING_INSIGHTS(R.string.activity_tab_customize_screen_editing_insights_switch_title),
    IMPACT(R.string.activity_tab_customize_screen_impact_switch_title),
    TIMELINE(R.string.activity_tab_customize_screen_timeline_switch_title)
}
