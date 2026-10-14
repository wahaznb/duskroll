package app.doomscroller.data

/** Per-app today counters. */
data class AppStat(
    val reels: Long = 0L,
    val ms: Long = 0L,
)

object AppStatCodec {
    // Hand-rolled JSON {"pkg":{"r":1,"m":60000}} — JVM-testable, no deps.
    fun decode(json: String): Map<String, AppStat> {
        return runCatching { parse(json.trim()) }.getOrDefault(emptyMap())
    }

    fun encode(map: Map<String, AppStat>): String {
        return map.entries.joinToString(",", "{", "}") { (pkg, s) ->
            "\"${pkg.replace("\\", "\\\\").replace("\"", "\\\"")}\":{\"r\":${s.reels},\"m\":${s.ms}}"
        }
    }

    fun merge(base: Map<String, AppStat>, pkg: String, reels: Long, ms: Long): Map<String, AppStat> {
        if (reels == 0L && ms == 0L) return base
        val cur = base[pkg] ?: AppStat()
        return base + (pkg to AppStat(cur.reels + reels, cur.ms + ms))
    }

    private fun parse(s: String): Map<String, AppStat> {
        val p = Parser(s)
        p.expect('{')
        val out = mutableMapOf<String, AppStat>()
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
        fun entry(): AppStat {
            expect('{')
            var r = 0L
            var m = 0L
            if (peek() != '}') {
                while (true) {
                    when (string()) {
                        "r" -> {
                            expect(':')
                            r = number()
                        }
                        "m" -> {
                            expect(':')
                            m = number()
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
            return AppStat(r.coerceAtLeast(0L), m.coerceAtLeast(0L))
        }
        fun number(): Long {
            skipWs()
            val start = i
            if (i < s.length && s[i] == '-') i++
            while (i < s.length && s[i].isDigit()) i++
            return s.substring(start, i).toLong()
        }
    }
}
