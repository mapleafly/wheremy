package com.wheremy.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.wheremy.app.reminder.ReminderNotifier
import com.wheremy.app.ui.WheremyRoot
import com.wheremy.app.ui.theme.WheremyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * 通知点进来的落点。
     *
     * ⚠️ 必须是**可变的**：应用已经在运行时点通知走的是 `onNewIntent`（`CLEAR_TOP` 会复用栈顶实例），
     * 只在 `onCreate` 读一次 `intent` 的话，这种情况会落在"用户原来那一屏"（真机上踩到过）。
     */
    private val entryState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 开发期脚手架可以直接拉起（不污染产品界面）：
        //   adb shell am start -n com.wheremy.app.dsh/com.wheremy.app.MainActivity --es dev selfcheck
        //   adb shell am start -n com.wheremy.app.dsh/com.wheremy.app.MainActivity --es dev design
        val devRoute = intent?.getStringExtra("dev")
        entryState.value = intent?.getStringExtra(ReminderNotifier.EXTRA_ENTRY)
        setContent {
            WheremyTheme {
                WheremyRoot(devRoute = devRoute, entry = entryState.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        entryState.value = intent.getStringExtra(ReminderNotifier.EXTRA_ENTRY)
    }
}
