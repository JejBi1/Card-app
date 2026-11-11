package cz.cardstudy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import java.io.IOException

class MainActivity : ComponentActivity() {
    private lateinit var speaker: Speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        speaker = Speaker(this)
        if (savedInstanceState == null) handleIntent(intent)
        setContent { SpanishTheme { App(speaker) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        speaker.recheck()
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }

    /** CSV otevřené / sdílené z jiné aplikace. */
    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND ->
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
        if (uri != null) {
            Store.message.value = importCsv(this, uri)
        } else if (intent?.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.length <= MAX_IMPORT } ?: return
            Store.message.value = try {
                describe(Store.importCsv("Sdílené kartičky", text.toByteArray()))
            } catch (e: Exception) {
                "Import se nepovedl: ${e.message}"
            }
        }
    }
}

// ---------- import ----------

fun importCsv(ctx: Context, uri: Uri): String = try {
    val name = displayName(ctx, uri)?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() } ?: "Kartičky"
    // jen content:// (file:// by šlo zneužít ke čtení interních souborů aplikace)
    if (uri.scheme != "content") throw IOException("nepodporovaný zdroj souboru")
    val bytes = ctx.contentResolver.openInputStream(uri)?.use { readLimited(it, MAX_IMPORT) }
        ?: throw IOException("soubor nelze otevřít")
    describe(Store.importCsv(name, bytes))
} catch (e: Exception) {
    "Import se nepovedl: ${e.message}"
}

private const val MAX_IMPORT = 5_000_000

/** Načte max. [limit] bajtů – obří soubor neshodí aplikaci na nedostatek paměti. */
private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        out.write(buf, 0, n)
        if (out.size() > limit) throw IOException("soubor je moc velký (max. 5 MB)")
    }
    return out.toByteArray()
}

fun importSample(ctx: Context): String =
    describe(Store.importCsv("Ukázka – španělština", ctx.assets.open("ukazka.csv").use { it.readBytes() }))

/** Text pro snackbar; u nového seznamu bez jazyka v hlavičce si řekne o výběr jazyka. */
fun describe(r: ImportResult) = buildString {
    if (r.isNew && !r.langDetected) Store.askLangFor.value = r.deck.id
    append("„${r.deck.name}“: přidáno ${r.added}")
    if (r.updated > 0) append(", upraveno ${r.updated}")
    if (r.skipped > 0) append(", přeskočeno ${r.skipped} řádků")
}

private fun displayName(ctx: Context, uri: Uri): String? {
    val fromProvider = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        null
    }
    return fromProvider ?: uri.lastPathSegment?.substringAfterLast('/')
}

fun openTtsSettings(ctx: Context) {
    try {
        ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
    } catch (e: Exception) {
        ctx.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

// ---------- téma a navigace ----------

val GoodGreen = Color(0xFF2E7D32)
val WarnAmber = Color(0xFFEF6C00)

@Composable
fun SpanishTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFFFFB4AB), onPrimary = Color(0xFF690005),
            primaryContainer = Color(0xFF8C1D18), onPrimaryContainer = Color(0xFFFFDAD6),
            secondary = Color(0xFFFFD54F), secondaryContainer = Color(0xFF5B4300), onSecondaryContainer = Color(0xFFFFE08B),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFFC62828), onPrimary = Color.White,
            primaryContainer = Color(0xFFFFDAD6), onPrimaryContainer = Color(0xFF410002),
            secondary = Color(0xFF8B6A00), secondaryContainer = Color(0xFFFFE08B), onSecondaryContainer = Color(0xFF2B2000),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

sealed interface Screen {
    data object Home : Screen
    data class Learn(val deckId: String, val onlyUnknown: Boolean) : Screen
    data class Pron(val deckId: String) : Screen
    data class Words(val deckId: String) : Screen
}

@Composable
fun App(speaker: Speaker) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val snackbar = remember { SnackbarHostState() }
    val msg = Store.message.value
    LaunchedEffect(msg) {
        if (msg != null) {
            snackbar.showSnackbar(msg, withDismissAction = true)
            Store.message.value = null
        }
    }
    val back = { screen = Screen.Home }
    if (screen != Screen.Home) BackHandler(onBack = back)

    when (val s = screen) {
        Screen.Home -> HomeScreen(speaker, snackbar) { screen = it }
        is Screen.Learn -> LearnScreen(s.deckId, s.onlyUnknown, speaker, back)
        is Screen.Pron -> PronScreen(s.deckId, speaker, back)
        is Screen.Words -> WordsScreen(s.deckId, speaker, back)
    }

    Store.askLangFor.value?.let { id ->
        val deck = Store.deck(id)
        if (deck == null) Store.askLangFor.value = null
        else LangDialog(deck, initial = Store.lastLang.value, isNew = true) { Store.askLangFor.value = null }
    }
}
