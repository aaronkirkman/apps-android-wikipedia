package org.wikipedia.feed.personalization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.wikipedia.database.AppDatabase
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.feed.personalization.interest.ArticlesState
import org.wikipedia.feed.personalization.interest.InterestSelectionRepository
import org.wikipedia.feed.personalization.interest.InterestUiState
import org.wikipedia.feed.personalization.interest.OnboardingTopic
import org.wikipedia.page.PageTitle
import org.wikipedia.settings.Prefs
import org.wikipedia.topics.ArticleTopics
import org.wikipedia.util.log.L

// this is a raw, flat, internal representation of ALL state
// needed across the personalization flow (interest selection)
// this enables SINGLE SOURCE OF TRUTH — one place to update, no risk of states going out of sync
// DERIVED UI STATES — each screen gets its own UI state derived from a function like toInterestUIState()
// instead of maintaining separate StateFlows per screen or one giant combined UI state
private data class PersonalizedViewModelState(
    // Interest screen
    val topics: List<OnboardingTopic> = ArticleTopics.all.map { OnboardingTopic(it) },
    val articles: List<PageTitle> = emptyList(),
    val articlesLoading: Boolean = false,
    val articlesError: Throwable? = null,
    val selectedArticles: Set<PageTitle> = emptySet(),
    val selectedTopics: List<OnboardingTopic> = emptyList(),
    val languageCode: String
) {
    fun toInterestUiState(): InterestUiState {
        return InterestUiState(
            topicsList = topics.map {
                it.copy(isSelected = selectedTopics.any { selected -> selected.topic.topicId == it.topic.topicId })
            },
            articlesState = when {
                articlesLoading -> ArticlesState.Loading
                articlesError != null -> ArticlesState.Error(articlesError)

                else -> ArticlesState.Success(
                    articles = articles,
                    selectedArticles = selectedArticles
                )
            },
            totalSelectedCount = selectedTopics.size + selectedArticles.size,
            languageCode = languageCode
        )
    }
}

class PersonalizationViewModel(
    private val interestSelectionRepository: InterestSelectionRepository
) : ViewModel() {
    // Single source of truth for all personalization state, can be easily extended to include language selection state as well
    private val state = MutableStateFlow(PersonalizedViewModelState(
        languageCode = interestSelectionRepository.wikiSite.languageCode
    ))
    private var articlesJob: Job? = null
    var interestsUpdated = false

    // Each screen observes only its own derived UI state
    // runs automatically when any part of the raw state changes
    val interestUiState = state
        .map { it.toInterestUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = state.value.toInterestUiState()
        )

    fun onPageChanged(screen: PersonalizationPage) {
        when (screen) {
            PersonalizationPage.INTERESTS -> loadInterestSelectionScreen()
            else -> {}
        }
    }

    private fun loadInterestSelectionScreen() {
        viewModelScope.launch( CoroutineExceptionHandler { _, throwable ->
            L.e(throwable)
         }) {
            initialize()
        }
    }

    private suspend fun initialize() {
        runCatching {
            val langCode = interestSelectionRepository.wikiSite.languageCode
            // check db for persisted interest (topic and articles) data
            val persistedTopics = interestSelectionRepository.getPersistedTopics()
            val persistedArticles = interestSelectionRepository.getPersistedArticles(langCode)

            val hasPersistedData = persistedTopics.isNotEmpty() || persistedArticles.isNotEmpty()
            if (!hasPersistedData && state.value.articles.isEmpty()) {
                loadInitialArticles()
                return@runCatching
            }

            // restore selections
            state.update { current ->
                current.copy(
                    selectedTopics = persistedTopics,
                    selectedArticles = persistedArticles.toSet()
                )
            }

            val lastTopic = persistedTopics.lastOrNull()
            if (lastTopic != null) {
                loadArticlesByTopic(topic = lastTopic)
            } else {
                loadInitialArticles()
            }
        }.onFailure { throwable ->
            state.update { it.copy(articlesLoading = false, articlesError = throwable) }
        }
    }

    private fun loadInitialArticles() {
        if (state.value.articles.isNotEmpty()) return
        articlesJob?.cancel()

        articlesJob = viewModelScope.launch(CoroutineExceptionHandler { _, throwable ->
            state.update { it.copy(articlesLoading = false, articlesError = throwable) }
        }) {
            state.update { it.copy(articlesLoading = true, articlesError = null) }

            val articles = interestSelectionRepository.loadInitialArticles()
            state.update { current ->
                val newArticles = (current.selectedArticles + articles).distinct()
                current.copy(
                    articles = newArticles,
                    articlesLoading = false
                )
            }
        }
    }

    private fun loadArticlesByTopic(topic: OnboardingTopic) {
        if (state.value.articles.isNotEmpty()) return
        articlesJob?.cancel()

        articlesJob = viewModelScope.launch(CoroutineExceptionHandler { _, throwable ->
            state.update { it.copy(articlesLoading = false, articlesError = throwable) }
        }) {
            state.update { it.copy(articlesLoading = true, articlesError = null) }

            val articles = interestSelectionRepository.getArticlesByTopic(topic.topic.queryTopicId)
            state.update { current ->
                val newArticles = (current.selectedArticles.toList() + articles).distinct()
                current.copy(articles = newArticles, articlesLoading = false)
            }
        }
    }

    // as we have a single state it becomes easier to update and control the state
    fun onTopicSelected(topic: OnboardingTopic) {
        // When a topic is selected, we want to reset the articles state and load articles for the selected topic
        viewModelScope.launch(CoroutineExceptionHandler { _, _ ->
        }) {
            interestsUpdated = true
            val currentTopics = state.value.selectedTopics
            val isSelected = currentTopics.any { selected -> selected.topic.topicId == topic.topic.topicId }

            val selectedTopics = if (isSelected) {
                currentTopics.filter { it.topic.topicId != topic.topic.topicId }
            } else {
                currentTopics + topic
            }

            if (isSelected) {
                interestSelectionRepository.deleteTopic(topic)
            } else {
                interestSelectionRepository.saveTopic(topic)
            }

            state.update { current ->
                current.copy(
                    selectedTopics = selectedTopics,
                    articles = emptyList(),
                    articlesError = null
                )
            }

            val lastSelectedTopic = selectedTopics.lastOrNull()
            if (lastSelectedTopic == null) loadInitialArticles() else loadArticlesByTopic(topic = lastSelectedTopic)
        }
    }

    fun addArticleFromSearch(title: PageTitle) {
        viewModelScope.launch(CoroutineExceptionHandler { _, throwable ->
                state.update { it.copy(articlesError = throwable) }
            }
        ) {
            interestsUpdated = true
            interestSelectionRepository.saveArticle(title, interestSelectionRepository.wikiSite.languageCode)
            state.update {
                val newItems = listOf(title) + it.articles
                val newSelection = it.selectedArticles + title
                it.copy(articles = newItems, selectedArticles = newSelection)
            }
        }
    }

    fun toggleArticleSelection(title: PageTitle) {
        val lang = interestSelectionRepository.wikiSite.languageCode

        viewModelScope.launch(CoroutineExceptionHandler { _, throwable ->
            state.update { it.copy(articlesError = throwable) }
        }) {
            interestsUpdated = true
            val current = state.value
            val isSelected = current.selectedArticles.contains(title)

            if (isSelected) {
                interestSelectionRepository.deleteArticle(title, lang)
            } else {
                interestSelectionRepository.saveArticle(title, lang)
            }

            state.update { currentState ->
                currentState.copy(
                    selectedArticles = if (isSelected) {
                        currentState.selectedArticles - title
                    } else {
                        currentState.selectedArticles + title
                    }
                )
            }
        }
    }

    fun deselectAllInterests() {
        viewModelScope.launch(CoroutineExceptionHandler { _, throwable ->
                state.update { it.copy(articlesError = throwable) }
            }
        ) {
            interestsUpdated = true
            interestSelectionRepository.deleteAllInterests()

            state.update {
                it.copy(
                    selectedArticles = emptySet(),
                    selectedTopics = emptyList(),
                    articlesLoading = false,
                    articlesError = null
                )
            }
        }
    }

    fun retryInterestsLoading() {
        val last = state.value.selectedTopics.lastOrNull()
        if (last != null) {
            loadArticlesByTopic(topic = last)
        } else {
            loadInitialArticles()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val appDatabase = AppDatabase.instance
                val wikiSite = WikiSite.forLanguageCode(Prefs.homeLanguageCode)
                PersonalizationViewModel(
                    interestSelectionRepository = InterestSelectionRepository(
                        interestTopicDao = appDatabase.topicInterestDao(),
                        interestArticleDao = appDatabase.articleInterestDao(),
                        historyEntryWithImageDao = appDatabase.historyEntryWithImageDao(),
                        readingListPageDao = appDatabase.readingListPageDao(),
                        wikiSite = wikiSite
                    )
                )
            }
        }
    }
}
