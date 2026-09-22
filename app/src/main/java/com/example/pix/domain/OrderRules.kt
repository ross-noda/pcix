package com.example.pix.domain

/** Move onto a target: before it when moving up, after it when moving down. */
object OrderRules {
    fun move(ids: List<String>, source: String, target: String): List<String> {
        val from = ids.indexOf(source)
        val to = ids.indexOf(target)
        if (from < 0 || to < 0 || from == to) return ids
        return ids.toMutableList().apply { add(to, removeAt(from)) }
    }
}
