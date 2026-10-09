package com.wheremy.app.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wheremy.app.data.ai.Prompt
import com.wheremy.app.data.repo.CATEGORIES
import com.wheremy.app.data.repo.EXPIRE_KINDS
import com.wheremy.app.data.repo.LOCATION_KINDS
import com.wheremy.app.data.repo.SEASONS
import com.wheremy.app.data.settings.SettingsRepository
import com.wheremy.app.data.settings.WordListKind
import com.wheremy.app.data.settings.WordLists
import com.wheremy.app.ui.theme.WheremySpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 词表冒烟（开发期，`FR-CFG-06`）。
 *
 * 为什么要有它：真机上**用输入法往设置页里敲词表**是不可靠的（`input text` 只认 ASCII、
 * 光标位置和 IME 会打架，试过两次都留下半截内容）。这里直接调仓储，把"改词表"这件事
 * 变成一条可重复的命令，然后就能去看**芯片行**与**留痕里的提示词**有没有跟着变。
 *
 * 用法：`adb shell am start -n <包名>/com.wheremy.app.MainActivity --es dev words`
 */
@HiltViewModel
class WordListSmokeViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val effective = settings.current().wordLists(CATEGORIES, SEASONS, EXPIRE_KINDS, LOCATION_KINDS)
            _lines.value = report(effective)
        }
    }

    /** 设成一组**一眼能认出来**的值（ASCII，免得又被输入法搅）。 */
    fun applySmokeValues() {
        viewModelScope.launch {
            settings.setWordList(WordListKind.CATEGORIES, listOf("Anime", "Model"))
            settings.setWordList(WordListKind.SEASONS, listOf("Dry", "Rainy"))
            refresh()
        }
    }

    fun reset() {
        viewModelScope.launch {
            settings.resetWordLists()
            refresh()
        }
    }

    private fun report(lists: WordLists): List<String> {
        val prompt = Prompt.build(lists.categories, lists.seasons, listOf("家 / 抽屉"))
        val categoryLine = prompt.lines().first { it.startsWith("- category") }
        return listOf(
            "物品类别：${lists.categories.joinToString("、")}",
            "季节：${lists.seasons.joinToString("、")}",
            "到期类型：${lists.expireKinds.joinToString("、")}",
            "位置类型：${lists.locationKinds.joinToString("、")}",
            "提示词里的类别行：$categoryLine",
        )
    }
}

/** 词表冒烟页：进门先报当前值，两颗按钮分别"设成冒烟值"与"恢复默认"。 */
@Composable
fun WordListSmokeScreen(onBack: () -> Unit, vm: WordListSmokeViewModel = hiltViewModel()) {
    val lines by vm.lines.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WheremySpacing.lg),
        verticalArrangement = Arrangement.spacedBy(WheremySpacing.sm),
    ) {
        Text("词表冒烟（开发期）", style = MaterialTheme.typography.titleMedium)
        lines.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("word_line"))
        }
        OutlinedButton(onClick = vm::applySmokeValues) { Text("设成 Anime / Model / Dry / Rainy") }
        OutlinedButton(onClick = vm::reset) { Text("恢复默认") }
        OutlinedButton(onClick = onBack) { Text("返回") }
    }
}
