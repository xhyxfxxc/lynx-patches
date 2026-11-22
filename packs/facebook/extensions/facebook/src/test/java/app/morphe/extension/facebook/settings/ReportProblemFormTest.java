/*
 * Copyright 2026 Hushfacebook contributors
 * https://github.com/SysAdminDoc/Hushfacebook
 */
package app.morphe.extension.facebook.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

import app.morphe.extension.shared.settings.preference.ReportProblem;

/**
 * Lynx build: Report a problem opens a new issue on the Lynx Patches repository, never on the
 * original author's. That repository has no form with this pack's field ids, so the report's text
 * goes in the issue's body.
 */
public class ReportProblemFormTest {
    @Test
    public void theLinkPointsAtTheLynxPatchesIssueTrackerWithoutAForm() {
        assertEquals("https://github.com/xhyxfxxc/lynx-patches/issues/new", ReportProblemForm.ISSUES_URL);
        assertEquals("xhyxfxxc/lynx-patches", ReportProblemForm.REPOSITORY);
        ReportProblem.Facts facts = new ReportProblem.Facts("Hushfacebook", "0.23.0-lynx.1", "Facebook",
                "587.0.0.39.55", 707414333L, "samsung", "SM-S901U", "16", 36, Arrays.asList("Hide sponsored posts"));
        String url = ReportProblem.issueUrl(ReportProblemForm.FORM, facts);
        assertTrue(url, url.startsWith(ReportProblemForm.ISSUES_URL + "?body="));
        assertFalse(url, url.contains("template="));
        assertFalse(url, url.toLowerCase(java.util.Locale.ROOT).contains("sysadmindoc"));
        assertTrue(url, url.length() <= ReportProblem.MAX_LENGTH);
    }
}
