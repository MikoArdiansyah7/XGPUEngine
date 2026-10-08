package com.xgpu.engine.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import java.util.LinkedList

class FpsGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val MAX_POINTS = 40
    private val gameFpsHistory = LinkedList<Float>()  // green: 119-120
    private val fakeFpsHistory = LinkedList<Float>()  // cyan: 165-185

    private val paintGame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF88")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val paintFake = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00CFFF")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val paintGameFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4000FF88")  // 25% green
        style = Paint.Style.FILL
    }
    private val paintFakeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4000CFFF")  // 25% cyan
        style = Paint.Style.FILL
    }
    private val paintGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1AFFFFFF")
        strokeWidth = 0.5f
        style = Paint.Style.STROKE
    }

    // min/max for combined scale
    private val minFps = 100f
    private val maxFps = 195f

    fun addDataPoint(gameFps: Float, fakeFps: Float) {
        gameFpsHistory.addLast(gameFps)
        fakeFpsHistory.addLast(fakeFps)
        if (gameFpsHistory.size > MAX_POINTS) gameFpsHistory.removeFirst()
        if (fakeFpsHistory.size > MAX_POINTS) fakeFpsHistory.removeFirst()
        invalidate()
    }

    fun clear() {
        gameFpsHistory.clear()
        fakeFpsHistory.clear()
        invalidate()
    }

    private fun fpsToY(fps: Float, h: Float): Float =
        h - ((fps - minFps) / (maxFps - minFps)) * h * 0.9f - h * 0.05f

    private fun xAt(i: Int, total: Int, w: Float): Float =
        if (total <= 1) 0f else i.toFloat() / (total - 1) * w

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Grid
        for (i in 1..3) canvas.drawLine(0f, h * i / 4, w, h * i / 4, paintGrid)

        val gameList = gameFpsHistory.toList()
        val fakeList = fakeFpsHistory.toList()
        val n = minOf(gameList.size, fakeList.size)
        if (n < 2) return

        // Fill fake (cyan) - behind game
        val fakeFillPath = Path()
        fakeFillPath.moveTo(xAt(0, n, w), h)
        for (i in 0 until n) fakeFillPath.lineTo(xAt(i, n, w), fpsToY(fakeList[i], h))
        fakeFillPath.lineTo(xAt(n - 1, n, w), h)
        fakeFillPath.close()
        canvas.drawPath(fakeFillPath, paintFakeFill)

        // Fill game (green) - on top
        val gameFillPath = Path()
        gameFillPath.moveTo(xAt(0, n, w), h)
        for (i in 0 until n) gameFillPath.lineTo(xAt(i, n, w), fpsToY(gameList[i], h))
        gameFillPath.lineTo(xAt(n - 1, n, w), h)
        gameFillPath.close()
        canvas.drawPath(gameFillPath, paintGameFill)

        // Stroke fake line
        val fakePath = Path()
        for (i in 0 until n) {
            val x = xAt(i, n, w); val y = fpsToY(fakeList[i], h)
            if (i == 0) fakePath.moveTo(x, y) else fakePath.lineTo(x, y)
        }
        canvas.drawPath(fakePath, paintFake)

        // Stroke game line
        val gamePath = Path()
        for (i in 0 until n) {
            val x = xAt(i, n, w); val y = fpsToY(gameList[i], h)
            if (i == 0) gamePath.moveTo(x, y) else gamePath.lineTo(x, y)
        }
        canvas.drawPath(gamePath, paintGame)
    }
}
