/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.pinkfong.extension;

import android.os.Handler;
import android.os.Looper;

import app.loki.extension.LokiLog;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Answers an ad load that the Hide ads patch blocked, the way a real ad network answers one it cannot fill: the
 * load callback gets an "onAdFailedToLoad" with a NO_FILL error, so the game is told no ad is coming instead of
 * waiting for one.
 *
 * The first blocked load of each ad format (each format's bridge callback is its own class) is answered at once, so
 * a game that waits for a start-up ad carries on straight away. Every later blocked load of the same format is
 * answered after {@link #RETRY_DELAY_MS}. A real network only answers a no-fill after a network round trip, but an
 * instant answer makes games that load again from their failure callback retry thousands of times a second, which
 * filled the JNI global reference table and crashed the app.
 *
 * The extension is not compiled against the Google Mobile Ads Next-Gen SDK (it is not a dependency of Lynx Patches),
 * so the error is built and delivered by reflection. The three lookups are made once, on the first blocked load,
 * and kept. All the names used are the SDK's public API, which R8 keeps.
 */
public final class NextGenAds {
    private static final String SDK = "com.google.android.libraries.ads.mobile.sdk.common.";
    private static final String MESSAGE = "No ad: blocked by Lynx Patches";
    /**
     * How long every blocked load after the first of its format waits for its no-fill answer. Several games (Smart
     * Study's "SSAdMob" manager) load again from inside the failure callback; answered at once, that loop ran about
     * 500 times a second, until the JNI global reference table overflowed (max 51200) and ART aborted the app. A
     * minute still tells the game no ad is coming, at the pace a real network failing repeatedly would.
     */
    private static final long RETRY_DELAY_MS = 60_000L;

    private static Constructor<?> errorConstructor;
    private static Object noFill;
    private static Method onAdFailedToLoad;
    private static boolean looked;
    private static boolean logged;
    private static Handler mainThread;
    /** The callback classes whose first blocked load has been answered. Guarded by itself. */
    private static final Set<String> answered = new HashSet<String>();

    private NextGenAds() {
    }

    /**
     * Called in place of the ad load, with the AdLoadCallback the game passed to it. Never throws: the patched
     * method has to return whatever happens.
     */
    public static void failLoad(final Object callback) {
        if (callback == null) return;
        try {
            if (!resolve(callback.getClass().getClassLoader())) return;
            boolean first;
            synchronized (answered) {
                first = answered.add(callback.getClass().getName());
            }
            if (first) {
                answer(callback);
            } else {
                handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        answer(callback);
                    }
                }, RETRY_DELAY_MS);
            }
        } catch (Throwable t) {
            note("could not answer an ad load", t);
        }
    }

    /** Builds the no-fill error and hands it to the callback. Never throws. */
    private static void answer(Object callback) {
        try {
            Object error = errorConstructor.newInstance(noFill, MESSAGE, null);
            onAdFailedToLoad.invoke(callback, error);
            LokiLog.debug("Pinkfong: an ad load was answered with no fill");
        } catch (Throwable t) {
            note("could not answer an ad load", t);
        }
    }

    private static synchronized Handler handler() {
        if (mainThread == null) mainThread = new Handler(Looper.getMainLooper());
        return mainThread;
    }

    /** Looks the SDK's types up once; false when they are missing, in which case it is not tried again. */
    private static synchronized boolean resolve(ClassLoader loader) {
        if (looked) return errorConstructor != null;
        looked = true;
        try {
            Class<?> error = Class.forName(SDK + "LoadAdError", false, loader);
            Class<?> code = Class.forName(SDK + "LoadAdError$ErrorCode", false, loader);
            Class<?> info = Class.forName(SDK + "ResponseInfo", false, loader);
            Class<?> listener = Class.forName(SDK + "AdLoadCallback", false, loader);
            Object[] constants = code.getEnumConstants();
            Object chosen = null;
            for (Object constant : constants) {
                if ("NO_FILL".equals(((Enum<?>) constant).name())) chosen = constant;
            }
            // Any error code still tells the game "no ad"; only the name of the reason differs.
            if (chosen == null && constants.length > 0) chosen = constants[0];
            if (chosen == null) throw new IllegalStateException("error codes not found");
            Constructor<?> constructor = error.getConstructor(code, String.class, info);
            Method method = listener.getMethod("onAdFailedToLoad", error);
            noFill = chosen;
            onAdFailedToLoad = method;
            errorConstructor = constructor;
            return true;
        } catch (Throwable t) {
            note("could not find the ad error types", t);
            return false;
        }
    }

    /** The log is only written once: a blocked load can repeat many times and would flood it. */
    private static synchronized void note(String what, Throwable t) {
        if (logged) return;
        logged = true;
        LokiLog.note("Pinkfong ads failed: " + what + ": " + t);
    }
}
