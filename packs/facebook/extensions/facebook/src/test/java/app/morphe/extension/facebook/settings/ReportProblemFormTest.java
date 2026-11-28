/*
 * Copyright 2026 Hushfacebook contributors
 * https://github.com/SysAdminDoc/Hushfacebook
 */
package app.morphe.extension.facebook.settings;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Lynx build: Report a problem opens a new issue on the Lynx Patches repository, never on the
 * original author's. That repository has no form with this pack's field ids, so the report's text
 * goes in the issue's body (the link's shape is checked in SettingsNavigationTest and ReportProblemTest).
 */
public class ReportProblemFormTest {
    @Test
    public void theLinkPointsAtTheLynxPatchesIssueTracker() {
        assertEquals("https://github.com/xhyxfxxc/lynx-patches/issues/new", ReportProblemForm.ISSUES_URL);
        assertEquals("xhyxfxxc/lynx-patches", ReportProblemForm.REPOSITORY);
    }
}
