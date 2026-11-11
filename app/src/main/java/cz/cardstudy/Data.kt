package cz.cardstudy

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.util.UUID

/** Kartička: přední strana = slovo / otázka (1. sloupec CSV), zadní = překlad / odpověď. */
class Card(
    val id: String = UUID.randomUUID().toString(),
    var front: String,
    var back: String,
    var note: String = "",
    var box: Int = 0,       // 0 = neumím, 1..MAX_BOX = umím (kolikrát po sobě)
    var seen: Int = 0,
    var correct: Int = 0,
) {
    val known get() = box > 0
}

/** Seznam kartiček; [lang] = jazyk přední strany pro hlas ("" = bez hlasu, např. jiné předměty). */
class Deck(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var lang: String = "",
    val cards: MutableList<Card> = mutableListOf(),
) {
    fun knownCount() = cards.count { it.known }
    fun unknownCount() = cards.count { !it.known }
}

class ImportResult(
    val deck: Deck,
    val isNew: Boolean,
    val langDetected: Boolean,
    val added: Int,
    val updated: Int,
    val skipped: Int,
)

const val MAX_BOX = 5

/** Malá písmena bez diakritiky – pro hledání a porovnávání. */
fun plain(s: String): String =
    Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

class Lang(val code: String, val name: String, val adjective: String)

val LANGS = listOf(
    Lang("", "bez hlasu", ""),
    Lang("es-ES", "španělština", "španělsky"),
    Lang("en-GB", "angličtina (UK)", "anglicky"),
    Lang("en-US", "angličtina (US)", "anglicky US"),
    Lang("de-DE", "němčina", "německy"),
    Lang("fr-FR", "francouzština", "francouzsky"),
    Lang("it-IT", "italština", "italsky"),
    Lang("pt-PT", "portugalština", "portugalsky"),
    Lang("ru-RU", "ruština", "rusky"),
    Lang("pl-PL", "polština", "polsky"),
    Lang("cs-CZ", "čeština", "česky"),
)

fun langOf(code: String) = LANGS.firstOrNull { it.code == code } ?: LANGS[0]

object Store {
    private lateinit var file: File
    private lateinit var prefs: SharedPreferences

    val decks = mutableListOf<Deck>()
    /** Zvyšuje se při každé změně dat, aby se UI překreslilo. */
    val version = mutableIntStateOf(0)
    /** Zpráva pro snackbar (výsledek importu apod.). */
    val message = mutableStateOf<String?>(null)
    /** Seznam, kterému je po importu potřeba vybrat jazyk. */
    val askLangFor = mutableStateOf<String?>(null)

    // nastavení
    val direction = mutableIntStateOf(0)        // 0 = slovo→překlad, 1 = překlad→slovo, 2 = náhodně
    val autoSpeak = mutableStateOf(false)
    val speechRate = mutableFloatStateOf(0.9f)
    val voices = mutableStateMapOf<String, String>()   // jazyk → zvolený hlas ("" = automaticky)
    val lastLang = mutableStateOf("es-ES")

    fun init(ctx: Context) {
        if (::file.isInitialized) return
        file = File(ctx.filesDir, "decks.json")
        prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        direction.intValue = prefs.getInt("direction", 0)
        autoSpeak.value = prefs.getBoolean("autoSpeak", false)
        speechRate.floatValue = prefs.getFloat("speechRate", 0.9f)
        lastLang.value = prefs.getString("lastLang", "es-ES") ?: "es-ES"
        for ((k, v) in prefs.all) if (k.startsWith("voice_") && v is String) voices[k.removePrefix("voice_")] = v
        load()
    }

    fun saveSettings() {
        val e = prefs.edit()
            .putInt("direction", direction.intValue)
            .putBoolean("autoSpeak", autoSpeak.value)
            .putFloat("speechRate", speechRate.floatValue)
            .putString("lastLang", lastLang.value)
        for ((k, v) in voices) e.putString("voice_$k", v)
        e.apply()
    }

    fun deck(id: String) = decks.firstOrNull { it.id == id }

    /** Jazyky použité v seznamech (pro výběr hlasu a upozornění na chybějící hlas). */
    fun usedLangs() = decks.map { it.lang }.filter { it.isNotEmpty() }.distinct()

    private fun changed() {
        save()
        version.intValue++
    }

    // ---------- učení ----------

    fun buildSession(deck: Deck, onlyUnknown: Boolean): List<Card> =
        (if (onlyUnknown) deck.cards.filter { !it.known } else deck.cards).shuffled()

    fun answer(c: Card, known: Boolean) {
        c.seen++
        if (known) {
            c.correct++
            c.box = (c.box + 1).coerceAtMost(MAX_BOX)
        } else {
            c.box = 0
        }
        changed()
    }

    fun setKnown(c: Card, known: Boolean) {
        c.box = if (known) c.box.coerceAtLeast(1) else 0
        changed()
    }

    fun setLang(d: Deck, code: String) {
        d.lang = code
        if (code.isNotEmpty()) lastLang.value = code
        saveSettings()
        changed()
    }

    fun resetDeck(d: Deck) {
        d.cards.forEach { it.box = 0; it.seen = 0; it.correct = 0 }
        changed()
    }

    fun deleteDeck(d: Deck) {
        decks.remove(d)
        changed()
    }

    fun deleteCard(d: Deck, c: Card) {
        d.cards.remove(c)
        changed()
    }

    // ---------- import / export ----------

    /**
     * Naimportuje CSV do seznamu [name]; existující seznam doplní a pokrok zachová.
     * Sloupce: slovo/otázka; překlad/odpověď; [poznámka]; [stav umím/neumím].
     * Jazyk se pozná z hlavičky (např. "anglicky;česky").
     */
    fun importCsv(name: String, bytes: ByteArray): ImportResult {
        require(bytes.size < 5_000_000) { "soubor je moc velký" }
        var rows = Csv.parse(Csv.decode(bytes))
            .map { r -> r.map { Csv.unsafe(it.trim()) } }
            .filter { r -> r.any { it.isNotEmpty() } && !r[0].startsWith("#") }
        var headerLang: String? = null
        if (rows.isNotEmpty() && Csv.isHeader(rows[0])) {
            headerLang = Csv.headerLang(rows[0])
            rows = rows.drop(1)
        }

        val existing = decks.firstOrNull { it.name.equals(name, ignoreCase = true) }
        val deck = existing ?: Deck(name = name, lang = headerLang ?: "")
        if (existing != null && headerLang != null && existing.lang.isEmpty()) existing.lang = headerLang
        val byFront = deck.cards.associateBy { it.front.lowercase() }.toMutableMap()
        var added = 0
        var updated = 0
        var skipped = 0
        for (r in rows) {
            val front = r.getOrElse(0) { "" }
            val back = r.getOrElse(1) { "" }
            if (front.isEmpty() || back.isEmpty()) {
                skipped++
                continue
            }
            val status = r.getOrNull(3)?.let { Csv.status(it) }
            val note = (if (status != null) r.subList(2, 3) else r.drop(2)).filter { it.isNotEmpty() }.joinToString(", ")
            val c = byFront[front.lowercase()]
            if (c == null) {
                val n = Card(front = front, back = back, note = note, box = if (status == true) 1 else 0)
                deck.cards += n
                byFront[front.lowercase()] = n
                added++
            } else {
                var changed = false
                if (c.back != back || c.note != note) {
                    c.back = back
                    c.note = note
                    changed = true
                }
                if (status != null && status != c.known) {
                    c.box = if (status) 1 else 0
                    changed = true
                }
                if (changed) updated++
            }
        }
        require(deck.cards.isNotEmpty()) { "v souboru nejsou žádné kartičky (formát: slovo;překlad)" }
        if (existing == null) decks += deck
        changed()
        return ImportResult(deck, existing == null, headerLang != null, added, updated, skipped)
    }

    /** CSV pro Excel: UTF-8 s BOM, oddělovač středník; hlavička nese jazyk, stav jde zpět importovat. */
    fun exportCsv(deck: Deck, onlyUnknown: Boolean): Pair<ByteArray, Int> {
        val cards = if (onlyUnknown) deck.cards.filter { !it.known } else deck.cards
        val header = if (deck.lang.isEmpty()) "otázka;odpověď" else "${langOf(deck.lang).adjective};česky"
        val text = buildString {
            append("$header;poznámka;stav\r\n")
            for (c in cards) {
                append(listOf(c.front, c.back, c.note, if (c.known) "umím" else "neumím").joinToString(";") { Csv.quote(it) })
                append("\r\n")
            }
        }
        return (byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)) to cards.size
    }

    // ---------- uložení ----------

    private fun load() {
        decks.clear()
        if (!file.exists()) return
        try {
            val arr = JSONObject(file.readText()).getJSONArray("decks")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val d = Deck(o.getString("id"), o.getString("name"), o.optString("lang"))
                val ca = o.getJSONArray("cards")
                for (j in 0 until ca.length()) {
                    val c = ca.getJSONObject(j)
                    d.cards += Card(
                        id = c.getString("id"), front = c.getString("front"), back = c.getString("back"),
                        note = c.optString("note"), box = c.optInt("box"),
                        seen = c.optInt("seen"), correct = c.optInt("correct"),
                    )
                }
                decks += d
            }
        } catch (e: Exception) {
            // poškozený soubor si necháme stranou, ať o data úplně nepřijdeme
            file.copyTo(File(file.parentFile, "decks-broken.json"), overwrite = true)
            decks.clear()
        }
    }

    private fun save() {
        val arr = JSONArray()
        for (d in decks) {
            val ca = JSONArray()
            for (c in d.cards) {
                ca.put(
                    JSONObject()
                        .put("id", c.id).put("front", c.front).put("back", c.back).put("note", c.note)
                        .put("box", c.box).put("seen", c.seen).put("correct", c.correct)
                )
            }
            arr.put(JSONObject().put("id", d.id).put("name", d.name).put("lang", d.lang).put("cards", ca))
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JSONObject().put("decks", arr).toString())
        tmp.renameTo(file)
    }
}

object Csv {
    private val GENERIC_HEADERS = setOf(
        "slovo", "slovicko", "word", "otazka", "odpoved", "pojem", "definice", "vyznam", "preklad", "translation",
        "meaning", "question", "answer", "term", "front", "back", "cz", "cs", "cze", "czech", "cesky", "cestina",
    )

    /** Název jazyka v hlavičce → kód jazyka. */
    private val HEADER_LANG: Map<String, String> = buildMap {
        for (l in LANGS) if (l.code.isNotEmpty()) {
            for (k in listOf(plain(l.adjective), plain(l.name), l.code.lowercase(), l.code.substringBefore('-').lowercase())) {
                putIfAbsent(k, l.code)
            }
        }
        listOf(
            "espanol" to "es-ES", "spanish" to "es-ES", "anglictina" to "en-GB", "english" to "en-GB",
            "deutsch" to "de-DE", "german" to "de-DE", "francais" to "fr-FR", "french" to "fr-FR",
            "italiano" to "it-IT", "italian" to "it-IT", "portugues" to "pt-PT", "russian" to "ru-RU", "polski" to "pl-PL",
        ).forEach { (k, v) -> putIfAbsent(k, v) }
    }

    /** UTF-8 (i s BOM), UTF-16 s BOM, jinak Windows-1250 (český Excel). */
    fun decode(b: ByteArray): String {
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte())
            return String(b, 3, b.size - 3, Charsets.UTF_8)
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte())
            return String(b, 2, b.size - 2, Charsets.UTF_16LE)
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte())
            return String(b, 2, b.size - 2, Charsets.UTF_16BE)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b)).toString()
        } catch (e: CharacterCodingException) {
            String(b, Charset.forName("windows-1250"))
        }
    }

    /** Oddělovač = znak, který se vyskytuje v nejvíce řádcích (při shodě vyhrává dřívější). */
    fun detectDelimiter(text: String): Char {
        val lines = text.lineSequence().filter { it.isNotBlank() }.take(30).toList()
        return listOf(';', '\t', ',', '|', '=').maxByOrNull { d -> lines.count { it.contains(d) } } ?: ';'
    }

    private fun isHeaderWord(s: String) = plain(s).let { it in GENERIC_HEADERS || it in HEADER_LANG }

    /** Hlavička = první dva sloupce jsou názvy (aby se slovo "es;je" nebralo jako hlavička). */
    fun isHeader(row: List<String>) = row.isNotEmpty() && isHeaderWord(row[0]) && (row.size < 2 || isHeaderWord(row[1]))

    fun headerLang(row: List<String>): String? = row.firstOrNull()?.let { HEADER_LANG[plain(it)] }

    /** Hodnota sloupce stav: true = umím, false = neumím, null = není to stav. */
    fun status(s: String): Boolean? = when (plain(s)) {
        "umim", "ano", "yes", "1", "x", "✓" -> true
        "neumim", "ne", "no", "0" -> false
        else -> null
    }

    private const val FORMULA = "=+-@\t\r"

    /** Buňka začínající = + - @ by se v Excelu spustila jako vzorec → předřadit apostrof. */
    fun safe(s: String) = if (s.isNotEmpty() && s[0] in FORMULA) "'$s" else s

    /** Opak [safe] při importu. */
    fun unsafe(s: String) = if (s.length > 1 && s[0] == '\'' && s[1] in FORMULA) s.substring(1) else s

    fun quote(raw: String): String {
        val s = safe(raw)
        return if (s.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    /** CSV s podporou uvozovek ("a;b", "" uvnitř uvozovek). */
    fun parse(text: String, delim: Char = detectDelimiter(text)): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else inQuotes = false
                } else sb.append(ch)
            } else when (ch) {
                '"' -> if (sb.isBlank()) {
                    sb.setLength(0)
                    inQuotes = true
                } else sb.append(ch)
                delim -> {
                    row.add(sb.toString())
                    sb.setLength(0)
                }
                '\r' -> {}
                '\n' -> {
                    row.add(sb.toString())
                    sb.setLength(0)
                    rows.add(row)
                    row = mutableListOf()
                }
                else -> sb.append(ch)
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) {
            row.add(sb.toString())
            rows.add(row)
        }
        return rows
    }
}
