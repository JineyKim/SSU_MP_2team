package com.ssu.mp.justsnapit.ui.components

import com.ssu.mp.justsnapit.ui.theme.Lime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

@Composable
// 기기 글꼴에 관계없이 일정한 모양의 아이콘을 선과 도형으로 그립니다.
fun SnapIcon(symbol: String, color: Color, size: Int = 26) {
    // 지정된 크기의 그리기 영역에 선과 도형으로 아이콘을 직접 그립니다.
    Canvas(Modifier.size(size.dp)) {
        scale(this.size.width / 24f, this.size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(2f, cap = StrokeCap.Round)
            fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, Offset(x,y), Offset(x2,y2), 2f, StrokeCap.Round)
            when (symbol) {
                "+" -> { line(12f,4f,12f,20f); line(4f,12f,20f,12f) }
                "•••" -> for (x in listOf(5f,12f,19f)) drawCircle(color,1.6f,Offset(x,12f))
                "⊞", "◉" -> for (x in listOf(3f,14f)) for (y in listOf(3f,14f))
                    drawRoundRect(color,Offset(x,y),Size(7f,7f),CornerRadius(1.5f),style=stroke)
                "▣" -> {
                    drawRoundRect(color,Offset(2f,6f),Size(20f,15f),CornerRadius(3f))
                    drawRoundRect(color,Offset(7f,3f),Size(10f,5f),CornerRadius(2f))
                    drawCircle(if (color == Color.Black) Lime else Color.Black,5f,Offset(12f,13f))
                    drawCircle(color,3f,Offset(12f,13f))
                }
                "☺" -> {
                    drawCircle(color,9f,Offset(12f,12f),style=stroke)
                    drawCircle(color,1.2f,Offset(9f,10f)); drawCircle(color,1.2f,Offset(15f,10f))
                    drawArc(color,20f,140f,false,Offset(7f,9f),Size(10f,9f),style=stroke)
                }
                "▧" -> {
                    drawRoundRect(color,Offset(3f,4f),Size(18f,16f),CornerRadius(2f),style=stroke)
                    drawCircle(color,2f,Offset(8f,9f))
                    line(4f,18f,10f,12f); line(10f,12f,14f,16f); line(14f,16f,17f,13f); line(17f,13f,21f,17f)
                }
                "●" -> { drawCircle(color,10f,Offset(12f,12f),style=stroke); drawCircle(color,7.5f,Offset(12f,12f)) }
                "✓" -> { line(4f,12f,10f,18f); line(10f,18f,21f,5f) }
                "◇" -> {
                    val p = Path().apply { moveTo(4f,15f); lineTo(14f,3f); lineTo(21f,9f); lineTo(11f,21f); lineTo(4f,15f); close() }
                    drawPath(p,color,style=stroke); line(7f,12f,14f,18f)
                }
                "↻", "↶" -> {
                    drawArc(color, -65f, 290f, false,Offset(4f,4f),Size(16f,16f),style=stroke)
                    line(13f,2f,17f,5f); line(17f,5f,13f,8f)
                }
                else -> for (x in listOf(5f,12f,19f)) drawCircle(color,1.6f,Offset(x,12f))
            }
        }
    }
}
