package com.pichs.filepicker.demo.scaleview.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import com.pichs.filepicker.demo.scaleview.GestureController;
import com.pichs.filepicker.demo.scaleview.GestureControllerForPager;
import com.pichs.filepicker.demo.scaleview.Settings;
import com.pichs.filepicker.demo.scaleview.State;
import com.pichs.filepicker.demo.scaleview.animation.ViewPositionAnimator;
import com.pichs.filepicker.demo.scaleview.internal.DebugOverlay;
import com.pichs.filepicker.demo.scaleview.internal.GestureDebug;
import com.pichs.filepicker.demo.scaleview.utils.ClipHelper;
import com.pichs.filepicker.demo.scaleview.utils.CropUtils;
import com.pichs.filepicker.demo.scaleview.views.interfaces.AnimatorView;
import com.pichs.filepicker.demo.scaleview.views.interfaces.ClipBounds;
import com.pichs.filepicker.demo.scaleview.views.interfaces.ClipView;
import com.pichs.filepicker.demo.scaleview.views.interfaces.GestureView;

/**
 * {@link ImageView} implementation controlled by {@link GestureController}
 * ({@link #getController()}).
 * <p>
 * View position can be animated with {@link ViewPositionAnimator}
 * ({@link #getPositionAnimator()}).
 */
public class GestureImageView extends AppCompatImageView
        implements GestureView, ClipView, ClipBounds, AnimatorView {

    private GestureControllerForPager controller;
    private final ClipHelper clipViewHelper = new ClipHelper(this);
    private final ClipHelper clipBoundsHelper = new ClipHelper(this);
    private final Matrix imageMatrix = new Matrix();

    private ViewPositionAnimator positionAnimator;

    public GestureImageView(Context context) {
        this(context, null, 0);
    }

    public GestureImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GestureImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        ensureControllerCreated();
        controller.getSettings().initFromAttributes(context, attrs);
        controller.addOnStateChangeListener(new GestureController.OnStateChangeListener() {
            @Override
            public void onStateChanged(State state) {
                applyState(state);
            }

            @Override
            public void onStateReset(State oldState, State newState) {
                applyState(newState);
            }
        });

        setScaleType(ImageView.ScaleType.MATRIX);
    }

    private void ensureControllerCreated() {
        if (controller == null) {
            controller = new GestureControllerForPager(this);
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        clipBoundsHelper.onPreDraw(canvas);
        clipViewHelper.onPreDraw(canvas);
        super.draw(canvas);
        clipViewHelper.onPostDraw(canvas);
        clipBoundsHelper.onPostDraw(canvas);

        if (GestureDebug.isDrawDebugOverlay()) {
            DebugOverlay.drawDebug(this, canvas);
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public GestureControllerForPager getController() {
        return controller;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ViewPositionAnimator getPositionAnimator() {
        if (positionAnimator == null) {
            positionAnimator = new ViewPositionAnimator(this);
        }
        return positionAnimator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clipView(@Nullable RectF rect, float rotation) {
        clipViewHelper.clipView(rect, rotation);
    }

    @Override
    public void clipBounds(@Nullable RectF rect) {
        clipBoundsHelper.clipView(rect, 0f);
    }

    /**
     * Crops bitmap as it is seen inside movement area: {@link Settings#setMovementArea(int, int)}.
     * <p>
     * Note, that size of cropped bitmap may vary from size of movement area,
     * since we will crop part of original image at base zoom level (zoom == 1).
     *
     * @return Cropped bitmap or null, if no image is set to this image view or if
     * {@link OutOfMemoryError} error was thrown during cropping.
     */
    @Nullable
    public Bitmap crop() {
        return CropUtils.crop(getDrawable(), controller);
    }

    @SuppressLint("ClickableViewAccessibility") // performClick() will be called by controller
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        return controller.onTouch(this, event);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        controller.getSettings().setViewport(width - getPaddingLeft() - getPaddingRight(),
                height - getPaddingTop() - getPaddingBottom());
        controller.resetState();
    }

    @Override
    public void setImageResource(int resId) {
        setImageDrawable(getDrawable(getContext(), resId));
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);

        // Method setImageDrawable can be called from super constructor,
        // so we have to ensure controller instance is created at this point.
        ensureControllerCreated();

        Settings settings = controller.getSettings();

        // Saving old image size
        float oldWidth = settings.getImageW();
        float oldHeight = settings.getImageH();

        // Setting image size
        if (drawable == null) {
            settings.setImage(0, 0);
        } else if (drawable.getIntrinsicWidth() == -1 || drawable.getIntrinsicHeight() == -1) {
            settings.setImage(settings.getMovementAreaW(), settings.getMovementAreaH());
        } else {
            settings.setImage(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        }

        // Getting new image size
        float newWidth = settings.getImageW();
        float newHeight = settings.getImageH();

        if (newWidth > 0f && newHeight > 0f && oldWidth > 0f && oldHeight > 0f) {
            float scaleFactor = Math.min(oldWidth / newWidth, oldHeight / newHeight);
            controller.getStateController().setTempZoomPatch(scaleFactor);
            controller.updateState();
            controller.getStateController().setTempZoomPatch(0f);
        } else {
            controller.resetState();
        }
    }

    protected void applyState(@NonNull State state) {
        state.get(imageMatrix);
        setImageMatrix(imageMatrix);
    }


    @SuppressWarnings("deprecation")
    private static Drawable getDrawable(Context context, @DrawableRes int id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return context.getDrawable(id);
        } else {
            return context.getResources().getDrawable(id);
        }
    }

}
