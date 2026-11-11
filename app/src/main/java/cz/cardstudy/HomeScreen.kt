@file:OptIn(ExperimentalMaterial3Api::class)

package cz.cardstudy

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.IOException

@Composable
fun HomeScreen(speaker: Speaker, snackbar: SnackbarHostState, open: (Screen) -> Unit) {
    val ctx = LocalContext.current
    Store.version.intValue // překreslit po změně dat
    var settings by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) Store.message.value = importCsv(ctx, uri)
    }
    val missing = speaker.missing(Store.usedLangs())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CardStudy") },
                actions = {
                    IconButton(onClick = { settings = true }) { Icon(Icons.Default.Settings, "Nastavení") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Import CSV") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (speaker.status == Speaker.Status.ERROR || missing.isNotEmpty()) {
                item { TtsWarning(missing) }
            }
            if (Store.decks.isEmpty()) item { EmptyState() }
            items(Store.decks.toList(), key = { it.id }) { d -> DeckCard(d, open) }
        }
    }
    if (settings) SettingsDialog(speaker) { settings = false }
}

@Composable
private fun TtsWarning(missing: List<String>) {
    val ctx = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (missing.isEmpty()) "Převod textu na řeč nefunguje"
                else "Chybí hlas: " + missing.joinToString(", ") { langOf(it).name },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Pro výslovnost stáhni v telefonu hlas v „Převodu textu na řeč“ (Google). " +
                    "Po návratu do aplikace se hlas načte sám.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    try {
                        ctx.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                    } catch (e: Exception) {
                        openTtsSettings(ctx)
                    }
                }) { Text("Stáhnout hlas") }
                TextButton(onClick = { openTtsSettings(ctx) }) { Text("Nastavení řeči") }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    val ctx = LocalContext.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Zatím tu nejsou žádné kartičky", style = MaterialTheme.typography.titleLarge)
            Text("Nahraj CSV soubor tlačítkem „Import CSV“. Každý řádek = jedna kartička:")
            SelectionContainer {
                Text(
                    "anglicky;česky;poznámka\nthe dog;pes\nto speak;mluvit;nepravidelné",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            Text(
                "1. sloupec = slovo nebo otázka, 2. = překlad nebo odpověď. Jazyk pro výslovnost se pozná " +
                    "z hlavičky (španělsky, anglicky, německy…), jinak se aplikace zeptá. Pro jiné předměty " +
                    "použij hlavičku otázka;odpověď. Poznámka a 4. sloupec stav (umím/neumím) jsou nepovinné. " +
                    "Oddělovač ; , nebo tabulátor, z Excelu ukládej jako „CSV UTF-8“.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = { Store.message.value = importSample(ctx) }) { Text("Načíst ukázku (španělština)") }
        }
    }
}

@Composable
private fun DeckCard(d: Deck, open: (Screen) -> Unit) {
    Store.version.intValue
    val ctx = LocalContext.current
    val total = d.cards.size
    val known = d.knownCount()
    val unknown = total - known
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var langDialog by remember { mutableStateOf(false) }
    var exportUnknown by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) Store.message.value = try {
            val (bytes, n) = Store.exportCsv(d, exportUnknown)
            ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: throw IOException("do souboru nelze zapisovat")
            "Exportováno $n kartiček"
        } catch (e: Exception) {
            "Export se nepovedl: ${e.message}"
        }
    }
    fun export(onlyUnknown: Boolean) {
        exportUnknown = onlyUnknown
        exporter.launch(if (onlyUnknown) "${d.name} - neumim.csv" else "${d.name}.csv")
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { open(Screen.Words(d.id)) }) {
                    Text(d.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${langOf(d.lang).name} · $total kartiček · umím $known · neumím $unknown",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Další možnosti") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Seznam kartiček") }, onClick = { menu = false; open(Screen.Words(d.id)) })
                        DropdownMenuItem(text = { Text("Jazyk a hlas…") }, onClick = { menu = false; langDialog = true })
                        if (d.lang.isNotEmpty()) {
                            DropdownMenuItem(text = { Text("Trénink výslovnosti 🎤") }, onClick = { menu = false; open(Screen.Pron(d.id)) })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Export CSV – všechny") }, onClick = { menu = false; export(false) })
                        DropdownMenuItem(text = { Text("Export CSV – jen neumím") }, onClick = { menu = false; export(true) })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Vynulovat pokrok") }, onClick = { menu = false; confirm = "reset" })
                        DropdownMenuItem(text = { Text("Smazat seznam") }, onClick = { menu = false; confirm = "delete" })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else known.toFloat() / total },
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { open(Screen.Learn(d.id, true)) },
                    enabled = unknown > 0,
                    modifier = Modifier.weight(1f),
                ) { Text("Neumím ($unknown)") }
                OutlinedButton(onClick = { open(Screen.Learn(d.id, false)) }, modifier = Modifier.weight(1f)) {
                    Text("Všechny ($total)")
                }
            }
        }
    }

    if (langDialog) LangDialog(d, initial = d.lang, isNew = false) { langDialog = false }

    confirm?.let { what ->
        val delete = what == "delete"
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (delete) "Smazat seznam „${d.name}“?" else "Vynulovat pokrok?") },
            text = {
                Text(
                    if (delete) "Seznam se smaže i s pokrokem. Pokud ho chceš zachovat, nejdřív ho exportuj."
                    else "Všechny kartičky v seznamu budou zase jako „neumím“."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (delete) Store.deleteDeck(d) else Store.resetDeck(d)
                }) { Text(if (delete) "Smazat" else "Vynulovat") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Zrušit") } },
        )
    }
}

/** Výběr jazyka přední strany kartiček (určuje hlas a rozpoznávání řeči). */
@Composable
fun LangDialog(deck: Deck, initial: String, isNew: Boolean, onClose: () -> Unit) {
    var sel by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isNew) "Jaký jazyk je v seznamu „${deck.name}“?" else "Jazyk seznamu") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Podle jazyka 1. sloupce se přehrává výslovnost. Pro jiné předměty zvol „bez hlasu“ " +
                        "(nebo češtinu, pokud chceš otázky předčítat).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                for (l in LANGS) {
                    Row(
                        Modifier.fillMaxWidth().clickable { sel = l.code },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = sel == l.code, onClick = { sel = l.code })
                        Text(l.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Store.setLang(deck, sel)
                onClose()
            }) { Text("Uložit") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Zrušit") } },
    )
}

@Composable
private fun SettingsDialog(speaker: Speaker, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val close = {
        Store.saveSettings()
        onClose()
    }
    AlertDialog(
        onDismissRequest = close,
        confirmButton = { TextButton(onClick = close) { Text("Hotovo") } },
        title = { Text("Nastavení") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Co se ukáže jako první", style = MaterialTheme.typography.titleSmall)
                listOf("Slovo / otázka", "Překlad / odpověď", "Náhodně").forEachIndexed { i, label ->
                    Row(
                        Modifier.fillMaxWidth().clickable { Store.direction.intValue = i },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = Store.direction.intValue == i, onClick = { Store.direction.intValue = i })
                        Text(label)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Automaticky přehrát výslovnost", Modifier.weight(1f))
                    Switch(checked = Store.autoSpeak.value, onCheckedChange = { Store.autoSpeak.value = it })
                }
                Spacer(Modifier.height(8.dp))
                Text("Rychlost řeči: ${"%.1f".format(Store.speechRate.floatValue)}×")
                Slider(
                    value = Store.speechRate.floatValue,
                    onValueChange = { Store.speechRate.floatValue = it },
                    valueRange = 0.4f..1.4f,
                )
                for (lang in Store.usedLangs()) {
                    val voices = speaker.voicesFor(lang)
                    if (voices.size < 2) continue
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Hlas – ${langOf(lang).name}", style = MaterialTheme.typography.titleSmall)
                    val chosen = voices.firstOrNull { it.name == Store.voices[lang] }?.name ?: ""
                    val pick = { name: String ->
                        Store.voices[lang] = name
                        speaker.speak(sampleSentence(lang), lang)
                    }
                    Row(Modifier.fillMaxWidth().clickable { pick("") }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = chosen == "", onClick = { pick("") })
                        Text("Automaticky (nejlepší)")
                    }
                    voices.forEach { v ->
                        Row(Modifier.fillMaxWidth().clickable { pick(v.name) }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = chosen == v.name, onClick = { pick(v.name) })
                            Text(speaker.describe(v, voices), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                TextButton(onClick = { openTtsSettings(ctx) }) { Text("Stáhnout další hlasy (nastavení Androidu)") }
            }
        },
    )
}

/** Krátká ukázková věta pro vyzkoušení hlasu. */
fun sampleSentence(lang: String) = when (lang.substringBefore('-')) {
    "es" -> "Hola, ¿qué tal?"
    "en" -> "Hello, how are you?"
    "de" -> "Hallo, wie geht's?"
    "fr" -> "Bonjour, ça va ?"
    "it" -> "Ciao, come stai?"
    "pt" -> "Olá, tudo bem?"
    "ru" -> "Привет, как дела?"
    "pl" -> "Cześć, jak się masz?"
    else -> "Ahoj, jak se máš?"
}
