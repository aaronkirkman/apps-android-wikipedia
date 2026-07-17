package org.wikipedia.page.linkpreview

import android.location.Location
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.wikipedia.database.AppDatabase
import org.wikipedia.dataclient.ServiceFactory
import org.wikipedia.history.HistoryEntry
import org.wikipedia.page.PageTitle
import org.wikipedia.settings.Prefs
import org.wikipedia.util.log.L

class LinkPreviewViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val _uiState = MutableStateFlow<LinkPreviewViewState>(LinkPreviewViewState.Loading)
    val uiState = _uiState.asStateFlow()
    val historyEntry = savedStateHandle.get<HistoryEntry>(LinkPreviewDialog.ARG_ENTRY)!!
    var pageTitle = historyEntry.title
    var location = savedStateHandle.get<Location>(LinkPreviewDialog.ARG_LOCATION)
    val fromPlaces = historyEntry.source == HistoryEntry.SOURCE_PLACES
    val lastKnownLocation = savedStateHandle.get<Location>(LinkPreviewDialog.ARG_LAST_KNOWN_LOCATION)
    var isInReadingList = false

    init {
        loadContent()
    }

    private fun loadContent() {
        viewModelScope.launch(CoroutineExceptionHandler { _, throwable ->
            _uiState.value = LinkPreviewViewState.Error(throwable)
        }) {
            val summaryCall = async { ServiceFactory.getRest(pageTitle.wikiSite)
                .getSummaryResponse(pageTitle.prefixedText) }

            val response = summaryCall.await()
            val summary = response.body()!!
            // Rebuild our PageTitle, since it may have been redirected or normalized.
            val oldFragment = pageTitle.fragment
            pageTitle = PageTitle(
                    summary.apiTitle, pageTitle.wikiSite, summary.thumbnailUrl,
                    summary.description, summary.displayTitle
            )

            // check if our URL was redirected, which might include a URL fragment that leads
            // to a specific section in the target article.
            if (!response.raw().request.url.fragment.isNullOrEmpty()) {
                pageTitle.fragment = response.raw().request.url.fragment
            } else if (!oldFragment.isNullOrEmpty()) {
                pageTitle.fragment = oldFragment
            }

            if (fromPlaces) {
                val readingList = AppDatabase.instance.readingListPageDao().findPageInAnyList(pageTitle)
                isInReadingList = readingList != null
            }

            if (location == null) {
                location = summary.coordinates
            }

            _uiState.value = LinkPreviewViewState.Content(summary)
        }
    }

    fun loadGallery() {
        if (Prefs.isImageDownloadEnabled && !pageTitle.isFilePage) {
            viewModelScope.launch(CoroutineExceptionHandler { _, throwable ->
                L.w("Failed to fetch gallery collection.", throwable)
            }) {
                val mediaList = ServiceFactory.getRest(pageTitle.wikiSite)
                    .getMediaList(pageTitle.prefixedText)
                val maxImages = 10
                val items = mediaList.getItems("image", "video").asReversed()
                val titleList =
                    items.filter { it.showInGallery }.map { it.title }.take(maxImages)
                if (titleList.isEmpty()) {
                    _uiState.value = LinkPreviewViewState.Completed
                } else {
                    val response = ServiceFactory.get(
                        pageTitle.wikiSite
                    ).getImageInfo(
                        titleList.joinToString("|"),
                        pageTitle.wikiSite.languageCode
                    )
                    val pageList =
                        response.query?.pages?.filter { it.imageInfo() != null }.orEmpty()
                    _uiState.value = LinkPreviewViewState.Gallery(pageList)
                }
            }
        } else {
            _uiState.value = LinkPreviewViewState.Completed
        }
    }

}
