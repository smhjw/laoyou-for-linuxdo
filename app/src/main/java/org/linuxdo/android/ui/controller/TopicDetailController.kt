package org.linuxdo.android.ui.controller

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.linuxdo.android.data.Post
import org.linuxdo.android.data.PostFlagReason
import org.linuxdo.android.data.Reaction
import org.linuxdo.android.data.TopicRepository
import org.linuxdo.android.data.effectiveReplyToPostNumber
import org.linuxdo.android.data.threadRows
import org.linuxdo.android.ui.DetailLoad
import org.linuxdo.android.ui.DetailUiState

/**
 * 话题详情:窗口化加载楼层、刷新、正倒序、楼中楼、回应 / 简评 / 解决方案、书签、举报、删除。
 *
 * 所有已打开话题的状态按 id 放在一张表里,返回栈上的多个详情页各取各的。
 */
class TopicDetailController(
    private val scope: CoroutineScope,
    private val repository: TopicRepository,
    private val jobs: JobRegistry,
    private val onPageFailure: PageFailureHandler,
    private val reading: ReadingTracker,
    /** 本地记下"这帖看过了"并摘掉各列表的未读点;postsCount 为 null 时由调用方自行估算。 */
    private val markSeen: (topicId: Long, postsCount: Int?) -> Unit,
    private val treeView: () -> Boolean,
    private val username: () -> String?,
    private val composerSubmitting: () -> Boolean,
) {
    /** 通知级别等跨列表操作需要直接改这张表,所以对 ViewModel 开放可写引用。 */
    internal val flow = MutableStateFlow<Map<Long, DetailUiState>>(emptyMap())
    val state: StateFlow<Map<Long, DetailUiState>> = flow.asStateFlow()

    private inline fun patch(topicId: Long, crossinline transform: (DetailUiState) -> DetailUiState) {
        flow.update { states -> states[topicId]?.let { states + (topicId to transform(it)) } ?: states }
    }

    fun clear() {
        flow.value = emptyMap()
    }

    fun retryTopic(id: Long, postNumber: Int? = null) {
        val failed = flow.value[id]?.failedLoad
        loadTopic(id, more = failed == DetailLoad.Next, previous = failed == DetailLoad.Previous,
            retry = true, postNumber = postNumber)
    }

    fun refreshTopic(id: Long, silent: Boolean = false) {
        val current = flow.value[id] ?: return
        if (current.loading || current.refreshing || current.reactingPosts.isNotEmpty()) return
        if (jobs["topic:$id"]?.isActive == true) return
        jobs["topic:$id"] = scope.launch {
            if (!silent) patch(id) { it.copy(refreshing = true, error = null) }
            try {
                // 默认带上 track_visit=true 记录用户活跃与话题浏览。
                val freshTopic = repository.fetchTopic(id, trackVisit = true)
                val freshById = freshTopic.postStream.posts.associateBy { it.id }
                val latest = flow.value[id] ?: current
                val wasAtEnd = latest.remaining.isEmpty()
                val updatedExisting = latest.posts.map { freshById[it.id] ?: it }
                var mergedPosts = (updatedExisting + freshTopic.postStream.posts)
                    .distinctBy { it.id }
                    .sortedBy { it.postNumber }
                var consumed = latest.consumedIds + freshTopic.postStream.posts.map { it.id }

                val windowIdx = freshTopic.postStream.stream.indexOf(latest.windowStartId).coerceAtLeast(0)
                val newRemaining = freshTopic.postStream.stream.drop(windowIdx).filterNot { it in consumed }
                // 刷新前已看完旧楼层、服务端又出现了新回复时,直接把最新一批并入列表。
                if (wasAtEnd && newRemaining.isNotEmpty()) {
                    val tailBatch = newRemaining.take(20)
                    val fetchedTail = runCatching { repository.fetchPosts(id, tailBatch) }.getOrNull()
                    if (fetchedTail != null) {
                        mergedPosts = (mergedPosts + fetchedTail).distinctBy { it.id }.sortedBy { it.postNumber }
                        consumed = consumed + tailBatch
                    }
                }
                patch(id) {
                    it.copy(topic = freshTopic, posts = mergedPosts, consumedIds = consumed, refreshing = false, error = null)
                }
                markSeen(id, maxOf(freshTopic.postsCount, freshTopic.postStream.stream.size,
                    mergedPosts.maxOfOrNull { it.postNumber } ?: 0))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!silent) {
                    patch(id) { it.copy(error = error.message ?: "刷新失败", failedLoad = DetailLoad.Topic) }
                    onPageFailure(error) { refreshTopic(id, silent = false) }
                }
            } finally {
                patch(id) { it.copy(refreshing = false) }
            }
        }
    }

    fun loadTopic(id: Long, more: Boolean = false, retry: Boolean = false, postNumber: Int? = null, previous: Boolean = false) {
        val current = flow.value[id] ?: DetailUiState()
        if (!more && !previous) {
            markSeen(id, null)
            reading.begin(id)
        }
        val targetLoaded = postNumber == null || current.posts.any { it.postNumber == postNumber }
        val isExplicitJump = !more && !previous && postNumber != null && !targetLoaded
        if (current.reactingPosts.isNotEmpty() || (current.loading && !isExplicitJump)) return
        if (!more && !previous && current.topic != null && !retry && targetLoaded) {
            // 再次进入已缓存的话题:后台静默增量同步,避免卡在旧评论数。
            // 带了 postNumber 时递增 locateVersion,让界面无论是否命中缓存都重新定位。
            if (postNumber != null) patch(id) { it.copy(locateVersion = it.locateVersion + 1) }
            refreshTopic(id, silent = true)
            return
        }
        if (more && current.remaining.isEmpty()) return
        if (previous && current.previousIds.isEmpty()) return
        jobs["topic:$id"]?.cancel()
        jobs["topic:$id"] = scope.launch {
            flow.update { it + (id to current.copy(loading = true, error = null)) }
            try {
                if (more || previous) {
                    val requested = when {
                        previous -> current.previousIds.takeLast(20)
                        // 倒序是由新到旧回溯,所以从尚未加载的楼层尾部往前取。
                        current.descending -> current.remaining.takeLast(20)
                        else -> current.remaining.take(20)
                    }
                    val posts = repository.fetchPosts(id, requested)
                    flow.update {
                        val latest = it[id] ?: current
                        it + (id to latest.copy(
                            posts = (latest.posts + posts).distinctBy { post -> post.id }.sortedBy { post -> post.postNumber },
                            consumedIds = latest.consumedIds + requested,
                            windowStartId = if (previous) requested.first() else current.windowStartId,
                            error = null, failedLoad = DetailLoad.Topic,
                        ))
                    }
                } else {
                    val topic = repository.fetchTopic(id, postNumber, trackVisit = true)
                    val category = repository.categoryById(topic.categoryId)
                    val emojis = repository.emojiUrls()
                    val rootPost = current.posts.filter { it.postNumber == 1 }
                    val posts = if (postNumber != null) (rootPost + topic.postStream.posts).distinctBy { it.id }.sortedBy { it.postNumber }
                        else topic.postStream.posts.distinctBy { it.id }
                    val missing = postNumber != null && posts.none { it.postNumber == postNumber }
                    flow.update {
                        it + (id to DetailUiState(topic, posts, posts.map { post -> post.id }.toSet(), category,
                            error = if (missing) "通知对应楼层 #$postNumber 已删除或不可访问" else null,
                            emojiUrls = emojis, windowStartId = topic.postStream.posts.minByOrNull { it.postNumber }?.id))
                    }
                    markSeen(id, maxOf(topic.postsCount, topic.postStream.stream.size, posts.maxOfOrNull { it.postNumber } ?: 0))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                flow.update { it + (id to current.copy(error = error.message ?: "加载失败",
                    failedLoad = when { previous -> DetailLoad.Previous; more -> DetailLoad.Next; else -> DetailLoad.Topic })) }
                onPageFailure(error) { loadTopic(id, more, retry = true, postNumber = postNumber, previous = previous) }
            } finally {
                patch(id) { it.copy(loading = false) }
            }
        }
    }

    /**
     * 切换评论区正序 / 倒序。
     *
     * 短帖(楼层已全在手上)和切回正序都是纯内存翻转,0 网络请求。
     * 长帖切到倒序时只保留 **1 楼 + stream 尾部的连续窗口**,而不是把开头的 2~20 楼和
     * 尾部的 281~300 楼混排:混排一是滑过 #281 会直接跳到 #20、中间断层两百多楼,
     * 二是 #299 若回复过 #5,树形排列会把最新回复吸进排在最底部的 #5 楼中楼里。
     * 切回正序时 windowStartId 仍指着尾窗首个,现有的"加载之前的楼层"缺口会自动补位。
     */
    fun toggleCommentOrder(topicId: Long) {
        val current = flow.value[topicId] ?: return
        if (current.loading || current.refreshing) return
        val target = !current.descending
        val stream = current.topic?.postStream?.stream.orEmpty()
        val tail = stream.takeLast(TAIL_WINDOW)
        val tailMissing = tail.filterNot { it in current.consumedIds }
        if (!target || tailMissing.isEmpty()) {
            patch(topicId) { it.copy(descending = target) }
            return
        }
        jobs["topic:$topicId"]?.cancel()
        jobs["topic:$topicId"] = scope.launch {
            flow.update { it + (topicId to current.copy(loading = true, error = null, descending = true)) }
            try {
                val fetched = repository.fetchPosts(topicId, tailMissing)
                patch(topicId) { latest ->
                    val merged = (latest.posts.filter { it.postNumber == 1 || it.id in tail } + fetched)
                        .distinctBy { it.id }
                        .sortedBy { it.postNumber }
                    val anchor = merged.firstOrNull { it.postNumber > 1 } ?: merged.firstOrNull()
                    latest.copy(
                        posts = merged,
                        consumedIds = merged.map { it.id }.toSet(),
                        windowStartId = anchor?.id ?: latest.windowStartId,
                        // 窗口换了一批楼层,楼中楼的游标和"已取完"标记跟着作废,否则展开按钮会点不动。
                        replyCursors = emptyMap(),
                        completedReplies = emptySet(),
                        descending = true,
                        error = null,
                        failedLoad = DetailLoad.Topic,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                patch(topicId) { it.copy(descending = current.descending, error = error.message ?: "切换顺序失败") }
                onPageFailure(error) { toggleCommentOrder(topicId) }
            } finally {
                patch(topicId) { it.copy(loading = false) }
            }
        }
    }

    /** 回应:先乐观更新本地计数,失败再回滚。 */
    fun react(topicId: Long, postId: Long, reaction: String) {
        val current = flow.value[topicId] ?: return
        if (current.loading) return
        val original = current.posts.firstOrNull { it.id == postId } ?: return

        val alreadyReacted = original.currentUserReaction?.id == reaction
        val newCurrentUserReaction = if (alreadyReacted) null else Reaction(id = reaction, count = 1, canUndo = true)
        val newReactions = original.reactions.toMutableList()
        fun decrement(index: Int) {
            val count = newReactions[index].count
            if (count <= 1) newReactions.removeAt(index) else newReactions[index] = newReactions[index].copy(count = count - 1)
        }
        val existingIndex = newReactions.indexOfFirst { it.id == reaction }
        if (alreadyReacted) {
            if (existingIndex >= 0) decrement(existingIndex)
        } else {
            original.currentUserReaction?.id?.let { oldId ->
                newReactions.indexOfFirst { it.id == oldId }.takeIf { it >= 0 }?.let(::decrement)
            }
            val index = newReactions.indexOfFirst { it.id == reaction }
            if (index >= 0) {
                newReactions[index] = newReactions[index].copy(count = newReactions[index].count + 1)
            } else {
                newReactions.add(Reaction(id = reaction, count = 1, canUndo = true))
            }
        }
        val optimisticPost = original.copy(currentUserReaction = newCurrentUserReaction, reactions = newReactions)
        patch(topicId) { latest ->
            latest.copy(
                posts = latest.posts.map { if (it.id == postId) optimisticPost else it },
                reactionErrors = latest.reactionErrors - postId,
            )
        }

        jobs["reaction:$postId"] = scope.launch {
            try {
                val updated = repository.toggleReaction(postId, reaction).copy(boosts = original.boosts, canBoost = original.canBoost)
                patch(topicId) { latest -> latest.copy(posts = latest.posts.map { if (it.id == postId) updated else it }) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                patch(topicId) { latest ->
                    latest.copy(
                        posts = latest.posts.map { if (it.id == postId) original else it },
                        reactionErrors = latest.reactionErrors + (postId to (error.message ?: "回应失败")),
                    )
                }
                onPageFailure(error) { react(topicId, postId, reaction) }
            }
        }
    }

    fun refreshPost(topicId: Long, postId: Long) {
        changePost(topicId, postId) { repository.fetchPosts(topicId, listOf(postId)).single() }
    }

    fun setSolution(topicId: Long, postId: Long) {
        val post = flow.value[topicId]?.posts?.firstOrNull { it.id == postId } ?: return
        if (post.acceptedAnswer && !post.canUnacceptAnswer || !post.acceptedAnswer && !post.canAcceptAnswer) return
        changePost(topicId, postId) {
            val accepted = !it.acceptedAnswer
            repository.setSolution(postId, accepted)
            it.copy(acceptedAnswer = accepted, canAcceptAnswer = !accepted, canUnacceptAnswer = accepted)
        }
    }

    fun createBoost(topicId: Long, postId: Long, raw: String) {
        changePost(topicId, postId) { post ->
            val boost = repository.createBoost(postId, raw)
            post.copy(boosts = post.boosts + boost, canBoost = false)
        }
    }

    fun deleteBoost(topicId: Long, postId: Long, boostId: Long) {
        changePost(topicId, postId) { post ->
            repository.deleteBoost(boostId)
            post.copy(boosts = post.boosts.filterNot { it.id == boostId }, canBoost = !post.yours)
        }
    }

    private fun changePost(topicId: Long, postId: Long, action: suspend (Post) -> Post) {
        val current = flow.value[topicId] ?: return
        if (current.loading || current.reactingPosts.isNotEmpty()) return
        val original = current.posts.firstOrNull { it.id == postId } ?: return
        jobs["reaction:$postId"] = scope.launch {
            flow.update { it + (topicId to current.copy(reactingPosts = setOf(postId), reactionErrors = current.reactionErrors - postId)) }
            try {
                val updated = action(original)
                patch(topicId) { latest ->
                    latest.copy(posts = latest.posts.map {
                        when {
                            it.id == updated.id -> updated
                            updated.acceptedAnswer && it.acceptedAnswer -> it.copy(acceptedAnswer = false,
                                canAcceptAnswer = it.canUnacceptAnswer, canUnacceptAnswer = false)
                            else -> it
                        }
                    })
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                patch(topicId) { it.copy(reactionErrors = it.reactionErrors + (postId to (error.message ?: "回应失败"))) }
                // toggle 不是幂等操作,验证后只刷新状态,不自动重放写请求。
                onPageFailure(error) { loadTopic(topicId, retry = true) }
            } finally {
                patch(topicId) { it.copy(reactingPosts = emptySet()) }
            }
        }
    }

    /**
     * 新楼层必须同时登记进 consumedIds 和 stream:
     * 不进 consumedIds,`remaining` 会把它当成"未加载楼层"再拉一次;
     * 不进 stream,话题总楼层数和楼层进度就对不上。
     */
    suspend fun applyCreatedPost(topicId: Long, post: Post, forceTailWindow: Boolean) {
        val snapshot = flow.value[topicId]
        val atTail = forceTailWindow || post.replyToPostNumber == null || !treeView()
        if (snapshot != null && atTail && snapshot.remaining.isNotEmpty()) {
            val tailTopic = runCatching {
                repository.fetchTopic(topicId, postNumber = post.postNumber, burst = true)
            }.getOrNull()
            if (tailTopic != null) {
                patch(topicId) { current ->
                    val firstPost = current.posts.firstOrNull { it.postNumber == 1 }
                    val tailPosts = (tailTopic.postStream.posts + post).distinctBy { it.id }.sortedBy { it.postNumber }
                    val windowAnchor = tailPosts.firstOrNull { it.postNumber > 1 }?.id
                        ?: tailPosts.firstOrNull()?.id
                        ?: post.id
                    val mergedPosts = (listOfNotNull(firstPost) + tailPosts).distinctBy { it.id }.sortedBy { it.postNumber }
                    val rawStream = tailTopic.postStream.stream
                    val fullStream = if (post.id in rawStream) rawStream else rawStream + post.id
                    current.copy(
                        topic = tailTopic.copy(postStream = tailTopic.postStream.copy(stream = fullStream)),
                        posts = mergedPosts,
                        consumedIds = mergedPosts.map { it.id }.toSet(),
                        windowStartId = windowAnchor,
                        createdPostId = post.id,
                        error = null,
                    )
                }
                return
            }
        }
        applyNewPost(topicId, post)
    }

    private fun applyNewPost(topicId: Long, post: Post) {
        patch(topicId) { current ->
            val stream = current.topic?.postStream?.stream.orEmpty()
            val topic = current.topic?.let {
                if (post.id in stream) it else it.copy(postStream = it.postStream.copy(stream = stream + post.id))
            }
            current.copy(
                topic = topic,
                posts = (current.posts + post).distinctBy { it.id }.sortedBy { it.postNumber }
                    // 父楼层的 reply_count 同步 +1,否则"更多回复"的计数判断会漏掉服务端其余回复。1 楼为主贴不挂楼中楼。
                    .map { if (it.postNumber == post.replyToPostNumber && it.postNumber > 1) it.copy(replyCount = it.replyCount + 1) else it },
                consumedIds = current.consumedIds + post.id,
                windowStartId = current.windowStartId ?: post.id,
                createdPostId = post.id,
            )
        }
    }

    /** 界面已经滚到新楼层了,清掉标记,免得后面再触发一次。 */
    fun clearCreatedPost(topicId: Long) {
        flow.update { states ->
            states[topicId]?.takeIf { it.createdPostId != null }
                ?.let { states + (topicId to it.copy(createdPostId = null)) } ?: states
        }
    }

    fun applyEditedPost(topicId: Long, post: Post) {
        patch(topicId) { current ->
            current.copy(
                // 编辑只改内容。响应里缺字段会被反序列化成默认值,整条替换会清空点赞/简评,
                // 更糟的是 reply_to_post_number 变 null 会把这条回复抬成根节点、打乱树形排列,
                // 所以除 cooked 之外的既有状态一律保留。
                posts = current.posts.map { existing ->
                    if (existing.id != post.id) existing
                    else post.copy(
                        postNumber = existing.postNumber,
                        replyToPostNumber = existing.replyToPostNumber,
                        replyCount = existing.replyCount,
                        boosts = existing.boosts, reactions = existing.reactions,
                        currentUserReaction = existing.currentUserReaction, canBoost = existing.canBoost,
                        acceptedAnswer = existing.acceptedAnswer, canAcceptAnswer = existing.canAcceptAnswer,
                        canUnacceptAnswer = existing.canUnacceptAnswer,
                        avatarTemplate = post.avatarTemplate ?: existing.avatarTemplate,
                        username = post.username.ifBlank { existing.username },
                        createdAt = post.createdAt ?: existing.createdAt,
                        yours = existing.yours,
                        canEdit = existing.canEdit,
                        canDelete = existing.canDelete,
                        bookmarked = existing.bookmarked,
                        bookmarkId = existing.bookmarkId,
                    )
                },
            )
        }
    }

    /** 删除楼层。#1 楼会连带删除整个话题,由界面负责在确认框里说清楚。 */
    fun deletePost(topicId: Long, postId: Long) {
        val current = flow.value[topicId] ?: return
        if (current.loading || current.reactingPosts.isNotEmpty()) return
        if (current.posts.none { it.id == postId && it.canDelete }) return
        jobs["delete:$postId"] = scope.launch {
            flow.update { it + (topicId to current.copy(reactingPosts = setOf(postId), reactionErrors = current.reactionErrors - postId)) }
            try {
                repository.deletePost(postId)
                patch(topicId) { latest ->
                    val removed = latest.posts.firstOrNull { it.id == postId }
                    val topic = latest.topic?.let { topic ->
                        topic.copy(postStream = topic.postStream.copy(stream = topic.postStream.stream.filterNot { it == postId }))
                    }
                    latest.copy(
                        topic = topic,
                        posts = latest.posts.filterNot { it.id == postId }.map {
                            if (it.postNumber == removed?.replyToPostNumber && it.postNumber > 1) {
                                it.copy(replyCount = (it.replyCount - 1).coerceAtLeast(0))
                            } else it
                        },
                        consumedIds = latest.consumedIds - postId,
                        windowStartId = if (latest.windowStartId == postId) {
                            latest.posts.firstOrNull { it.id != postId }?.id
                        } else latest.windowStartId,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                patch(topicId) { it.copy(reactionErrors = it.reactionErrors + (postId to (error.message ?: "删除失败"))) }
                onPageFailure(error) { loadTopic(topicId, retry = true) }
            } finally {
                patch(topicId) { it.copy(reactingPosts = emptySet()) }
            }
        }
    }

    /** 详情页底部的轻提示,3.5 秒后自动消失(期间被新提示覆盖则不清)。 */
    fun setNoticeMessage(topicId: Long, message: String) {
        patch(topicId) { it.copy(noticeMessage = message) }
        scope.launch {
            delay(3500)
            patch(topicId) { if (it.noticeMessage == message) it.copy(noticeMessage = null) else it }
        }
    }

    fun toggleBookmark(topicId: Long, post: Post) {
        val snapshot = flow.value[topicId] ?: return
        val original = snapshot.posts.firstOrNull { it.id == post.id } ?: return
        if (username() == null || composerSubmitting()) return
        if (original.bookmarked && (original.bookmarkId ?: 0L) <= 0L) {
            patch(topicId) { it.copy(reactionErrors = it.reactionErrors + (post.id to "缺少书签信息，请刷新楼层后再取消收藏")) }
            return
        }
        val newBookmarked = !original.bookmarked
        patch(topicId) { current ->
            current.copy(
                posts = current.posts.map { if (it.id == post.id) it.copy(bookmarked = newBookmarked) else it },
                reactionErrors = current.reactionErrors - post.id,
            )
        }
        jobs["bookmark:${post.id}"] = scope.launch {
            var confirmed = false
            try {
                val bookmarkId = if (newBookmarked) {
                    repository.bookmarkPost(post.id)
                } else {
                    repository.unbookmarkPost(requireNotNull(original.bookmarkId))
                    null
                }
                patch(topicId) { current ->
                    current.copy(posts = current.posts.map {
                        if (it.id == post.id) it.copy(bookmarked = newBookmarked, bookmarkId = bookmarkId) else it
                    })
                }
                confirmed = true
                setNoticeMessage(topicId, if (newBookmarked) "已添加到书签" else "已从书签移除")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                patch(topicId) { it.copy(reactionErrors = it.reactionErrors + (post.id to (error.message ?: "书签操作失败"))) }
                onPageFailure(error) {}
            } finally {
                if (!confirmed) {
                    patch(topicId) { current ->
                        current.copy(posts = current.posts.map {
                            if (it.id == post.id) it.copy(bookmarked = original.bookmarked, bookmarkId = original.bookmarkId) else it
                        })
                    }
                }
            }
        }
    }

    /** 列表长按话题:把首帖加入书签。首帖 id 不在手上时由仓库补取。 */
    fun bookmarkTopicFromList(topicId: Long, onResult: (String) -> Unit) {
        if (username() == null) {
            onResult("请先登录账号")
            return
        }
        if (jobs["listBookmark:$topicId"]?.isActive == true) return
        val cachedFirstPostId = flow.value[topicId]?.posts?.firstOrNull { it.postNumber == 1 }?.id
        jobs["listBookmark:$topicId"] = scope.launch {
            try {
                val bookmarkId = repository.bookmarkTopic(topicId, cachedFirstPostId)
                patch(topicId) { detail ->
                    detail.copy(posts = detail.posts.map {
                        if (it.postNumber == 1) it.copy(bookmarked = true, bookmarkId = bookmarkId) else it
                    })
                }
                onResult("已添加到书签")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onResult(error.message ?: "添加书签失败")
                onPageFailure(error) {}
            }
        }
    }

    /** 列表长按话题:举报该话题(自动解析 1 楼 postId 提交举报)。 */
    fun submitTopicFlagFromList(
        topicId: Long,
        reasonId: Int,
        message: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (username() == null) {
            onError("请先登录账号")
            return
        }
        val reason = PostFlagReason.entries.firstOrNull { it.id == reasonId } ?: return
        if (reason.requiresMessage && message.isNullOrBlank()) {
            onError("请填写具体说明")
            return
        }
        if (jobs["listFlag:$topicId"]?.isActive == true) return
        val cachedFirstPostId = flow.value[topicId]?.posts?.firstOrNull { it.postNumber == 1 }?.id
        jobs["listFlag:$topicId"] = scope.launch {
            try {
                val postId = cachedFirstPostId ?: repository.fetchTopicFirstPostId(topicId)
                repository.flagPost(postId, reasonId, message)
                patch(topicId) { it.copy(flaggedPosts = it.flaggedPosts + postId) }
                onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onError(error.message ?: "举报提交失败，请稍后再试")
                onPageFailure(error) {}
            }
        }
    }

    fun openFlagDialog(topicId: Long, post: Post) {
        val user = username() ?: return
        patch(topicId) { current ->
            val latest = current.posts.firstOrNull { it.id == post.id } ?: return@patch current
            if (latest.yours || latest.username.equals(user, true) || post.id in current.flaggedPosts ||
                current.reactingPosts.isNotEmpty()) current
            else current.copy(flagDialogPost = latest, flagError = null)
        }
    }

    fun closeFlagDialog(topicId: Long) {
        patch(topicId) { current ->
            if (current.flagDialogPost?.id in current.reactingPosts) current
            else current.copy(flagDialogPost = null, flagError = null)
        }
    }

    fun submitFlag(topicId: Long, postId: Long, reasonId: Int, message: String? = null) {
        val current = flow.value[topicId] ?: return
        val post = current.posts.firstOrNull { it.id == postId } ?: return
        val user = username() ?: return
        if (current.flagDialogPost?.id != postId || current.reactingPosts.isNotEmpty() ||
            post.yours || post.username.equals(user, true) || postId in current.flaggedPosts) return
        val reason = PostFlagReason.entries.firstOrNull { it.id == reasonId } ?: return
        if (reason.requiresMessage && message.isNullOrBlank()) return
        patch(topicId) { it.copy(reactingPosts = it.reactingPosts + postId, flagError = null) }
        jobs["flag:$postId"] = scope.launch {
            try {
                repository.flagPost(postId, reasonId, message)
                patch(topicId) { it.copy(flagDialogPost = null, flaggedPosts = it.flaggedPosts + postId) }
                setNoticeMessage(topicId, "举报已提交，感谢您的反馈")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                patch(topicId) { it.copy(flagError = error.message ?: "举报提交失败，请稍后再试") }
                onPageFailure(error) {}
            } finally {
                patch(topicId) { it.copy(reactingPosts = it.reactingPosts - postId) }
            }
        }
    }

    /** 回到 1 楼。窗口化加载下 1 楼可能还没取回来,这时从头重拉整个话题。 */
    fun reloadFromStart(topicId: Long) {
        val current = flow.value[topicId] ?: return
        if (current.loading) return
        jobs["topic:$topicId"]?.cancel()
        flow.update { it - topicId }
        loadTopic(topicId)
    }

    /** 展开楼中楼:沿着同一棵子树逐个候选楼层翻页,直到取完或达到迭代上限。 */
    fun loadReplies(topicId: Long, postId: Long) {
        val current = flow.value[topicId] ?: return
        if (postId in current.loadingReplies || current.reactingPosts.isNotEmpty()) return
        val tree = threadRows(current.posts)
        val matchedNode = tree.firstOrNull { it.post.id == postId }
        val rootPostId = matchedNode?.rootPostId ?: postId
        val rootNode = tree.firstOrNull { it.post.id == rootPostId }
        val pendingSubtreeIds = rootNode?.unloadedCandidateIds?.filterNot { it in current.completedReplies }.orEmpty()
        if (postId in current.completedReplies && pendingSubtreeIds.isEmpty()) return

        val activeLoadingIds = mutableSetOf(postId, rootPostId)
        jobs["replies:$topicId:$rootPostId"] = scope.launch {
            patch(topicId) { it.copy(loadingReplies = it.loadingReplies + activeLoadingIds, error = null) }
            try {
                var iterations = 0
                while (isActive && iterations < MAX_REPLY_ITERATIONS) {
                    iterations++
                    val snapshot = flow.value[topicId] ?: break
                    val currentRootRow = threadRows(snapshot.posts).firstOrNull { it.post.id == rootPostId }
                    val nextCandidateId = if (postId !in snapshot.completedReplies) {
                        postId
                    } else {
                        currentRootRow?.unloadedCandidateIds?.firstOrNull { it !in snapshot.completedReplies }
                    } ?: break

                    if (activeLoadingIds.add(nextCandidateId)) {
                        patch(topicId) { it.copy(loadingReplies = it.loadingReplies + nextCandidateId) }
                    }

                    val after = snapshot.replyCursors[nextCandidateId] ?: 1
                    val replies = repository.fetchReplies(nextCandidateId, after)
                    val cursor = replies.maxOfOrNull { it.postNumber }

                    patch(topicId) { latest ->
                        val parent = latest.posts.firstOrNull { it.id == nextCandidateId }
                        val parentNumber = parent?.postNumber
                        val normalizedReplies = replies.map { reply ->
                            if (parentNumber != null && reply.postNumber > parentNumber) reply.copy(replyToPostNumber = parentNumber) else reply
                        }
                        val replyById = normalizedReplies.associateBy { it.id }
                        val patchedExisting = latest.posts.map { existing ->
                            val fetched = replyById[existing.id] ?: return@map existing
                            val targetParent = fetched.effectiveReplyToPostNumber() ?: parentNumber
                            val existingParent = existing.effectiveReplyToPostNumber()
                            val hasLoadedParent = existingParent != null &&
                                latest.posts.any { it.postNumber == existingParent && it.postNumber < existing.postNumber }
                            if (!hasLoadedParent && targetParent != null && existing.postNumber > targetParent) {
                                existing.copy(replyToPostNumber = targetParent)
                            } else existing
                        }
                        val existingIds = patchedExisting.map { it.id }.toSet()
                        val mergedPosts = (patchedExisting + normalizedReplies.filterNot { it.id in existingIds }).sortedBy { it.postNumber }
                        val childCount = if (parentNumber != null) {
                            mergedPosts.count { it.postNumber > parentNumber && it.effectiveReplyToPostNumber() == parentNumber }
                        } else 0
                        val progressed = cursor != null && cursor > after
                        val isCandidateDone = replies.isEmpty() || replies.size < 20 || !progressed ||
                            (parent != null && childCount >= parent.replyCount)
                        latest.copy(
                            posts = mergedPosts,
                            consumedIds = latest.consumedIds + replies.map { post -> post.id },
                            replyCursors = if (cursor == null) latest.replyCursors else latest.replyCursors + (nextCandidateId to cursor),
                            completedReplies = if (isCandidateDone) latest.completedReplies + nextCandidateId else latest.completedReplies,
                        )
                    }

                    if (replies.isEmpty()) {
                        patch(topicId) { it.copy(completedReplies = it.completedReplies + nextCandidateId) }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                patch(topicId) { it.copy(error = error.message ?: "回复加载失败") }
                onPageFailure(error) { loadReplies(topicId, postId) }
            } finally {
                patch(topicId) { it.copy(loadingReplies = it.loadingReplies - activeLoadingIds) }
            }
        }
    }

    private companion object {
        /** 长帖切倒序时一次拉取的尾部楼层数,与 fetchPosts 的单批上限一致。 */
        const val TAIL_WINDOW = 20

        /** 展开一棵楼中楼子树时最多翻多少页,防止服务端数据异常时死循环。 */
        const val MAX_REPLY_ITERATIONS = 30
    }
}
