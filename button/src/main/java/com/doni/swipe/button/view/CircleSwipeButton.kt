package com.doni.swipe.button.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.res.getDrawableOrThrow
import androidx.core.graphics.drawable.toBitmap
import com.doni.swipe.button.R
import com.doni.swipe.button.util.addListenerExt
import com.doni.swipe.button.util.animator
import com.doni.swipe.button.util.merge
import com.doni.swipe.button.util.playTogether
import kotlin.math.pow
import kotlin.math.sqrt

class CircleSwipeButton @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private lateinit var animationPaint: Paint
    private lateinit var shadowPaint: Paint
    private lateinit var circlePaint: Paint
    private lateinit var centerImage: Bitmap

    private var cartesianXCenterCoordinate = 0f
    private var cartesianYCenterCoordinate = 0f

    private var fixedInternalRadius = 0f
    private var fixedPressedExternalRadius = 0f

    private var lastXPressedCoordinate = 0f
    private var lastYPressedCoordinate = 0f

    //Idle animation variables
    private var idleExpandingPercentage = 0f
    private var idleAlphaPercentage = 0f
    private var idleExternalRadius = 0f
    private var idleAlphaValue = 0

    //Close animation variables
    private var closePressedInternalSize = 0f
    private var closePressedExternalSize = 0f

    //Open pressed animation variables
    private var openPressedExternalSize = 0f

    private var path = Path()
    private var animationState = AnimationState.IDLE_RUNNING

    init {
        context?.let {
            val attributes = it.theme.obtainStyledAttributes(
                attrs,
                R.styleable.CircleSwipeButton,
                0,
                0
            )
            fixedInternalRadius =
                attributes.getDimensionPixelSize(
                    R.styleable.CircleSwipeButton_fixedInternalRadius,
                    120
                ).toFloat()
            fixedPressedExternalRadius =
                attributes.getDimensionPixelSize(
                    R.styleable.CircleSwipeButton_fixedPressedExternalRadius,
                    240
                ).toFloat()

            idleExternalRadius =
                attributes.getDimensionPixelSize(
                    R.styleable.CircleSwipeButton_idleExternalRadius,
                    180
                ).toFloat()

            centerImage = (attributes.getDrawable(
                R.styleable.CircleSwipeButton_centerImage
            ) ?: ResourcesCompat.getDrawable(resources, R.drawable.x, null)!!)
                .toBitmap(fixedInternalRadius.toInt(), fixedInternalRadius.toInt())

            idleAlphaValue = 180
            animationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
            }

            shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.BLACK
                alpha = 100
                maskFilter = BlurMaskFilter(
                    25F,
                    BlurMaskFilter.Blur.NORMAL
                )
            }
            circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
            }

            attributes.recycle()
        }
    }

    private fun distanceBetweenPoints(xa: Float, ya: Float, xb: Float, yb: Float) =
        sqrt((xa - xb).toDouble().pow(2.0) + (ya - yb).toDouble().pow(2.0)).toFloat()

    private fun touchInCenter(x: Float, y: Float): Boolean {
        lastXPressedCoordinate = x
        lastYPressedCoordinate = y
        val radius = distanceBetweenPoints(
            lastXPressedCoordinate, lastYPressedCoordinate,
            cartesianXCenterCoordinate, cartesianYCenterCoordinate
        )
        return fixedInternalRadius >= radius
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        cartesianXCenterCoordinate = MeasureSpec.getSize(widthMeasureSpec) / 2f
        cartesianYCenterCoordinate = MeasureSpec.getSize(heightMeasureSpec) / 2f
        startIdleAnimation()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (animationState == AnimationState.IDLE_RUNNING) {
                    if (touchInCenter(event.x, event.y)) {
                        animationState = AnimationState.PRESSED_RUNNING
                        startOpenPressedAnimation()
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (animationState == AnimationState.PRESSED_RUNNING) {
                    lastXPressedCoordinate = event.x
                    lastYPressedCoordinate = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (animationState == AnimationState.PRESSED_RUNNING) {
                    animationState = AnimationState.CLOSE_PRESSED_RUNNING
                    startClosePressedAnimation()
                }
                return true
            }
        }
        return false
    }

    private fun startOpenPressedAnimation() {
        animator(
            fixedInternalRadius * 2,
            fixedPressedExternalRadius
        ) {
            it.duration = 350
        }.addListenerExt<Float>(
            updateListener = update@{ value ->
                takeUnless {
                    (animationState == AnimationState.PRESSED_RUNNING)
                }?.let {
                    cancel()
                    return@update
                }
                openPressedExternalSize = value
                invalidate()
            }

        ).start()
    }

    private fun startClosePressedAnimation() {
        if (animationState != AnimationState.CLOSE_PRESSED_RUNNING) {
            return
        }
        var newRadius = distanceBetweenPoints(
            lastXPressedCoordinate, lastYPressedCoordinate,
            cartesianXCenterCoordinate, cartesianYCenterCoordinate
        )
        if (newRadius > fixedPressedExternalRadius) {
            newRadius = fixedPressedExternalRadius
        }

        merge(
            animator(newRadius, 0f) {
                it.duration = 450
            }.addListenerExt<Float>(
                updateListener = { value ->
                    closePressedInternalSize = value
                    invalidate()
                },
                onAnimationEnd = {
                    animationState = AnimationState.IDLE_RUNNING
                    startIdleAnimation()
                }
            )
        ).merge(
            animator(openPressedExternalSize, 0f) {
                it.duration = 500
            }.addListenerExt<Float>(
                updateListener = { value ->
                    closePressedExternalSize = value
                }
            )
        ).playTogether()
    }

    private fun startIdleAnimation() {
        if (animationState != AnimationState.IDLE_RUNNING) {
            return
        }

        merge(
            animator(0f, 100f) {
                it.duration = 1800
            }.addListenerExt<Float>(
                updateListener = update@{ value ->
                    if (animationState == AnimationState.IDLE_RUNNING) {
                        invalidate()
                    } else {
                        cancel()
                        return@update
                    }
                    idleExpandingPercentage = value
                },
                onAnimationEnd = {
                    idleExpandingPercentage = 0f
                    if (animationState == AnimationState.IDLE_RUNNING) {
                        startIdleAnimation()
                    }
                }
            )
        ).merge(
            animator(100f, 0f) {
                it.duration = 1800
            }.addListenerExt<Float>(
                updateListener = update@{ value ->
                    if (animationState != AnimationState.IDLE_RUNNING) {
                        cancel()
                        return@update
                    }
                    idleAlphaPercentage = value
                }
            )
        ).playTogether()
    }

    private fun drawIdleAnimation(canvas: Canvas) {
        path.addCircle(
            cartesianXCenterCoordinate, cartesianYCenterCoordinate, fixedInternalRadius,
            Path.Direction.CW
        )
        path.addCircle(
            cartesianXCenterCoordinate, cartesianYCenterCoordinate,
            fixedInternalRadius + idleExternalRadius * (idleExpandingPercentage / 100),
            Path.Direction.CW
        )
        path.fillType = Path.FillType.EVEN_ODD
        animationPaint.alpha = (idleAlphaValue * idleAlphaPercentage / 100).toInt()
        canvas.drawPath(path, animationPaint)
        path.reset()
    }

    private fun drawPressedAnimation(canvas: Canvas) {
        var newInternalRadius = distanceBetweenPoints(
            lastXPressedCoordinate,
            lastYPressedCoordinate, cartesianXCenterCoordinate,
            cartesianYCenterCoordinate
        )
        if (newInternalRadius > fixedPressedExternalRadius) {
            newInternalRadius = fixedPressedExternalRadius
            animationState = AnimationState.CLOSE_PRESSED_RUNNING
            startClosePressedAnimation()
        }
        path.addCircle(
            cartesianXCenterCoordinate, cartesianYCenterCoordinate, newInternalRadius,
            Path.Direction.CW
        )
        path.addCircle(
            cartesianXCenterCoordinate, cartesianYCenterCoordinate,
            openPressedExternalSize, Path.Direction.CW
        )
        path.fillType = Path.FillType.EVEN_ODD
        animationPaint.alpha = idleAlphaValue
        canvas.drawPath(path, animationPaint)
        path.reset()
    }

    private fun drawClosePressedAnimation(canvas: Canvas) {
        path.addCircle(
            cartesianXCenterCoordinate, cartesianYCenterCoordinate,
            closePressedInternalSize, Path.Direction.CW
        )
        path.addCircle(
            cartesianXCenterCoordinate, cartesianYCenterCoordinate,
            closePressedExternalSize, Path.Direction.CW
        )
        path.fillType = Path.FillType.EVEN_ODD
        animationPaint.alpha = idleAlphaValue
        canvas.drawPath(path, animationPaint)
        path.reset()
    }

    private fun drawCenterIndicator(canvas: Canvas) {
        canvas.drawCircle(
            cartesianXCenterCoordinate, cartesianYCenterCoordinate,
            fixedInternalRadius, shadowPaint
        )
        canvas.drawCircle(
            cartesianXCenterCoordinate, cartesianYCenterCoordinate,
            fixedInternalRadius, circlePaint
        )
        val drawableCenterCorrection = fixedInternalRadius / 2
        canvas.drawBitmap(
            centerImage, cartesianXCenterCoordinate - drawableCenterCorrection,
            cartesianYCenterCoordinate - drawableCenterCorrection, circlePaint
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (animationState) {
            AnimationState.IDLE_RUNNING -> drawIdleAnimation(canvas)
            AnimationState.PRESSED_RUNNING -> drawPressedAnimation(canvas)
            AnimationState.CLOSE_PRESSED_RUNNING -> drawClosePressedAnimation(canvas)
        }
        drawCenterIndicator(canvas)
    }

    private enum class AnimationState {
        IDLE_RUNNING, PRESSED_RUNNING, CLOSE_PRESSED_RUNNING
    }
}