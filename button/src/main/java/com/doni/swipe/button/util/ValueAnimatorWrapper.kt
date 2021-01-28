package com.doni.swipe.button.util

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator

fun <T> ValueAnimator.addListenerExt(
    updateListener: ValueAnimator.(T) -> Unit = {},
    onAnimationEnd: Animator?.() -> Unit = {},
    onAnimationStart: Animator?.() -> Unit = {}
): ValueAnimator {
    addUpdateListener {
        val value: T = it.animatedValue as T
        it.updateListener(value)
    }
    addListener(object : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator?) {
            animation.onAnimationStart()
        }

        override fun onAnimationEnd(animation: Animator?) {
            animation.onAnimationEnd()
        }

        override fun onAnimationCancel(animation: Animator?) {
        }

        override fun onAnimationRepeat(animation: Animator?) {
        }
    })
    return this
}

fun animator(vararg values: Float, onGenerateAnimation: (ValueAnimator) -> Unit): ValueAnimator =
    ValueAnimator.ofFloat(*values).apply(onGenerateAnimation)

fun merge(other: ValueAnimator) = other
fun ValueAnimator.merge(other: ValueAnimator) = listOf(this, other)
fun ValueAnimator.merge(others: MutableList<ValueAnimator>) = others.run {
    add(this@merge)
    this
}

fun List<ValueAnimator>.playTogether() {
    val animatorSet = AnimatorSet()
    animatorSet.playTogether(this)
    animatorSet.start()
}