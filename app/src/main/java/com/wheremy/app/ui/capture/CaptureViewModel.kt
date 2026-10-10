package com.wheremy.app.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wheremy.app.domain.CaptureFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 选图入口（`RULES.md` §3.1）：照片一到就落库，落库那一刻回调里就有真实的 `itemId`。
 * 界面拿这个 id 打开确认页——从这一刻起「稍后处理」才有东西可兑现。
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(private val flow: CaptureFlow) : ViewModel() {

    private val _captured = MutableStateFlow<String?>(null)
    val captured: StateFlow<String?> = _captured.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * 录入页**已经开着**时换一张（`UI.md` §2.4 的第三条分支）。
     *
     * 录入页的 VM 由它自己那一路创建，外壳拿不到；所以用一个一格的显影槽把它接起来：
     * 外壳把字节放进这里，录入页收到就 `replacePhoto(bytes)`——**同一个 itemId，填过的字段都留着**。
     * 走到下面那条路会新建一件物品，老的变成待归类里一具没名字的壳。
     */
    private val _replacements = MutableSharedFlow<ByteArray>(extraBufferCapacity = 1)
    val replacements: SharedFlow<ByteArray> = _replacements.asSharedFlow()

    fun replaceWith(jpeg: ByteArray) {
        _replacements.tryEmit(jpeg)
    }

    fun capture(jpeg: ByteArray) {
        viewModelScope.launch {
            runCatching { flow.capture(jpeg) { }.itemId }
                .onSuccess { _captured.value = it }
                .onFailure { _error.value = it.message ?: "照片没存下来" }
        }
    }

    /**
     * 「不拍照，手动填」（`FR-SRC-09`）：复用 `captured` 这条通道——
     * 外壳那边只认"有个新 itemId 要去确认页"，不关心它是不是拍的。
     */
    fun startBlank() {
        viewModelScope.launch {
            runCatching { flow.startBlank() }
                .onSuccess { _captured.value = it }
                .onFailure { _error.value = it.message ?: "建不了这条，再试一次" }
        }
    }

    fun consumeCaptured() {
        _captured.value = null
    }

    fun consumeError() {
        _error.value = null
    }
}
