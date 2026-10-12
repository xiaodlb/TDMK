package com.xiaodlb.tdmk.data

import org.junit.Assert.*
import org.junit.Test

class RichBodyCodecTest {
    @Test fun richFormattingAndWhitespaceRoundTripToExactPlainText() {
        val body = RichBody(listOf(
            RichBlock("heading", 1, listOf(RichRun("Title", false))),
            RichBlock("heading", 2, listOf(RichRun("Two", true))),
            RichBlock("heading", 3, emptyList()),
            RichBlock("bullet", null, listOf(RichRun("  план 🌿", true), RichRun("\tcafé", false))),
            RichBlock("paragraph", null, listOf(RichRun("", true))),
        ))
        assertEquals(body, RichBodyCodec.decode(RichBodyCodec.encode(body)))
        assertEquals("Title\nTwo\n\n  план 🌿\tcafé\n", RichBodyCodec.plainText(body))
    }

    @Test fun strictSchemaRejectsMalformedUnsupportedAndDuplicateFields() {
        val base = "{\"format\":\"tdmk.blocks\",\"version\":1,\"blocks\":[]}"
        val run = "{\"format\":\"tdmk.blocks\",\"version\":1,\"blocks\":[{\"kind\":\"paragraph\",\"level\":null,\"runs\":[{\"text\":\"valid\",\"bold\":false}]}]}"
        listOf("{", "[]", base.replace("tdmk.blocks", "html"), base.replace("\"version\":1", "\"version\":2"),
            base.replace("\"version\":1", "\"version\":\"1\""), base.replace("\"version\":1", "\"version\":1.0"),
            base.replace("\"blocks\":[]", "\"blocks\":null"), base.replace(",\"blocks\":[]", ""),
            base.dropLast(1) + ",\"extra\":false}", base.replace("\"version\":1", "\"version\":1,\"version\":1"),
            base.replace("\"version\":1", "\"version\":1,\"\\u0076ersion\":1"),
            run.replace("\"paragraph\"", "\"image\""), run.replace("\"level\":null", "\"level\":1"),
            run.replace("valid", "bad\\nline"), run.replace("valid", "bad\\ud800"),
            run.replace("\"bold\":false", "\"bold\":\"false\""), run.replace("\"bold\":false", "\"bold\":false,\"bold\":true"),
            run.replace("\"paragraph\"", "\"heading\"").replace("\"level\":null", "\"level\":4")
        ).forEach { raw -> assertThrows("Must reject $raw", IllegalArgumentException::class.java) { RichBodyCodec.decode(raw) } }
    }
}
