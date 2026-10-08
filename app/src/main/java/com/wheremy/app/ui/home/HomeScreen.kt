package com.wheremy.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.wheremy.app.ui.theme.WheremySpacing

@Composable
fun HomeScreen(greeting: String, onOpenDesignGallery: () -> Unit) {
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
                text = "Phase 0：工程骨架 + 设计系统。屏幕还是空的——P2 起按纵向切片填。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onOpenDesignGallery) {
                Text("看设计系统")
            }
        }
    }
}
