package org.linuxdo.android.ui.controller

import android.os.SystemClock
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.linuxdo.android.Config
import org.linuxdo.android.data.ReadingStore
import org.linuxdo.android.data.Diagnostics
import org.linuxdo.android.data.TopicRepository

/**
 * 「同步阅读进度与统计」开启时的阅读计时与 timings 上报。
 *
 * 只上报屏幕上真实可见过的楼层;关闭时不发任何请求,未读蓝点改由本地已读集合消除。
 */
class ReadingTracker(
    private val scope: CoroutineScope,
    private val repository: TopicRepository,
    private val reading: ReadingStore,
    private val diagnostics: Diagnostics,
    /** 楼层是否已经到手(弱网首拉时要等它就绪再首报)。 */
    private val hasPosts: (topicId: Long) -> Boolean,
    /** 当前是否停在浏览界面(验证页、登录页上不计时)。 */
    private val isBrowsing: () -> Boolean,
) {
    private val _syncEnabled = MutableStateFlow(reading.syncReadProgress)
    val syncEnabled: StateFlow<Boolean> = _syncEnabled.asStateFlow()

    /** 话题的进入时刻、上次上报时刻与已上报时长。 */
    private val topicEnteredAt = mutableMapOf<Long, Long>()
    private val lastReportedAt = mutableMapOf<Long, Long>()
    private val reportedReadMs = mutableMapOf<Long, Long>()
    private val readReportJobs = mutableMapOf<Long, Job>()
    /** 当前处于前台可见、活跃状态的话题(被验证页遮挡、切后台或被下层路由盖住时不在其中)。 */
    private val activeTopicIds = Collections.synchronizedSet(mutableSetOf<Long>())
    /** 各话题当前在屏幕上可见的楼层号。 */
    private val currentVisiblePostsByTopic = mutableMapOf<Long, List<Int>>()
    /** 实际浏览过但尚未结算上报的楼层号队列(保留阅读顺序,按批次结算,避免倒序饥饿)。 */
    private val pendingVisiblePostsByTopic = mutableMapOf<Long, LinkedHashSet<Int>>()

    fun setTopicActive(topicId: Long, active: Boolean) {
        if (active) activeTopicIds.add(topicId) else activeTopicIds.remove(topicId)
    }

    private fun isTopicActive(topicId: Long): Boolean = isBrowsing() && activeTopicIds.contains(topicId)

    /** 关闭时顺手清掉在途计时,避免刚关完又飞出去一个上报。 */
    fun setSyncEnabled(enabled: Boolean) {
        if (_syncEnabled.value == enabled) return
        _syncEnabled.value = enabled
        reading.setSyncReadProgress(enabled)
        if (!enabled) clearTimers()
        diagnostics.log(if (enabled) "已开启阅读进度同步(低频 timings 上报)" else "已关闭阅读进度同步(仅本地标记已读)")
    }

    /** 记录用户在屏幕上实际浏览到的楼层号。 */
    fun onPostsVisible(topicId: Long, postNumbers: List<Int>) {
        if (topicId <= 0L || postNumbers.isEmpty()) return
        val valid = postNumbers.filter { it > 0 }
        if (valid.isEmpty()) return
        currentVisiblePostsByTopic[topicId] = valid
        pendingVisiblePostsByTopic.getOrPut(topicId) { LinkedHashSet() }.addAll(valid)
    }

    /**
     * 开始为这次阅读计时。
     * 停留满 3.5s 时首次上报以建立 baseline 与初始可见楼层;
     * 随后按心跳(35s)周期性上报,确保两次上报间隔 < 100s,使服务端 time_read 正确累加。
     */
    fun begin(topicId: Long) {
        if (!_syncEnabled.value || topicId in topicEnteredAt) return
        val now = SystemClock.elapsedRealtime()
        topicEnteredAt[topicId] = now
        lastReportedAt[topicId] = now
        readReportJobs[topicId]?.cancel()
        readReportJobs[topicId] = scope.launch {
            delay(Config.READ_REPORT_THRESHOLD_MS)
            val startedAt = topicEnteredAt[topicId] ?: return@launch
            // 弱网或首拉时楼层可能尚未写入,轮询等待楼层就绪(最多 5 秒)。
            var waited = 0
            while (!hasPosts(topicId) && waited < 5 && topicId in topicEnteredAt) {
                delay(1_000L)
                waited++
            }
            if (topicId !in topicEnteredAt) return@launch
            if (isTopicActive(topicId)) {
                val stayed = SystemClock.elapsedRealtime() - startedAt
                if (sendTimings(topicId, stayed)) {
                    reportedReadMs[topicId] = stayed
                    lastReportedAt[topicId] = SystemClock.elapsedRealtime()
                }
            } else {
                lastReportedAt[topicId] = SystemClock.elapsedRealtime()
            }

            // 帖内驻留心跳:每隔 READ_REPORT_HEARTBEAT_MS 累计增量上报一次,维持 0 < diff < 100s。
            while (isActive && topicId in topicEnteredAt) {
                delay(Config.READ_REPORT_HEARTBEAT_MS)
                if (topicId !in topicEnteredAt) break
                if (!isTopicActive(topicId)) {
                    // 页面失活或应用在后台:暂停心跳,顺延基准时间。
                    lastReportedAt[topicId] = SystemClock.elapsedRealtime()
                    continue
                }
                val lastReport = lastReportedAt[topicId] ?: SystemClock.elapsedRealtime()
                val delta = SystemClock.elapsedRealtime() - lastReport
                if (delta >= 10_000L && sendTimings(topicId, delta)) {
                    reportedReadMs[topicId] = (reportedReadMs[topicId] ?: 0L) + delta
                    lastReportedAt[topicId] = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    /** 离开话题。停留时长比上次上报多出一截时,在有效窗口(5s~90s)内补报一次。 */
    fun leave(topicId: Long) {
        setTopicActive(topicId, false)
        readReportJobs.remove(topicId)?.cancel()
        val startedAt = topicEnteredAt.remove(topicId) ?: return
        val lastReport = lastReportedAt.remove(topicId) ?: startedAt
        reportedReadMs.remove(topicId)
        val pendingSet = pendingVisiblePostsByTopic.remove(topicId)
        val currentVisible = currentVisiblePostsByTopic.remove(topicId).orEmpty()
        if (!_syncEnabled.value) return
        val now = SystemClock.elapsedRealtime()
        if (now - startedAt < Config.READ_REPORT_THRESHOLD_MS) return
        val pendingDelta = now - lastReport
        // 上游 Discourse 仅在 diff < 100s 时累加阅读时长。
        if (pendingDelta in 5_000L..90_000L) {
            val batch = pendingSet?.take(READ_REPORT_POST_CAP)?.takeIf { it.isNotEmpty() }
                ?: currentVisible.take(READ_REPORT_POST_CAP)
            if (batch.isNotEmpty()) {
                scope.launch { sendTimings(topicId, pendingDelta, numbersOverride = batch) }
            }
        }
    }

    private suspend fun sendTimings(topicId: Long, timeMs: Long, numbersOverride: List<Int>? = null): Boolean {
        // 优先用显式指定的楼层,其次是待上报队列(FIFO 至多 60 楼),最后是当前可见楼层。
        val pendingSet = pendingVisiblePostsByTopic[topicId]
        val candidateNumbers = numbersOverride
            ?: pendingSet?.take(READ_REPORT_POST_CAP)?.takeIf { it.isNotEmpty() }
            ?: currentVisiblePostsByTopic[topicId]?.take(READ_REPORT_POST_CAP).orEmpty()
        val numbers = candidateNumbers.filter { it > 0 }.distinct()
        // 严禁回退到整个话题已加载楼层:从未采集到可见楼层就不报,绝不虚报。
        if (numbers.isEmpty()) return false

        val success = repository.recordTopicTimings(topicId, timeMs, numbers)
        if (success && numbersOverride == null) {
            pendingSet?.removeAll(numbers.toSet())
        }
        return success
    }

    private fun clearTimers() {
        readReportJobs.values.forEach { it.cancel() }
        readReportJobs.clear()
        topicEnteredAt.clear()
        lastReportedAt.clear()
        reportedReadMs.clear()
        pendingVisiblePostsByTopic.clear()
        currentVisiblePostsByTopic.clear()
    }

    /** 登出:连同活跃话题集合一起清空。 */
    fun clear() {
        clearTimers()
        activeTopicIds.clear()
    }

    private companion object {
        /** 单次阅读进度上报最多携带多少个楼层号。 */
        const val READ_REPORT_POST_CAP = 60
    }
}
