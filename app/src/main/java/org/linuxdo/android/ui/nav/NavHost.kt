package org.linuxdo.android.ui.nav

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.core.CubicBezierEasing

/** Material 3 的强调减速曲线,用于页面进出。 */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val PushSpec = tween<Float>(durationMillis = 400, easing = EmphasizedDecelerate)
private val PopSpec = tween<Float>(durationMillis = 300, easing = EmphasizedDecelerate)

/** 共享 X 轴转场:进出的两页各沿水平方向错开这段距离并交叉淡化。 */
private val SharedAxisOffset = 30.dp

/** 预测性返回时,当前页最多缩小到这个比例、圆角放大到这么多。 */
private const val PredictiveBackMinScale = 0.9f
private val PredictiveBackCorner = 28.dp

/**
 * 页面栈容器。
 *
 * - 入栈 / 出栈用 Material 共享 X 轴转场(平移 + 交叉淡化)。
 * - 系统返回手势走预测性返回:手指拖动时当前页缩小、带圆角并向手势方向偏移,
 *   露出下层页面;松手提交才真正出栈,取消则弹回。
 *
 * position 表示当前停留的栈深度:push 从 n 到 n+1,pop 从 n 到 n-1。
 * 只渲染栈顶两层,每层用 key(entry.id) 固定组合身份,切换上下层时滚动位置不丢。
 *
 * 注意:全局单例 WebView 不能放进这里 —— 它必须挂在导航之外的最外层,否则出栈会把它卸载,
 * Cloudflare 挑战就再也没法渲染。
 */
@Composable
fun AppNavHost(
    stack: NavStack,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    content: @Composable (Route) -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    val position = remember { Animatable(stack.entries.lastIndex.toFloat()) }
    val backProgress = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var width by remember { mutableFloatStateOf(1f) }

    // 实际参与渲染的栈。push 立刻跟上真实栈;pop 要等退出动画播完才裁剪。
    var rendered by remember { mutableStateOf(stack.entries) }

    LaunchedEffect(stack.entries) {
        val target = stack.entries
        when {
            target.size > rendered.size -> {
                rendered = target
                position.animateTo(target.lastIndex.toFloat(), PushSpec)
            }
            target.size < rendered.size -> {
                val dropped = rendered.drop(target.size)
                position.animateTo(target.lastIndex.toFloat(), PopSpec)
                rendered = target
                backProgress.snapTo(0f)
                dropped.forEach { holder.removeState(it.id) }
            }
            else -> {
                rendered = target
                if (position.value != target.lastIndex.toFloat()) {
                    position.animateTo(target.lastIndex.toFloat(), PopSpec)
                }
            }
        }
    }

    PredictiveBackHandler(enabled = interactive && stack.canPop) { events ->
        try {
            events.collect { event ->
                swipeEdge = event.swipeEdge
                backProgress.snapTo(event.progress)
            }
            // 手势提交:出栈交给 stack,由上面的 LaunchedEffect 接着把动画播完。
            stack.pop()
        } catch (cancelled: CancellationException) {
            backProgress.animateTo(0f, PopSpec)
            throw cancelled
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { if (it.width > 0) width = it.width.toFloat() },
    ) {
        val lastIndex = rendered.lastIndex
        // 一次返回多层时,下层应直接展示目标页。
        val belowIndex = if (stack.entries.size < rendered.size) stack.entries.lastIndex else lastIndex - 1
        val transitionSpan = (lastIndex - belowIndex).coerceAtLeast(1).toFloat()
        rendered.forEachIndexed { index, entry ->
            if (index != lastIndex && index != belowIndex) return@forEachIndexed
            val isTop = index == lastIndex

            key(entry.id) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // >0:在当前位置之上(正在进入或退出);<0:在当前位置之下(被盖住)。
                            val distance = (index - position.value) / transitionSpan
                            val offsetPx = SharedAxisOffset.toPx()
                            if (distance >= 0f) {
                                translationX = distance * offsetPx
                                alpha = (1f - distance).coerceIn(0f, 1f)
                            } else {
                                translationX = distance * offsetPx
                                // 预测性返回期间下层必须完整可见,手势结束前不能跟着淡化。
                                alpha = if (backProgress.value > 0f) 1f else (1f + distance).coerceIn(0f, 1f)
                            }
                            if (isTop && lastIndex > 0 && backProgress.value > 0f) {
                                val p = backProgress.value
                                val scale = 1f - (1f - PredictiveBackMinScale) * p
                                scaleX = scale
                                scaleY = scale
                                val direction = if (swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                                translationX += direction * p * width * 0.06f
                                shape = RoundedCornerShape(PredictiveBackCorner * p)
                                clip = true
                            }
                        },
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    holder.SaveableStateProvider(entry.id) { content(entry.route) }
                }
            }
        }
    }
}
