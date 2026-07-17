package org.wikipedia.page.leadimages

import android.net.Uri
import androidx.core.app.ActivityOptionsCompat
import androidx.core.net.toUri
import org.wikipedia.R
import org.wikipedia.bridge.JavaScriptActionHandler
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.gallery.GalleryActivity
import org.wikipedia.page.PageFragment
import org.wikipedia.settings.Prefs
import org.wikipedia.util.DimenUtil
import org.wikipedia.views.ObservableWebView

class LeadImagesHandler(private val parentFragment: PageFragment,
                        webView: ObservableWebView,
                        private val pageHeaderView: PageHeaderView,
                        private val callback: PageFragment.Callback?) {
    private var displayHeightDp = 0
    private val isMainPage get() = page?.run { isMainPage } ?: false
    private val title get() = parentFragment.title
    private val page get() = parentFragment.page
    private val activity get() = parentFragment.requireActivity()

    private val isLeadImageEnabled get() = Prefs.isImageDownloadEnabled && !DimenUtil.isLandscape(activity) && displayHeightDp >= MIN_SCREEN_HEIGHT_DP && !isMainPage && !leadImageUrl.isNullOrEmpty()
    private val leadImageWidth get() = page?.run { pageProperties.leadImageWidth } ?: pageHeaderView.imageView.width
    private val leadImageHeight get() = page?.run { pageProperties.leadImageHeight } ?: pageHeaderView.imageView.height

    // Conditionally add the PageTitle's URL scheme and authority if these are missing from the
    // PageProperties' URL.
    private val leadImageUrl: String?
        get() {
            return title?.let {
                // Conditionally add the PageTitle's URL scheme and authority if these are missing from the
                // PageProperties' URL.
                val url = page?.run { pageProperties.leadImageUrl } ?: return@let null
                val fullUri = url.toUri()
                var scheme: String? = it.wikiSite.scheme()
                var authority: String? = it.wikiSite.authority()
                if (fullUri.scheme != null) {
                    scheme = fullUri.scheme
                }
                if (fullUri.authority != null) {
                    authority = fullUri.authority
                }
                return Uri.Builder()
                    .scheme(scheme)
                    .authority(authority)
                    .path(fullUri.path)
                    .toString()
            }
        }

    val topMargin get() = DimenUtil.roundedPxToDp(
        (if (isLeadImageEnabled) DimenUtil.leadImageHeightForDevice(activity) else parentFragment.toolbarMargin.toFloat()).toFloat()
    )

    init {
        pageHeaderView.setWebView(webView)
        webView.addOnScrollChangeListener(pageHeaderView)
        initDisplayDimensions()
        initArticleHeaderView()
    }

    private fun initDisplayDimensions() {
        displayHeightDp = (DimenUtil.displayHeightPx / DimenUtil.densityScalar).toInt()
    }

    private fun initArticleHeaderView() {
        pageHeaderView.callback = object : PageHeaderView.Callback {
            override fun onImageClicked() {
                openImageInGallery(null)
            }

            override fun onCallToActionClicked() {
                // no-op: editing is not supported in this build
            }
        }
    }

    fun hide() {
        pageHeaderView.hide()
    }

    fun refreshCallToActionVisibility() {
        pageHeaderView.refreshCallToActionVisibility()
    }

    fun loadLeadImage() {
        val url = leadImageUrl
        initDisplayDimensions()
        if (page != null && !isMainPage && !url.isNullOrEmpty() && isLeadImageEnabled) {
            pageHeaderView.show()
            pageHeaderView.loadImage(url)
        } else {
            pageHeaderView.loadImage(null)
        }
    }

    fun openImageInGallery(language: String?) {
        if (isLeadImageEnabled) {
            page?.pageProperties?.leadImageName?.let { imageName ->
                title?.let {
                    val filename = "File:$imageName"
                    val wiki = language?.run { WikiSite.forLanguageCode(this) } ?: it.wikiSite
                    val hitInfo = JavaScriptActionHandler.ImageHitInfo(pageHeaderView.imageView.left.toFloat(),
                        pageHeaderView.imageView.top.toFloat(), leadImageWidth.toFloat(), leadImageHeight.toFloat(),
                        leadImageUrl!!)
                    GalleryActivity.setTransitionInfo(hitInfo)
                    val options = ActivityOptionsCompat.makeSceneTransitionAnimation(activity, pageHeaderView.imageView, activity.getString(R.string.transition_page_gallery))
                    callback?.onPageRequestGallery(it, filename, wiki, parentFragment.revision, true, options)
                }
            }
        }
    }

    fun dispose() { }

    companion object {
        private const val MIN_SCREEN_HEIGHT_DP = 480
    }
}
