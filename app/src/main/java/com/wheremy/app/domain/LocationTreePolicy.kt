package com.wheremy.app.domain

/**
 * 位置删除门槛（`RULES.md` §8.5）：**只有没有任何子位置、也没有任何物品时才能删**。
 * 这是门槛的唯一一处定义；实际执行在 `LocationRepository.delete`。
 */
fun canDeleteLocation(children: List<LocationNode>, itemCount: Int): Boolean = children.isEmpty() && itemCount == 0
