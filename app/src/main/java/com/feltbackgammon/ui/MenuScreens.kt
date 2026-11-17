package com.feltbackgammon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feltbackgammon.AnimSpeed
import com.feltbackgammon.GameViewModel
import com.feltbackgammon.Screen
import com.feltbackgammon.UndoMode
import com.feltbackgammon.engine.Difficulty
import com.feltbackgammon.engine.LevelStats
import java.util.Locale

@Composable
fun HomeScreen(vm: GameViewModel) {
    FeltBackground {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Backgammon", fontFamily = TitleFont, fontSize = 52.sp, fontWeight = FontWeight.Bold, color = Ivory)
            Text("Offline · Private · No ads", color = Ivory.copy(alpha = 0.6f), fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            val w = Modifier.width(260.dp)
            if (vm.hasResumableGame) GameButton("Continue", vm::continueGame, w, primary = true)
            GameButton("New game", { vm.go(Screen.NEW_GAME) }, w, primary = !vm.hasResumableGame)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameButton("Statistics", { vm.go(Screen.STATS) }, Modifier.width(125.dp))
                GameButton("Settings", { vm.go(Screen.SETTINGS) }, Modifier.width(125.dp))
            }
        }
    }
}

@Composable
fun NewGameScreen(vm: GameViewModel) {
    var level by remember { mutableStateOf(vm.lastDifficulty) }
    var target by remember { mutableStateOf(vm.lastTarget) }
    var confirm by remember { mutableStateOf(false) }
    FeltBackground {
        Column(
            Modifier.align(Alignment.Center).panel().padding(24.dp).widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("New game", fontFamily = TitleFont, fontSize = 30.sp, color = Ivory, fontWeight = FontWeight.Bold)
            Text("Difficulty", color = Brass, fontWeight = FontWeight.Bold)
            ChoiceRow(Difficulty.entries.toList(), level, { it.label }) { level = it }
            Text(
                when (level) {
                    Difficulty.EASY -> "Relaxed play. Makes mistakes and is loose with the cube."
                    Difficulty.MEDIUM -> "Solid, greedy play. A fair challenge."
                    Difficulty.HARD -> "Looks ahead at every reply you could roll. Plays the cube carefully."
                },
                color = Ivory.copy(alpha = 0.7f), fontSize = 13.sp,
            )
            Text("Length", color = Brass, fontWeight = FontWeight.Bold)
            ChoiceRow(listOf(0, 3, 5, 7), target, { if (it == 0) "Single game" else "Match to $it" }) { target = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameButton("Start", {
                    if (vm.hasResumableGame) confirm = true else vm.startNewMatch(level, target)
                }, Modifier.width(160.dp), primary = true)
                GameButton("Back", vm::goHome, Modifier.width(120.dp))
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Abandon current game?") },
            text = { Text("Your saved game will be discarded. It won't count in your statistics.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.startNewMatch(level, target) }) { Text("Start new") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

private fun pct(v: Double) = String.format(Locale.getDefault(), "%.0f%%", v)

@Composable
fun StatsScreen(vm: GameViewModel) {
    @Suppress("UNUSED_VARIABLE") val version = vm.statsVersion // re-read when stats change
    val st = vm.stats
    val total = st.total()
    var confirm by remember { mutableStateOf(false) }
    FeltBackground {
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth(0.92f).verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Statistics", fontFamily = TitleFont, fontSize = 30.sp, color = Ivory, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                GameButton("Reset", { confirm = true })
                Spacer(Modifier.width(8.dp))
                GameButton("Back", vm::goHome, primary = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Games", total.games.toString(), Modifier.weight(1f))
                Tile("Win rate", pct(total.winPct), Modifier.weight(1f))
                Tile(
                    "Streak",
                    when {
                        st.streak > 0 -> "W${st.streak}"
                        st.streak < 0 -> "L${-st.streak}"
                        else -> "–"
                    },
                    Modifier.weight(1f)
                )
                Tile("Best streak", st.bestStreak.toString(), Modifier.weight(1f))
                Tile("Matches won", "${total.matchesWon}/${total.matchesPlayed}", Modifier.weight(1f))
                Tile("Doublets", if (st.totalRolls == 0) "–" else pct(st.doublets * 100.0 / st.totalRolls), Modifier.weight(1f))
            }
            Column(Modifier.fillMaxWidth().panel().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatRow(listOf("Level", "Played", "Won", "Win %", "Gammons W/L", "Backgammons W/L", "Points +/−", "Matches W/P", "AI passed"), header = true)
                for (d in Difficulty.entries) StatRow(rowFor(d.label, st.levels.getValue(d)))
                StatRow(rowFor("All", total), header = true)
            }
            Text(
                "Doublets: share of your rolls that were doubles (expected about 17%). Dice use the system's secure random generator.",
                color = Ivory.copy(alpha = 0.5f), fontSize = 12.sp,
            )
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Reset all statistics?") },
            text = { Text("This permanently clears every record. It can't be undone.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.resetStats() }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

private fun rowFor(name: String, s: LevelStats) = listOf(
    name, s.games.toString(), s.wins.toString(), if (s.games == 0) "–" else pct(s.winPct),
    "${s.gammonsWon}/${s.gammonsLost}", "${s.backgammonsWon}/${s.backgammonsLost}",
    "${s.pointsFor}/${s.pointsAgainst}", "${s.matchesWon}/${s.matchesPlayed}",
    "${s.doublesPassedByAi}/${s.doublesOffered}",
)

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.panel().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Ivory, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Brass, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StatRow(cells: List<String>, header: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        cells.forEachIndexed { i, c ->
            Text(
                c,
                modifier = Modifier.weight(if (i == 0) 1.1f else 1f),
                color = if (header) Brass else Ivory,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.Center,
            )
        }
    }
}

@Composable
fun SettingsScreen(vm: GameViewModel) {
    val s = vm.settings
    FeltBackground {
        Column(
            Modifier.align(Alignment.Center).panel().padding(24.dp).widthIn(max = 620.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Settings", fontFamily = TitleFont, fontSize = 30.sp, color = Ivory, fontWeight = FontWeight.Bold)
            Text("Undo", color = Brass, fontWeight = FontWeight.Bold)
            ChoiceRow(UndoMode.entries.toList(), s.undo, { it.label }) { vm.updateSettings(s.copy(undo = it)) }
            Text(s.undo.blurb, color = Ivory.copy(alpha = 0.7f), fontSize = 13.sp)
            Text("Animation speed", color = Brass, fontWeight = FontWeight.Bold)
            ChoiceRow(AnimSpeed.entries.toList(), s.speed, { it.label }) { vm.updateSettings(s.copy(speed = it)) }
            Toggle("Show pip counts", s.showPips) { vm.updateSettings(s.copy(showPips = it)) }
            Toggle("Highlight movable checkers", s.highlight) { vm.updateSettings(s.copy(highlight = it)) }
            Toggle("Auto-roll when doubling isn't possible", s.autoRoll) { vm.updateSettings(s.copy(autoRoll = it)) }
            Box(Modifier.padding(top = 6.dp)) { GameButton("Back", vm::goHome, primary = true) }
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Ivory, modifier = Modifier.weight(1f))
        Switch(
            checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = DeepWood, checkedTrackColor = Brass),
        )
    }
}
