package org.wikipedia.categories

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.wikipedia.Constants
import org.wikipedia.R
import org.wikipedia.databinding.DialogCategoriesBinding
import org.wikipedia.extensions.setLayoutDirectionByLang
import org.wikipedia.page.ExtendedBottomSheetDialogFragment
import org.wikipedia.page.PageTitle
import org.wikipedia.readinglist.database.ReadingList
import org.wikipedia.util.FeedbackUtil
import org.wikipedia.util.Resource
import org.wikipedia.util.StringUtil
import org.wikipedia.util.log.L
import org.wikipedia.views.DrawableItemDecoration
import org.wikipedia.views.PageItemView

class CategoryDialog : ExtendedBottomSheetDialogFragment() {
    private var _binding: DialogCategoriesBinding? = null
    private val binding get() = _binding!!

    private val itemCallback = ItemCallback()
    private val viewModel: CategoryDialogViewModel by viewModels()
    private var categoryCounts: Map<String, Int> = emptyMap()
    private val savingCategories = mutableSetOf<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogCategoriesBinding.inflate(inflater, container, false)
        binding.categoriesRecycler.layoutManager = LinearLayoutManager(requireActivity())
        binding.categoriesRecycler.addItemDecoration(DrawableItemDecoration(requireContext(), R.attr.list_divider, drawStart = false, drawEnd = false))
        binding.categoriesDialogPageTitle.text = StringUtil.fromHtml(viewModel.pageTitle.displayText)
        binding.root.setLayoutDirectionByLang(viewModel.pageTitle.wikiSite.languageCode)

        binding.categoriesError.isVisible = false
        binding.categoriesNoneFound.isVisible = false
        binding.categoriesRecycler.isVisible = false
        binding.dialogCategoriesProgress.isVisible = true
        binding.categoriesError.backClickListener = View.OnClickListener { dismiss() }

        viewModel.categoriesData.observe(this) {
            binding.dialogCategoriesProgress.isVisible = false
            if (it is Resource.Success) {
                layOutCategories(it.data)
            } else if (it is Resource.Error) {
                binding.categoriesRecycler.isVisible = false
                binding.categoriesError.setError(it.throwable)
                binding.categoriesError.isVisible = true
                L.e(it.throwable)
            }
        }

        viewModel.categoryCounts.observe(this) {
            categoryCounts = it
            binding.categoriesRecycler.adapter?.notifyDataSetChanged()
        }

        viewModel.saveAllState.observe(this) {
            when (it) {
                is Resource.Success -> {
                    val (category, count) = it.data
                    savingCategories.remove(category.prefixedText)
                    binding.categoriesRecycler.adapter?.notifyDataSetChanged()
                    FeedbackUtil.makeSnackbar(requireActivity(),
                        resources.getQuantityString(R.plurals.category_save_all_offline_success, count, count,
                            StringUtil.removeNamespace(category.displayText))).show()
                }
                is Resource.Error -> {
                    savingCategories.clear()
                    binding.categoriesRecycler.adapter?.notifyDataSetChanged()
                    FeedbackUtil.showMessage(requireActivity(), R.string.category_save_all_offline_error)
                    L.e(it.throwable)
                }
                else -> {}
            }
        }

        return binding.root
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

    private fun layOutCategories(categoryList: List<PageTitle>) {
        binding.categoriesRecycler.isVisible = categoryList.isNotEmpty()
        binding.categoriesNoneFound.isVisible = categoryList.isEmpty()
        binding.categoriesError.visibility = View.GONE
        binding.categoriesRecycler.adapter = CategoryAdapter(categoryList)
    }

    private inner class CategoryItemHolder constructor(val view: PageItemView<PageTitle>) : RecyclerView.ViewHolder(view) {
        fun bindItem(title: PageTitle) {
            view.item = title
            view.setTitle(StringUtil.removeNamespace(title.displayText))
            val count = categoryCounts[title.prefixedText]
            view.setDescription(count?.let { view.context.resources.getQuantityString(R.plurals.category_article_count, it, it) })
            view.setActionHint(R.string.category_save_all_offline_action_hint)
            view.setSecondaryActionIcon(
                if (savingCategories.contains(title.prefixedText)) R.drawable.ic_download_in_progress else R.drawable.ic_download_24px,
                true
            )
        }
    }

    private inner class CategoryAdapter(val categoryList: List<PageTitle>) : RecyclerView.Adapter<CategoryItemHolder>() {
        override fun getItemCount(): Int {
            return categoryList.size
        }

        override fun onCreateViewHolder(parent: ViewGroup, pos: Int): CategoryItemHolder {
            val view = PageItemView<PageTitle>(requireContext())
            view.setImageVisible(false)
            view.setTitleTypeface(Typeface.NORMAL)
            return CategoryItemHolder(view)
        }

        override fun onBindViewHolder(holder: CategoryItemHolder, pos: Int) {
            holder.bindItem(categoryList[pos])
        }

        override fun onViewAttachedToWindow(holder: CategoryItemHolder) {
            super.onViewAttachedToWindow(holder)
            holder.view.callback = itemCallback
        }

        override fun onViewDetachedFromWindow(holder: CategoryItemHolder) {
            holder.view.callback = null
            super.onViewDetachedFromWindow(holder)
        }
    }

    private inner class ItemCallback : PageItemView.Callback<PageTitle?> {
        override fun onClick(item: PageTitle?) {
            if (item != null) {
                startActivity(CategoryActivity.newIntent(requireActivity(), item))
            }
        }

        override fun onLongClick(item: PageTitle?): Boolean {
            return false
        }

        override fun onActionClick(item: PageTitle?, view: View) {
            if (item != null && savingCategories.add(item.prefixedText)) {
                binding.categoriesRecycler.adapter?.notifyDataSetChanged()
                viewModel.saveCategoryForOffline(item)
            }
        }

        override fun onListChipClick(readingList: ReadingList) {}
    }

    companion object {
        fun newInstance(title: PageTitle): CategoryDialog {
            return CategoryDialog().apply { arguments = bundleOf(Constants.ARG_TITLE to title) }
        }
    }
}
