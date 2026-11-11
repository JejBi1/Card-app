package cz.cardstudy

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import kotlin.math.max

private val PARENS = Regex("\\(.*?\\)|\\[.*?]")
private val CZECH = Locale.forLanguageTag("cs")

/** Přehrávání výslovnosti přes systémový TTS (Google Převod textu na řeč), jazyk podle seznamu. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    enum class Status { LOADING, READY, ERROR }

    var status by mutableStateOf(Status.LOADING)
        private set

    /** Nainstalované hlasy všech jazyků; obnoví se po návratu z nastavení. */
    private var allVoices by mutableStateOf<List<Voice>>(emptyList())

    private val tts = TextToSpeech(context.applicationContext, this)

    override fun onInit(result: Int) {
        if (result != TextToSpeech.SUCCESS) {
            status = Status.ERROR
            return
        }
        refresh()
        status = Status.READY
    }

    /** Po návratu z nastavení načte nově stažené hlasy. */
    fun recheck() {
        if (status == Status.READY) refresh()
    }

    private fun refresh() {
        allVoices = try {
            tts.voices.orEmpty().filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Hlasy pro jazyk, nejlepší první (stejná země, offline, vyšší kvalita). */
    fun voicesFor(lang: String): List<Voice> {
        if (lang.isEmpty()) return emptyList()
        val loc = Locale.forLanguageTag(lang)
        return allVoices.filter { it.locale.language == loc.language }.sortedWith(
            compareByDescending<Voice> { it.locale.country == loc.country }
                .thenBy { it.isNetworkConnectionRequired }
                .thenByDescending { it.quality }
                .thenBy { it.name }
        )
    }

    /** Z daných jazyků ty, pro které v telefonu chybí hlas. */
    fun missing(langs: List<String>): List<String> =
        if (status != Status.READY) emptyList()
        else langs.filter {
            voicesFor(it).isEmpty() &&
                tts.isLanguageAvailable(Locale.forLanguageTag(it)) < TextToSpeech.LANG_AVAILABLE
        }

    fun speak(text: String, lang: String, slow: Boolean = false) {
        if (status != Status.READY || lang.isEmpty()) return
        val vs = voicesFor(lang)
        val v = vs.firstOrNull { it.name == Store.voices[lang] } ?: vs.firstOrNull()
        try {
            if (v != null) tts.voice = v else tts.setLanguage(Locale.forLanguageTag(lang))
        } catch (e: Exception) {
            tts.setLanguage(Locale.forLanguageTag(lang))
        }
        val rate = Store.speechRate.floatValue
        tts.setSpeechRate(if (slow) rate * 0.55f else rate)
        tts.speak(spokenForm(text), TextToSpeech.QUEUE_FLUSH, null, "w${System.nanoTime()}")
    }

    /** Čitelný popis hlasu, např. "Španělsko · hlas 2 · offline". */
    fun describe(v: Voice, all: List<Voice>): String {
        val place = v.locale.getDisplayCountry(CZECH).ifEmpty { v.locale.getDisplayLanguage(CZECH) }
        val nr = all.filter { it.locale.country == v.locale.country }.indexOf(v) + 1
        val net = if (v.isNetworkConnectionRequired) "online" else "offline"
        return "$place · hlas $nr · $net"
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    companion object {
        /** Bez poznámek v závorkách, alternativy "a / b" čte s pauzou. */
        fun spokenForm(t: String) = t.replace(PARENS, " ").replace('/', ',').replace("…", " ").trim()
    }
}

class PronResult(val score: Float, val best: String, val others: List<String>)

/** Porovnání rozpoznané řeči s kartičkou. */
object Pronunciation {
    /** Členy (a "to" u anglických sloves) – slovo se uzná i bez nich. */
    private val ARTICLES = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas", "lo",     // es
        "the", "a", "an", "to",                                         // en
        "der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", // de
        "le", "les", "l", "une",                                        // fr
        "il", "gli", "i", "uno",                                        // it
        "o", "os", "as", "um", "uma",                                   // pt
    )

    private fun norm(s: String) =
        plain(s).replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun variants(s: String): List<String> {
        val w = s.split(' ')
        return if (w.size > 1 && w[0] in ARTICLES) listOf(s, w.drop(1).joinToString(" ")) else listOf(s)
    }

    private fun targets(text: String): List<String> {
        val base = text.replace(PARENS, " ")
        val alts = base.split('/', ';').map { norm(it) }.filter { it.isNotEmpty() }
        return (alts + norm(base)).flatMap { variants(it) }.distinct()
    }

    fun evaluate(text: String, heard: List<String>): PronResult {
        var bestScore = 0f
        var bestText = heard.first()
        val targets = targets(text)
        for (h in heard) for (hv in variants(norm(h))) for (t in targets) {
            val s = similarity(t, hv)
            if (s > bestScore) {
                bestScore = s
                bestText = h
            }
        }
        return PronResult(bestScore, bestText, heard.filter { it != bestText }.take(3))
    }

    private fun similarity(a: String, b: String): Float {
        val len = max(a.length, b.length)
        return if (len == 0) 1f else 1f - levenshtein(a, b).toFloat() / len
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
