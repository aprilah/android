package uikit.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.tonapps.uikit.color.backgroundContentTintColor
import com.tonapps.uikit.color.buttonPrimaryBackgroundColor
import uikit.extensions.dp

class RadioView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private companion object {
        private val size = 24.dp
        private val strokeSize = 2f.dp
        private val activeSize = 12f.dp
    }

    var doOnCheckedChanged: ((Boolean) -> Unit)? = null

    var checked: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                defaultPaint.color = if (value) {
                    context.buttonPrimaryBackgroundColor
                } else {
                    context.backgroundContentTintColor
                }
                doOnCheckedChanged?.invoke(value)
                invalidate()
            }
        }

    private val defaultPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.backgroundContentTintColor
        style = Paint.Style.STROKE
        strokeWidth = strokeSize
    }

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.buttonPrimaryBackgroundColor
    }

    init {
        setOnClickListener { toggle() }
    }

    fun toggle() {
        checked = !checked
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = size / 2f

        canvas.drawCircle(radius, radius, radius - strokeSize / 2f, defaultPaint)

        if (checked) {
            canvas.drawCircle(radius, radius, activeSize / 2f, activePaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        super.onMeasure(size, size)
    }

    override fun hasOverlappingRendering() = false
}