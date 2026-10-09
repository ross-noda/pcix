package com.example.pix.domain

/** Plain text storage; checkbox prefixes are only the backwards-compatible checklist encoding. */
object DescriptionText {
    data class Item(val text: String, val checked: Boolean? = null)
    private val prefix = Regex("""^[-*+] \[([ xX])\] ?(.*)$""")
    fun items(source: String): List<Item> = source.split('\n').map { line ->
        prefix.matchEntire(line)?.let { Item(it.groupValues[2], it.groupValues[1] != " ") } ?: Item(line)
    }
    /** Compact token for rejecting actions generated from an obsolete widget rendering. */
    fun revision(source: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(source.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun isChecklist(source: String) = items(source).any { it.checked != null }
    fun encode(items: List<Item>) = items.joinToString("\n") { item ->
        (item.checked?.let { if (it) "- [x] " else "- [ ] " } ?: "") + item.text
    }
    fun convert(source: String, checklist: Boolean) = encode(items(source).map {
        it.copy(checked = if (checklist) it.checked ?: false else null)
    })
    fun replace(source: String, index: Int, text: String): String {
        val rows = items(source).toMutableList()
        val old = rows.removeAt(index)
        rows.addAll(index, text.split('\n').mapIndexed { i, line -> Item(line, if (i == 0) old.checked else false) })
        return encode(rows)
    }
}
