package org.wikipedia.feed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wikipedia.R
import org.wikipedia.compose.components.TabsBox
import org.wikipedia.compose.components.WikiLangCodeBox
import org.wikipedia.compose.components.menu.PageOverflowMenuViewModel
import org.wikipedia.compose.extensions.pulse
import org.wikipedia.compose.theme.BaseTheme
import org.wikipedia.compose.theme.WikipediaTheme
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.extensions.getString
import org.wikipedia.feed.model.Card
import org.wikipedia.language.AppLanguageState
import org.wikipedia.main.MainActivity
import org.wikipedia.theme.Theme
import org.wikipedia.util.DimenUtil
import kotlin.collections.orEmpty

@Composable
fun HomeScreen(
    wikiSite: WikiSite,
    languageState: AppLanguageState? = null,
    selectedTab: HomeTab,
    communityContentState: CommunityContentState,
    overflowMenuState: PageOverflowMenuViewModel.PageOverflowMenuState? = null,
    tabsState: TabsState,
    onAction: (HomeAction) -> Unit = {}
) {
    val context = LocalContext.current
    val topInset = if (context is MainActivity) {
        DimenUtil.roundedPxToDp((context.getStatusBarInsets()?.top ?: 0).toFloat())
    } else 64
    val pullToRefreshState = rememberPullToRefreshState()
    val isRefreshing = pullToRefreshState.isAnimating && communityContentState.isInitialLoading

    PullToRefreshBox(
        modifier = Modifier.fillMaxSize(),
        state = pullToRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = { onAction(HomeAction.RefreshTab(selectedTab)) },
        indicator = {
            Indicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = isRefreshing,
                containerColor = WikipediaTheme.colors.paperColor,
                color = WikipediaTheme.colors.progressiveColor,
                state = pullToRefreshState
            )
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            when (selectedTab) {
                HomeTab.COMMUNITY -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxSize()
                            .background(WikipediaTheme.colors.paperColor)
                    ) {
                        HomeToolbar(
                            topInset = topInset,
                            tabsState = tabsState,
                            onTabClick = { onAction(HomeAction.TabClick) },
                            onUpdateTabCount = { onAction(HomeAction.UpdateTabCount) }
                        )

                        HomeTabBar(
                            modifier = Modifier.padding(top = 8.dp),
                            wikiSite = wikiSite,
                            selectedTab = selectedTab,
                            languageState = languageState,
                            onSelectTab = { tab, card -> onAction(HomeAction.SelectTab(tab, card)) },
                            onLanguageSelected = { onAction(HomeAction.LanguageSelected(it)) },
                            onManageLanguagesClick = { onAction(HomeAction.ManageLanguagesClick) }
                        )

                        CommunityContentTab(
                            modifier = Modifier.weight(1f),
                            wikiSite = wikiSite,
                            state = communityContentState,
                            overflowMenuState = overflowMenuState,
                            onAction = onAction
                        )
                    }
                }

            }
        }
    }
}

@Composable
fun HomeToolbar(
    topInset: Int,
    tabsState: TabsState,
    onTabClick: () -> Unit,
    onUpdateTabCount: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Image(
            painter = painterResource(R.drawable.feed_header_wordmark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(WikipediaTheme.colors.primaryColor),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, top = (topInset + 16).dp)
                .width(128.dp)
        )
        Spacer(modifier = Modifier.weight(1f))

        val actionButtonModifier = Modifier
            .statusBarsPadding()
            .padding(top = topInset.dp)
            .size(48.dp)

        if (tabsState.count > 0) {
            IconButton(
                modifier = actionButtonModifier,
                onClick = { onTabClick() }
            ) {
                TabsBox(
                    modifier = Modifier
                        .width(21.dp)
                        .height(20.dp)
                        .then(
                            if (tabsState.pulse) {
                                Modifier.pulse(
                                    durationMillis = 300,
                                    toScale = 1.25f,
                                    onCompleted = {
                                        onUpdateTabCount()
                                    }
                                )
                            } else {
                                Modifier
                            }),
                    backgroundColor = Color.Transparent,
                    count = tabsState.count
                )
            }
        }
        if (tabsState.count == 0) {
            Spacer(modifier = actionButtonModifier)
        }
    }
}

@Composable
fun HomeTabBar(
    modifier: Modifier,
    wikiSite: WikiSite,
    selectedTab: HomeTab,
    languageState: AppLanguageState? = null,
    onSelectTab: (HomeTab, Card?) -> Unit = { _, _ -> },
    onLanguageSelected: (String) -> Unit,
    onManageLanguagesClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f)
        ) {
            HomeTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                val label = when (tab) {
                    HomeTab.COMMUNITY -> LocalContext.current.getString(wikiSite.languageCode, R.string.explore_feed_community_tab_label)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { onSelectTab(tab, null) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) WikipediaTheme.colors.progressiveColor else WikipediaTheme.colors.primaryColor,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(
                                if (isSelected) WikipediaTheme.colors.progressiveColor else Color.Transparent
                            )
                    )
                }
            }
        }
        LanguageDropDownMenu(
            selectedLanguageCode = wikiSite.languageCode,
            onLanguageSelected = { onLanguageSelected(it) },
            onManageLanguagesClick = { onManageLanguagesClick() },
            languageState = languageState
        )
    }
}

@Composable
fun LanguageDropDownMenu(
    selectedLanguageCode: String,
    onLanguageSelected: (String) -> Unit,
    onManageLanguagesClick: () -> Unit,
    languageState: AppLanguageState? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                expanded = true
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = WikipediaTheme.colors.primaryColor.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WikiLangCodeBox(
                modifier = Modifier
                    .height(20.dp)
                    .widthIn(min = 20.dp),
                languageCode = selectedLanguageCode,
                backgroundColor = WikipediaTheme.colors.primaryColor.copy(alpha = 0.8f),
                borderColor = Color.Transparent,
                textColor = WikipediaTheme.colors.paperColor,
            )
            Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(R.drawable.ic_arrow_down_24),
                contentDescription = null,
                tint = WikipediaTheme.colors.primaryColor
            )
        }
        DropdownMenu(
            expanded = expanded,
            containerColor = WikipediaTheme.colors.paperColor,
            onDismissRequest = { expanded = false }
        ) {
            val languageCodes = languageState?.appLanguageCodes.orEmpty()
            repeat(languageCodes.size) {
                val langCode = languageCodes[it]
                DropdownMenuItem(
                    leadingIcon = {
                        WikiLangCodeBox(
                            modifier = Modifier
                                .height(20.dp)
                                .widthIn(min = 20.dp),
                            languageCode = langCode,
                            borderColor = WikipediaTheme.colors.secondaryColor,
                            textColor = WikipediaTheme.colors.secondaryColor,
                        )
                    },
                    trailingIcon = {
                        if (langCode == selectedLanguageCode) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check_black_24dp),
                                contentDescription = null,
                                tint = WikipediaTheme.colors.secondaryColor
                            )
                        }
                    },
                    text = {
                        Text(
                            text = languageState?.getAppLanguageLocalizedName(langCode) ?: langCode,
                            style = MaterialTheme.typography.bodyLarge,
                            color = WikipediaTheme.colors.primaryColor
                        )
                    },
                    onClick = {
                        onLanguageSelected(langCode)
                        expanded = false
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = WikipediaTheme.colors.borderColor
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onManageLanguagesClick()
                        expanded = false
                    }
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = LocalContext.current.getString(selectedLanguageCode, R.string.explore_feed_manage_languages_label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = WikipediaTheme.colors.primaryColor
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenCommunityAllModulesOffPreview() {
    BaseTheme(currentTheme = Theme.LIGHT) {
        HomeScreen(
            wikiSite = WikiSite.preview(),
            selectedTab = HomeTab.COMMUNITY,
            communityContentState = CommunityContentState(emptyState = FeedEmptyState.ALL_MODULES_HIDDEN, wikiSite = WikiSite.preview()),
            tabsState = TabsState(1, false)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenCommunityPreview() {
    BaseTheme(currentTheme = Theme.LIGHT) {
        HomeScreen(
            wikiSite = WikiSite.preview(),
            selectedTab = HomeTab.COMMUNITY,
            communityContentState = CommunityContentState(isInitialLoading = true, wikiSite = WikiSite.preview()),
            tabsState = TabsState(1, false)
        )
    }
}

@Preview
@Composable
fun LanguageDropDownMenuPreview() {
    BaseTheme(currentTheme = Theme.LIGHT) {
        LanguageDropDownMenu(
            selectedLanguageCode = "en",
            onLanguageSelected = {},
            onManageLanguagesClick = {}
        )
    }
}
