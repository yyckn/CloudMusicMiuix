package com.musichub.miuix.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.cos
import kotlin.math.sin

/**
 * Live封面标识
 * 图标和文字都常显示
 *
 * @param visible 是否可见（控制整体显示/隐藏，用于歌词显示时隐藏）
 * @param modifier Modifier
 * @param compact 是否使用紧凑模式（手机布局使用）
 * @param onClick 点击回调
 */
@Composable
fun LiveBadge(
    visible: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit = {}
) {
    if (visible) {
        val iconSize = if (compact) 14.dp else 20.dp
        val fontSize = if (compact) 11.sp else 14.sp
        val horizontalPadding = if (compact) 6.dp else 10.dp
        val verticalPadding = if (compact) 3.dp else 6.dp
        val spacing = if (compact) 3.dp else 6.dp
        
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF333333).copy(alpha = 0.3f))
                .clickable { onClick() }
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 自定义图标：空心圈 + 周围圆点
                LiveIcon(
                    modifier = Modifier.size(iconSize),
                    color = Color.White
                )
                
                // Live文字
                Text(
                    text = "Live",
                    fontSize = fontSize,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Live图标：中间空心圈，周围一圈圆点
 */
@Composable
private fun LiveIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 2
        
        // 中间空心圈
        val circleRadius = radius * 0.35f
        drawCircle(
            color = color,
            radius = circleRadius,
            center = Offset(centerX, centerY),
            style = Stroke(width = radius * 0.12f)
        )
        
        // 周围圆点
        val dotRadius = radius * 0.1f
        val dotDistance = radius * 0.7f
        val dotCount = 8
        
        for (i in 0 until dotCount) {
            val angle = (i * 360.0 / dotCount) * (Math.PI / 180.0)
            val x = centerX + (dotDistance * cos(angle)).toFloat()
            val y = centerY + (dotDistance * sin(angle)).toFloat()
            
            drawCircle(
                color = color,
                radius = dotRadius,
                center = Offset(x, y)
            )
        }
    }
}
