package com.example.pix.data

import android.content.SharedPreferences
import com.example.pix.domain.*
import org.json.JSONArray
import org.json.JSONObject

fun SharedPreferences.readMatrixConfig() = MatrixConfig(
    cards = readMatrixCards(),
    cardOrder = getString("cardOrder", "0,1,2,3").orEmpty().split(",").mapNotNull { it.toIntOrNull() }.let { (it.filter { id -> id in 0..3 }.distinct() + (0..3)).distinct() },
    hideChildren = getBoolean("hideChildren", false),
    layout = getInt("layout", 0).coerceIn(0, 2),
    columnSplit = getFloat("columns", .5f).coerceIn(.3f, .7f),
    rowSplit = getFloat("rows", .5f).coerceIn(.3f, .7f),
    cornerRadius = getFloat("corners", 20f).coerceIn(0f, 32f),
    urgentDays = getInt("urgent", 0).coerceIn(0, 30),
    importantPriority = getInt("important", 3).takeIf { it in listOf(1, 3, 5) } ?: 3,
)

private fun SharedPreferences.readMatrixCards(): List<MatrixCard> = List(4) { id ->
    runCatching {
        val json = JSONObject(getString("card:$id", "{}") ?: "{}")
        fun strings(key: String): Set<String> = json.optJSONArray(key)?.let { a ->
            (0 until a.length()).map { a.getString(it) }.toSet()
        } ?: emptySet()
        MatrixCard(title = json.optString("title"), custom = json.optBoolean("custom"),
            listIds = strings("lists"), tagIds = strings("tags"),
            date = runCatching { MatrixDate.valueOf(json.optString("date", "ALL")) }.getOrDefault(MatrixDate.ALL),
            fromDay = if (json.has("from")) json.getLong("from") else null,
            toDay = if (json.has("to")) json.getLong("to") else null,
            priorities = strings("priorities").mapNotNull { it.toIntOrNull() }.filter { it in listOf(0, 1, 3, 5) }.toSet())
    }.getOrDefault(MatrixCard())
}

fun SharedPreferences.Editor.putMatrixCards(config: MatrixConfig): SharedPreferences.Editor {
    putString("cardOrder", MatrixRules.orderedIds(config).joinToString(","))
    repeat(4) { id ->
        val card = MatrixRules.card(config, id)
        putString("card:$id", JSONObject().put("title", card.title).put("custom", card.custom)
            .put("lists", JSONArray(card.listIds.toList())).put("tags", JSONArray(card.tagIds.toList()))
            .put("date", card.date.name).put("from", card.fromDay).put("to", card.toDay)
            .put("priorities", JSONArray(card.priorities.map { it.toString() })).toString())
    }
    return this
}
