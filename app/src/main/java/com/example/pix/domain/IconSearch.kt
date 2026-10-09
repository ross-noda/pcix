package com.example.pix.domain

import java.text.Normalizer
import java.util.Locale

object IconSearch {
    private fun normalized(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    private val synonyms = mapOf(
        "acqua" to "water droplet glass", "bere" to "water cup glass", "caffe" to "coffee cup",
        "sonno" to "sleep bed moon", "dormire" to "sleep bed moon", "letto" to "bed",
        "leggere" to "book library", "lettura" to "book library", "libro" to "book",
        "sport" to "sport fitness exercise", "palestra" to "dumbbell fitness", "allenamento" to "fitness exercise dumbbell",
        "correre" to "running footprints", "corsa" to "running footprints", "camminare" to "walking footprints",
        "bici" to "bike bicycle", "nuoto" to "swimming waves", "yoga" to "yoga meditation",
        "meditazione" to "meditation brain", "salute" to "health heart", "cuore" to "heart",
        "denti" to "tooth brush", "doccia" to "shower", "bagno" to "bath", "capelli" to "hair scissors",
        "viso" to "face", "cura" to "heart health", "farmaci" to "pill medicine", "medicine" to "pill medicine",
        "cibo" to "food", "cucinare" to "cooking chef", "cucina" to "cooking kitchen", "mangiare" to "food utensils",
        "frutta" to "fruit apple cherry", "verdura" to "vegetable carrot", "casa" to "house home",
        "pulizia" to "clean brush spray", "lavoro" to "work briefcase", "studio" to "study school book",
        "studiare" to "study school book", "scuola" to "school graduation", "scrivere" to "pen pencil notebook",
        "musica" to "music", "chitarra" to "guitar", "disegno" to "palette pencil", "arte" to "art palette",
        "soldi" to "money wallet", "risparmio" to "piggy bank savings", "tempo" to "time clock", "orologio" to "clock",
        "sole" to "sun", "luna" to "moon", "natura" to "nature leaf tree", "albero" to "tree",
        "fiore" to "flower", "pianta" to "plant sprout", "animali" to "animals paw", "cane" to "dog", "gatto" to "cat",
        "famiglia" to "family users", "amici" to "friends users", "telefono" to "phone", "viaggio" to "travel plane",
        "obiettivo" to "target goal", "calendario" to "calendar", "foto" to "camera image", "stelle" to "star",
    )
    fun terms(query: String): List<List<String>> = normalized(query).split(Regex("[\\s-]+")).filter { it.isNotBlank() }
        .map { listOf(it) + synonyms[it].orEmpty().split(' ').filter(String::isNotEmpty) }
    fun matches(keywords: String, terms: List<List<String>>): Boolean {
        val text = normalized(keywords)
        return terms.all { alternatives -> alternatives.any { it in text } }
    }
}
