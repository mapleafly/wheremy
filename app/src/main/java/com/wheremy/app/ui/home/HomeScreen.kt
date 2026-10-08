package com.wheremy.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.wheremy.app.ui.theme.WheremySpacing

@Composable
fun HomeScreen(greeting: String, onOpenDesignGallery: () -> Unit, onOpenDataSelfCheck: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(WheremySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WheremySpacing.md),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag("home_greeting"),
            )
            Text(
                text = "Phase 0/1：工程骨架 + 设计系统 + 数据与照片层。产品屏从 P2 起按纵向切片填。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onOpenDesignGallery) {
                Text("看设计系统")
            }
            // 开发期脚手架：P2 起被真正的照片墙取代（DataSelfCheckScreen 的注释里有原因）
            OutlinedButton(onClick = onOpenDataSelfCheck) {
                Text("数据层自检（P1）")
            }
        }
    }
}
