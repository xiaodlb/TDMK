package com.xiaodlb.tdmk.data

internal object StoredText {
    fun validate(value: String): String {
        var i = 0
        while (i < value.length) {
            val c = value[i]
            require(!c.isLowSurrogate()) { "Unpaired Unicode surrogate" }
            if (c.isHighSurrogate()) {
                require(i + 1 < value.length && value[i + 1].isLowSurrogate()) { "Unpaired Unicode surrogate" }
                i++
            }
            i++
        }
        return value
    }

    fun label(value: String): String = validate(value).trim {
        it in '\u0009'..'\u000D' || it == ' ' || it == '\u0085' || it == '\u00A0' ||
            it == '\u1680' || it in '\u2000'..'\u200A' || it == '\u2028' || it == '\u2029' ||
            it == '\u202F' || it == '\u205F' || it == '\u3000'
    }.also { require(it.isNotEmpty()) { "Label must contain a non-whitespace scalar" } }
}
