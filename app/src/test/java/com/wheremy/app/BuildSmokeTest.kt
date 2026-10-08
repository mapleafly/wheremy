package com.wheremy.app

import org.junit.Assert.assertTrue
import org.junit.Test

/** 第一条测试：证明 JVM 测试链路通了（PLAN.md §3.3 第 3 步）。 */
class BuildSmokeTest {
    @Test
    fun theTestRuntimeIsAlive() {
        assertTrue("JVM 测试能跑起来", true)
    }
}
