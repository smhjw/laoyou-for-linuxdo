package org.linuxdo.android.ui.controller

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linuxdo.android.data.AttachedMedia
import org.linuxdo.android.data.DraftItem
import org.linuxdo.android.data.PostResult
import org.linuxdo.android.data.TopicRepository
import org.linuxdo.android.data.UploadLimits
import org.linuxdo.android.ui.ComposerTarget
import org.linuxdo.android.ui.ComposerUiState
import org.linuxdo.android.ui.DraftsUiState
import org.linuxdo.android.ui.TopicCreatorUiState

/**
 * 写作相关的全部状态:楼层回复框、发帖 / 草稿编辑器、草稿箱,以及两者共用的附件上传。
 */
class ComposerController(
    private val app: Application,
    private val scope: CoroutineScope,
    private val repository: TopicRepository,
    private val jobs: JobRegistry,
    private val onPageFailure: PageFailureHandler,
    private val details: TopicDetailController,
    private val username: () -> String?,
    /** 打开发帖器时分类表还没加载就顺手拉一次。 */
    private val ensureCategories: () -> Unit,
    /** 发出新话题后刷新首页列表。 */
    private val refreshLatest: () -> Unit,
) {
    private val _composer = MutableStateFlow(ComposerUiState())
    val composer: StateFlow<ComposerUiState> = _composer.asStateFlow()

    private val _topicCreator = MutableStateFlow(TopicCreatorUiState())
    val topicCreator: StateFlow<TopicCreatorUiState> = _topicCreator.asStateFlow()

    private val _drafts = MutableStateFlow(DraftsUiState())
    val drafts: StateFlow<DraftsUiState> = _drafts.asStateFlow()

    private var tagSearchDebounceJob: Job? = null

    fun clear() {
        _composer.value = ComposerUiState()
        _topicCreator.value = TopicCreatorUiState()
        _drafts.value = DraftsUiState()
    }

    // ── 楼层回复框 ───────────────────────────────────────────────────────────────

    /** 打开编辑器。编辑已有楼层时顺带把原文取回来填进输入框。 */
    fun openComposer(target: ComposerTarget) {
        if (_composer.value.submitting) return
        val needsDraft = target is ComposerTarget.Edit
        _composer.value = ComposerUiState(target = target, loadingDraft = needsDraft)
        if (target !is ComposerTarget.Edit) return
        jobs["draft:${target.postId}"] = scope.launch {
            try {
                val raw = repository.fetchPostRaw(target.postId)
                _composer.update { if (it.target == target) it.copy(draft = raw, loadingDraft = false) else it }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _composer.update {
                    if (it.target == target) it.copy(loadingDraft = false, error = error.message ?: "原文加载失败，请关闭后重试") else it
                }
            }
        }
    }

    fun updateDraft(text: String) {
        _composer.update { if (it.open) it.copy(draft = text, error = null) else it }
    }

    fun closeComposer() {
        if (_composer.value.submitting) return
        _composer.value = ComposerUiState()
    }

    fun attachToComposer(uri: Uri) {
        val current = _composer.value
        if (!current.open || current.uploading || current.submitting) return
        upload(
            jobKey = "upload",
            uri = uri,
            begin = { _composer.update { it.copy(uploading = true, error = null) } },
            done = { media -> _composer.update { if (!it.open) it.copy(uploading = false) else it.copy(attachments = it.attachments + media, uploading = false) } },
            failed = { message -> _composer.update { it.copy(uploading = false, error = message) } },
        )
    }

    fun removeComposerAttachment(id: String) {
        _composer.update { it.copy(attachments = it.attachments.filterNot { a -> a.id == id }) }
    }

    /** 提交编辑器内容。成功后就地并入已加载楼层,不整页重拉。 */
    fun submitComposer() {
        val current = _composer.value
        val target = current.target ?: return
        if (!current.canSubmit) return
        val raw = assembleRaw(current.draft, current.attachments)
        jobs["composer"] = scope.launch {
            _composer.update { it.copy(submitting = true, error = null) }
            try {
                when (target) {
                    is ComposerTarget.NewReply -> when (val result = repository.createPost(target.topicId, raw)) {
                        is PostResult.Success -> details.applyCreatedPost(target.topicId, result.post, forceTailWindow = true)
                        is PostResult.Enqueued -> details.setNoticeMessage(target.topicId, REVIEW_PENDING_REPLY)
                    }
                    is ComposerTarget.ReplyTo -> when (val result = repository.createPost(target.topicId, raw, target.postNumber)) {
                        is PostResult.Success -> details.applyCreatedPost(target.topicId, result.post, forceTailWindow = false)
                        is PostResult.Enqueued -> details.setNoticeMessage(target.topicId, REVIEW_PENDING_REPLY)
                    }
                    is ComposerTarget.Edit -> details.applyEditedPost(target.topicId, repository.updatePost(target.postId, raw))
                }
                _composer.value = ComposerUiState()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _composer.update { it.copy(submitting = false, error = error.message ?: "提交失败，请稍后重试") }
                onPageFailure(error) {}
            }
        }
    }

    // ── 草稿箱 ───────────────────────────────────────────────────────────────────

    fun loadDrafts(refresh: Boolean = false, silent: Boolean = false) {
        if (username() == null) return
        val current = _drafts.value
        if (current.loading) return
        if (!refresh && current.loaded) return
        jobs["drafts"] = scope.launch {
            if (!silent || !current.loaded) _drafts.update { it.copy(loading = true, error = null) }
            try {
                val list = repository.fetchDrafts()
                _drafts.update { it.copy(items = list, loaded = true, loading = false, error = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _drafts.update {
                    it.copy(loading = false, error = if (silent && it.loaded) null else (error.message ?: "草稿加载失败"))
                }
                if (!silent) onPageFailure(error) { loadDrafts(refresh = true) }
            }
        }
    }

    fun deleteDraft(draft: DraftItem) {
        if (username() == null) return
        if (draft.draftKey in _drafts.value.deletingKeys) return
        jobs["deleteDraft:${draft.draftKey}"] = scope.launch {
            _drafts.update { it.copy(deletingKeys = it.deletingKeys + draft.draftKey, error = null) }
            try {
                repository.deleteDraft(draft.draftKey, draft.sequence)
                _drafts.update {
                    it.copy(
                        items = it.items.filterNot { item -> item.draftKey == draft.draftKey },
                        deletingKeys = it.deletingKeys - draft.draftKey,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _drafts.update { it.copy(deletingKeys = it.deletingKeys - draft.draftKey, error = error.message ?: "删除草稿失败") }
                onPageFailure(error) {}
            }
        }
    }

    private fun forgetDraft(draftKey: String) {
        _drafts.update { state -> state.copy(items = state.items.filterNot { it.draftKey == draftKey }) }
    }

    /** 话题回复类草稿:直接打开该话题的回复栏并填入草稿内容。 */
    fun openReplyWithDraft(draft: DraftItem) {
        val topicId = draft.topicId ?: return
        val target = if (draft.replyToPostNumber != null) {
            ComposerTarget.ReplyTo(topicId = topicId, postNumber = draft.replyToPostNumber, username = "")
        } else {
            ComposerTarget.NewReply(topicId = topicId)
        }
        _composer.value = ComposerUiState(target = target, draft = draft.raw)
    }

    // ── 发帖 / 草稿编辑器 ────────────────────────────────────────────────────────

    /**
     * 打开创建新话题界面。
     * - 从首页进入时 `initialCategoryId` 为 `null`(分类默认为空)。
     * - 从指定分区进入时为当前分区的 `categoryId`(已包含当前所选等级 Lv.0~3)。
     */
    fun openTopicCreator(initialCategoryId: Int? = null) {
        if (_topicCreator.value.submitting || _topicCreator.value.savingDraft) return
        ensureCategories()
        val existingNewTopicDraft = _drafts.value.items.firstOrNull { it.draftKey == NEW_TOPIC_KEY }
        openCreator(
            TopicCreatorUiState(
                open = true,
                draftKey = NEW_TOPIC_KEY,
                sequence = existingNewTopicDraft?.sequence ?: 0L,
                categoryId = initialCategoryId,
            )
        )
    }

    /** 从草稿列表点击编辑,把草稿内容填入发布器。 */
    fun openDraftInCreator(draft: DraftItem) {
        if (_topicCreator.value.submitting || _topicCreator.value.savingDraft) return
        ensureCategories()
        openCreator(
            TopicCreatorUiState(
                open = true,
                draftKey = draft.draftKey,
                sequence = draft.sequence,
                action = draft.action,
                replyTopicId = draft.topicId,
                replyToPostNumber = draft.replyToPostNumber,
                title = draft.title,
                raw = draft.raw,
                categoryId = draft.categoryId,
                tags = draft.tags,
            )
        )
    }

    private fun openCreator(base: TopicCreatorUiState) {
        _topicCreator.value = base.copy(savedSnapshot = base.currentSnapshot)
        populateCreatorEmojisAndSequence(base.draftKey)
        loadTopicCreatorTags(base.categoryId)
    }

    private fun populateCreatorEmojisAndSequence(targetDraftKey: String) {
        scope.launch {
            val emojis = runCatching { repository.emojiUrls() }.getOrDefault(emptyMap())
            if (emojis.isNotEmpty()) _topicCreator.update { if (it.open) it.copy(emojiUrls = emojis) else it }
            if (!_drafts.value.loaded) {
                runCatching { repository.fetchDrafts(burst = true) }.getOrNull()?.let { list ->
                    _drafts.update { it.copy(items = list, loaded = true, loading = false, error = null) }
                    val matched = list.firstOrNull { it.draftKey == targetDraftKey }
                    if (matched != null) {
                        _topicCreator.update { if (it.open && it.draftKey == targetDraftKey) it.copy(sequence = matched.sequence) else it }
                    }
                }
            }
        }
    }

    fun loadTopicCreatorTags(categoryId: Int?, query: String = "") {
        jobs["tags:creator"]?.cancel()
        jobs["tags:creator"] = scope.launch {
            _topicCreator.update { it.copy(loadingTags = true) }
            val searchResult = repository.searchTags(
                categoryId = categoryId,
                query = query,
                selectedTags = _topicCreator.value.tags,
                limit = 8,
                filterForInput = true,
            )
            _topicCreator.update {
                if (it.open) it.copy(
                    availableTags = searchResult.results,
                    requiredTagGroupName = searchResult.requiredTagName,
                    requiredTagMinCount = searchResult.requiredTagMinCount,
                    loadingTags = false,
                ) else it
            }
        }
    }

    fun searchTopicCreatorTags(query: String) {
        tagSearchDebounceJob?.cancel()
        tagSearchDebounceJob = scope.launch {
            delay(300L) // 照搬 FluxDO 300ms 防抖
            loadTopicCreatorTags(_topicCreator.value.categoryId, query)
        }
    }

    fun updateTopicCreatorTitle(title: String) = editCreator { it.copy(title = title) }

    fun updateTopicCreatorRaw(raw: String) = editCreator { it.copy(raw = raw) }

    fun updateTopicCreatorCategory(categoryId: Int?) {
        editCreator { it.copy(categoryId = categoryId) }
        loadTopicCreatorTags(categoryId)
    }

    fun updateTopicCreatorTags(tags: List<String>) = editCreator { it.copy(tags = tags.distinct()) }

    /** 编辑器内容一改,旧的报错与提示就都不再适用。 */
    private inline fun editCreator(crossinline transform: (TopicCreatorUiState) -> TopicCreatorUiState) {
        _topicCreator.update { if (it.open) transform(it).copy(error = null, notice = null) else it }
    }

    fun clearTopicCreatorNotice() {
        _topicCreator.update { it.copy(notice = null) }
    }

    fun closeTopicCreator() {
        val current = _topicCreator.value
        if (current.submitting || current.savingDraft) return
        _topicCreator.value = TopicCreatorUiState()
    }

    fun attachToTopicCreator(uri: Uri) {
        val current = _topicCreator.value
        if (!current.open || current.uploading || current.submitting || current.savingDraft) return
        upload(
            jobKey = "upload:creator",
            uri = uri,
            begin = { _topicCreator.update { it.copy(uploading = true, error = null, notice = null) } },
            done = { media -> _topicCreator.update { if (!it.open) it.copy(uploading = false) else it.copy(attachments = it.attachments + media, uploading = false) } },
            failed = { message -> _topicCreator.update { it.copy(uploading = false, error = message) } },
        )
    }

    fun removeTopicCreatorAttachment(id: String) {
        _topicCreator.update { it.copy(attachments = it.attachments.filterNot { a -> a.id == id }) }
    }

    fun saveTopicDraft(closeAfterSave: Boolean = false) {
        val current = _topicCreator.value
        if (!current.open || !current.canSaveDraft) return
        val rawToSave = assembleRaw(current.raw, current.attachments)
        jobs["saveDraft"] = scope.launch {
            _topicCreator.update { it.copy(savingDraft = true, error = null, notice = null) }
            try {
                val newSeq = repository.saveDraft(
                    draftKey = current.draftKey,
                    sequence = current.sequence,
                    title = current.title.trim(),
                    raw = rawToSave,
                    categoryId = current.categoryId,
                    tags = current.tags,
                    action = current.action,
                    topicId = current.replyTopicId,
                    replyToPostNumber = current.replyToPostNumber,
                )
                if (closeAfterSave) {
                    _topicCreator.value = TopicCreatorUiState()
                    Toast.makeText(app, "草稿已保存", Toast.LENGTH_SHORT).show()
                } else {
                    _topicCreator.update {
                        val next = it.copy(sequence = newSeq, savingDraft = false, notice = "草稿已保存")
                        next.copy(savedSnapshot = next.currentSnapshot)
                    }
                }
                loadDrafts(refresh = true, silent = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _topicCreator.update { it.copy(savingDraft = false, error = error.message ?: "保存草稿失败，请稍后重试") }
                onPageFailure(error) {}
            }
        }
    }

    fun submitTopicCreator(onPublished: (Long, String, Int?) -> Unit) {
        val current = _topicCreator.value
        if (!current.open || !current.canSubmit) return
        if (!current.isReplyDraft && current.categoryId == null) {
            _topicCreator.update { it.copy(error = "请先选择话题分类") }
            return
        }
        val rawToSubmit = assembleRaw(current.raw, current.attachments)
        jobs["createTopic"] = scope.launch {
            _topicCreator.update { it.copy(submitting = true, error = null, notice = null) }
            try {
                if (current.isReplyDraft) {
                    val targetTopicId = requireNotNull(current.replyTopicId)
                    val result = repository.createPost(
                        topicId = targetTopicId,
                        raw = rawToSubmit,
                        replyToPostNumber = current.replyToPostNumber,
                        draftKey = current.draftKey,
                    )
                    forgetDraft(current.draftKey)
                    when (result) {
                        is PostResult.Success -> {
                            details.applyCreatedPost(targetTopicId, result.post, forceTailWindow = current.replyToPostNumber == null)
                            _topicCreator.value = TopicCreatorUiState()
                            onPublished(targetTopicId, current.title.ifBlank { "话题 #$targetTopicId" }, result.post.postNumber)
                        }
                        is PostResult.Enqueued -> {
                            _topicCreator.value = TopicCreatorUiState()
                            Toast.makeText(app, REVIEW_PENDING_REPLY, Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    val result = repository.createTopic(
                        title = current.title,
                        raw = rawToSubmit,
                        categoryId = current.categoryId,
                        tags = current.tags,
                        draftKey = current.draftKey,
                    )
                    forgetDraft(current.draftKey)
                    _topicCreator.value = TopicCreatorUiState()
                    when (result) {
                        is PostResult.Success -> {
                            val newTopicId = result.post.topicId ?: 0L
                            refreshLatest()
                            if (newTopicId > 0L) {
                                onPublished(newTopicId, current.title.trim(), null)
                            } else {
                                Toast.makeText(app, "话题已发布", Toast.LENGTH_SHORT).show()
                            }
                        }
                        is PostResult.Enqueued ->
                            Toast.makeText(app, "您的话题已提交，正等待管理员审核后显示", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _topicCreator.update { it.copy(submitting = false, error = error.message ?: "发布失败，请稍后重试") }
                onPageFailure(error) {}
            }
        }
    }

    // ── 附件 ─────────────────────────────────────────────────────────────────────

    /**
     * 上传选中的文件或图片。成功后以缩略图 / 卡片展示供用户确认,
     * 不把难懂的 Markdown 短码塞进输入框;发送或存草稿时再由 [assembleRaw] 补进正文。
     */
    private fun upload(
        jobKey: String,
        uri: Uri,
        begin: () -> Unit,
        done: (AttachedMedia) -> Unit,
        failed: (String) -> Unit,
    ) {
        jobs[jobKey] = scope.launch {
            begin()
            try {
                val picked = withContext(Dispatchers.IO) { readPickedFile(uri) }
                val uploaded = repository.uploadFile(picked.name, picked.mimeType, picked.bytes)
                val isImage = uploaded.width > 0 || UploadLimits.isCompressibleImage(picked.name) ||
                    picked.mimeType.startsWith("image/")
                done(AttachedMedia(name = uploaded.fileName, previewUrl = uri.toString(), uploaded = uploaded, isImage = isImage))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failed(error.message ?: "上传失败，请稍后重试")
                onPageFailure(error) {}
            }
        }
    }

    private fun assembleRaw(raw: String, attachments: List<AttachedMedia>): String {
        val unincluded = attachments.filter { !raw.contains(it.uploaded.shortUrl) }
        if (unincluded.isEmpty()) return raw
        val gap = if (raw.isBlank() || raw.endsWith("\n")) "" else "\n\n"
        return raw + gap + unincluded.joinToString("\n") { it.toMarkdown() }
    }

    private class PickedFile(val name: String, val mimeType: String, val bytes: ByteArray)

    /** 先按大小、类型预检,再限量读取;位图按需降采样。 */
    private fun readPickedFile(uri: Uri): PickedFile {
        val resolver = app.contentResolver
        var displayName: String? = null
        var reportedSize: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameColumn >= 0 && !cursor.isNull(nameColumn)) displayName = cursor.getString(nameColumn)
                    val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                        reportedSize = cursor.getLong(sizeColumn).takeIf { it >= 0 }
                    }
                }
            }
        val name = displayName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "upload"
        val bytes = UploadLimits.readBytes(name, reportedSize) { resolver.openInputStream(uri) }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        if (!UploadLimits.isCompressibleImage(name)) return PickedFile(name, mime, bytes)
        return shrinkImage(name, bytes) ?: PickedFile(name, mime, bytes)
    }

    /**
     * 把超尺寸 / 超大的位图收一收。返回 null 表示不需要动或处理不了(调用方原样上传)。
     *
     * PNG 优先仍编码成 PNG —— 转 JPEG 会把透明区域填成黑色。只有降采样后 PNG 还是太大时
     * 才退成 JPEG,那种情况下用户本来也没得选(4MB 是硬上限)。
     * GIF/SVG/AVIF/JXL 不走这里:动图会被压成静态,后两者解码支持也不稳。
     */
    private fun shrinkImage(name: String, bytes: ByteArray): PickedFile? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return@runCatching null
        // 既没超尺寸也没超大小就别碰,重编码只会白掉画质。
        if (longEdge <= UploadLimits.MAX_IMAGE_EDGE && bytes.size <= UploadLimits.MAX_BYTES) return@runCatching null

        var sample = 1
        while (longEdge / sample > UploadLimits.MAX_IMAGE_EDGE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return@runCatching null

        val isPng = UploadLimits.extensionOf(name) == "png"
        val out = ByteArrayOutputStream()
        if (isPng) {
            decoded.compress(Bitmap.CompressFormat.PNG, 100, out)
            if (out.size() <= UploadLimits.MAX_BYTES) {
                decoded.recycle()
                return@runCatching PickedFile(name, "image/png", out.toByteArray())
            }
        }
        var quality = UploadLimits.JPEG_QUALITY
        do {
            out.reset()
            decoded.compress(Bitmap.CompressFormat.JPEG, quality, out)
            quality -= 10
        } while (out.size() > UploadLimits.MAX_BYTES && quality >= 40)
        decoded.recycle()
        PickedFile("${name.substringBeforeLast('.', name)}.jpg", "image/jpeg", out.toByteArray())
    }.getOrNull()

    private companion object {
        const val NEW_TOPIC_KEY = "new_topic"
        const val REVIEW_PENDING_REPLY = "您的回复已提交，正等待管理员审核后显示"
    }
}
