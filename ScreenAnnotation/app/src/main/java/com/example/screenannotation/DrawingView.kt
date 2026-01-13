package com.example.screenannotation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class DrawingView(context: Context) : View(context) {

    enum class Mode {
        NONE, ARROW, RECTANGLE, FREEHAND
    }

    var currentMode: Mode = Mode.NONE

    private val shapes = mutableListOf<Shape>()
    private var currentShape: Shape? = null

    private val paint = Paint().apply {
        color = Color.RED
        strokeWidth = 6f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val arrowPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 6f
        style = Paint.Style.FILL_AND_STROKE
        isAntiAlias = true
    }

    // 细红线画笔
    private val freehandPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 3f  // 细线
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // 当前正在绘制的自由线条路径
    private var currentPath: Path? = null

    sealed class Shape {
        data class Arrow(val start: PointF, val end: PointF) : Shape()
        data class Rectangle(val start: PointF, val end: PointF) : Shape()
        data class FreehandLine(val path: Path) : Shape()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制已完成的形状
        for (shape in shapes) {
            drawShape(canvas, shape)
        }

        // 绘制当前正在绘制的形状
        currentShape?.let {
            drawShape(canvas, it)
        }

        // 绘制当前正在绘制的自由线条
        currentPath?.let {
            canvas.drawPath(it, freehandPaint)
        }
    }

    private fun drawShape(canvas: Canvas, shape: Shape) {
        when (shape) {
            is Shape.Arrow -> drawArrow(canvas, shape.start, shape.end)
            is Shape.Rectangle -> drawRectangle(canvas, shape.start, shape.end)
            is Shape.FreehandLine -> canvas.drawPath(shape.path, freehandPaint)
        }
    }

    private fun drawArrow(canvas: Canvas, start: PointF, end: PointF) {
        // 绘制箭头主线
        canvas.drawLine(start.x, start.y, end.x, end.y, paint)

        // 计算箭头头部
        val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
        val arrowLength = 40f
        val arrowAngle = Math.PI / 6  // 30度

        val x1 = end.x - arrowLength * cos(angle - arrowAngle).toFloat()
        val y1 = end.y - arrowLength * sin(angle - arrowAngle).toFloat()
        val x2 = end.x - arrowLength * cos(angle + arrowAngle).toFloat()
        val y2 = end.y - arrowLength * sin(angle + arrowAngle).toFloat()

        // 绘制箭头头部
        val arrowPath = Path().apply {
            moveTo(end.x, end.y)
            lineTo(x1, y1)
            lineTo(x2, y2)
            close()
        }
        canvas.drawPath(arrowPath, arrowPaint)
    }

    private fun drawRectangle(canvas: Canvas, start: PointF, end: PointF) {
        val left = minOf(start.x, end.x)
        val top = minOf(start.y, end.y)
        val right = maxOf(start.x, end.x)
        val bottom = maxOf(start.y, end.y)
        canvas.drawRect(left, top, right, bottom, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (currentMode == Mode.NONE) return false

        // 自由绘制模式的特殊处理
        if (currentMode == Mode.FREEHAND) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    currentPath = Path().apply {
                        moveTo(event.x, event.y)
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    currentPath?.lineTo(event.x, event.y)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    currentPath?.let {
                        shapes.add(Shape.FreehandLine(Path(it)))
                    }
                    currentPath = null
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        // 箭头和矩形模式的处理
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val start = PointF(event.x, event.y)
                currentShape = when (currentMode) {
                    Mode.ARROW -> Shape.Arrow(start, start)
                    Mode.RECTANGLE -> Shape.Rectangle(start, start)
                    else -> null
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val end = PointF(event.x, event.y)
                currentShape = when (val shape = currentShape) {
                    is Shape.Arrow -> shape.copy(end = end)
                    is Shape.Rectangle -> shape.copy(end = end)
                    else -> null
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                currentShape?.let {
                    shapes.add(it)
                }
                currentShape = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun clearDrawings() {
        shapes.clear()
        currentShape = null
        currentPath = null
        invalidate()
    }

    fun getDrawingBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas)
        return bitmap
    }
}
