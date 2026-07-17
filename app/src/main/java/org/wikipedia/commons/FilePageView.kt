package org.wikipedia.commons

import android.content.Context
import android.icu.text.ListFormatter
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import org.wikipedia.Constants
import org.wikipedia.Constants.PREFERRED_GALLERY_IMAGE_SIZE
import org.wikipedia.R
import org.wikipedia.WikipediaApp
import org.wikipedia.databinding.ViewFilePageBinding
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.dataclient.mwapi.MwQueryPage
import org.wikipedia.history.HistoryEntry
import org.wikipedia.page.LinkMovementMethodExt
import org.wikipedia.page.PageActivity
import org.wikipedia.page.PageTitle
import org.wikipedia.util.ImageUrlUtil
import org.wikipedia.util.ResourceUtil
import org.wikipedia.util.StringUtil
import org.wikipedia.util.UriUtil
import org.wikipedia.views.ImageDetailView
import org.wikipedia.views.ImageZoomHelper
import org.wikipedia.views.ViewUtil
import java.util.Locale

class FilePageView(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {

    val binding = ViewFilePageBinding.inflate(LayoutInflater.from(context), this)

    init {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    fun setup(summaryForEdit: FilePageSummary,
              imageTags: Map<String, List<String>>,
              page: MwQueryPage,
              containerWidth: Int,
              thumbWidth: Int,
              thumbHeight: Int,
              imageFromCommons: Boolean,
              showFilename: Boolean) {

        loadImage(summaryForEdit, containerWidth, thumbWidth, thumbHeight)

        if (showFilename) {
            binding.filenameView.visibility = View.VISIBLE
            binding.filenameView.binding.titleText.text = context.getString(if (imageFromCommons) R.string.suggested_edits_image_preview_dialog_file_commons else R.string.suggested_edits_image_preview_dialog_image)
            binding.filenameView.binding.contentText.setTextIsSelectable(false)
            binding.filenameView.binding.contentText.maxLines = 3
            binding.filenameView.binding.contentText.ellipsize = TextUtils.TruncateAt.END
            binding.filenameView.binding.contentText.text = StringUtil.removeNamespace(summaryForEdit.displayTitle)
            binding.filenameView.binding.divider.visibility = View.GONE
        }

        binding.detailsContainer.removeAllViews()

        addDetail(
            titleString = context.getString(R.string.suggested_edits_image_preview_dialog_caption_in_language_title,
                WikipediaApp.instance.languageState.getAppLanguageLocalizedName(getProperLanguageCode(summaryForEdit, imageFromCommons))),
            detail = if (summaryForEdit.pageTitle.description.isNullOrEmpty()) summaryForEdit.description else summaryForEdit.pageTitle.description
        )

        addDetail(titleString = context.getString(R.string.suggested_edits_image_tags), detail = getImageTags(imageTags, getProperLanguageCode(summaryForEdit, imageFromCommons)))

        addDetail(titleString = context.getString(R.string.suggested_edits_image_caption_summary_title_author), detail = summaryForEdit.metadata!!.artist())
        addDetail(titleString = context.getString(R.string.suggested_edits_image_preview_dialog_date), detail = summaryForEdit.metadata!!.dateTime())
        addDetail(titleString = context.getString(R.string.suggested_edits_image_caption_summary_title_source), detail = summaryForEdit.metadata!!.credit())
        addDetail(titleString = context.getString(R.string.suggested_edits_image_preview_dialog_licensing), detail = summaryForEdit.metadata!!.licenseShortName(), externalLink = summaryForEdit.metadata!!.licenseUrl())
        if (imageFromCommons) {
            addDetail(false, context.getString(R.string.suggested_edits_image_preview_dialog_more_info), context.getString(R.string.suggested_edits_image_preview_dialog_file_page_link_text), context.getString(R.string.suggested_edits_image_file_page_commons_link, summaryForEdit.title))
        } else {
            addDetail(false, context.getString(R.string.suggested_edits_image_preview_dialog_more_info), context.getString(R.string.suggested_edits_image_preview_dialog_file_page_wikipedia_link_text), summaryForEdit.pageTitle.uri)
        }
        requestLayout()
    }

    private fun getImageTags(imageTags: Map<String, List<String>>, languageCode: String): String? {
        if (!imageTags.containsKey(languageCode)) {
            return null
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && imageTags.isNotEmpty()) {
            ListFormatter.getInstance(Locale(languageCode)).format(imageTags[languageCode])
        } else {
            imageTags[languageCode]?.joinToString(separator = "\n")
        }
    }

    private fun getProperLanguageCode(summary: FilePageSummary, imageFromCommons: Boolean): String {
        return if (!imageFromCommons || summary.lang == Constants.WIKI_CODE_COMMONS) {
            WikipediaApp.instance.languageState.appLanguageCode
        } else {
            summary.lang
        }
    }

    private fun loadImage(summaryForEdit: FilePageSummary, containerWidth: Int, thumbWidth: Int, thumbHeight: Int) {
        ImageZoomHelper.setViewZoomable(binding.imageView)
        ViewUtil.loadImage(binding.imageView, ImageUrlUtil.getUrlForPreferredSize(summaryForEdit.thumbnailUrl!!, PREFERRED_GALLERY_IMAGE_SIZE),
            force = true,
            listener = null
        )
        binding.imageViewPlaceholder.layoutParams = LayoutParams(containerWidth, ViewUtil.adjustImagePlaceholderHeight(containerWidth.toFloat(), thumbWidth.toFloat(), thumbHeight.toFloat()))
    }

    private fun addDetail(
        showDivider: Boolean = true, titleString: String, detail: String? = null,
        externalLink: String? = null, listener: OnClickListener? = null
    ) {
        if (!detail.isNullOrEmpty()) {
            val view = ImageDetailView(context)
            view.binding.titleText.text = titleString
            view.binding.contentText.text = StringUtil.fromHtml(StringUtil.removeStyleTags(detail)).trim()
            if (!externalLink.isNullOrEmpty()) {
                view.binding.contentText.setTextColor(ResourceUtil.getThemedColor(context, R.attr.progressive_color))
                view.binding.contentText.setTextIsSelectable(false)
                view.binding.externalLink.visibility = View.VISIBLE
                view.binding.contentContainer.setOnClickListener {
                    UriUtil.visitInExternalBrowser(context, Uri.parse(UriUtil.resolveProtocolRelativeUrl(externalLink)))
                }
            } else {
                view.binding.contentText.movementMethod = movementMethod
            }
            if (!showDivider) {
                view.binding.divider.visibility = View.GONE
            }
            if (listener != null) {
                view.binding.editButton.visibility = View.VISIBLE
                view.binding.editButton.setOnClickListener(listener)
                view.binding.editButton.contentDescription = context.getString(R.string.file_page_edit_button_content_description, titleString)
            }
            binding.detailsContainer.addView(view)
        }
    }

    private val movementMethod = LinkMovementMethodExt { url ->
        val uri = Uri.parse(UriUtil.resolveProtocolRelativeUrl(url))
        if (UriUtil.isValidPageLink(uri)) {
            val entry = HistoryEntry(PageTitle.titleForUri(uri, WikiSite(uri)), HistoryEntry.SOURCE_FILE_PAGE)
            context.startActivity(PageActivity.newIntentForNewTab(context, entry, entry.title))
        } else {
            UriUtil.handleExternalLink(context, Uri.parse(UriUtil.resolveProtocolRelativeUrl(url)))
        }
    }
}
