package com.example.myapplication.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.myapplication.data.Series
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Line chart ringan (tanpa library). Dua mode:
 * - live  : jendela mengikuti sampel terbaru selebar [windowSec].
 * - replay: zoom (cubit), pan (geser), dobel-ketuk = tampilkan semua.
 * Nilai NaN pada deret = garis putus. Data panjang di-decimate per piksel (min/max) hanya untuk tampilan.
 */
class WaveformView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var series: Series? = null
    var title = ""
    /** Teks kecil di kanan atas (mis. laju sampling / durasi jendela). */
    var subtitle = ""
    var lineColor = Color.parseColor("#3DDC84")
    var live = true
    var windowSec = 10.0
    /** Pesan di tengah grafik (mis. "Elektroda terlepas"); null = tidak ada. */
    var message: String? = null
    var onWindowChanged: ((Double, Double) -> Unit)? = null

    private var viewMin = 0.0
    private var viewMax = 10.0

    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val bg = Paint().apply { color = Color.parseColor("#0F151B") }
    private val grid = Paint().apply { color = Color.parseColor("#26323C"); strokeWidth = 1f }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f * dp; strokeJoin = Paint.Join.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8FA3B3"); textSize = 10 * sp }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13 * sp; isFakeBoldText = true }
    private val msgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFB74D"); textSize = 14 * sp; textAlign = Paint.Align.CENTER }
    private val path = Path()

    private val padL get() = 52 * dp
    private val padT get() = 30 * dp
    private val padR get() = 12 * dp
    private val padB get() = 22 * dp
    private val plotW get() = max(1f, width - padL - padR)

    fun setWindow(min: Double, max: Double) { viewMin = min; viewMax = max; invalidate() }

    fun fitAll() {
        val s = series ?: return
        if (s.size == 0) return
        viewMin = s.firstX(); viewMax = max(s.lastX(), viewMin + 1.0)
        invalidate(); onWindowChanged?.invoke(viewMin, viewMax)
    }

    private fun clampWindow() {
        val s = series ?: return
        if (s.size == 0) return
        val dMin = s.firstX(); val dMax = max(s.lastX(), dMin + 1.0)
        val span = (viewMax - viewMin).coerceIn(0.5, dMax - dMin)
        viewMin = viewMin.coerceIn(dMin, dMax - span)
        viewMax = viewMin + span
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
                val left = padL; val top = padT; val right = w - padR; val bottom = h - padB
        val pw = right - left; val ph = bottom - top
        if (pw <= 0 || ph <= 0) return
        titlePaint.color = lineColor
        c.drawText(title, left, top - 7 * dp, titlePaint)
        if (subtitle.isNotEmpty()) c.drawText(subtitle, right - txt.measureText(subtitle), top - 7 * dp, txt)

        val s = series
        var x0 = viewMin; var x1 = viewMax
        if (live) {
            x1 = if (s != null && s.size > 0) s.lastX() else windowSec
            x0 = x1 - windowSec
        }
        val span = x1 - x0

        // grid vertikal + label waktu
        val step = niceStep(span / 6)
        var t = ceil(x0 / step) * step
        while (t <= x1) {
            val px = left + ((t - x0) / span * pw).toFloat()
            c.drawLine(px, top, px, bottom, grid)
            val label = if (live) "%.0fs".format(t - x1) else "%.1fs".format(t)
            val lw = txt.measureText(label)
            c.drawText(label, (px - lw / 2).coerceIn(left, w - lw - 8 * dp), h - 7 * dp, txt)
            t += step
        }
        for (i in 0..4) { val py = top + ph * i / 4; c.drawLine(left, py, right, py, grid) }

        if (s == null || s.size == 0) { drawMessage(c, message ?: "Menunggu data…", w, h); return }

        val i0 = max(0, s.lowerBound(x0) - 1)
        val i1 = min(s.size - 1, s.lowerBound(x1))
        var ymin = Float.MAX_VALUE; var ymax = -Float.MAX_VALUE
        for (i in i0..i1) { val y = s.ys[i]; if (!y.isNaN()) { if (y < ymin) ymin = y; if (y > ymax) ymax = y } }
        if (ymin > ymax) { drawMessage(c, message ?: "Tidak ada data valid", w, h); return }
        if (ymax - ymin < 1e-3f) { ymin -= 1; ymax += 1 }
        val pad = (ymax - ymin) * 0.08f
        ymin -= pad; ymax += pad
        c.drawText("%.0f".format(ymax), 10 * dp, top + 10 * sp, txt)
        c.drawText("%.0f".format(ymin), 10 * dp, bottom, txt)

        val sx = pw / span
        val sy = ph / (ymax - ymin)
        path.rewind()
        val n = i1 - i0 + 1
        var pen = false
        if (n <= pw * 2) {
            for (i in i0..i1) {
                val y = s.ys[i]
                if (y.isNaN()) { pen = false; continue }
                val px = left + ((s.xs[i] - x0) * sx).toFloat()
                val py = bottom - (y - ymin) * sy
                if (!pen) { path.moveTo(px, py); pen = true } else path.lineTo(px, py)
            }
        } else {                       // decimation min/max per kolom piksel
            var col = Int.MIN_VALUE; var top0 = Float.MAX_VALUE; var bot0 = -Float.MAX_VALUE
            fun flush() {
                if (col == Int.MIN_VALUE || top0 > bot0) return
                val px = left + col + 0.5f
                if (!pen) { path.moveTo(px, bot0); pen = true } else path.lineTo(px, bot0)
                path.lineTo(px, top0)
                top0 = Float.MAX_VALUE; bot0 = -Float.MAX_VALUE
            }
            for (i in i0..i1) {
                val y = s.ys[i]
                if (y.isNaN()) { flush(); pen = false; col = Int.MIN_VALUE; continue }
                val cx = ((s.xs[i] - x0) * sx).toInt()
                if (cx != col) { flush(); col = cx }
                val py = bottom - (y - ymin) * sy
                if (py < top0) top0 = py
                if (py > bot0) bot0 = py
            }
            flush()
        }
        c.save(); c.clipRect(left, top, right, bottom)
        line.color = lineColor
        c.drawPath(path, line)
        c.restore()
        message?.let { drawMessage(c, it, w, h) }
    }

    private fun drawMessage(c: Canvas, m: String, w: Float, h: Float) {
        c.drawText(m, w / 2, h / 2, msgPaint)
    }

    private fun niceStep(raw: Double): Double {
        val steps = doubleArrayOf(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0, 60.0, 120.0, 300.0)
        return steps.firstOrNull { it >= raw } ?: steps.last()
    }

    // ---- gestur (hanya mode replay) --------------------------------------------------------

    private val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            if (live) return false
            val span = viewMax - viewMin
            val focus = viewMin + (d.focusX - padL) / plotW * span
            val newSpan = span / d.scaleFactor
            viewMin = focus - (focus - viewMin) * newSpan / span
            viewMax = viewMin + newSpan
            clampWindow(); invalidate(); onWindowChanged?.invoke(viewMin, viewMax)
            return true
        }
    })

    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (live) return false
            val dt = dx / plotW * (viewMax - viewMin)
            viewMin += dt; viewMax += dt
            clampWindow(); invalidate(); onWindowChanged?.invoke(viewMin, viewMax)
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean { if (!live) fitAll(); return true }
    })

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (live) return false
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(e)
        if (!scaleDetector.isInProgress) gestures.onTouchEvent(e)
        return true
    }

}
