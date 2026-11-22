/*
 * Copyright 2026 HushPinterest contributors
 * Taken from HushPinterest's
 * extensions/pinterest/src/test/java/app/hushpinterest/extension/pinterest/settings/ReportProblemFormTest.java
 * (https://github.com/SysAdminDoc/HushPinterest, commit 44fb93384a319d8e5d324f51b416efdc3d3147e6).
 * Modified for HushGram (Instagram), 2026.
 *
 * Built on SysAdminDoc/hushfeed (GPL-3.0).
 */
package app.hushgram.extension.instagram.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Report a problem fills GitHub's bug form by field id, and GitHub drops a value whose id the form
 * doesn't have without a word. Each id the link uses is held to the form in the repository.
 */
public class ReportProblemFormTest {
    @Test
    public void theLinkPointsAtThisRepositorysBugFormAndItsFields() throws IOException {
        assertEquals("xhyxfxxc/lynx-patches", ReportProblemForm.REPOSITORY);
        // Lynx build: the form is the Lynx Patches repository's own, two folders above this pack
        // (packs/<name>). A copy of the pack on its own has no form to read, so the check is skipped.
        File form = lynxBugForm();
        assumeTrue("no Lynx Patches bug form above " + root(), form != null);
        String yaml = new String(Files.readAllBytes(form.toPath()), StandardCharsets.UTF_8);
        for (String id : Arrays.asList("patches-version", "device", "description", "context")) {
            assertTrue("bug_report.yml has no field " + id,
                    Pattern.compile("(?m)^\\s*id:\\s*" + Pattern.quote(id) + "\\s*$").matcher(yaml).find());
        }
    }

    /** The bug form of the repository this pack sits in (packs/<name>/../..), or null when there is none. */
    private static File lynxBugForm() {
        for (File dir = root().getParentFile(); dir != null; dir = dir.getParentFile()) {
            File form = new File(dir, ".github/ISSUE_TEMPLATE/bug_report.yml");
            if (form.isFile()) return form;
        }
        return null;
    }

    /** The pack's root, found from wherever Gradle runs the test. */
    private static File root() {
        for (File dir = new File("").getAbsoluteFile(); dir != null; dir = dir.getParentFile()) {
            if (new File(dir, "settings.gradle.kts").isFile() && new File(dir, "provenance.json").isFile()) return dir;
        }
        throw new AssertionError("no repository root above " + new File("").getAbsolutePath());
    }
}
