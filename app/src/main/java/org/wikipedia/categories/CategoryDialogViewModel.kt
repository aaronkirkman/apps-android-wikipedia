package org.wikipedia.categories

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.wikipedia.Constants
import org.wikipedia.database.AppDatabase
import org.wikipedia.dataclient.ServiceFactory
import org.wikipedia.page.PageTitle
import org.wikipedia.util.Resource
import org.wikipedia.util.StringUtil

class CategoryDialogViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    val pageTitle = savedStateHandle.get<PageTitle>(Constants.ARG_TITLE)!!
    val categoriesData = MutableLiveData<Resource<List<PageTitle>>>()
    val categoryCounts = MutableLiveData<Map<String, Int>>()
    val saveAllState = MutableLiveData<Resource<Pair<PageTitle, Int>>>()

    init {
        fetchCategories()
    }

    private fun fetchCategories() {
        viewModelScope.launch(CoroutineExceptionHandler { _, throwable ->
            categoriesData.postValue(Resource.Error(throwable))
        }) {
            val response = ServiceFactory.get(pageTitle.wikiSite).getCategories(pageTitle.prefixedText)
            val titles = response.query?.pages?.map { page ->
                PageTitle(page.title, pageTitle.wikiSite).also {
                    it.displayText = page.displayTitle(pageTitle.wikiSite.languageCode)
                }
            }.orEmpty()
            categoriesData.postValue(Resource.Success(titles))
            fetchCategoryCounts(titles)
        }
    }

    private fun fetchCategoryCounts(titles: List<PageTitle>) {
        if (titles.isEmpty()) {
            return
        }
        viewModelScope.launch(CoroutineExceptionHandler { _, _ -> }) {
            val response = ServiceFactory.get(pageTitle.wikiSite).getCategoryInfo(titles.joinToString("|") { it.prefixedText })
            val counts = response.query?.pages?.associate { StringUtil.addUnderscores(it.title) to (it.categoryInfo?.size ?: 0) }.orEmpty()
            categoryCounts.postValue(counts)
        }
    }

    fun saveCategoryForOffline(category: PageTitle) {
        viewModelScope.launch(CoroutineExceptionHandler { _, throwable ->
            saveAllState.postValue(Resource.Error(throwable))
        }) {
            saveAllState.postValue(Resource.Loading())

            val members = mutableListOf<PageTitle>()
            var continueStr: String? = null
            do {
                val response = ServiceFactory.get(category.wikiSite)
                    .getCategoryMembers(category.prefixedText, "page", 500, continueStr)
                response.query?.pages?.forEach { page ->
                    members.add(PageTitle(page.title, category.wikiSite).also {
                        it.displayText = page.displayTitle(category.wikiSite.languageCode)
                    })
                }
                continueStr = response.continuation?.gcmContinuation
            } while (continueStr != null)

            val listTitle = StringUtil.removeNamespace(category.displayText)
            val existingList = AppDatabase.instance.readingListDao().getListsWithoutContents()
                .find { it.title.equals(listTitle, ignoreCase = true) }
            val list = existingList ?: AppDatabase.instance.readingListDao().createList(listTitle, null)
            val added = AppDatabase.instance.readingListPageDao()
                .addPagesToListIfNotExist(list, members, excludeImages = true)

            saveAllState.postValue(Resource.Success(category to added.size))
        }
    }
}
