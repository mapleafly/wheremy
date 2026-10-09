package com.wheremy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.wheremy.app.ui.WheremyRoot
import com.wheremy.app.ui.theme.WheremyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 开发期脚手架可以直接拉起（不污染产品界面）：
        //   adb shell am start -n com.wheremy.app.dsh/com.wheremy.app.MainActivity --es dev selfcheck
        //   adb shell am start -n com.wheremy.app.dsh/com.wheremy.app.MainActivity --es dev design
        val devRoute = intent?.getStringExtra("dev")
        setContent {
            WheremyTheme {
                WheremyRoot(devRoute = devRoute)
            }
        }
    }
}
