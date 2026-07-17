package org.wikipedia.readinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.wikipedia.R
import org.wikipedia.database.AppDatabase
import org.wikipedia.readinglist.database.ReadingList
import org.wikipedia.readinglist.database.ReadingListPage
import org.wikipedia.settings.Prefs
import org.wikipedia.util.L10nUtil
import org.wikipedia.util.Resource

class ReadingListFragmentViewModel : ViewModel() {

    private val _updateListByIdFlow = MutableSharedFlow<Resource<ReadingList>>()
    val updateListByIdFlow = _updateListByIdFlow.asSharedFlow()

    private val _updateListFlow = MutableSharedFlow<Resource<ReadingList>>()
    val updateListFlow = _updateListFlow.asSharedFlow()

    private val _saveReadingListFlow = MutableSharedFlow<Resource<ReadingList>>()
    val saveReadingListFlow = _saveReadingListFlow.asSharedFlow()

    private val _deleteSelectedPagesFlow = MutableSharedFlow<Resource<List<ReadingListPage>>>()
    val deleteSelectedPagesFlow = _deleteSelectedPagesFlow.asSharedFlow()

    private val _yirListFlow = MutableStateFlow(Resource<ReadingList>())
    val yirListFlow = _yirListFlow.asStateFlow()

    fun updateListById(readingListId: Long) {
         viewModelScope.launch(CoroutineExceptionHandler { _, throwable ->
             viewModelScope.launch {
                 _updateListByIdFlow.emit(Resource.Error(throwable))
             }
        }) {
             val list = AppDatabase.instance.readingListDao().getListById(readingListId, true)
             if (list == null) {
                 _updateListByIdFlow.emit(Resource.Error(Throwable(L10nUtil.getString(R.string.error_message_generic))))
             } else {
                 _updateListByIdFlow.emit(Resource.Success(list))
             }
        }
    }

    fun updateList(emptyTitle: String, emptyDescription: String, encoded: Boolean) {
        viewModelScope.launch(CoroutineExceptionHandler { _, throwable ->
            viewModelScope.launch {
                _updateListFlow.emit(Resource.Error(throwable))
            }
        }) {
            val json = Prefs.receiveReadingListsData
            if (!json.isNullOrEmpty()) {
                val list = ReadingListsReceiveHelper.receiveReadingLists(emptyTitle, emptyDescription, json, encoded)
                _updateListFlow.emit(Resource.Success(list))
            }
        }
    }

    fun saveReadingList(readingList: ReadingList) {
        viewModelScope.launch(CoroutineExceptionHandler { _, throwable ->
            viewModelScope.launch {
                _saveReadingListFlow.emit(Resource.Error(throwable))
            }
        }) {
            readingList.id = AppDatabase.instance.readingListDao().insertReadingList(readingList)
            AppDatabase.instance.readingListPageDao().addPagesToList(readingList, readingList.pages, true)
            Prefs.readingListRecentReceivedId = readingList.id
            _saveReadingListFlow.emit(Resource.Success(readingList))
        }
    }

    fun deleteSelectedPages(readingList: ReadingList, pages: List<ReadingListPage>) {
        viewModelScope.launch(CoroutineExceptionHandler { _, throwable ->
            viewModelScope.launch {
                _deleteSelectedPagesFlow.emit(Resource.Error(throwable))
            }
        }) {
            if (pages.isNotEmpty()) {
                AppDatabase.instance.readingListPageDao().markPagesForDeletion(readingList, pages)
                _deleteSelectedPagesFlow.emit(Resource.Success(pages))
            }
        }
    }
}
