@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package cz.cardstudy

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun WordsScreen(deckId: String, speaker: Speaker, onBack: () -> Unit) {
    val deck = remember(deckId) { Store.deck(deckId) }
    if (deck == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    Store.version.intValue
    var query by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Card?>(null) }
    val q = plain(query)
    val list = deck.cards.filter { q.isEmpty() || plain(it.front).contains(q) || plain(it.back).contains(q) || plain(it.note).contains(q) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deck.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zpět") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Hledat…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
            )
            Text(
                "${list.size} z ${deck.cards.size} · " + (if (deck.lang.isEmpty()) "" else "klepnutí = přehrát, ") + "podržení = smazat",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(list, key = { it.id }) { c ->
                    ListItem(
                        headlineContent = { Text(c.front, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(if (c.note.isEmpty()) c.back else "${c.back} · ${c.note}") },
                        trailingContent = {
                            TextButton(onClick = { Store.setKnown(c, !c.known) }) {
                                Text(
                                    if (c.known) "✓ umím" else "neumím",
                                    color = if (c.known) GoodGreen else MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        modifier = Modifier.combinedClickable(
                            onClick = { speaker.speak(c.front, deck.lang) },
                            onLongClick = { toDelete = c },
                        ),
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    toDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Smazat „${c.front}“?") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    Store.deleteCard(deck, c)
                }) { Text("Smazat") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Zrušit") } },
        )
    }
}
