package com.pnikosis.materialishprogress;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;

/**
 * A Material style progress wheel, compatible up to 2.2.
 * Todd Davies' Progress Wheel https://github.com/Todd-Davies/ProgressWheel
 *
 * @author Nico Hormazábal, takahirom
 *         <p/>
 *         Licensed under the Apache License 2.0 license see:
 *         http://www.apache.org/licenses/LICENSE-2.0
 */
public class ProgressWheel extends View {
    private static final String TAG = ProgressWheel.class.getSimpleName();
    private final int barLength = 16;
    private final int barMaxLength = 270;
    private final long pauseGrowingTime = 200;
    /**
     * *********
     * DEFAULTS *
     * **********
     */
    //Sizes (with defaults in DP)
    private int circleRadius = 28;
    private int barWidth = 4;
    private int rimWidth = 4;
    private boolean fillRadius = false;
    private double timeStartGrowing = 0;
    private double barSpinCycleTime = 460;
    private float barExtraLength = 0;
    private boolean barGrowingFromFront = false;
    private long pausedTimeWithoutGrowing = 0;
    //Colors (with defaults)
    private int barColor = 0xAA000000;
    private int rimColor = 0x00FFFFFF;
    //Paints
    private Paint barPaint = new Paint();
    private Paint rimPaint = new Paint();
    //Rectangles
    private RectF circleBounds = new RectF();
    //Animation
    //The amount of degrees per second
    private float spinSpeed = 230.0f;
    //private float spinSpeed = 120.0f;
    // The last time the spinner was animated
    private long lastTimeAnimated = 0;
    private boolean linearProgress;
    private float mProgress = 0.0f;
    private float mTargetProgress = 0.0f;
    private boolean isSpinning = false;
    private ProgressCallback callback;
    private boolean shouldAnimate;
    private boolean isLineArrow = false;
    private int maxArrowLineLength = 15;

    // FIXME use xml attribute
    private boolean isStartingArrow = true;
    private boolean isFinishingArrow = false;
    private Paint arrowPaint;
    private boolean isPostFinishingArrow = false;
    private Path arrowPath = new Path();

    /**
     * The constructor for the ProgressWheel
     */
    public ProgressWheel(Context context, AttributeSet attrs) {
        super(context, attrs);

        parseAttributes(context.obtainStyledAttributes(attrs, R.styleable.ProgressWheel));

        setAnimationEnabled();

    }

    /**
     * The constructor for the ProgressWheel
     */
    public ProgressWheel(Context context) {
        super(context);
        setAnimationEnabled();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void setAnimationEnabled() {
        int currentApiVersion = android.os.Build.VERSION.SDK_INT;

        float animationValue;
        if (currentApiVersion >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            animationValue = Settings.Global.getFloat(getContext().getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1);
        } else {
            animationValue = Settings.System.getFloat(getContext().getContentResolver(),
                    Settings.System.ANIMATOR_DURATION_SCALE, 1);
        }

        shouldAnimate = animationValue != 0;
    }

    //----------------------------------
    //Setting up stuff
    //----------------------------------

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int viewWidth = circleRadius + this.getPaddingLeft() + this.getPaddingRight();
        int viewHeight = circleRadius + this.getPaddingTop() + this.getPaddingBottom();

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        //Measure Width
        if (widthMode == MeasureSpec.EXACTLY) {
            //Must be this size
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            width = Math.min(viewWidth, widthSize);
        } else {
            //Be whatever you want
            width = viewWidth;
        }

        //Measure Height
        if (heightMode == MeasureSpec.EXACTLY || widthMode == MeasureSpec.EXACTLY) {
            //Must be this size
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            height = Math.min(viewHeight, heightSize);
        } else {
            //Be whatever you want
            height = viewHeight;
        }

        setMeasuredDimension(width, height);
    }

    /**
     * Use onSizeChanged instead of onAttachedToWindow to get the dimensions of the view,
     * because this method is called after measuring the dimensions of MATCH_PARENT & WRAP_CONTENT.
     * Use this dimensions to setup the bounds and paints.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        setupBounds(w, h);
        setupPaints();
        invalidate();
    }

    /**
     * Set the properties of the paints we're using to
     * draw the progress wheel
     */
    private void setupPaints() {
        barPaint.setColor(barColor);
        barPaint.setAntiAlias(true);
        barPaint.setStyle(Style.STROKE);
        barPaint.setStrokeWidth(barWidth);

        rimPaint.setColor(rimColor);
        rimPaint.setAntiAlias(true);
        rimPaint.setStyle(Style.STROKE);
        rimPaint.setStrokeWidth(rimWidth);


        arrowPaint = new Paint();
        arrowPaint.setAntiAlias(true);
        arrowPaint.setColor(barColor);
    }

    /**
     * Set the bounds of the component
     */
    private void setupBounds(int layout_width, int layout_height) {
        int paddingTop = getPaddingTop();
        int paddingBottom = getPaddingBottom();
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();

        if (!fillRadius) {
            // Width should equal to Height, find the min value to setup the circle
            int minValue = Math.min(layout_width - paddingLeft - paddingRight,
                    layout_height - paddingBottom - paddingTop);

            int circleDiameter = Math.min(minValue, circleRadius * 2 - barWidth * 2);

            // Calc the Offset if needed for centering the wheel in the available space
            int xOffset = (layout_width - paddingLeft - paddingRight - circleDiameter) / 2 + paddingLeft;
            int yOffset = (layout_height - paddingTop - paddingBottom - circleDiameter) / 2 + paddingTop;

            circleBounds =
                    new RectF(xOffset + barWidth, yOffset + barWidth, xOffset + circleDiameter - barWidth,
                            yOffset + circleDiameter - barWidth);
        } else {
            circleBounds = new RectF(paddingLeft + barWidth, paddingTop + barWidth,
                    layout_width - paddingRight - barWidth, layout_height - paddingBottom - barWidth);
        }
    }

    /**
     * Parse the attributes passed to the view from the XML
     *
     * @param a the attributes to parse
     */
    private void parseAttributes(TypedArray a) {
        // We transform the default values from DIP to pixels
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        barWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, barWidth, metrics);
        rimWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, rimWidth, metrics);
        circleRadius =
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, circleRadius, metrics);

        circleRadius =
                (int) a.getDimension(R.styleable.ProgressWheel_matProg_circleRadius, circleRadius);

        fillRadius = a.getBoolean(R.styleable.ProgressWheel_matProg_fillRadius, false);

        barWidth = (int) a.getDimension(R.styleable.ProgressWheel_matProg_barWidth, barWidth);
        maxArrowLineLength = (int) a.getDimension(R.styleable.ProgressWheel_matProg_arrowLineLength, maxArrowLineLength);

        rimWidth = (int) a.getDimension(R.styleable.ProgressWheel_matProg_rimWidth, rimWidth);

        float baseSpinSpeed =
                a.getFloat(R.styleable.ProgressWheel_matProg_spinSpeed, spinSpeed / 360.0f);
        spinSpeed = baseSpinSpeed * 360;

        barSpinCycleTime =
                a.getInt(R.styleable.ProgressWheel_matProg_barSpinCycleTime, (int) barSpinCycleTime);

        barColor = a.getColor(R.styleable.ProgressWheel_matProg_barColor, barColor);

        rimColor = a.getColor(R.styleable.ProgressWheel_matProg_rimColor, rimColor);

        linearProgress = a.getBoolean(R.styleable.ProgressWheel_matProg_linearProgress, false);

        isLineArrow = a.getBoolean(R.styleable.ProgressWheel_matProg_lineArrow, false);

        if (a.getBoolean(R.styleable.ProgressWheel_matProg_progressIndeterminate, false)) {
            spin();
        }
        lastTimeAnimated = SystemClock.uptimeMillis();
        start();

        // Recycle
        a.recycle();
    }

    private void start() {
        invalidate();
    }

    public void setCallback(ProgressCallback progressCallback) {
        callback = progressCallback;

        if (!isSpinning) {
            runCallback();
        }
    }

    //----------------------------------
    //Animation stuff
    //----------------------------------

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawArc(circleBounds, 360, 360, false, rimPaint);

        boolean mustInvalidate = false;

        if (!shouldAnimate) {
            return;
        }

        if (isSpinning) {
            //Draw the spinning bar
            mustInvalidate = true;
        }
        long deltaTime = (SystemClock.uptimeMillis() - lastTimeAnimated) / 2;
        float deltaNormalized = deltaTime * spinSpeed / 1000.0f;
        if (!isSpinning) {
            pausedTimeWithoutGrowing = pauseGrowingTime;
            timeStartGrowing = 0;
            deltaTime = 0;
        }

        updateBarLength(deltaTime);

        mProgress += deltaNormalized;
        if (mProgress > 360) {
            mProgress -= 360f;

            // A full turn has been completed
            // we run the callback with -1 in case we want to
            // do something, like changing the color
            runCallback(-1.0f);
        }
        lastTimeAnimated = SystemClock.uptimeMillis();

        float from = mProgress - 90;
        float length = barLength + barExtraLength;

        if (isInEditMode()) {
            from = 0;
            length = 135;
        }

        canvas.drawArc(circleBounds, from, length, false, barPaint);

        if (barGrowingFromFront && isPostFinishingArrow) {
            isFinishingArrow = true;
            isPostFinishingArrow = false;
        }

        boolean isLastDrawArrow = false;
        if (barGrowingFromFront) {
            isStartingArrow = false;
        }
        if (isFinishingArrow && !barGrowingFromFront) {
            isStartingArrow = false;
            isFinishingArrow = false;
            isSpinning = false;
            mustInvalidate = false;
            isLastDrawArrow = true;
        }

        boolean startSpinning = !barGrowingFromFront && isStartingArrow;
        boolean endSpinning = barGrowingFromFront && isFinishingArrow;
        boolean isShowArrow = startSpinning || endSpinning || isLastDrawArrow;
        if (isShowArrow) {
            if (isLineArrow) {
                drawLineArrow(canvas, from, length);
            } else {
                drawArrow(canvas, from, length);
            }
        }


        if (mustInvalidate) {
            invalidate();
        }
    }

    private void drawLineArrow(Canvas canvas, float fromDegree, float lengthDegree) {
        float progress = (barMaxLength - lengthDegree) / (barMaxLength - barLength);
        System.out.println(progress);

        double sin = Math.sin(Math.toRadians(fromDegree + lengthDegree + 5 - 5 * progress));
        double cos = Math.cos(Math.toRadians(fromDegree + lengthDegree + 5 - 5 * progress));

        double sin_45 = Math.sin(Math.toRadians(fromDegree + lengthDegree + 45 - 5 * progress));
        double sin_minus_45 = Math.sin(Math.toRadians(fromDegree + lengthDegree - (45 - 5 * progress)));


        float arrowLength = maxArrowLineLength * (1 - progress);
        int inX = (int) ((sin_minus_45 * maxArrowLineLength) * (1 - progress) + (sin * arrowLength) * progress);
        int inY = (int) ((-sin_45 * maxArrowLineLength) * (1 - progress) + ((-cos * arrowLength) * progress));

        float circleRadius = circleBounds.width() / 2;

        int inBaseX = (int) (cos * (circleRadius + barWidth / 4) + circleBounds.centerX());
        int inBaseY = (int) (sin * (circleRadius + barWidth / 4) + circleBounds.centerY());

        double rotateSin = Math.sin(Math.toRadians(fromDegree + lengthDegree + 5 - 5 * progress + 45 + progress * 115));
        double rotateSinMinus = Math.sin(Math.toRadians(fromDegree + lengthDegree - (5 - 5 * progress + 45) + progress * 115));
        double advancedSin = Math.sin(Math.toRadians(fromDegree + lengthDegree + 5 - 5 * progress - progress * barWidth));
        double advancedCos = Math.cos(Math.toRadians(fromDegree + lengthDegree + 5 - 5 * progress - progress * barWidth));

        int outX = (int) (rotateSin * arrowLength);
        int outY = (int) ((rotateSinMinus * arrowLength));
        int outBaseX;
        int outBaseY;
        if (progress < 0.5f) {
            outBaseX = (int) (advancedCos * (circleRadius + maxArrowLineLength * progress - barWidth / 4) + circleBounds.centerX());
            outBaseY = (int) (advancedSin * (circleRadius + maxArrowLineLength * progress - barWidth / 4) + circleBounds.centerY());
        } else {
            outBaseX = (int) (advancedCos * (circleRadius + maxArrowLineLength * (1 - progress) - barWidth / 4) + circleBounds.centerX());
            outBaseY = (int) (advancedSin * (circleRadius + maxArrowLineLength * (1 - progress) - barWidth / 4) + circleBounds.centerY());
        }

        arrowPaint.setStyle(Style.STROKE);
        arrowPaint.setStrokeWidth(barWidth);
        canvas.drawLine(inBaseX, inBaseY, inBaseX + inX, inBaseY + inY, arrowPaint);
        canvas.drawLine(outBaseX, outBaseY, outBaseX + outX, outBaseY + outY, arrowPaint);
    }

    private void drawArrow(Canvas canvas, float fromDegree, float lengthDegree) {
        int arrowSize = (int) (barWidth * 2 * (1 - (barMaxLength - barExtraLength) / barMaxLength));

        double sin = Math.sin(Math.toRadians(fromDegree + lengthDegree));
        double cos = Math.cos(Math.toRadians(fromDegree + lengthDegree));

        float circleRadius = circleBounds.width() / 2;
        int x = (int) (cos * circleRadius + circleBounds.centerX());
        int y = (int) (sin * circleRadius + circleBounds.centerY());

        int aX = (int) (cos * (circleRadius - barWidth - arrowSize) + circleBounds.centerX());
        int aY = (int) (sin * (circleRadius - barWidth - arrowSize) + circleBounds.centerY());
        int bX = (int) (cos * (circleRadius + barWidth + arrowSize) + circleBounds.centerX());
        int bY = (int) (sin * (circleRadius + barWidth + arrowSize) + circleBounds.centerY());

        int cX = (int) (-sin * arrowSize * 2);
        int cY = (int) (cos * arrowSize * 2);

        arrowPath.rewind();
        arrowPath.setFillType(Path.FillType.EVEN_ODD);
        arrowPath.moveTo(aX, aY);
        arrowPath.lineTo(aX, aY);
        arrowPath.lineTo(bX, bY);
        arrowPath.lineTo(x + cX, y + cY);
        arrowPath.close();

        arrowPaint.setAntiAlias(true);
        arrowPaint.setColor(barColor);

        canvas.drawPath(arrowPath, arrowPaint);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);

        if (visibility == VISIBLE) {
            lastTimeAnimated = SystemClock.uptimeMillis();
        }
    }

    private void updateBarLength(long deltaTimeInMilliSeconds) {
        if (pausedTimeWithoutGrowing >= pauseGrowingTime) {
            timeStartGrowing += deltaTimeInMilliSeconds;

            if (timeStartGrowing > barSpinCycleTime) {
                // We completed a size change cycle
                // (growing or shrinking)
                timeStartGrowing -= barSpinCycleTime;
                //if(barGrowingFromFront) {
                pausedTimeWithoutGrowing = 0;
                //}
                barGrowingFromFront = !barGrowingFromFront;
            }

            float distance =
                    (float) Math.cos((timeStartGrowing / barSpinCycleTime + 1) * Math.PI) / 2 + 0.5f;
            float destLength = (barMaxLength - barLength);

            if (barGrowingFromFront) {
                barExtraLength = distance * destLength;
            } else {
                float newLength = destLength * (1 - distance);
                mProgress += (barExtraLength - newLength);
                barExtraLength = newLength;
            }
        } else {
            pausedTimeWithoutGrowing += deltaTimeInMilliSeconds;
        }
    }

    /**
     * Check if the wheel is currently spinning
     */

    public boolean isSpinning() {
        return isSpinning;
    }

    /**
     * Reset the count (in increment mode)
     */
    public void resetCount() {
        mProgress = 0.0f;
        mTargetProgress = 0.0f;
        invalidate();
    }

    /**
     * Turn off spin mode
     */
    public void stopSpinning() {
        if (isSpinning) {
            isFinishingArrow = false;
            isPostFinishingArrow = true;
        }
        invalidate();
    }


    /**
     * Puts the view on spin mode
     */
    public void spin() {
        lastTimeAnimated = SystemClock.uptimeMillis();
        if (!isSpinning) {
            isSpinning = true;
            isStartingArrow = true;
        }
        invalidate();
    }

    private void runCallback(float value) {
        if (callback != null) {
            callback.onProgressUpdate(value);
        }
    }

    private void runCallback() {
        if (callback != null) {
            float normalizedProgress = (float) Math.round(mProgress * 100 / 360.0f) / 100;
            callback.onProgressUpdate(normalizedProgress);
        }
    }

    /**
     * Set the progress to a specific value,
     * the bar will be set instantly to that value
     *
     * @param progress the progress between 0 and 1
     */
    public void setInstantProgress(float progress) {
        if (isSpinning) {
            mProgress = 0.0f;
            isSpinning = false;
        }

        if (progress > 1.0f) {
            progress -= 1.0f;
        } else if (progress < 0) {
            progress = 0;
        }

        if (progress == mTargetProgress) {
            return;
        }

        mTargetProgress = Math.min(progress * 360.0f, 360.0f);
        mProgress = mTargetProgress;
        lastTimeAnimated = SystemClock.uptimeMillis();
        invalidate();
    }

    // Great way to save a view's state http://stackoverflow.com/a/7089687/1991053
    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        WheelSavedState ss = new WheelSavedState(superState);

        // We save everything that can be changed at runtime
        ss.mProgress = this.mProgress;
        ss.mTargetProgress = this.mTargetProgress;
        ss.isSpinning = this.isSpinning;
        ss.spinSpeed = this.spinSpeed;
        ss.barWidth = this.barWidth;
        ss.barColor = this.barColor;
        ss.rimWidth = this.rimWidth;
        ss.rimColor = this.rimColor;
        ss.circleRadius = this.circleRadius;
        ss.linearProgress = this.linearProgress;
        ss.fillRadius = this.fillRadius;

        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof WheelSavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        WheelSavedState ss = (WheelSavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        this.mProgress = ss.mProgress;
        this.mTargetProgress = ss.mTargetProgress;
        this.isSpinning = ss.isSpinning;
        this.spinSpeed = ss.spinSpeed;
        this.barWidth = ss.barWidth;
        this.barColor = ss.barColor;
        this.rimWidth = ss.rimWidth;
        this.rimColor = ss.rimColor;
        this.circleRadius = ss.circleRadius;
        this.linearProgress = ss.linearProgress;
        this.fillRadius = ss.fillRadius;

        this.lastTimeAnimated = SystemClock.uptimeMillis();
    }

    /**
     * @return the current progress between 0.0 and 1.0,
     * if the wheel is indeterminate, then the result is -1
     */
    public float getProgress() {
        return isSpinning ? -1 : mProgress / 360.0f;
    }

    //----------------------------------
    //Getters + setters
    //----------------------------------

    /**
     * Set the progress to a specific value,
     * the bar will smoothly animate until that value
     *
     * @param progress the progress between 0 and 1
     */
    public void setProgress(float progress) {
        if (isSpinning) {
            mProgress = 0.0f;
            isSpinning = false;

            runCallback();
        }

        if (progress > 1.0f) {
            progress -= 1.0f;
        } else if (progress < 0) {
            progress = 0;
        }

        if (progress == mTargetProgress) {
            return;
        }

        // If we are currently in the right position
        // we set again the last time animated so the
        // animation starts smooth from here
        if (mProgress == mTargetProgress) {
            lastTimeAnimated = SystemClock.uptimeMillis();
        }

        mTargetProgress = Math.min(progress * 360.0f, 360.0f);

        invalidate();
    }

    /**
     * Sets the determinate progress mode
     *
     * @param isLinear if the progress should increase linearly
     */
    public void setLinearProgress(boolean isLinear) {
        linearProgress = isLinear;
        if (!isSpinning) {
            invalidate();
        }
    }

    /**
     * @return the radius of the wheel in pixels
     */
    public int getCircleRadius() {
        return circleRadius;
    }

    /**
     * Sets the radius of the wheel
     *
     * @param circleRadius the expected radius, in pixels
     */
    public void setCircleRadius(int circleRadius) {
        this.circleRadius = circleRadius;
        if (!isSpinning) {
            invalidate();
        }
    }

    /**
     * @return the width of the spinning bar
     */
    public int getBarWidth() {
        return barWidth;
    }

    /**
     * Sets the width of the spinning bar
     *
     * @param barWidth the spinning bar width in pixels
     */
    public void setBarWidth(int barWidth) {
        this.barWidth = barWidth;
        if (!isSpinning) {
            invalidate();
        }
    }

    /**
     * @return the color of the spinning bar
     */
    public int getBarColor() {
        return barColor;
    }

    /**
     * Sets the color of the spinning bar
     *
     * @param barColor The spinning bar color
     */
    public void setBarColor(int barColor) {
        this.barColor = barColor;
        setupPaints();
        if (!isSpinning) {
            invalidate();
        }
    }

    /**
     * @return the color of the wheel's contour
     */
    public int getRimColor() {
        return rimColor;
    }

    /**
     * Sets the color of the wheel's contour
     *
     * @param rimColor the color for the wheel
     */
    public void setRimColor(int rimColor) {
        this.rimColor = rimColor;
        setupPaints();
        if (!isSpinning) {
            invalidate();
        }
    }

    /**
     * @return the base spinning speed, in full circle turns per second
     * (1.0 equals on full turn in one second), this value also is applied for
     * the smoothness when setting a progress
     */
    public float getSpinSpeed() {
        return spinSpeed / 360.0f;
    }

    /**
     * Sets the base spinning speed, in full circle turns per second
     * (1.0 equals on full turn in one second), this value also is applied for
     * the smoothness when setting a progress
     *
     * @param spinSpeed the desired base speed in full turns per second
     */
    public void setSpinSpeed(float spinSpeed) {
        this.spinSpeed = spinSpeed * 360.0f;
    }

    /**
     * @return the width of the wheel's contour in pixels
     */
    public int getRimWidth() {
        return rimWidth;
    }

    /**
     * Sets the width of the wheel's contour
     *
     * @param rimWidth the width in pixels
     */
    public void setRimWidth(int rimWidth) {
        this.rimWidth = rimWidth;
        if (!isSpinning) {
            invalidate();
        }
    }

    public interface ProgressCallback {
        /**
         * Method to call when the progress reaches a value
         * in order to avoid float precision issues, the progress
         * is rounded to a float with two decimals.
         * <p/>
         * In indeterminate mode, the callback is called each time
         * the wheel completes an animation cycle, with, the progress value is -1.0f
         *
         * @param progress a double value between 0.00 and 1.00 both included
         */
        void onProgressUpdate(float progress);
    }

    static class WheelSavedState extends BaseSavedState {
        //required field that makes Parcelables from a Parcel
        public static final Parcelable.Creator<WheelSavedState> CREATOR =
                new Parcelable.Creator<WheelSavedState>() {
                    public WheelSavedState createFromParcel(Parcel in) {
                        return new WheelSavedState(in);
                    }

                    public WheelSavedState[] newArray(int size) {
                        return new WheelSavedState[size];
                    }
                };
        float mProgress;
        float mTargetProgress;
        boolean isSpinning;
        float spinSpeed;
        int barWidth;
        int barColor;
        int rimWidth;
        int rimColor;
        int circleRadius;
        boolean linearProgress;
        boolean fillRadius;

        WheelSavedState(Parcelable superState) {
            super(superState);
        }

        private WheelSavedState(Parcel in) {
            super(in);
            this.mProgress = in.readFloat();
            this.mTargetProgress = in.readFloat();
            this.isSpinning = in.readByte() != 0;
            this.spinSpeed = in.readFloat();
            this.barWidth = in.readInt();
            this.barColor = in.readInt();
            this.rimWidth = in.readInt();
            this.rimColor = in.readInt();
            this.circleRadius = in.readInt();
            this.linearProgress = in.readByte() != 0;
            this.fillRadius = in.readByte() != 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeFloat(this.mProgress);
            out.writeFloat(this.mTargetProgress);
            out.writeByte((byte) (isSpinning ? 1 : 0));
            out.writeFloat(this.spinSpeed);
            out.writeInt(this.barWidth);
            out.writeInt(this.barColor);
            out.writeInt(this.rimWidth);
            out.writeInt(this.rimColor);
            out.writeInt(this.circleRadius);
            out.writeByte((byte) (linearProgress ? 1 : 0));
            out.writeByte((byte) (fillRadius ? 1 : 0));
        }
    }
}
