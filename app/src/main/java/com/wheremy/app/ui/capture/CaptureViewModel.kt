package com.wheremy.app.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wheremy.app.domain.CaptureFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    fun capture(jpeg: ByteArray) {
        viewModelScope.launch {
            runCatching { flow.capture(jpeg) { }.itemId }
                .onSuccess { _captured.value = it }
                .onFailure { _error.value = it.message ?: "照片没存下来" }
        }
    }

    fun consumeCaptured() {
        _captured.value = null
    }

    fun consumeError() {
        _error.value = null
    }
}
