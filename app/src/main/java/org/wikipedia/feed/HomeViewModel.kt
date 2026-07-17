package org.wikipedia.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.wikipedia.R
import org.wikipedia.WikipediaApp
import org.wikipedia.dataclient.ServiceFactory
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.feed.dayheader.DayHeaderCard
import org.wikipedia.feed.didyouknow.DidYouKnowCard
import org.wikipedia.feed.featured.FeaturedArticleCard
import org.wikipedia.feed.image.FeaturedImageCard
import org.wikipedia.feed.model.Card
import org.wikipedia.feed.news.NewsCard
import org.wikipedia.feed.onthisday.OnThisDayCard
import org.wikipedia.feed.topread.TopReadCard
import org.wikipedia.settings.Prefs
import org.wikipedia.settings.SettingsRepository
import org.wikipedia.settings.homefeed.CommunityModuleType
import java.time.LocalDate

enum class HomeTab { COMMUNITY }
private const val MAX_STOP_TIMEOUT_MILLIS = 5000L

data class CommunityContentState(
    val cards: List<Card> = emptyList(),
    val wikiSite: WikiSite = WikiSite.forLanguageCode(Prefs.homeLanguageCode),
    val isInitialLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: Throwable? = null,
    val canLoadMore: Boolean = true,
    val emptyState: FeedEmptyState? = null
)

enum class FeedEmptyState { ALL_MODULES_HIDDEN, NO_DATA }

data class TabsState(val count: Int, val pulse: Boolean)

val noImageCardBackgroundColors = listOf(R.color.maroon800, R.color.purple800, R.color.pink800)
val noImageCardForegroundColors = listOf(R.color.maroon300, R.color.purple300, R.color.pink300)

class HomeViewModel : ViewModel() {
    private val _wikiSite = MutableStateFlow(WikiSite.forLanguageCode(Prefs.homeLanguageCode))
    val wikiSite = _wikiSite.asStateFlow()

    private val _selectedTab = MutableStateFlow(HomeTab.COMMUNITY)
    val selectedTab = _selectedTab.asStateFlow()

    private val _communityState = MutableStateFlow(CommunityContentState())
    val communityState = combine(
        _communityState,
        SettingsRepository.hiddenModules,
        SettingsRepository.hiddenCards
    ) { state, hiddenModules, hiddenCards ->
        val visibleItems = state.cards
            .filterNot { hiddenModules.contains(it.moduleKey()) }
            .filterNot { hiddenCards.contains(it.hideKey) }
        val hasContent = visibleItems.any { it !is DayHeaderCard }
        val areAllModulesHidden = CommunityModuleType.entries.all { hiddenModules.contains(it.name) }
        val emptyState = when {
            areAllModulesHidden -> FeedEmptyState.ALL_MODULES_HIDDEN
            !state.isInitialLoading && state.error == null && !hasContent -> FeedEmptyState.NO_DATA
            else -> null
        }
        state.copy(cards = visibleItems, emptyState = emptyState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(MAX_STOP_TIMEOUT_MILLIS), CommunityContentState())

    // "age" in days from today. 0 = today, 1 = yesterday, etc.
    private var nextCommunityAge = 0

    private val _tabsState = MutableStateFlow(TabsState(WikipediaApp.instance.tabCount, pulse = false))
    val tabsState = _tabsState.asStateFlow()

    private val communityHandler = CoroutineExceptionHandler { _, throwable ->
        _communityState.value = _communityState.value.copy(
            isInitialLoading = false,
            isLoadingMore = false,
            error = throwable
        )
    }

    init {
        viewModelScope.launch {
            SettingsRepository.migrateLegacyHiddenCards()
        }
        viewModelScope.launch {
            combine(_selectedTab, _wikiSite) { tab, site -> tab to site }
            .distinctUntilChanged()
            .collect { (tab, site) ->
                ensureContentLoaded(tab, site)
            }
        }
    }

    fun refreshCommunityContent() {
        nextCommunityAge = 0
        _communityState.update { CommunityContentState() }
        loadCommunityContent()
    }

    fun selectTab(tab: HomeTab) {
        _selectedTab.value = tab
    }

    private fun ensureContentLoaded(tab: HomeTab, site: WikiSite) {
        when (tab) {
            HomeTab.COMMUNITY -> {
                if (_communityState.value.wikiSite != site) {
                    refreshCommunityContent()
                } else if (_communityState.value.cards.isEmpty() && !_communityState.value.isInitialLoading) {
                    loadCommunityContent()
                }
            }
        }
    }

    fun updateLanguage(langCode: String) {
        Prefs.homeLanguageCode = langCode
        _wikiSite.value = WikiSite.forLanguageCode(langCode)
    }

    fun updateTabCount(pulse: Boolean = false) {
        _tabsState.value = TabsState(WikipediaApp.instance.tabCount, pulse)
    }

    fun updateSelectedLanguageIfNeeded() {
        if (!WikipediaApp.instance.languageState.appLanguageCodes.contains(wikiSite.value.languageCode)) {
            updateLanguage(WikipediaApp.instance.languageState.appLanguageCode)
        }
    }

    /**
     * Loads the next day's community content (today on first call, then progressively older).
     * Safe to call as a retry — the age only advances after a successful fetch.
     */
    fun loadCommunityContent() {
        if (_communityState.value.isInitialLoading || _communityState.value.isLoadingMore) return

        viewModelScope.launch(communityHandler) {
            val isInitial = _communityState.value.cards.isEmpty()
            _communityState.value = _communityState.value.copy(
                wikiSite = wikiSite.value,
                isInitialLoading = isInitial,
                isLoadingMore = !isInitial,
                error = null
            )

            val age = nextCommunityAge
            val date = LocalDate.now().minusDays(nextCommunityAge.toLong())
            val content = ServiceFactory.getRest(wikiSite.value)
                .getFeedFeatured(date.year.toString(), "%02d".format(date.monthValue), "%02d".format(date.dayOfMonth), wikiSite.value.languageCode)

            // Construct Card objects based on the day's content
            val cardsForDay = buildList<Card> {
                content.tfa?.let {
                    add(FeaturedArticleCard(it, age, wikiSite.value))
                }
                content.topRead?.let {
                    add(TopReadCard(it, age, wikiSite.value))
                }
                content.dyk?.let {
                    add(DidYouKnowCard(it, date.toString(), wikiSite.value))
                }
                if (!content.news.isNullOrEmpty()) {
                    add(NewsCard(content.news, age, wikiSite.value))
                }
                if (!content.onthisday.isNullOrEmpty()) {
                    add(OnThisDayCard(content.onthisday.take(2), age, wikiSite.value))
                }
                content.potd?.let {
                    add(FeaturedImageCard(it, age, wikiSite.value))
                }
            }.toMutableList()
            if (cardsForDay.isNotEmpty()) {
                cardsForDay.add(0, DayHeaderCard(age))
            }

            // Advance age only after success, so retry on failure re-fetches the same day.
            nextCommunityAge = age + 1

            _communityState.value = _communityState.value.copy(
                cards = _communityState.value.cards + cardsForDay,
                isInitialLoading = false,
                isLoadingMore = false,
                error = null,
                canLoadMore = true
            )
        }
    }

    fun hideCommunityCard(card: Card) {
        viewModelScope.launch {
            SettingsRepository.addHiddenCard(card.hideKey)
        }
    }

    fun restoreCommunityCard(card: Card) {
        viewModelScope.launch {
            SettingsRepository.removeHiddenCard(card.hideKey)
        }
    }

    fun hideModule(moduleKey: String) {
        viewModelScope.launch {
            SettingsRepository.addHiddenModule(moduleKey)
        }
    }

    fun restoreModule(moduleKey: String) {
        viewModelScope.launch {
            SettingsRepository.removeHiddenModule(moduleKey)
        }
    }
}
