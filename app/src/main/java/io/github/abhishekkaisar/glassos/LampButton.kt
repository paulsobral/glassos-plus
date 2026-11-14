package io.github.abhishekkaisar.glassos.personal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.KeyEvent
import android.view.View

/**
 * Round glass button beside the user pill that toggles one Home Assistant entity (a lamp).
 * Lit amber with a dark bulb when on; clear glass with a white bulb when off; dimmed when the state is unknown.
 */
class LampButton(ctx: Context) : View(ctx) {
    var onClick: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null
    var navigate: ((Int) -> Boolean)? = null
    /** True on, false off, null unknown (not reachable, unavailable, not loaded yet). */
    var state: Boolean? = null
        set(v) { field = v; invalidate() }
    var busy = false
        set(v) {
            field = v
            animate().cancel()
            if (v) animate().alpha(0.55f).setDuration(160).start() else animate().alpha(1f).setDuration(160).start()
        }
    private var longFired = false

    private val glass = GlassPainter().apply {
        tint = 0.20f; tintColor = 0xFFFFF4E8.toInt(); sheen = 0.10f; highlight = 0.40f; border = 0.14f; stroke = ctx.dp(0.6f)
    }
    private val focus = FocusAnim(this, 1.12f) { invalidate() }
    private val rect = RectF()
    private val gp = Paint()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val size = ctx.dpi(29f)

    init {
        isFocusable = true
        isClickable = true
        outlineProvider = null
        contentDescription = "Lamp"
        setOnClickListener { onClick?.invoke() }
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(size, size)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        pivotX = w / 2f
        pivotY = h / 2f
    }

    // OK toggles; a long press opens Glass settings (to pick another lamp).
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.repeatCount == 0) { longFired = false; event.startTracking() }
            return true
        }
        if (navigate?.invoke(keyCode) == true) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) && onLongPress != null) {
            longFired = true
            onLongPress?.invoke()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (!longFired && !event.isCanceled) onClick?.invoke()
            longFired = false
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onFocusChanged(gained: Boolean, direction: Int, previous: Rect?) {
        super.onFocusChanged(gained, direction, previous)
        focus.set(gained)
    }

    override fun onDraw(c: Canvas) {
        val f = focus.amount
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        val on = state == true
        rect.set(0f, 0f, w, h)
        GlowCache.draw(c, w, h, r, dp(8f), Color.BLACK, 0.22f + 0.2f * f, dp(3f), gp)
        if (on) GlowCache.draw(c, w, h, r, dp(12f), 0xFFFFB238.toInt(), 0.55f, 0f, gp)
        GlowCache.draw(c, w, h, r, dp(10f), Color.WHITE, 0.28f * f, 0f, gp)
        if (on) {
            fill.style = Paint.Style.FILL
            fill.color = if (f > 0.5f) 0xFFFFD27A.toInt() else 0xFFFFC14D.toInt()
            c.drawCircle(w / 2, h / 2, r, fill)
        } else {
            val g = Tone.pillGlass(Tone.pill)
            glass.tint = g.tint + 0.15f * f
            glass.dark = g.dark
            glass.highlight = 0.40f - 0.18f * g.t
            glass.border = 0.14f - 0.06f * g.t
            glass.draw(c, this, rect, r)
        }

        // Bulb: a round glass on a short neck, with a two-line base.
        val ink = when {
            on -> 0xFF2A1E08.toInt()
            state == null -> 0x80FFFFFF.toInt()
            else -> Color.WHITE
        }
        val cx = w / 2
        val bulbR = h * 0.19f
        val bulbCy = h * 0.42f
        line.color = ink
        line.strokeWidth = h * 0.065f
        if (on) {
            fill.color = alphaOf(ink, 0.22f)
            c.drawCircle(cx, bulbCy, bulbR, fill)
        }
        c.drawCircle(cx, bulbCy, bulbR, line)
        val neckTop = bulbCy + bulbR * 0.80f
        val baseY1 = h * 0.70f
        val baseY2 = h * 0.77f
        val half = bulbR * 0.48f
        c.drawLine(cx - half, neckTop, cx - half, baseY1, line)
        c.drawLine(cx + half, neckTop, cx + half, baseY1, line)
        c.drawLine(cx - half, baseY1, cx + half, baseY1, line)
        c.drawLine(cx - half * 0.7f, baseY2, cx + half * 0.7f, baseY2, line)

        if (f > 0.01f) {
            line.strokeWidth = dp(1.2f)
            line.color = alphaOf(if (on) 0xFF2A1E08.toInt() else Color.WHITE, 0.85f * f)
            val hs = line.strokeWidth / 2
            c.drawCircle(cx, h / 2, r - hs, line)
        }
    }
}
