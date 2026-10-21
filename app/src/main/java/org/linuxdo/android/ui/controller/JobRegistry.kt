package org.linuxdo.android.ui.controller

import kotlinx.coroutines.Job

/**
 * 按 key 登记的在途协程。各控制器共用同一份:登出时一次取消干净,
 * 后台预取也靠 [anyActive] 判断用户是否有操作在跑、自己该不该让路。
 */
class JobRegistry {
    private val jobs = mutableMapOf<String, Job>()

    operator fun get(key: String): Job? = jobs[key]

    operator fun set(key: String, job: Job) {
        jobs[key] = job
    }

    fun anyActive(): Boolean = jobs.values.any { it.isActive }

    fun cancelWhere(predicate: (String) -> Boolean) {
        jobs.filterKeys(predicate).values.forEach { it.cancel() }
    }

    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }
}

/** 页面请求失败的统一出口:记日志,需要人机验证时切到验证页并在通过后重放 [retry]。 */
typealias PageFailureHandler = suspend (error: Exception, retry: () -> Unit) -> Unit
