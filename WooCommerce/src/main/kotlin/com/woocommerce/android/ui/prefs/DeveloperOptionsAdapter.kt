package com.woocommerce.android.ui.prefs

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.woocommerce.android.ui.payments.cardreader.hub.CardReaderHubAdapter.Companion.VIEW_TYPE_NON_TOGGELABLE
import com.woocommerce.android.ui.prefs.DeveloperOptionsViewModel.DeveloperOptionsViewState.ListItem
import com.woocommerce.android.ui.prefs.DeveloperOptionsViewModel.DeveloperOptionsViewState.ListItem.NonToggleableListItem
import com.woocommerce.android.ui.prefs.DeveloperOptionsViewModel.DeveloperOptionsViewState.ListItem.ToggleableListItem

class DeveloperOptionsAdapter :
    ListAdapter<ListItem, DeveloperOptionsViewHolder>(ListItemDiffCallback) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ToggleableListItem -> {
                VIEW_TYPE_TOGGLEABLE
            }
            is NonToggleableListItem -> {
                VIEW_TYPE_NON_TOGGLEABLE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeveloperOptionsViewHolder {
        return when (viewType) {
            VIEW_TYPE_TOGGLEABLE -> {
                DeveloperOptionsViewHolder.ToggleableViewHolder(parent)
            }
            VIEW_TYPE_NON_TOGGELABLE -> {
                DeveloperOptionsViewHolder.RowViewHolder(parent)
            }
            else -> {
                throw error("Unknown section")
            }
        }
    }

    override fun onBindViewHolder(holder: DeveloperOptionsViewHolder, position: Int) {
        holder.onBind(getItem(position))
    }

    fun setItems(rows: List<ListItem>) {
        submitList(rows)
    }

    @Suppress("ReturnCount")
    object ListItemDiffCallback : DiffUtil.ItemCallback<ListItem>() {
        override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            if (oldItem is ToggleableListItem && newItem is ToggleableListItem) {
                return oldItem.label == newItem.label
            }
            if (oldItem is NonToggleableListItem && newItem is NonToggleableListItem) {
                return oldItem.label == newItem.label
            }
            return false
        }

        override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        const val VIEW_TYPE_TOGGLEABLE = 0
        const val VIEW_TYPE_NON_TOGGLEABLE = 1
    }
}
