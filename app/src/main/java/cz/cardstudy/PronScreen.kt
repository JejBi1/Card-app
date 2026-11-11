@file:OptIn(ExperimentalMaterial3Api::class)

package cz.cardstudy

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun PronScreen(deckId: String, speaker: Speaker, onBack: () -> Unit) {
    val deck = remember(deckId) { Store.deck(deckId) }
    if (deck == null || deck.cards.isEmpty() || deck.lang.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val order = remember { deck.cards.shuffled() }
    var idx by remember { mutableIntStateOf(0) }
    val card = order[idx]
    var result by remember(idx) { mutableStateOf<PronResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val heard = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        if (res.resultCode == Activity.RESULT_OK && !heard.isNullOrEmpty()) {
            result = Pronunciation.evaluate(card.front, heard)
        }
    }

    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, deck.lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, deck.lang)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Řekni: ${card.front}")
        }
        try {
            error = null
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            error = "Telefon nemá rozpoznávání řeči. Nainstaluj nebo zapni aplikaci Google (hlasové zadávání)."
        }
    }

    LaunchedEffect(idx, speaker.status) {
        if (Store.autoSpeak.value) speaker.speak(card.front, deck.lang)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Výslovnost") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") }
                },
                actions = {
                    Text("${idx + 1} / ${order.size}", Modifier.padding(end = 16.dp), style = MaterialTheme.typography.titleMedium)
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        card.front,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        card.back,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (card.note.isNotEmpty()) {
                        Text(card.note, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { speaker.speak(card.front, deck.lang) }) { Text("🔊  Poslechnout") }
                        FilledTonalButton(onClick = { speaker.speak(card.front, deck.lang, slow = true) }) { Text("🐢  Pomalu") }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Button(onClick = { listen() }, shape = CircleShape, modifier = Modifier.size(120.dp)) {
                Text("🎤", fontSize = 48.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Klepni a řekni to nahlas",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            result?.let { r ->
                val pct = (r.score * 100).roundToInt()
                val (color, verdict) = when {
                    pct >= 90 -> GoodGreen to "Výborně!"
                    pct >= 70 -> WarnAmber to "Skoro – zkus to ještě jednou"
                    else -> MaterialTheme.colorScheme.error to "Zkus to znovu"
                }
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.14f)),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("$pct %  ·  $verdict", style = MaterialTheme.typography.titleLarge, color = color)
                        Spacer(Modifier.height(4.dp))
                        Text("Rozpoznáno: „${r.best}“", style = MaterialTheme.typography.bodyLarge)
                        if (r.others.isNotEmpty()) {
                            Text(
                                "Další varianty: " + r.others.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { idx = (idx - 1 + order.size) % order.size },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("← Předchozí") }
                Button(
                    onClick = { idx = (idx + 1) % order.size },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Další →") }
            }
        }
    }
}
