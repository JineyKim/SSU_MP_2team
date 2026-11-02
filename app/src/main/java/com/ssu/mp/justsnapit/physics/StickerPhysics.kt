package com.ssu.mp.justsnapit.physics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

internal class StickerPhysics {
    var x by mutableFloatStateOf(0f)
        private set
    var y by mutableFloatStateOf(-200f)
        private set
    var angle by mutableFloatStateOf(0f)
        private set
    private var vx = 0f
    private var vy = 0f
    var dragging = false
        private set

    // 수집함 너비와 목록 순서를 기준으로 스티커의 가로 위치를 정합니다.
    fun place(width: Float, size: Float, index: Int) {
        x = (width - size).coerceAtLeast(0f) * ((index * .31f + .38f) % 1f)
    }

    // 바닥에서 충분히 느려진 스티커가 정지 상태인지 확인합니다.
    fun isResting(height: Float, size: Float): Boolean =
        y >= (height - size).coerceAtLeast(0f) && vy == 0f && abs(vx) < 1f

    // 경과 시간에 따른 중력, 이동, 바닥과 벽의 반사 및 회전을 계산합니다.
    fun update(dt: Float, width: Float, height: Float, size: Float) {
        if (dragging) return
        vy += 1700f * dt
        x += vx * dt
        y += vy * dt
        val floor = (height - size).coerceAtLeast(0f)
        if (y >= floor) {
            y = floor
            vy = if (abs(vy) < 65f) 0f else -vy * .28f
            vx *= .92f
        }
        val wall = (width - size).coerceAtLeast(0f)
        if (x < 0 || x > wall) {
            x = x.coerceIn(0f, wall)
            vx *= -.4f
        }
        angle = (vx * .025f).coerceIn(-25f, 25f)
    }

    // 드래그를 시작하면서 낙하 계산을 멈추고 이동 속도를 초기화합니다.
    fun startDrag() {
        dragging = true
        vx = 0f
        vy = 0f
    }

    // 손가락 이동량을 영역 안의 위치와 놓은 뒤의 속도에 반영합니다.
    fun dragBy(dx: Float, dy: Float, width: Float, height: Float, size: Float) {
        x = (x + dx).coerceIn(0f, (width - size).coerceAtLeast(0f))
        y = (y + dy).coerceIn(0f, (height - size).coerceAtLeast(0f))
        vx = (dx * 20).coerceIn(-900f, 900f)
        vy = (dy * 20).coerceIn(-900f, 900f)
    }

    // 드래그가 끝나면 물리 계산을 다시 진행할 수 있게 합니다.
    fun endDrag() {
        dragging = false
    }
}
