/*
 * Forked from https://github.com/SysAdminDoc/HushPinterest at 44fb9338 (GPL-3.0),
 * modified for HushThreads (Threads), 2026.
 *
 * Copyright 2026 HushPinterest contributors
 * https://github.com/SysAdminDoc/HushPinterest
 *
 * Built on SysAdminDoc/hushfeed (GPL-3.0).
 */
package app.morphe.extension.hushthreads.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

import app.morphe.extension.shared.settings.preference.ReportProblem;

/**
 * Lynx build: Report a problem opens a new issue on Lynx Patches, not on the upstream repository.
 * Lynx Patches has no HushThreads issue form, so the text goes in as the issue's body.
 */
public class ReportProblemFormTest {
    @Test
    public void theLinkPointsAtLynxPatchesAndFillsTheBody() {
        assertEquals("https://github.com/xhyxfxxc/lynx-patches/issues/new", ReportProblemForm.NEW_ISSUE_URL);
        assertEquals("xhyxfxxc/lynx-patches", ReportProblemForm.REPOSITORY);
        assertEquals("github.com/xhyxfxxc/lynx-patches/issues", ReportProblemForm.ISSUES_ADDRESS);
        String url = ReportProblem.issueUrl(ReportProblemForm.FORM, new ReportProblem.Facts("HushThreads", "0.0.18",
                "Threads", "455.0.0.55.72", 1L, "Acme", "Phone", "16", 36, Arrays.asList("Hide ads")));
        assertTrue(url, url.startsWith(ReportProblemForm.NEW_ISSUE_URL + "?body="));
        assertFalse(url, url.contains("SysAdminDoc"));
        assertFalse(url, url.contains("template="));
    }
}
