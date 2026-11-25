package com.ayang.glacier.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Glacier 的雪花图标（品牌标记简化版，24dp 画布）。
 *
 * MIUIX 图标库没有雪花（全量搜过 `Snow` 没有结果），冻结按钮用这个：
 * 3 条主轴 + 6 条臂各一对 60° 分叉，全部按同一半径/角度生成，六向对称。
 * 用描边而不是填充，和官方 Glass 图标（24dp、1.5~1.8dp 圆头描边）的观感一致。
 */
val SnowflakeIcon: ImageVector by lazy {
    val mainColor = SolidColor(Color.Black)
    ImageVector.Builder(
        name = "Snowflake",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.80f, strokeLineCap = StrokeCap.Round) {
            moveTo(12.00f, 4.00f)
            lineTo(12.00f, 20.00f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.80f, strokeLineCap = StrokeCap.Round) {
            moveTo(5.07f, 8.00f)
            lineTo(18.93f, 16.00f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.80f, strokeLineCap = StrokeCap.Round) {
            moveTo(18.93f, 8.00f)
            lineTo(5.07f, 16.00f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.50f, strokeLineCap = StrokeCap.Round) {
            moveTo(12.00f, 17.00f)
            lineTo(9.75f, 18.30f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.50f, strokeLineCap = StrokeCap.Round) {
            moveTo(12.00f, 17.00f)
            lineTo(14.25f, 18.30f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.50f, strokeLineCap = StrokeCap.Round) {
            moveTo(16.33f, 14.50f)
            lineTo(16.33f, 17.10f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.50f, strokeLineCap = StrokeCap.Round) {
            moveTo(16.33f, 14.50f)
            lineTo(18.58f, 13.20f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.50f, strokeLineCap = StrokeCap.Round) {
            moveTo(16.33f, 9.50f)
            lineTo(18.58f, 10.80f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.50f, strokeLineCap = StrokeCap.Round) {
            moveTo(16.33f, 9.50f)
            lineTo(16.33f, 6.90f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.50f, strokeLineCap = StrokeCap.Round) {
            moveTo(12.00f, 7.00f)
            lineTo(14.25f, 5.70f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.50f, strokeLineCap = StrokeCap.Round) {
            moveTo(12.00f, 7.00f)
            lineTo(9.75f, 5.70f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.50f, strokeLineCap = StrokeCap.Round) {
            moveTo(7.67f, 9.50f)
            lineTo(7.67f, 6.90f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.50f, strokeLineCap = StrokeCap.Round) {
            moveTo(7.67f, 9.50f)
            lineTo(5.42f, 10.80f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.50f, strokeLineCap = StrokeCap.Round) {
            moveTo(7.67f, 14.50f)
            lineTo(5.42f, 13.20f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.50f, strokeLineCap = StrokeCap.Round) {
            moveTo(7.67f, 14.50f)
            lineTo(7.67f, 17.10f)
        }
    }.build()
}
