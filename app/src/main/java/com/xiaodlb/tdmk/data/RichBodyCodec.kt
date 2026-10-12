package com.xiaodlb.tdmk.data

import kotlinx.serialization.json.*

data class RichRun(val text: String, val bold: Boolean)
data class RichBlock(val kind: String, val level: Int?, val runs: List<RichRun>)
data class RichBody(val blocks: List<RichBlock>)

object RichBodyCodec {
    fun decode(raw: String): RichBody {
        val root = Json.parseToJsonElement(raw)
        UniqueKeys(raw).check()
        val body = objectFields(root, setOf("format", "version", "blocks"))
        require(string(body.getValue("format")) == "tdmk.blocks") { "Unsupported rich body format" }
        require(body.getValue("version") == JsonPrimitive(1)) { "Unsupported rich body version" }
        return RichBody(array(body.getValue("blocks")).map { element ->
            val block = objectFields(element, setOf("kind", "level", "runs"))
            val kind = string(block.getValue("kind"))
            require(kind in setOf("paragraph", "heading", "bullet")) { "Unsupported block kind" }
            val levelElement = block.getValue("level")
            val level = if (kind == "heading") {
                require(levelElement in listOf(JsonPrimitive(1), JsonPrimitive(2), JsonPrimitive(3))) { "Heading level must be 1–3" }
                (levelElement as JsonPrimitive).int
            } else {
                require(levelElement == JsonNull) { "Only headings have a level" }
                null
            }
            RichBlock(kind, level, array(block.getValue("runs")).map { item ->
                val run = objectFields(item, setOf("text", "bold"))
                val text = string(run.getValue("text"))
                require('\n' !in text && '\r' !in text) { "Run text cannot contain a line break" }
                val bold = run.getValue("bold")
                require(bold == JsonPrimitive(true) || bold == JsonPrimitive(false)) { "Bold must be a boolean" }
                RichRun(text, (bold as JsonPrimitive).boolean)
            })
        })
    }

    fun encode(body: RichBody): String {
        val raw = buildJsonObject {
            put("format", "tdmk.blocks"); put("version", 1)
            put("blocks", buildJsonArray {
                body.blocks.forEach { block -> add(buildJsonObject {
                    put("kind", block.kind); put("level", block.level?.let(::JsonPrimitive) ?: JsonNull)
                    put("runs", buildJsonArray { block.runs.forEach { run -> add(buildJsonObject {
                        put("text", run.text); put("bold", run.bold)
                    }) } })
                }) }
            })
        }.toString()
        decode(raw) // Public typed construction must obey the same schema as imported JSON.
        return raw
    }

    fun plainText(body: RichBody): String = body.blocks.joinToString("\n") { block -> block.runs.joinToString("") { it.text } }

    private fun objectFields(element: JsonElement, fields: Set<String>): JsonObject {
        require(element is JsonObject && element.keys == fields) { "Rich object has missing/extra fields or wrong type" }
        return element
    }
    private fun array(element: JsonElement): JsonArray { require(element is JsonArray) { "Expected rich array" }; return element }
    private fun string(element: JsonElement): String {
        require(element is JsonPrimitive && element.isString) { "Expected rich string" }
        return StoredText.validate(element.content)
    }

    /** Json's tree parser retains the last duplicate key. Reject duplicates from the validated source. */
    private class UniqueKeys(private val raw: String) {
        private var position = 0
        fun check() { value() }
        private fun space() { while (position < raw.length && raw[position] in " \t\r\n") position++ }
        private fun stringToken(): String {
            val start = position++
            while (true) {
                when (raw[position++]) {
                    '\\' -> position++
                    '"' -> return (Json.parseToJsonElement(raw.substring(start, position)) as JsonPrimitive).content
                }
            }
        }
        private fun value() {
            space()
            when (raw[position]) {
                '{' -> {
                    position++; space()
                    val keys = mutableSetOf<String>()
                    if (raw[position] != '}') do {
                        space(); require(keys.add(stringToken())) { "Duplicate rich JSON property" }
                        space(); position++; value(); space()
                        if (raw[position] != ',') break
                        position++
                    } while (true)
                    position++
                }
                '[' -> {
                    position++; space()
                    if (raw[position] != ']') do {
                        value(); space(); if (raw[position] != ',') break; position++
                    } while (true)
                    position++
                }
                '"' -> stringToken()
                else -> while (position < raw.length && raw[position] !in " ,]}\t\r\n") position++
            }
        }
    }
}
