package com.xiaodlb.tdmk

import org.junit.Test
import org.junit.Assert.*

class ChecklistDraftTest {
    @Test fun labelsTrimEdgesPreserveInternalSpaceAndUnicode() {
        assertEquals("猫  ✓", ChecklistDraft("key", null, "\u00a0猫  ✓\u2003", false).label())
    }
    @Test fun invalidLabelsAreRejected() {
        for(text in listOf("", "\u00a0\u2003", "broken\uD800")) {
            try { ChecklistDraft("key", null, text, false).label(); fail("Accepted invalid label") }
            catch(_: IllegalArgumentException) {}
        }
    }
}
