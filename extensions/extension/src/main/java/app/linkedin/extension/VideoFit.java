/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.linkedin.extension;

import android.util.DisplayMetrics;
import android.view.View;

/**
 * Full-screen videos that LinkedIn crops. The server sends scalingType CROP for the video viewer, which the app
 * draws in one of two ways: AspectRatioFrameLayout grows past its parent (resize mode 4, zoom), or VideoTextureView
 * center-crops with a matrix (scaling mode 1). Either way the sides of a 9:16 video are cut off on a tall phone.
 * <p>
 * When the view is as big as the screen, both answers are replaced by fit (frame 0, texture 2), so the whole video
 * shows, as large as it fits. Smaller views, such as a video in the feed, keep what LinkedIn asked for.
 */
@SuppressWarnings("unused")
public final class VideoFit {
    private static final int FRAME_ZOOM = 4;
    private static final int FRAME_FIT = 0;
    private static final int TEXTURE_CROP = 1;
    private static final int TEXTURE_FIT = 2;

    /** The decision logged last, so onMeasure repeating the same answer does not fill the log. */
    private static volatile String lastLogged = "";

    private VideoFit() {
    }

    /** Injected after AspectRatioFrameLayout.onMeasure reads its resizeMode. */
    public static int frameResizeMode(View frame, int mode) {
        if (mode != FRAME_ZOOM) return mode;
        return isFullScreen(frame, "frame", frame.getMeasuredWidth(), frame.getMeasuredHeight(), "zoom") ? FRAME_FIT : mode;
    }

    /** Injected after VideoTextureView.updateTransform reads its scalingMode. */
    public static int textureScalingMode(View texture, int mode) {
        if (mode != TEXTURE_CROP) return mode;
        return isFullScreen(texture, "texture", texture.getWidth(), texture.getHeight(), "crop") ? TEXTURE_FIT : mode;
    }

    private static boolean isFullScreen(View view, String kind, int width, int height, String from) {
        try {
            if (!Settings.fitVideos() || width <= 0 || height <= 0) return false;
            DisplayMetrics screen = view.getResources().getDisplayMetrics();
            boolean full = width >= 0.9f * screen.widthPixels && height >= 0.75f * screen.heightPixels;
            String key = kind + " " + width + "x" + height + " on " + screen.widthPixels + "x" + screen.heightPixels
                    + " " + full;
            if (!key.equals(lastLogged)) {
                lastLogged = key;
                Settings.debugLog("video fit: " + kind + " " + width + "x" + height + " on " + screen.widthPixels + "x"
                        + screen.heightPixels + ", " + from + (full ? " -> fit" : " kept"));
            }
            return full;
        } catch (Throwable failure) {
            return false;
        }
    }
}
