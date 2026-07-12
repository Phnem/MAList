package com.example.myapplication.ui.inspect

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import com.example.myapplication.ui.shared.theme.MotionTokens
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.settings.SegmentedOption
import com.example.myapplication.ui.shared.theme.SnProFamily

/**
 * Тот же принцип, что [com.example.myapplication.ui.settings.CapsuleChipRow] в настройках,
 * но выше и крупнее — только для экрана «Поиск по кадру».
 */
@Composable
fun InspectCapsuleChipRow(
    options: List<SegmentedOption>,
    selectedIndex: Int,
    accentColor: Color,
    pillColor: Color = accentColor.copy(alpha = 0.25f),
    modifier: Modifier = Modifier,
    containerHeight: Dp = 56.dp,
    contentDescription: (Int) -> String? = { null },
    onOptionClick: (Int) -> Unit
) {
    val containerCornerRadius = containerHeight / 2
    val containerShape = RoundedCornerShape(containerCornerRadius)

    BoxWithConstraints(
        modifier = modifier
            .height(containerHeight)
            .clip(containerShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        val innerWidth = maxWidth
        val contentHeight = containerHeight
        val segmentWidth = innerWidth / options.size
        val pillWidth = segmentWidth
        val pillCornerRadius = contentHeight / 2
        val innerShape = RoundedCornerShape(pillCornerRadius)
        val pillOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = MotionTokens.standardDp,
            label = "inspect_pill_offset"
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = pillOffset)
                    .width(pillWidth)
                    .fillMaxHeight()
                    .clip(innerShape)
                    .background(pillColor)
            )
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEachIndexed { index, opt ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onOptionClick(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (opt.icon != null) {
                            Icon(
                                opt.icon,
                                contentDescription = contentDescription(index),
                                modifier = Modifier.size(26.dp),
                                tint = if (selectedIndex == index) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = opt.label ?: "",
                                fontFamily = SnProFamily,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (selectedIndex == index) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
