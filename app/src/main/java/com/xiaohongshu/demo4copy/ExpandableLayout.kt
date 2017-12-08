package com.xiaohongshu.demo4copy

import android.animation.ValueAnimator
import android.content.Context
import android.support.v4.view.MotionEventCompat
import android.util.AttributeSet
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.expandable_layout.view.*

/**
 * Created by wupengjian on 17/12/7.
 */
class ExpandableLayout(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : FrameLayout(context, attrs, defStyleAttr) {

    private val VELOCITY_THRESHOLD = 300
    private val MOVE_EVENT_THRESHOLD = 5

    private val mInflater: LayoutInflater = LayoutInflater.from(context)
    private var mExpandBodyScrollCallback: IExpandeBodyScollEnabledCallback? = null
    private var mOffsetListener: OnOffsetChangedListener? = null

    private var mParallax: Float = 1f
    private var mLastY: Int = 0
    private var mVelocityTracker: VelocityTracker? = null
    private val mValueAnimator by lazy {
        ValueAnimator.ofInt().apply {
            interpolator = AccelerateDecelerateInterpolator()
            duration = 500
            addUpdateListener {
                val currentValue = it.animatedValue as Int
                setBodyTopMargin(currentValue)
            }
        }
    }

    constructor(context: Context?) : this(context, null)

    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)

    init {
        if (context == null) {
            throw IllegalArgumentException("context can NOT be null !!!")
        }
        mInflater.inflate(R.layout.expandable_layout, this)

        var headerResId: Int = 0
        var bodyResId: Int = 0

        val typedArr = context.obtainStyledAttributes(attrs, R.styleable.ExpandableLayout, defStyleAttr, 0)
        (0..typedArr.indexCount)
                .asSequence()
                .map { typedArr.getIndex(it) }
                .forEach {
                    when (it) {
                        R.styleable.ExpandableLayout_expandableHeader -> {
                            headerResId = typedArr.getResourceId(it, 0)
                        }
                        R.styleable.ExpandableLayout_expandableBody -> {
                            bodyResId = typedArr.getResourceId(it, 0)
                        }
                        R.styleable.ExpandableLayout_expandableParallax -> {
                            mParallax = typedArr.getFloat(it, 1f)
                        }
                    }
                }
        typedArr.recycle()

        if (headerResId != 0) {
            mInflater.inflate(headerResId, expandableHeader)
        }
        if (bodyResId != 0) {
            mInflater.inflate(bodyResId, expandableBody)
        }

        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                doExpand()
                viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }

    /**
     * 手指从上到下，优先子view处理，发生滚动时优先检查子view是否需要处理滚动
     * 手指从下到上，优先父view处理，优先检查父view是否需要折叠
     */
    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) {
            return false
        }
        val action = MotionEventCompat.getActionMasked(ev)
        var handle = false
        if (action == MotionEvent.ACTION_DOWN) {
            if (mVelocityTracker == null) {
                mVelocityTracker = VelocityTracker.obtain()
            } else {
                mVelocityTracker?.clear()
            }
            handle = false
        } else {
            val dy = ev.rawY - mLastY
            //手指从下到上
            if (dy < -MOVE_EVENT_THRESHOLD) {
                handle = getCurrentTopMargin() > getMinTopMargin()
            }
            //手指从上到下
            else if (dy > MOVE_EVENT_THRESHOLD) {
                val canBodyScrollUp2Down = mExpandBodyScrollCallback?.canBodyScroll(true) ?: false
                //如果子view要处理，我们就不处理
                //确保当子view不处理事件时，我们能拦截所有的滑动事件
                handle = !canBodyScrollUp2Down
            }
            if (!handle) {
                handle = super.onInterceptTouchEvent(ev)
            }
        }
        mLastY = ev.rawY.toInt()
        return handle
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null || mValueAnimator.isRunning) {
            return false
        }
        mVelocityTracker?.addMovement(event)

        val dy = event.rawY - mLastY
        val action = MotionEventCompat.getActionMasked(event)
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                //onInterceptTouchEvent中ACTION_DOWN返回false,这里收不到事件
                // 无法做初始化
            }
            MotionEvent.ACTION_MOVE -> {
                //手指从下往上
                if ((dy < -MOVE_EVENT_THRESHOLD && getCurrentTopMargin() > getMinTopMargin())
                        || (dy > MOVE_EVENT_THRESHOLD && getCurrentTopMargin() < getMaxTopMargin())) {
                    moveBy((dy * mParallax).toInt())
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mVelocityTracker?.computeCurrentVelocity(1000)
                val velocityY = -(mVelocityTracker?.yVelocity ?: 0f)
                val halfMargin = getMaxTopMargin() / 2
                if (velocityY > VELOCITY_THRESHOLD) {

                    doFolding()
                } else if (velocityY < -VELOCITY_THRESHOLD) {

                    doExpand()
                } else {
                    if (getCurrentTopMargin() < halfMargin) {
                        doFolding()
                    } else if (getCurrentTopMargin() > halfMargin) {
                        doExpand()
                    }
                }
                mVelocityTracker?.recycle()
                mVelocityTracker = null
            }
        }
        mLastY = event.rawY.toInt()
        return true
    }

    fun moveBy(offset: Int) {
        setBodyTopMargin(getCurrentTopMargin() + offset)
    }

    fun doExpand() {
        adjustTopMarginWithAnimation(getCurrentTopMargin(), expandableHeader.measuredHeight)
    }

    fun doFolding() {
        adjustTopMarginWithAnimation(getCurrentTopMargin(), 0)
    }

    fun adjustTopMarginWithAnimation(start: Int, end: Int) {
        if (mValueAnimator.isRunning) {
            return
        }
        mValueAnimator.setIntValues(start, end)
        mValueAnimator.start()
    }

    fun setBodyTopMargin(topMargin: Int) {

        val maxTopMargin = getMaxTopMargin()
        val minTopMargin = getMinTopMargin()

        var margin = topMargin
        if (margin <= minTopMargin) {
            margin = minTopMargin
        } else if (margin >= maxTopMargin) {
            margin = maxTopMargin
        }

        var params = expandableBody.layoutParams as FrameLayout.LayoutParams
        if (params == null) {
            params = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        if (params.topMargin != margin) {
            params.topMargin = margin
            expandableBody.layoutParams = params
            mOffsetListener?.onOffsetChanged((params.topMargin * 1.0 / getMaxTopMargin()).toFloat())
        }
    }

    fun getMinTopMargin(): Int {
        return 0
    }

    fun getMaxTopMargin(): Int {
        return expandableHeader.measuredHeight
    }

    fun getCurrentTopMargin(): Int {
        var params = expandableBody.layoutParams as FrameLayout.LayoutParams
        if (params == null) {
            params = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        return params.topMargin
    }

    fun getHeader(): View {
        return expandableHeader
    }

    fun getBody(): View {
        return expandableBody
    }

    fun setOnOffsetChangedListener(listener: OnOffsetChangedListener) {
        mOffsetListener = listener
    }

    fun setExpandeBodyScollEnabledCallback(callback: IExpandeBodyScollEnabledCallback) {
        mExpandBodyScrollCallback = callback
    }

    interface IExpandeBodyScollEnabledCallback {
        fun canBodyScroll(isUp2Down: Boolean): Boolean
    }

    interface OnOffsetChangedListener {
        /**
         * @param offsetRate 当前位移百分比 [0,1]
         */
        fun onOffsetChanged(offsetRate: Float)
    }
}
