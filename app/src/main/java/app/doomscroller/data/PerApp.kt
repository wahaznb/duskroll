package app.doomscroller.data

/**
 * Per-app overrides. Null field = fall back to the global setting.
 * Stored as one JSON string via a hand-rolled codec (no new deps,
 * JVM-testable).
 */
data class PerApp(
    val thresholdMin: Int? = null,
    val enforce: Boolean? = null,
)

/** Effective limit: halved per consecutive forced close (min 1). */
fun effectiveLimit(baseMin: Int, streak: Int): Int {
    var t = baseMin.coerceIn(1, 120)
    repeat(streak.coerceIn(0, 10)) { t = maxOf(1, t / 2) }
    return t
}

object PerAppCodec {
    // Hand-rolled JSON for {"pkg":{"t":5,"e":false}} — no org.json, so this
    // stays JVM-unit-testable without Robolectric or mocked android.jar.
    fun decode(json: String): Map<String, PerApp> {
        return runCatching { parseObject(json.trim()) }.getOrDefault(emptyMap())
    }

    fun encode(map: Map<String, PerApp>): String {
        return map.entries.joinToString(",", "{", "}") { (pkg, cfg) ->
            val inner = listOfNotNull(
                cfg.thresholdMin?.let { "\"t\":$it" },
                cfg.enforce?.let { "\"e\":$it" },
            ).joinToString(",")
            "\"${escape(pkg)}\":{$inner}"
        }
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun parseObject(s: String): Map<String, PerApp> {
        val p = Parser(s)
        p.expect('{')
        val out = mutableMapOf<String, PerApp>()
        if (p.peek() == '}') {
            p.next()
            return out
        }
        while (true) {
            val key = p.string()
            p.expect(':')
            out[key] = p.entry()
            when (p.next()) {
                ',' -> continue
                '}' -> break
                else -> throw IllegalArgumentException("bad json")
            }
        }
        return out
    }

    private class Parser(val s: String) {
        var i = 0
        fun peek(): Char {
            skipWs()
            return s[i]
        }
        fun next(): Char {
            skipWs()
            return s[i++]
        }
        fun expect(c: Char) {
            if (next() != c) throw IllegalArgumentException("expected $c")
        }
        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }
        fun string(): String {
            expect('"')
            val b = StringBuilder()
            while (true) {
                val c = s[i++]
                if (c == '"') break
                if (c == '\\') b.append(s[i++]) else b.append(c)
            }
            return b.toString()
        }
        fun entry(): PerApp {
            expect('{')
            var t: Int? = null
            var e: Boolean? = null
            if (peek() != '}') {
                while (true) {
                    when (string()) {
                        "t" -> {
                            expect(':')
                            t = number().coerceIn(1, 120)
                        }
                        "e" -> {
                            expect(':')
                            e = literal()
                        }
                        else -> throw IllegalArgumentException("unknown key")
                    }
                    when (next()) {
                        ',' -> continue
                        '}' -> break
                        else -> throw IllegalArgumentException("bad entry")
                    }
                }
            } else {
                next()
            }
            return PerApp(t, e)
        }
        fun number(): Int {
            skipWs()
            val start = i
            if (i < s.length && s[i] == '-') i++
            while (i < s.length && s[i].isDigit()) i++
            return s.substring(start, i).toInt()
        }
        fun literal(): Boolean {
            skipWs()
            return when {
                s.startsWith("true", i) -> {
                    i += 4
                    true
                }
                s.startsWith("false", i) -> {
                    i += 5
                    false
                }
                else -> throw IllegalArgumentException("bad bool")
            }
        }
    }
}
