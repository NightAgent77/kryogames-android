package com.kryogames.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DownloadTray(
    transfer: LiveTransfer?,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf(transfer) }
    if (transfer != null) shown = transfer
    AnimatedVisibility(
        visible = transfer != null,
        modifier = modifier,
        enter = slideInVertically(tween(220)) { it } + fadeIn(tween(180)),
        exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
    ) {
        shown?.let { current ->
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(KryoColors.Surface)
                    .border(1.dp, KryoColors.Border, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)
                    .testTag("download_tray"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        current.title,
                        modifier = Modifier.weight(1f),
                        color = KryoColors.Text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (current.settling) "Done" else formatTransferSpeed(current.bytesPerSecond),
                        color = if (current.settling) KryoColors.Green else KryoColors.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (!current.settling) {
                        FocusBox(
                            Modifier.size(36.dp).testTag("download_tray_stop"),
                            description = "Stop download",
                            outlined = true,
                            onClick = onStop,
                        ) {
                            Icon(Icons.Outlined.Stop, null, tint = KryoColors.Text, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                TransferBlades(
                    fraction = current.fraction,
                    settling = current.settling,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(16.dp),
                )
                Text(
                    if (current.settling) formatTransferSize(current.received) else formatTransferAmount(current.received, current.total),
                    modifier = Modifier.padding(top = 6.dp),
                    color = KryoColors.Muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
internal fun DownloadsPane(
    m: Metrics,
    transfer: LiveTransfer?,
    finished: List<FinishedDownload>,
    stopHighlighted: Boolean,
    onStop: () -> Unit,
) {
    val active = transfer?.takeIf { !it.settling }
    val history = finished.filter { item -> active == null || item.id != active.id }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = m.d(8), bottom = m.d(12)),
        verticalArrangement = Arrangement.spacedBy(m.d(14)),
    ) {
        Text("Downloads", color = KryoColors.Text, fontSize = m.t(32), fontWeight = FontWeight.Bold)
        if (active != null) {
            Column(verticalArrangement = Arrangement.spacedBy(m.d(8))) {
                Text(active.title, color = KryoColors.Text, fontSize = m.t(18), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TransferBlades(fraction = active.fraction, settling = false, modifier = Modifier.fillMaxWidth().height(m.d(16)))
                Text(formatTransferAmount(active.received, active.total), color = KryoColors.Muted, fontSize = m.t(13))
                Text(formatTransferSpeed(active.bytesPerSecond), color = KryoColors.Accent, fontSize = m.t(13), fontWeight = FontWeight.Medium)
                FocusBox(
                    Modifier.widthIn(min = m.d(120)).height(m.d(44)).testTag("download_page_stop"),
                    description = "Stop download",
                    outlined = true,
                    highlighted = stopHighlighted,
                    onClick = onStop,
                ) {
                    Text("Stop", color = KryoColors.Text, fontSize = m.t(15), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (history.isEmpty() && active == null) {
            Text("Nothing downloaded yet.", color = KryoColors.Muted, fontSize = m.t(15), modifier = Modifier.testTag("downloads_empty"))
        }
        history.forEach { item ->
            Row(
                Modifier.fillMaxWidth().testTag("download_${item.id}"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.title,
                    modifier = Modifier.weight(1f).padding(end = m.d(12)),
                    color = KryoColors.Text,
                    fontSize = m.t(16),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatTransferSize(item.bytes), color = KryoColors.Text, fontSize = m.t(14))
                    Text(formatDownloadedAt(item.finishedAt), color = KryoColors.Muted, fontSize = m.t(12))
                }
            }
        }
    }
}

@Composable
private fun TransferBlades(fraction: Float, settling: Boolean, modifier: Modifier) {
    val travel by rememberInfiniteTransition(label = "transfer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    val ink = if (settling) KryoColors.Green else KryoColors.Accent
    val dim = KryoColors.Border
    Canvas(modifier.clip(RoundedCornerShape(3.dp)).testTag("transfer_blades")) {
        val count = 46
        val gap = 2.dp.toPx()
        val bladeW = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
        val known = fraction >= 0f
        val filled = if (known) fraction * count else 0f
        for (index in 0 until count) {
            val leading = known && index.toFloat() <= filled && index + 1f > filled
            val on = when {
                settling || (known && index + 1f <= filled) -> 1f
                leading -> (filled - index).coerceIn(0.35f, 1f)
                !known -> {
                    val head = travel * count
                    val distance = kotlin.math.abs(index - head)
                    val wrapped = minOf(distance, count - distance)
                    (1f - wrapped / 7f).coerceIn(0f, 1f)
                }
                else -> 0f
            }
            val height = size.height * if (leading && !settling) 1f else 0.72f
            val top = (size.height - height) / 2f
            drawRoundRect(
                color = if (on <= 0f) dim else ink.copy(alpha = 0.22f + 0.78f * on),
                topLeft = Offset(index * (bladeW + gap), top),
                size = Size(bladeW, height),
                cornerRadius = CornerRadius(bladeW / 2f, bladeW / 2f),
            )
        }
    }
}
