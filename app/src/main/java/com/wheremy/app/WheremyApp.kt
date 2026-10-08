package com.wheremy.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Hilt 的根（TECH_STACK.md §3.1：DI 用官方推荐的 Hilt）。 */
@HiltAndroidApp
class WheremyApp : Application()
