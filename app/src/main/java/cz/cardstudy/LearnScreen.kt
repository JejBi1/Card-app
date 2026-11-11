@file:OptIn(ExperimentalMaterial3Api::class)

package cz.cardstudy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random

@Composable
fun LearnScreen(deckId: String, onlyUnknown: Boolean, speaker: Speaker, onBack: () -> Unit) {
    val deck = remember(deckId) { Store.deck(deckId) }
    if (deck == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var mode by remember { mutableStateOf(onlyUnknown) }
    var round by remember { mutableIntStateOf(0) }
    val queue = remember(round) { Store.buildSession(deck, mode).toMutableStateList() }
    val total = remember(round) { queue.size }
    var known by remember(round) { mutableIntStateOf(0) }
    var unknown by remember(round) { mutableIntStateOf(0) }
    var step by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(deck.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (mode) "jen neumím" else "všechny kartičky",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") }
                },
                actions = {
                    Text("✓ $known   ✗ $unknown", Modifier.padding(end = 16.dp), style = MaterialTheme.typography.titleMedium)
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            val card = queue.firstOrNull()
            if (card == null) {
                Finished(
                    total, known, unknown,
                    unknownLeft = deck.unknownCount(),
                    onAgain = { onlyUnk -> mode = onlyUnk; round++ },
                    onBack = onBack,
                )
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { (total - queue.size).toFloat() / total.coerceAtLeast(1) },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "  ${total - queue.size + 1} / $total",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(16.dp))
            key(step) {
                CardArea(card, deck.lang, speaker, Modifier.weight(1f)) { ok ->
                    Store.answer(card, ok)
                    queue.removeAt(0)
                    if (ok) known++ else unknown++
                    step++
                }
            }
        }
    }
}

@Composable
private fun CardArea(card: Card, lang: String, speaker: Speaker, modifier: Modifier, onAnswer: (Boolean) -> Unit) {
    // true = nejdřív se ukáže přední strana (slovo / otázka)
    val frontFirst = remember {
        when (Store.direction.intValue) {
            0 -> true
            1 -> false
            else -> Random.nextBoolean()
        }
    }
    var flipped by remember { mutableStateOf(false) }
    val frontVisible = flipped != frontFirst
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(flipped, speaker.status) {
        if (Store.autoSpeak.value && frontVisible) {
            if (flipped) delay(250)
            speaker.speak(card.front, lang)
        }
    }

    Column(modifier) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = offset.value
                    rotationZ = offset.value / 60f
                }
                .pointerInput(Unit) {
                    // swipe doleva = umím, doprava = neumím
                    val limit = size.width * 0.28f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val o = offset.value
                                if (abs(o) > limit) {
                                    offset.animateTo(sign(o) * size.width * 1.5f, tween(180))
                                    onAnswer(o < 0)
                                } else offset.animateTo(0f)
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    ) { change, dx ->
                        change.consume()
                        scope.launch { offset.snapTo(offset.value + dx) }
                    }
                },
        ) {
            FlipCard(card, lang, frontFirst, flipped, speaker) { flipped = !flipped }
            val o = offset.value
            if (o < -40f) SwipeLabel("UMÍM ✓", GoodGreen, Alignment.TopEnd)
            if (o > 40f) SwipeLabel("✗ NEUMÍM", MaterialTheme.colorScheme.error, Alignment.TopStart)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onAnswer(true) },
                colors = ButtonDefaults.buttonColors(containerColor = GoodGreen, contentColor = Color.White),
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("←  Umím", style = MaterialTheme.typography.titleMedium) }
            Button(
                onClick = { onAnswer(false) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("Neumím  →", style = MaterialTheme.typography.titleMedium) }
        }
        Text(
            "klepnutí = otočit · swipe ← umím · swipe → neumím",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.SwipeLabel(text: String, color: Color, align: Alignment) {
    Text(
        text,
        color = color,
        fontWeight = FontWeight.Black,
        fontSize = 26.sp,
        modifier = Modifier.align(align).padding(24.dp),
    )
}

@Composable
private fun FlipCard(card: Card, lang: String, frontFirst: Boolean, flipped: Boolean, speaker: Speaker, onClick: () -> Unit) {
    val rot by animateFloatAsState(if (flipped) 180f else 0f, tween(400), label = "flip")
    val density = LocalDensity.current.density
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            rotationY = rot
            cameraDistance = 16f * density
        },
    ) {
        val showBack = rot > 90f
        Face(
            card = card,
            lang = lang,
            frontSide = if (showBack) !frontFirst else frontFirst,
            isAnswer = showBack,
            speaker = speaker,
            onClick = onClick,
            modifier = Modifier.fillMaxSize().graphicsLayer { if (showBack) rotationY = 180f },
        )
    }
}

@Composable
private fun Face(
    card: Card,
    lang: String,
    frontSide: Boolean,
    isAnswer: Boolean,
    speaker: Speaker,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (frontSide) scheme.primaryContainer else scheme.secondaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val label = when {
                frontSide && lang.isNotEmpty() -> langOf(lang).name.substringBefore(" (")
                frontSide -> "otázka"
                lang.isNotEmpty() -> "překlad"
                else -> "odpověď"
            }
            Text(label.uppercase(), style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp)
            Spacer(Modifier.height(16.dp))
            val text = if (frontSide) card.front else card.back
            Text(
                text,
                style = when {
                    text.length > 80 -> MaterialTheme.typography.titleLarge
                    text.length > 22 -> MaterialTheme.typography.headlineMedium
                    else -> MaterialTheme.typography.displaySmall
                },
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (isAnswer && card.note.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(card.note, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
            }
            if (frontSide && lang.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { speaker.speak(card.front, lang) }) { Text("🔊  Přehrát") }
                    FilledTonalButton(onClick = { speaker.speak(card.front, lang, slow = true) }) { Text("🐢") }
                }
            }
            if (!isAnswer) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "klepni pro otočení",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Finished(
    total: Int,
    known: Int,
    unknown: Int,
    unknownLeft: Int,
    onAgain: (onlyUnknown: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (total == 0) {
            Text("🎉", fontSize = 72.sp)
            Text("Žádné neznámé kartičky", style = MaterialTheme.typography.titleLarge)
        } else {
            Text(if (unknown == 0) "🎉" else "💪", fontSize = 72.sp)
            Text("Kolo dokončeno", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text("$total kartiček · ✓ $known  ✗ $unknown", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(24.dp))
        if (unknownLeft > 0) {
            Button(onClick = { onAgain(true) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Znovu jen neumím ($unknownLeft)")
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = { onAgain(false) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Znovu všechny")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Zpět na seznamy") }
    }
}
