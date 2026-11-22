/*
 * Copyright 2026 Hushfacebook contributors
 * https://github.com/SysAdminDoc/Hushfacebook
 */
package app.morphe.extension.facebook.settings;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.TwoStatePreference;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.L10n;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.shared.settings.preference.ReportProblem;

/**
 * Hushfacebook's half of Report a problem: the repository, its bug form's field ids
 * (.github/ISSUE_TEMPLATE/bug_report.yml) and the names that go in the versions line.
 * {@link ReportProblem} builds the address.
 */
@SuppressWarnings("deprecation")
final class ReportProblemForm {
    /** Lynx build: where Report a problem goes. Reports of this modified build go to Lynx Patches, not to the original author. */
    static final String ISSUES_URL = "https://github.com/xhyxfxxc/lynx-patches/issues/new";

    /** owner/name, read off the issues link so the two can't drift apart. */
    static final String REPOSITORY = ISSUES_URL.substring("https://github.com/".length(),
            ISSUES_URL.length() - "/issues/new".length());

    /**
     * Lynx build: no issue form. The Lynx Patches repository's own bug form does not have this
     * pack's field ids, so the versions, the phone and the switches that are on go in the issue's body.
     */
    static final ReportProblem.Form FORM = new ReportProblem.Form(REPOSITORY, null, null, null, null);

    private ReportProblemForm() {
    }

    /** The titles of the switches that are on, as the screen shows them and in its order. */
    static List<String> switchesOn(PreferenceGroup group) {
        List<String> titles = new ArrayList<>();
        collect(group, titles);
        return titles;
    }

    private static void collect(PreferenceGroup group, List<String> titles) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference row = group.getPreference(i);
            if (row instanceof PreferenceGroup) {
                collect((PreferenceGroup) row, titles);
            } else if (row instanceof TwoStatePreference && ((TwoStatePreference) row).isChecked()
                    && row.hasKey() && Setting.getSettingFromPath(row.getKey()) != null && row.getTitle() != null) {
                titles.add(row.getTitle().toString());
            }
        }
    }

    /** The new bug report's address, with this install's facts and [screen]'s switches filled in. */
    static String issueUrl(PreferenceGroup screen) {
        return ReportProblem.issueUrl(FORM, ReportProblem.facts("Hushfacebook", "Facebook", switchesOn(screen)));
    }

    /** Opens the report in a browser, in a task of its own, or says no app here can, as Source code and issues does. */
    static void open(Context context, PreferenceGroup screen) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(issueUrl(screen)))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException missing) {
            // No browser, or none switched on. Uncaught, Android's exception closed Facebook.
            Logger.printInfo(() -> "No app opened the bug report link");
            Utils.showToastLong(L10n.f("No app on this phone can open the link. The address is %1$s.",
                    L10n.isolate(HushfacebookPreferenceFragment.SOURCE_ADDRESS)));
        }
    }
}
