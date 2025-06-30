package com.pichs.filepicker.demo.scaleview.views.interfaces;

import androidx.annotation.NonNull;

import com.pichs.filepicker.demo.scaleview.animation.ViewPositionAnimator;

/**
 * Common interface for views supporting position animation.
 */
public interface AnimatorView {

    /**
     * @return {@link ViewPositionAnimator} instance to control animation from other view position.
     */
    @NonNull
    ViewPositionAnimator getPositionAnimator();

}
