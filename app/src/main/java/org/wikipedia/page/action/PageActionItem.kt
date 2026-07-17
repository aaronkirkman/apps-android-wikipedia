package org.wikipedia.page.action

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.wikipedia.R
import org.wikipedia.model.EnumCode

@Suppress("unused")
enum class PageActionItem constructor(val id: Int,
                                      val viewId: Int,
                                      @StringRes val titleResId: Int,
                                      @DrawableRes val iconResId: Int = R.drawable.ic_settings_black_24dp,
                                      val isAvailableOnMobileWeb: Boolean = true,
                                      val isExternalLink: Boolean = false) : EnumCode {
    // TODO: Need to add the newly added item to the default const lists below
    SAVE(0, R.id.page_save, R.string.article_menu_bar_save_button, R.drawable.ic_bookmark_border_white_24dp, false) {
        override fun select(cb: Callback) {
            cb.onSaveSelected()
        }
    },
    LANGUAGE(1, R.id.page_language, R.string.article_menu_bar_language_button, R.drawable.ic_translate_white_24dp, false) {
        override fun select(cb: Callback) {
            cb.onLanguageSelected()
        }
    },
    FIND_IN_ARTICLE(2, R.id.page_find_in_article, R.string.menu_page_find_in_page, R.drawable.ic_find_in_page_24px) {
        override fun select(cb: Callback) {
            cb.onFindInArticleSelected()
        }
    },
    THEME(3, R.id.page_theme, R.string.article_menu_bar_theme_button, R.drawable.ic_icon_format_size, false) {
        override fun select(cb: Callback) {
            cb.onThemeSelected()
        }
    },
    CONTENTS(4, R.id.page_contents, R.string.article_menu_bar_contents_button, R.drawable.ic_icon_list, false) {
        override fun select(cb: Callback) {
            cb.onContentsSelected()
        }
    },
    SHARE(5, R.id.page_share, R.string.menu_page_article_share, R.drawable.ic_share) {
        override fun select(cb: Callback) {
            cb.onShareSelected()
        }
    },
    NEW_TAB(9, R.id.page_new_tab, R.string.menu_new_tab, R.drawable.ic_add_gray_white_24dp) {
        override fun select(cb: Callback) {
            cb.onNewTabSelected()
        }
    },
    HOME(10, R.id.page_home, R.string.home, R.drawable.ic_home_filled_24dp) {
        override fun select(cb: Callback) {
            cb.onHomeSelected()
        }
    },
    CATEGORIES(11, R.id.page_categories, R.string.action_item_categories, R.drawable.ic_category_black_24dp) {
        override fun select(cb: Callback) {
            cb.onCategoriesSelected()
        }
    },
    VIEW_ON_MAP(13, R.id.page_view_on_map, R.string.action_item_view_on_map, R.drawable.baseline_location_on_24, false) {
        override fun select(cb: Callback) {
            cb.onViewOnMapSelected()
        }
    };

    abstract fun select(cb: Callback)

    override fun code(): Int {
        // This enumeration is not marshalled so tying declaration order to presentation order is
        // convenient and consistent.
        return ordinal
    }

    interface Callback {
        fun onSaveSelected()
        fun onLanguageSelected()
        fun onFindInArticleSelected()
        fun onThemeSelected()
        fun onContentsSelected()
        fun onShareSelected()
        fun onNewTabSelected()
        fun onHomeSelected()
        fun onCategoriesSelected()
        fun onViewOnMapSelected()
        fun forwardClick()
    }

    companion object {
        val DEFAULT_TOOLBAR_LIST = listOf(SAVE, LANGUAGE, FIND_IN_ARTICLE, THEME, CONTENTS).map { it.id }
        val DEFAULT_OVERFLOW_MENU_LIST = listOf(SHARE, VIEW_ON_MAP, NEW_TAB, HOME, CATEGORIES).map { it.id }

        fun find(id: Int): PageActionItem {
            return entries.find { id == it.id || id == it.viewId } ?: entries[0]
        }

        @DrawableRes
        fun readingListIcon(pageSaved: Boolean): Int {
            return if (pageSaved) R.drawable.ic_bookmark_white_24dp else R.drawable.ic_bookmark_border_white_24dp
        }
    }
}
