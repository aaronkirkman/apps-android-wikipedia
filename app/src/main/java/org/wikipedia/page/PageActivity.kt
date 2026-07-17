package org.wikipedia.page

import android.app.SearchManager
import android.app.assist.AssistContent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ActionMode
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.wikipedia.Constants
import org.wikipedia.Constants.InvokeSource
import org.wikipedia.R
import org.wikipedia.WikipediaApp
import org.wikipedia.activity.BaseActivity
import org.wikipedia.analytics.eventplatform.BreadCrumbLogEvent
import org.wikipedia.analytics.testkitchen.TestKitchenAdapter
import org.wikipedia.commons.FilePageActivity
import org.wikipedia.concurrency.FlowEventBus
import org.wikipedia.databinding.ActivityPageBinding
import org.wikipedia.dataclient.Service
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.events.ArticleSavedOrDeletedEvent
import org.wikipedia.events.ChangeTextSizeEvent
import org.wikipedia.extensions.parcelableExtra
import org.wikipedia.gallery.GalleryActivity
import org.wikipedia.history.HistoryEntry
import org.wikipedia.language.LangLinksActivity
import org.wikipedia.navtab.NavTab
import org.wikipedia.page.linkpreview.LinkPreviewDialog
import org.wikipedia.page.tabs.TabActivity
import org.wikipedia.readinglist.ReadingListActivity
import org.wikipedia.readinglist.ReadingListMode
import org.wikipedia.search.HybridSearchAbCTest
import org.wikipedia.search.SearchActivity
import org.wikipedia.settings.Prefs
import org.wikipedia.staticdata.MainPageNameData
import org.wikipedia.util.DeviceUtil
import org.wikipedia.util.DimenUtil
import org.wikipedia.util.FeedbackUtil
import org.wikipedia.util.ReleaseUtil
import org.wikipedia.util.StringUtil
import org.wikipedia.util.ThrowableUtil
import org.wikipedia.util.UriUtil
import org.wikipedia.util.log.L
import org.wikipedia.views.FrameLayoutNavMenuTriggerer
import org.wikipedia.views.ObservableWebView
import org.wikipedia.views.ViewUtil
import org.wikipedia.widgets.readingchallenge.ReadingChallengeWidgetRepository
import java.time.LocalDate
import java.util.Locale

class PageActivity : BaseActivity(), PageFragment.Callback, LinkPreviewDialog.LoadPageCallback, FrameLayoutNavMenuTriggerer.Callback {

    enum class TabPosition {
        CURRENT_TAB, CURRENT_TAB_SQUASH, NEW_TAB_BACKGROUND, NEW_TAB_FOREGROUND, EXISTING_TAB
    }

    lateinit var binding: ActivityPageBinding
    private lateinit var toolbarHideHandler: ViewHideHandler
    private lateinit var pageFragment: PageFragment
    private lateinit var app: WikipediaApp
    private var hasTransitionAnimation = false
    private var wasTransitionShown = false
    private val currentActionModes = mutableSetOf<ActionMode>()
    private val isCabOpen get() = currentActionModes.isNotEmpty()
    private var exclusiveTooltipRunnable: Runnable? = null
    private var isTooltipShowing = false

    private val requestHandleIntentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == LangLinksActivity.ACTIVITY_RESULT_LANGLINK_SELECT || it.resultCode == GalleryActivity.ACTIVITY_RESULT_PAGE_SELECTED) {
            it.data?.let {
                binding.pageToolbarContainer.post { handleIntent(it) }
            }
        }
    }

    private val requestBrowseTabLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (app.tabCount == 0 && it.resultCode != TabActivity.RESULT_NEW_TAB) {
            // They browsed the tabs and cleared all of them, without wanting to open a new tab.
            finish()
            return@registerForActivityResult
        }
        if (it.resultCode == TabActivity.RESULT_NEW_TAB) {
            loadMainPage(TabPosition.NEW_TAB_FOREGROUND)
            animateTabsButton()
        } else if (it.resultCode == TabActivity.RESULT_LOAD_FROM_BACKSTACK) {
            pageFragment.reloadFromBackstack(false)
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!DeviceUtil.assertAppContext(this)) {
            return
        }

        app = WikipediaApp.instance

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        binding = ActivityPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                FlowEventBus.events.collectLatest { event ->
                    when (event) {
                        is ChangeTextSizeEvent -> {
                            pageFragment.updateFontSize()
                        }
                        is ArticleSavedOrDeletedEvent -> {
                            pageFragment.title?.run {
                                if (event.pages.any { it.apiTitle == prefixedText && it.lang == wikiSite.languageCode }) {
                                    pageFragment.updateBookmarkAndMenuOptionsFromDao()
                                }
                            }
                        }
                    }
                }
            }
        }

        updateProgressBar(false)
        pageFragment = supportFragmentManager.findFragmentById(R.id.page_fragment) as PageFragment

        // Toolbar setup
        setSupportActionBar(binding.pageToolbar)
        clearActionBarTitle()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.pageToolbarButtonSearch.setOnClickListener {
            pageFragment.articleInteractionEvent?.logSearchWikipediaClick()
            val articleTitle = if (pageFragment.title?.namespace() == Namespace.MAIN) pageFragment.title?.displayText else null
            startActivity(SearchActivity.newIntent(
                context = this@PageActivity,
                source = InvokeSource.TOOLBAR,
                query = null,
                title = articleTitle))
        }
        binding.pageToolbarButtonTabs.updateTabCount(false)
        binding.pageToolbarButtonTabs.setOnClickListener {
            pageFragment.articleInteractionEvent?.logTabsClick()
            requestBrowseTabLauncher.launch(TabActivity.newIntentFromPageActivity(this))
        }
        toolbarHideHandler = ViewHideHandler(binding.pageToolbarContainer, null, Gravity.TOP) { isTooltipShowing }
        FeedbackUtil.setButtonTooltip(binding.pageToolbarButtonTabs, binding.pageToolbarButtonShowOverflowMenu)
        binding.pageToolbarButtonShowOverflowMenu.setOnClickListener {
            pageFragment.showOverflowMenu(it)
            pageFragment.articleInteractionEvent?.logMoreClick()
            Prefs.showOneTimeCustomizeToolbarTooltip = false
        }

        // Navigation setup
        binding.navigationDrawer.setScrimColor(Color.TRANSPARENT)
        binding.containerWithNavTrigger.callback = this
        ViewCompat.setOnApplyWindowInsetsListener(binding.navigationDrawer) { view, insets ->
            val insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<MarginLayoutParams> {
                topMargin = insets.top
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            WindowInsetsCompat.CONSUMED
        }

        // WikiArticleCard setup
        hasTransitionAnimation = intent.getBooleanExtra(Constants.INTENT_EXTRA_HAS_TRANSITION_ANIM, false)
        binding.wikiArticleCardView.visibility = if (hasTransitionAnimation) View.VISIBLE else View.GONE

        val languageChanged = savedInstanceState?.let {
            app.appOrSystemLanguageCode != savedInstanceState.getString(LANGUAGE_CODE_BUNDLE_KEY).orEmpty()
        } ?: false

        if (languageChanged) {
            app.resetWikiSite()
            loadMainPage(TabPosition.EXISTING_TAB)
        }

        if (savedInstanceState == null) {
            // if there's no savedInstanceState, and we're not coming back from a Theme change,
            // then we must have been launched with an Intent, so... handle it!
            handleIntent(intent)
        }
    }

    override fun onStart() {
        try {
            super.onStart()
        } catch (e: Exception) {
            if (e.message.orEmpty().contains(EXCEPTION_MESSAGE_WEBVIEW, true) ||
                ThrowableUtil.getInnermostThrowable(e).message.orEmpty().contains(EXCEPTION_MESSAGE_WEBVIEW, true)) {
                // If the system failed to inflate our activity because of the WebView (which could
                // be one of several types of exceptions), it likely means that the system WebView
                // is in the process of being updated. In this case, show the user a message and
                // bail immediately.
                Toast.makeText(app, R.string.error_webview_updating, Toast.LENGTH_LONG).show()
                finish()
                return
            }
            throw e
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (!isDestroyed) {
            binding.pageToolbarButtonTabs.updateTabCount(false)
        }
        return false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (app.haveMainActivity) {
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    pageFragment.goToMainActivity(tab = NavTab.HOME, tabExtra = Constants.INTENT_EXTRA_GO_TO_MAIN_TAB)
                }
                true
            } else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        app.resetWikiSite()
        Prefs.temporaryWikitext = null
    }

    override fun onPause() {
        if (isCabOpen) {
            onPageCloseActionMode()
        }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(LANGUAGE_CODE_BUNDLE_KEY, app.appOrSystemLanguageCode)
    }

    override fun onActionModeStarted(mode: ActionMode) {
        super.onActionModeStarted(mode)
        if (!isCabOpen && mode.tag == null) {
            modifyMenu(mode)
            ViewUtil.setCloseButtonInActionMode(pageFragment.requireContext(), mode)
            pageFragment.onActionModeShown(mode)
        }
        currentActionModes.add(mode)
    }

    override fun onActionModeFinished(mode: ActionMode) {
        super.onActionModeFinished(mode)
        currentActionModes.remove(mode)
    }

    override fun onDestroy() {
        Prefs.hasVisitedArticlePage = true
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (isCabOpen) {
                onPageCloseActionMode()
                return
            }
            app.appSessionEvent.backPressed()
            if (pageFragment.onBackPressed()) {
                return
            }

            // If user enter PageActivity in portrait and leave in landscape,
            // we should hide the transition animation view to prevent bad animation.
            if (DimenUtil.isLandscape(this@PageActivity) || !hasTransitionAnimation) {
                binding.wikiArticleCardView.visibility = View.GONE
            } else {
                binding.wikiArticleCardView.visibility = View.VISIBLE
                binding.pageFragment.visibility = View.GONE
            }
            this.isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_F || !event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_F3) {
            pageFragment.showFindInPage()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNavMenuSwipeRequest(gravity: Int) {
        if (!isCabOpen && gravity == Gravity.END) {
            pageFragment.articleInteractionEvent?.logTocSwipe()
            pageFragment.sidePanelHandler.showToC()
        }
    }

    override fun onPageLoadComplete() {
        removeTransitionAnimState()
        maybeShowThemeTooltip()
        updateSearchHint()
        lifecycleScope.launch {
            ReadingChallengeWidgetRepository(this@PageActivity).updateOnArticleRead(LocalDate.now())
        }
    }

    override fun onPageDismissBottomSheet() {
        ExclusiveBottomSheetPresenter.dismiss(supportFragmentManager)
    }

    override fun onPageInitWebView(v: ObservableWebView) {
        toolbarHideHandler.setScrollView(v)
    }

    override fun onPageLoadPage(title: PageTitle, entry: HistoryEntry) {
        loadPage(title, entry, TabPosition.CURRENT_TAB)
    }

    override fun onPageShowLinkPreview(entry: HistoryEntry) {
        ExclusiveBottomSheetPresenter.show(supportFragmentManager, LinkPreviewDialog.newInstance(entry))
    }

    override fun onPageLoadMainPageInForegroundTab() {
        loadMainPage(TabPosition.EXISTING_TAB)
    }

    override fun onPageUpdateProgressBar(visible: Boolean) {
        updateProgressBar(visible)
    }

    override fun onPageStartSupportActionMode(callback: ActionMode.Callback) {
        startActionMode(callback)
    }

    override fun onPageHideSoftKeyboard() {
        DeviceUtil.hideSoftKeyboard(this)
    }

    override fun onPageLoadError(title: PageTitle) {
        supportActionBar?.title = title.displayText
        removeTransitionAnimState()
    }

    override fun onPageLoadErrorBackPressed() {
        finish()
    }

    override fun onPageSetToolbarElevationEnabled(enabled: Boolean) {
        binding.pageToolbarContainer.elevation = DimenUtil.dpToPx(if (enabled) DimenUtil.getDimension(R.dimen.toolbar_default_elevation) else 0F)
    }

    override fun onPageCloseActionMode() {
        val actionModesToFinish = HashSet(currentActionModes)
        for (mode in actionModesToFinish) {
            mode.finish()
        }
        currentActionModes.clear()
    }

    override fun onPageRequestLangLinks(title: PageTitle, historyEntryId: Long) {
        requestHandleIntentLauncher.launch(LangLinksActivity.newIntent(this, title, historyEntryId))
    }

    override fun onPageRequestGallery(title: PageTitle, fileName: String, wikiSite: WikiSite, revision: Long, isLeadImage: Boolean, options: ActivityOptionsCompat?) {
        requestHandleIntentLauncher.launch(GalleryActivity.newIntent(this, title, fileName, title.wikiSite, revision), options)
    }

    override fun onLinkPreviewLoadPage(title: PageTitle, entry: HistoryEntry, inNewTab: Boolean) {
        loadPage(title, entry, if (inNewTab) TabPosition.NEW_TAB_BACKGROUND else TabPosition.CURRENT_TAB)
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_VIEW == intent.action && intent.data != null) {
            TestKitchenAdapter.client.getInstrument("apps-open")
                .submitInteraction(action = "app_open", actionSource = "external_link")
            var uri = intent.data
            if (!ReleaseUtil.isPreBetaRelease && uri?.scheme != null && uri.scheme == "http") {
                // For external links, ensure that they're using https.
                uri = uri.buildUpon().scheme(WikiSite.DEFAULT_SCHEME).build()
            }
            uri?.let {
                if (!Service.isWikimediaAuthority(it.authority)) {
                    UriUtil.visitInExternalBrowser(this, it)
                    finish()
                    return
                }
                val wiki = WikiSite(it)
                val title = PageTitle.titleForUri(it, wiki)
                val historyEntry = HistoryEntry(title, if (intent.hasExtra(Constants.INTENT_EXTRA_NOTIFICATION_ID))
                    HistoryEntry.SOURCE_NOTIFICATION_SYSTEM else HistoryEntry.SOURCE_EXTERNAL_LINK)
                // Populate the referrer with the externally-referring URL, e.g. an external Browser URL, if present.
                ActivityCompat.getReferrer(this)?.let { uri ->
                    historyEntry.referrer = uri.toString()
                }
                if (title.namespace() == Namespace.SPECIAL && title.prefixedText.startsWith("Special:ReadingLists")) {
                    L.d("Received shareable reading lists")
                    val encodedListFromParameter = uri.getQueryParameter("limport")
                    Prefs.importReadingListsDialogShown = false
                    Prefs.receiveReadingListsData = encodedListFromParameter
                    startActivity(ReadingListActivity.newIntent(this, ReadingListMode.PREVIEW).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    finish()
                    return
                }
                // Special cases:
                // If the subdomain of the URL is not a "language" subdomain as we expect, then
                // bounce it out to an external browser. This can be links to the "donate." or
                // "thankyou." subdomains, or the Wikiquote "quote." subdomain, and possibly others.
                val language = wiki.languageCode.lowercase(Locale.getDefault())
                if (Constants.NON_LANGUAGE_SUBDOMAINS.contains(language) || (title.isSpecial && !title.isContributions)) {
                    UriUtil.visitInExternalBrowser(this, it)
                    finish()
                    return
                }
                loadPage(title, historyEntry, TabPosition.NEW_TAB_FOREGROUND)
            }
        } else if ((ACTION_LOAD_IN_NEW_TAB == intent.action || ACTION_LOAD_IN_CURRENT_TAB == intent.action ||
                    ACTION_LOAD_IN_CURRENT_TAB_SQUASH == intent.action) && intent.hasExtra(EXTRA_HISTORYENTRY)) {
            val title = intent.parcelableExtra<PageTitle>(Constants.ARG_TITLE)
            val historyEntry = intent.parcelableExtra<HistoryEntry>(EXTRA_HISTORYENTRY)
            when (intent.action) {
                ACTION_LOAD_IN_NEW_TAB -> loadPage(title, historyEntry, TabPosition.NEW_TAB_FOREGROUND)
                ACTION_LOAD_IN_CURRENT_TAB -> loadPage(title, historyEntry, TabPosition.CURRENT_TAB)
                ACTION_LOAD_IN_CURRENT_TAB_SQUASH -> loadPage(title, historyEntry, TabPosition.CURRENT_TAB_SQUASH)
            }
        } else if (ACTION_LOAD_FROM_EXISTING_TAB == intent.action && intent.hasExtra(EXTRA_HISTORYENTRY)) {
            val title = intent.parcelableExtra<PageTitle>(Constants.ARG_TITLE)
            val historyEntry = intent.parcelableExtra<HistoryEntry>(EXTRA_HISTORYENTRY)
            loadPage(title, historyEntry, TabPosition.EXISTING_TAB)
        } else if (ACTION_RESUME_READING == intent.action || intent.hasExtra(Constants.INTENT_APP_SHORTCUT_CONTINUE_READING)) {
            loadFilePageFromBackStackIfNeeded()
        } else if (Intent.ACTION_SEARCH == intent.action) {
            val query = intent.getStringExtra(SearchManager.QUERY)
            val title = PageTitle(query, app.wikiSite)
            val historyEntry = HistoryEntry(title, HistoryEntry.SOURCE_SEARCH)
            loadPage(title, historyEntry, TabPosition.EXISTING_TAB)
        } else if (ACTION_CREATE_NEW_TAB == intent.action) {
            loadMainPage(TabPosition.NEW_TAB_FOREGROUND)
        } else {
            loadMainPage(TabPosition.CURRENT_TAB)
        }
    }

    /**
     * Load a new page, and put it on top of the backstack, optionally allowing state loss of the
     * fragment manager. Useful for when this function is called from an AsyncTask result.
     * @param pageTitle Title of the page to load.
     * @param entry HistoryEntry associated with this page.
     * @param position Whether to open this page in the current tab, a new background tab, or new
     * foreground tab.
     */
    private fun loadPage(pageTitle: PageTitle?, entry: HistoryEntry?, position: TabPosition) {
        if (isDestroyed || pageTitle == null || entry == null) {
            return
        }
        if (hasTransitionAnimation && !wasTransitionShown) {
            binding.pageFragment.visibility = View.GONE
            binding.wikiArticleCardView.prepareForTransition(pageTitle)
            wasTransitionShown = true
        }
        if (loadNonArticlePageIfNeeded(pageTitle)) {
            return
        }

        // Accessibility
        title = getString(R.string.page_content_description, pageTitle.displayText)

        binding.pageToolbarContainer.post {
            if (!pageFragment.isAdded) {
                return@post
            }

            // Close the link preview, if one is open.
            hideLinkPreview()
            onPageCloseActionMode()
            when (position) {
                TabPosition.CURRENT_TAB -> pageFragment.loadPage(pageTitle, entry, pushBackStack = true, squashBackstack = false)
                TabPosition.CURRENT_TAB_SQUASH -> pageFragment.loadPage(pageTitle, entry, pushBackStack = true, squashBackstack = true)
                TabPosition.NEW_TAB_BACKGROUND -> pageFragment.openInNewBackgroundTab(pageTitle, entry)
                TabPosition.NEW_TAB_FOREGROUND -> pageFragment.openInNewForegroundTab(pageTitle, entry)
                else -> pageFragment.openFromExistingTab(pageTitle, entry)
            }
        }
    }

    private fun loadMainPage(position: TabPosition) {
        val title = PageTitle(MainPageNameData.valueFor(app.appOrSystemLanguageCode), app.wikiSite)
        val historyEntry = HistoryEntry(title, HistoryEntry.SOURCE_MAIN_PAGE)
        loadPage(title, historyEntry, position)
    }

    private fun loadFilePageFromBackStackIfNeeded() {
        if (pageFragment.currentTab.backStack.isNotEmpty()) {
            val item = pageFragment.currentTab.backStack[pageFragment.currentTab.backStackPosition]
            loadNonArticlePageIfNeeded(item.title)
        }
    }

    private fun loadNonArticlePageIfNeeded(title: PageTitle?): Boolean {
        if (title != null) {
            if (title.isFilePage) {
                startActivity(FilePageActivity.newIntent(this, title))
                finish()
                return true
            } else if (title.isSpecial) {
                UriUtil.visitInExternalBrowser(this, title.uri.toUri())
                finish()
                return true
            }
        }
        return false
    }

    private fun updateProgressBar(visible: Boolean) {
        binding.pageProgressBar.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun hideLinkPreview() {
        ExclusiveBottomSheetPresenter.dismiss(supportFragmentManager)
    }

    private fun removeTransitionAnimState() {
        if (binding.pageFragment.visibility != View.VISIBLE) {
            binding.pageFragment.visibility = View.VISIBLE
        }
        if (binding.wikiArticleCardView.visibility != View.GONE) {
            binding.wikiArticleCardView.postDelayed({ binding.wikiArticleCardView.visibility = View.GONE }, 250L)
        }
    }

    private fun modifyMenu(mode: ActionMode) {
        val menu = mode.menu

        // Hide context items that are intended for showing in external apps.
        menu.children.forEach {
            if (it.title.toString().contains(getString(R.string.search_hint)) ||
                (it.title.toString().contains(getString(R.string.menu_text_select_define)) && pageFragment.shareHandler.shouldEnableWiktionaryDialog())) {
                it.isVisible = false
            }
        }
        // Append our custom items to the context menu.
        mode.menuInflater.inflate(R.menu.menu_text_select, menu)
    }

    private fun maybeShowThemeTooltip() {
        if (!Prefs.showOneTimeCustomizeToolbarTooltip) {
            return
        }
        enqueueTooltip {
            FeedbackUtil.getTooltip(
                this,
                getString(R.string.theme_chooser_menu_item_short_tooltip),
                arrowAnchorPadding = -DimenUtil.roundedDpToPx(7f),
                topOrBottomMargin = 0,
                aboveOrBelow = true,
                autoDismiss = false,
                showDismissButton = true
            ).apply {
                setOnBalloonDismissListener {
                    Prefs.showOneTimeCustomizeToolbarTooltip = false
                    isTooltipShowing = false
                }
                BreadCrumbLogEvent.logTooltipShown(this@PageActivity, binding.pageToolbarButtonShowOverflowMenu)
                showAlignBottom(binding.pageToolbarButtonShowOverflowMenu)
                setCurrentTooltip(this)
                isTooltipShowing = true
            }
        }
    }

    private fun enqueueTooltip(runnable: Runnable) {
        if (exclusiveTooltipRunnable != null) {
            return
        }
        exclusiveTooltipRunnable = runnable
        binding.pageToolbar.postDelayed({
            exclusiveTooltipRunnable = null
            if (isDestroyed) {
                return@postDelayed
            }
            runnable.run()
        }, 500)
    }

    fun animateTabsButton() {
        toolbarHideHandler.ensureDisplayed()
        binding.pageToolbarButtonTabs.updateTabCount(true)
    }

    fun clearActionBarTitle() {
        supportActionBar?.title = ""
    }

    fun getToolbarMargin(): Int {
        return binding.pageToolbarContainer.height
    }

    fun getOverflowMenu(): View {
        return binding.pageToolbarButtonShowOverflowMenu
    }

    fun updateSearchHint() {
        if (Prefs.isHybridSearchOnboardingShown && HybridSearchAbCTest().isHybridSearchEnabled(WikipediaApp.instance.languageState.appLanguageCode) &&
            pageFragment.title?.namespace() == Namespace.MAIN) {
            val title = StringUtil.fromHtml(pageFragment.title?.displayText)
            binding.pageToolbarButtonSearch.text = getString(R.string.hybrid_search_article_search_hint, title)
        } else {
            binding.pageToolbarButtonSearch.text = getString(R.string.search_hint)
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        pageFragment.model.title?.let {
            outContent.webUri = it.uri.toUri()
        }
    }

    companion object {
        private const val LANGUAGE_CODE_BUNDLE_KEY = "language"
        private const val EXCEPTION_MESSAGE_WEBVIEW = "webview"
        const val ACTION_LOAD_IN_NEW_TAB = "org.wikipedia.load_in_new_tab"
        const val ACTION_LOAD_IN_CURRENT_TAB = "org.wikipedia.load_in_current_tab"
        const val ACTION_LOAD_IN_CURRENT_TAB_SQUASH = "org.wikipedia.load_in_current_tab_squash"
        const val ACTION_LOAD_FROM_EXISTING_TAB = "org.wikipedia.load_from_existing_tab"
        const val ACTION_CREATE_NEW_TAB = "org.wikipedia.create_new_tab"
        const val ACTION_RESUME_READING = "org.wikipedia.resume_reading"
        const val EXTRA_HISTORYENTRY = "org.wikipedia.history.historyentry"

        fun newIntent(context: Context): Intent {
            return Intent(ACTION_RESUME_READING).setClass(context, PageActivity::class.java)
        }

        fun newIntentForNewTab(context: Context): Intent {
            return Intent(ACTION_CREATE_NEW_TAB).setClass(context, PageActivity::class.java)
        }

        fun newIntentForNewTab(context: Context, entry: HistoryEntry, title: PageTitle): Intent {
            return Intent(ACTION_LOAD_IN_NEW_TAB)
                .setClass(context, PageActivity::class.java)
                .putExtra(EXTRA_HISTORYENTRY, entry)
                .putExtra(Constants.ARG_TITLE, title)
        }

        fun newIntentForCurrentTab(context: Context, entry: HistoryEntry, title: PageTitle, squashBackstack: Boolean = true): Intent {
            return Intent(if (squashBackstack) ACTION_LOAD_IN_CURRENT_TAB_SQUASH else ACTION_LOAD_IN_CURRENT_TAB)
                .setClass(context, PageActivity::class.java)
                .putExtra(EXTRA_HISTORYENTRY, entry)
                .putExtra(Constants.ARG_TITLE, title)
        }

        fun newIntentForExistingTab(context: Context, entry: HistoryEntry, title: PageTitle): Intent {
            return Intent(ACTION_LOAD_FROM_EXISTING_TAB)
                .setClass(context, PageActivity::class.java)
                .putExtra(EXTRA_HISTORYENTRY, entry)
                .putExtra(Constants.ARG_TITLE, title)
        }
    }
}
