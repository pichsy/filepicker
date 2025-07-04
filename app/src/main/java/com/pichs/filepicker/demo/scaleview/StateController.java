package com.pichs.filepicker.demo.scaleview;

import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pichs.filepicker.demo.scaleview.internal.MovementBounds;
import com.pichs.filepicker.demo.scaleview.internal.ZoomBounds;
import com.pichs.filepicker.demo.scaleview.utils.GravityUtils;

/**
 * Helper class that holds reference to {@link Settings} object and controls some aspects of view
 * {@link State}, such as movement bounds restrictions
 * (see {@link #getMovementArea(State, RectF)}) and dynamic min / max zoom levels
 * (see {@link #getMinZoom(State)} and {@link #getMaxZoom(State)}).
 */
public class StateController {

    // Temporary objects
    private static final State tmpState = new State();
    private static final Rect tmpRect = new Rect();
    private static final RectF tmpRectF = new RectF();
    private static final PointF tmpPointF = new PointF();


    private final Settings settings;
    private final ZoomBounds zoomBounds;
    private final MovementBounds movBounds;

    private boolean isResetRequired = true;

    private float zoomPatch;

    StateController(Settings settings) {
        this.settings = settings;
        this.zoomBounds = new ZoomBounds(settings);
        this.movBounds = new MovementBounds(settings);
    }

    /**
     * Resets to initial state (min zoom, position according to gravity). Reset will only occur
     * when image and viewport sizes are known, otherwise reset will occur sometime in the future
     * when {@link #updateState(State)} method will be called.
     *
     * @param state State to be reset
     * @return {@code true} if reset was completed or {@code false} if reset is scheduled for future
     */
    boolean resetState(State state) {
        isResetRequired = true;
        return updateState(state);
    }

    /**
     * Updates state (or resets state if reset was scheduled, see {@link #resetState(State)}).
     *
     * @param state State to be updated
     * @return {@code true} if state was reset to initial state or {@code false} if state was
     * updated.
     */
    boolean updateState(State state) {
        if (isResetRequired) {
            // Applying initial state
            state.set(0f, 0f, zoomBounds.set(state).getFitZoom(), 0f);
            GravityUtils.getImagePosition(state, settings, tmpRect);
            state.translateTo(tmpRect.left, tmpRect.top);

            // We can correctly reset state only when we have both image size and viewport size
            // but there can be a delay before we have all values properly set
            // (waiting for layout or waiting for image to be loaded)
            isResetRequired = !settings.hasImageSize() || !settings.hasViewportSize();
            return !isResetRequired;
        } else {
            // Restricts state's translation and zoom bounds, disallowing overscroll / overzoom.
            restrictStateBounds(state, state, Float.NaN, Float.NaN, false, false, true);
            return false;
        }
    }

    public void setTempZoomPatch(float factor) {
        zoomPatch = factor;
    }

    public void applyZoomPatch(@NonNull State state) {
        if (zoomPatch > 0f) {
            state.set(state.getX(), state.getY(), state.getZoom() * zoomPatch, state.getRotation());
        }
    }

    public float applyZoomPatch(float zoom) {
        return zoomPatch > 0f ? zoomPatch * zoom : zoom;
    }

    /**
     * Maximizes zoom if it closer to min zoom or minimizes it if it closer to max zoom.
     *
     * @param state Current state
     * @param pivotX Pivot's X coordinate
     * @param pivotY Pivot's Y coordinate
     * @return End state for toggle animation.
     */
    State toggleMinMaxZoom(State state, float pivotX, float pivotY) {
        zoomBounds.set(state);
        final float minZoom = zoomBounds.getFitZoom();
        final float maxZoom = settings.getDoubleTapZoom() > 0f
                ? settings.getDoubleTapZoom() : zoomBounds.getMaxZoom();

        final float middleZoom = 0.5f * (minZoom + maxZoom);
        final float targetZoom = state.getZoom() < middleZoom ? maxZoom : minZoom;

        final State end = state.copy();
        end.zoomTo(targetZoom, pivotX, pivotY);
        return end;
    }

    /**
     * Restricts state's translation and zoom bounds.
     *
     * @param state State to be restricted
     * @param prevState Previous state to calculate overscroll and overzoom (optional)
     * @param pivotX Pivot's X coordinate
     * @param pivotY Pivot's Y coordinate
     * @param allowOverscroll Whether overscroll is allowed
     * @param allowOverzoom Whether overzoom is allowed
     * @param restrictRotation Whether rotation should be restricted to a nearest N*90 angle
     * @return End state to animate changes or null if no changes are required.
     */
    @SuppressWarnings("SameParameterValue") // Using same method params as in restrictStateBounds
    @Nullable
    State restrictStateBoundsCopy(State state, State prevState, float pivotX, float pivotY,
            boolean allowOverscroll, boolean allowOverzoom, boolean restrictRotation) {
        tmpState.set(state);
        boolean changed = restrictStateBounds(tmpState, prevState, pivotX, pivotY,
                allowOverscroll, allowOverzoom, restrictRotation);
        return changed ? tmpState.copy() : null;
    }

    /**
     * Restricts state's translation and zoom bounds. If {@code prevState} is not null and
     * {@code allowOverscroll (allowOverzoom)} parameter is true then resilience
     * will be applied to translation (zoom) changes if they are out of bounds.
     *
     * @param state State to be restricted
     * @param prevState Previous state to calculate overscroll and overzoom (optional)
     * @param pivotX Pivot's X coordinate
     * @param pivotY Pivot's Y coordinate
     * @param allowOverscroll Whether overscroll is allowed
     * @param allowOverzoom Whether overzoom is allowed
     * @param restrictRotation Whether rotation should be restricted to a nearest N*90 angle
     * @return true if state was changed, false otherwise.
     */
    boolean restrictStateBounds(State state, State prevState, float pivotX, float pivotY,
            boolean allowOverscroll, boolean allowOverzoom, boolean restrictRotation) {

        if (!settings.isRestrictBounds()) {
            return false;
        }

        if (Float.isNaN(pivotX) || Float.isNaN(pivotY)) {
            pivotX = state.getX();
            pivotY = state.getY();
        }

        boolean isStateChanged = false;

        if (restrictRotation && settings.isRestrictRotation()) {
            float rotation = Math.round(state.getRotation() / 90f) * 90f;
            if (!State.equals(rotation, state.getRotation())) {
                state.rotateTo(rotation, pivotX, pivotY);
                isStateChanged = true;
            }
        }

        zoomBounds.set(state);
        final float minZoom = zoomBounds.getMinZoom();
        final float maxZoom = zoomBounds.getMaxZoom();

        final float extraZoom = allowOverzoom ? settings.getOverzoomFactor() : 1f;
        float zoom = zoomBounds.restrict(state.getZoom(), extraZoom);

        // Applying elastic overzoom
        if (prevState != null) {
            zoom = applyZoomResilience(zoom, prevState.getZoom(), minZoom, maxZoom, extraZoom);
        }

        if (!State.equals(zoom, state.getZoom())) {
            state.zoomTo(zoom, pivotX, pivotY);
            isStateChanged = true;
        }

        float extraX = allowOverscroll ? settings.getOverscrollDistanceX() : 0f;
        float extraY = allowOverscroll ? settings.getOverscrollDistanceY() : 0f;

        movBounds.set(state);
        movBounds.restrict(state.getX(), state.getY(), extraX, extraY, tmpPointF);
        float newX = tmpPointF.x;
        float newY = tmpPointF.y;

        if (zoom < minZoom && extraZoom > 1f) {
            // Decreasing overscroll if zooming less than minimum zoom
            float factor = (extraZoom * zoom / minZoom - 1f) / (extraZoom - 1f);
            factor = (float) Math.sqrt(factor);

            movBounds.restrict(newX, newY, tmpPointF);
            float strictX = tmpPointF.x;
            float strictY = tmpPointF.y;

            newX = strictX + factor * (newX - strictX);
            newY = strictY + factor * (newY - strictY);
        }

        if (prevState != null) {
            movBounds.getExternalBounds(tmpRectF);
            newX = applyTranslationResilience(newX, prevState.getX(),
                    tmpRectF.left, tmpRectF.right, extraX);
            newY = applyTranslationResilience(newY, prevState.getY(),
                    tmpRectF.top, tmpRectF.bottom, extraY);
        }

        if (!State.equals(newX, state.getX()) || !State.equals(newY, state.getY())) {
            state.translateTo(newX, newY);
            isStateChanged = true;
        }

        return isStateChanged;
    }

    private float applyZoomResilience(float zoom, float prevZoom,
            float minZoom, float maxZoom, float overzoom) {
        if (overzoom == 1f) {
            return zoom;
        }

        float minZoomOver = minZoom / overzoom;
        float maxZoomOver = maxZoom * overzoom;

        float resilience = 0f;

        if (zoom < minZoom && zoom < prevZoom) {
            resilience = (minZoom - zoom) / (minZoom - minZoomOver);
        } else if (zoom > maxZoom && zoom > prevZoom) {
            resilience = (zoom - maxZoom) / (maxZoomOver - maxZoom);
        }

        if (resilience == 0f) {
            return zoom;
        } else {
            resilience = (float) Math.sqrt(resilience);
            return zoom + resilience * (prevZoom - zoom);
        }
    }

    private float applyTranslationResilience(float value, float prevValue,
            float boundsMin, float boundsMax, float overscroll) {
        if (overscroll == 0f) {
            return value;
        }

        float resilience = 0f;

        float avg = (value + prevValue) * 0.5f;

        if (avg < boundsMin && value < prevValue) {
            resilience = (boundsMin - avg) / overscroll;
        } else if (avg > boundsMax && value > prevValue) {
            resilience = (avg - boundsMax) / overscroll;
        }

        if (resilience == 0f) {
            return value;
        } else {
            if (resilience > 1f) {
                resilience = 1f;
            }
            resilience = (float) Math.sqrt(resilience);
            return value - resilience * (value - prevValue);
        }
    }


    /**
     * @param state Current state
     * @return Min zoom level as it's used by state controller.
     */
    public float getMinZoom(@NonNull State state) {
        return zoomBounds.set(state).getMinZoom();
    }

    /**
     * @param state Current state
     * @return Max zoom level as it's used by state controller.
     * Note, that it may be different from {@link Settings#getMaxZoom()}.
     */
    @SuppressWarnings({ "unused", "WeakerAccess" }) // Public API
    public float getMaxZoom(@NonNull State state) {
        return zoomBounds.set(state).getMaxZoom();
    }

    /**
     * @param state Current state
     * @return Zoom level which will fit the image into viewport (or min zoom level if
     * {@link Settings#getFitMethod()} is {@link Settings.Fit#NONE}).
     */
    public float getFitZoom(@NonNull State state) {
        return zoomBounds.set(state).getFitZoom();
    }

    /**
     * Calculates area in which {@link State#getX()} &amp; {@link State#getY()} values can change.
     * Note, that this is different from {@link Settings#setMovementArea(int, int)} which defines
     * part of the viewport in which image can move.
     *
     * @param state Current state
     * @param out Output movement area rectangle
     */
    public void getMovementArea(@NonNull State state, @NonNull RectF out) {
        movBounds.set(state).getExternalBounds(out);
    }

}
