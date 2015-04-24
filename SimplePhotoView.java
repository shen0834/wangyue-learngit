
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewTreeObserver;

import com.facebook.drawee.drawable.FadeDrawable;
import com.facebook.drawee.generic.GenericDraweeHierarchy;
import com.facebook.drawee.view.SimpleDraweeView;
import com.lidroid.xutils.util.LogUtils;

import butterknife.OnTouch;

/**
 * A SimpleDraweeView that supports Pinch to zoom and drag
 */
public class SimplePhotoView extends SimpleDraweeView implements View.OnTouchListener {
    private final ScaleGestureDetector mScaleDetector;
    private final ScaleGestureDetector.OnScaleGestureListener mScaleListener;
    private boolean isAutoScale;
    private float mCurrentScale = 1.0f;
    private final Matrix mCurrentMatrix;
    private float mMidX;
    private float mMidY;
    private final float[] matrixValues = new float[9];
    private GestureDetector mGestureDetector;

    public static float SCALE_MAX = 2.0f;
    public static float SCALE_MID = 1.5f;

    public PinchToZoomDraweeView(Context context) {
        this(context, null);
    }

    public PinchToZoomDraweeView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PinchToZoomDraweeView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mScaleListener = new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                float newScale = mCurrentScale * scaleFactor;
                LogUtils.d("scaleFactor=" + scaleFactor + ",newScale=" + newScale);
                // Prevent from zooming out more than original
                if (newScale > 1.0f && newScale < SCALE_MAX) {
                    if (mMidX == 0.0f) {
                        mMidX = getWidth() / 2;//from center to zoom
                    }
                    if (mMidY == 0.0f) {
                        mMidY = getHeight() / 2;//from center to zoom
                    }
                    mCurrentScale = newScale;
                    PinchToZoomDraweeView.this.postDelayed(new AutoRunableZoom(newScale), 16);
                } else if (newScale > SCALE_MAX) {
                    newScale = SCALE_MAX;
                    mCurrentScale = newScale;
                }

                return true;
            }
        };
        mScaleDetector = new ScaleGestureDetector(getContext(), mScaleListener);
        mCurrentMatrix = new Matrix();
        mGestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        float newscale = 0.0f;
                        if (mCurrentScale == 1.0f) {
                            newscale = SCALE_MID;
                        } else if (mCurrentScale >= SCALE_MID && mCurrentScale < SCALE_MAX) {
                            newscale = SCALE_MAX;
                        } else if (mCurrentScale >= SCALE_MAX) {
                            newscale = 1.0f;
                        }
                        mCurrentScale = newscale;
                        PinchToZoomDraweeView.this.postDelayed(new AutoRunableZoom(newscale), 16);
                        isAutoScale = true;
                        return true;
                    }
                });
        this.setOnTouchListener(this);
    }

    float mLastX, mLastY;
    private boolean isCanDrag;
    private int lastPointerCount;
    private int mTouchSlop;
    private boolean isCheckTopAndBottom = true;
    private boolean isCheckLeftAndRight = true;
    private int bitmapW, bitmapH;

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (mGestureDetector.onTouchEvent(event))
            return true;
        mScaleDetector.onTouchEvent(event);

        float x = 0, y = 0;
        // pointer num count
        final int pointerCount = event.getPointerCount();

        for (int i = 0; i < pointerCount; i++) {
            x += event.getX(i);
            y += event.getY(i);
        }
        x = x / pointerCount;
        y = y / pointerCount;
        /**
         * when pointer change ,rest  x and y
         */
        if (pointerCount != lastPointerCount) {
            isCanDrag = false;
            mLastX = x;
            mLastY = y;
        }

        lastPointerCount = pointerCount;
        RectF rectF = getMatrixRectF();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (rectF.width() > getWidth() || rectF.height() > getHeight()) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (rectF.width() > getWidth() || rectF.height() > getHeight()) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                float dx = x - mLastX;
                float dy = y - mLastY;
                if (!isCanDrag) {
                    isCanDrag = isCanDrag(dx, dy);
                }
                if (isCanDrag) {
                    if (getDrawable() != null) {
                        isCheckLeftAndRight = isCheckTopAndBottom = true;
                        if (rectF.width() < getWidth()) {
                            isCheckLeftAndRight = false;
                        }
                        if (rectF.height() < getHeight()) {
                            isCheckTopAndBottom = false;
                        }
                        mCurrentMatrix.postTranslate(dx, dy);
                        checkMatrixBounds();
                        invalidate();
                    }
                }
                mLastX = x;
                mLastY = y;
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                lastPointerCount = 0;
                break;
        }

        return true;
    }

	// you can get it from ControllerListener or Postprocessor 
    public void setBitmapWandH(int width, int height) {
        bitmapH = height;
        bitmapW = width;
    }


    private RectF getMatrixRectF() {
        RectF rect = new RectF();
        rect.set(0, 0, bitmapW, bitmapH);
        getHierarchy().getActualImageBounds(rect);
        Matrix matrix = mCurrentMatrix;
        matrix.mapRect(rect);
        return rect;
    }

    private void checkMatrixBounds() {
        RectF rect = getMatrixRectF();
        float deltaX = 0, deltaY = 0;
        final float viewWidth = getWidth();
        final float viewHeight = getHeight();

        if (rect.top > 0 && isCheckTopAndBottom) {
            deltaY = -rect.top;
        }
        if (rect.bottom < viewHeight && isCheckTopAndBottom) {
            deltaY = viewHeight - rect.bottom;
        }
        if (rect.left > 0 && isCheckLeftAndRight) {
            deltaX = -rect.left;
        }
        if (rect.right < viewWidth && isCheckLeftAndRight) {
            deltaX = viewWidth - rect.right;
        }
        mCurrentMatrix.postTranslate(deltaX, deltaY);
    }

    private boolean isCanDrag(float dx, float dy) {
        return Math.sqrt((dx * dx) + (dy * dy)) >= mTouchSlop;
    }

    private class AutoRunableZoom implements Runnable {

        static final float BIGGER = 1.07f;
        static final float SMALLER = 0.93f;
        private float mTargetScale;
        private float tmpScale;

        public AutoRunableZoom(float TargetScale) {
            this.mTargetScale = TargetScale;
            if (getScale() < mTargetScale) {
                tmpScale = BIGGER;
            } else {
                tmpScale = SMALLER;
            }
            if (mMidX == 0.0f) {
                mMidX = getWidth() / 2;//from center to zoom
            }
            if (mMidY == 0.0f) {
                mMidY = getHeight() / 2;//from center to zoom
            }
        }

        @Override
        public void run() {
            mCurrentMatrix.postScale(tmpScale, tmpScale, mMidX, mMidY);
            invalidate();
            mCurrentScale = getScale() > SCALE_MAX ? SCALE_MAX : getScale() < 1.0f ? 1.0f : getScale();
            if (((tmpScale > 1f) && (mCurrentScale < mTargetScale))
                    || ((tmpScale < 1f) && (mTargetScale < mCurrentScale))) {
                PinchToZoomDraweeView.this.postDelayed(this, 16);
            } else {
                final float deltaScale = mTargetScale / mCurrentScale;
                mCurrentMatrix.postScale(deltaScale, deltaScale, mMidX, mMidY);
                invalidate();
                isAutoScale = false;
            }
        }
    }


    public final float getScale() {
        mCurrentMatrix.getValues(matrixValues);
        return matrixValues[Matrix.MSCALE_X];
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int saveCount = canvas.save();
        canvas.concat(mCurrentMatrix);
        super.onDraw(canvas);
        canvas.restoreToCount(saveCount);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mScaleDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }


    /**
     * Resets the zoom of the attached image.
     * This has no effect if the image has been destroyed
     */
    public void reset() {
        mCurrentMatrix.reset();
        mCurrentScale = 1.0f;
        invalidate();
    }
}