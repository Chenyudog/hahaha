package com.cyd.timemaster.ui.me

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 占位：v0.5.0 起为设置页（配对/提醒时刻/壁纸），v0.6.0 增加花费入口 */
@Composable
fun MeScreen() {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text("设置将在 v0.5.0 开放")
    }
}
