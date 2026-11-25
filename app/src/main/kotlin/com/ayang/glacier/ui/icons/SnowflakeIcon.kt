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
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.90f, strokeLineCap = StrokeCap.Round) {
            moveTo(12.00f, 1.00f)
            lineTo(12.00f, 23.00f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.90f, strokeLineCap = StrokeCap.Round) {
            moveTo(2.47f, 6.50f)
            lineTo(21.53f, 17.50f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.90f, strokeLineCap = StrokeCap.Round) {
            moveTo(21.53f, 6.50f)
            lineTo(2.47f, 17.50f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.60f, strokeLineCap = StrokeCap.Round) {
            moveTo(12.00f, 18.90f)
            lineTo(9.06f, 20.60f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.60f, strokeLineCap = StrokeCap.Round) {
            moveTo(12.00f, 18.90f)
            lineTo(14.94f, 20.60f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.60f, strokeLineCap = StrokeCap.Round) {
            moveTo(17.98f, 15.45f)
            lineTo(17.98f, 18.85f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.60f, strokeLineCap = StrokeCap.Round) {
            moveTo(17.98f, 15.45f)
            lineTo(20.92f, 13.75f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.60f, strokeLineCap = StrokeCap.Round) {
            moveTo(17.98f, 8.55f)
            lineTo(20.92f, 10.25f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.60f, strokeLineCap = StrokeCap.Round) {
            moveTo(17.98f, 8.55f)
            lineTo(17.98f, 5.15f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.60f, strokeLineCap = StrokeCap.Round) {
            moveTo(12.00f, 5.10f)
            lineTo(14.94f, 3.40f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.60f, strokeLineCap = StrokeCap.Round) {
            moveTo(12.00f, 5.10f)
            lineTo(9.06f, 3.40f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.60f, strokeLineCap = StrokeCap.Round) {
            moveTo(6.02f, 8.55f)
            lineTo(6.02f, 5.15f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.60f, strokeLineCap = StrokeCap.Round) {
            moveTo(6.02f, 8.55f)
            lineTo(3.08f, 10.25f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.60f, strokeLineCap = StrokeCap.Round) {
            moveTo(6.02f, 15.45f)
            lineTo(3.08f, 13.75f)
        }
        path(fill = null, stroke = mainColor, strokeLineWidth = 1.60f, strokeLineCap = StrokeCap.Round) {
            moveTo(6.02f, 15.45f)
            lineTo(6.02f, 18.85f)
        }
    }.build()
}
