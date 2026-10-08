package com.wheremy.app.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.ItemSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 待归类列表（`RULES.md` §1.1：没有位置，或还是草稿）。 */
@HiltViewModel
class InboxViewModel @Inject constructor(private val items: ItemRepository) : ViewModel() {

    private val _rows = MutableStateFlow<List<ItemSummary>>(emptyList())
    val rows: StateFlow<List<ItemSummary>> = _rows.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            val inbox = items.inbox()
            _rows.value = items.summaries(inbox).map { summary ->
                // 缩略图给界面的是绝对路径（库里存的是 PhotoStore 的相对路径）
                summary.copy(
                    thumbPath = summary.thumbPath?.let { items.photoStore.file(it).absolutePath },
                )
            }
            _loading.value = false
        }
    }
}
