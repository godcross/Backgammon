package com.feltbackgammon.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feltbackgammon.GameDialog
import com.feltbackgammon.GameViewModel
import com.feltbackgammon.UndoMode
import com.feltbackgammon.engine.WinType

@Composable
fun GameScreen(vm: GameViewModel) {
    val s = vm.ui
    Row(Modifier.fillMaxSize().background(DeepWood)) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Board(
                state = s,
                onTap = vm::onBoardTap,
                onFlightFinished = vm::onFlightFinished,
                modifier = Modifier.fillMaxSize(),
            )
            MessageBanner(s.message, Modifier.align(Alignment.TopCenter).padding(top = 30.dp))
        }
        SidePanel(vm)
    }
    Dialogs(vm)
}

@Composable
private fun MessageBanner(msg: String?, modifier: Modifier) {
    // Keep showing the last text while the banner fades out.
    val lastMsg = remember { arrayOf("") }
    if (msg != null) lastMsg[0] = msg
    AnimatedVisibility(visible = msg != null, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Text(
            lastMsg[0],
            color = Ivory, fontSize = 15.sp,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SidePanel(vm: GameViewModel) {
    val s = vm.ui
    val settings = vm.settings
    Column(
        modifier = Modifier
            .width(150.dp)
            .fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(Color(0xFF1C0E05), Color(0xFF341D0D))))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.fillMaxWidth().panel().padding(10.dp)) {
            Text(
                if (s.target == 0) "Single game" else "Match to ${s.target}",
                color = Brass, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
            if (s.target > 0) Text("Game ${s.gameNumber}", color = Ivory.copy(alpha = 0.6f), fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            ScoreLine("You", s.scoreHuman, Ivory, if (settings.showPips) s.humanPips else null)
            ScoreLine("AI · ${s.difficulty.label}", s.scoreAi, Color(0xFF3A3330), if (settings.showPips) s.aiPips else null)
            if (s.crawford) Text("Crawford game — no doubling", color = Brass, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.weight(1f))
        if (s.canRoll) GameButton("Roll", vm::onRoll, Modifier.fillMaxWidth(), primary = true)
        if (s.canDouble) GameButton("Double", vm::onDouble, Modifier.fillMaxWidth())
        if (s.canDone) GameButton("Done", vm::onDone, Modifier.fillMaxWidth(), primary = true)
        if (settings.undo != UndoMode.OFF && (s.canUndo || s.canDone)) GameButton("Undo", vm::onUndo, Modifier.fillMaxWidth(), enabled = s.canUndo)
        if (s.canTakeback) GameButton("Take back", vm::onTakeback, Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        GameButton("Menu", vm::goHome, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ScoreLine(name: String, score: Int, chip: Color, pips: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(Modifier.size(14.dp).background(chip, CircleShape))
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Ivory, fontSize = 13.sp, maxLines = 1)
            if (pips != null) Text("$pips pips", color = Ivory.copy(alpha = 0.55f), fontSize = 11.sp)
        }
        Text("$score", color = Ivory, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Dialogs(vm: GameViewModel) {
    when (val d = vm.ui.dialog) {
        GameDialog.AiDoubles -> AlertDialog(
            onDismissRequest = {},
            title = { Text("The AI doubles") },
            text = { Text("Take and play on for ${vm.ui.cubeValue * 2} points, or pass and concede ${vm.ui.cubeValue}.") },
            confirmButton = { TextButton(onClick = { vm.onAnswerDouble(true) }) { Text("Take") } },
            dismissButton = { TextButton(onClick = { vm.onAnswerDouble(false) }) { Text("Pass") } },
        )
        is GameDialog.GameOver -> {
            val r = d.result
            val kind = when {
                r.byPass -> if (r.humanWon) "The AI passed your double." else "You passed the double."
                r.type == WinType.SINGLE -> ""
                else -> "${r.type.label}!"
            }
            val title = when {
                r.matchOver && d.target > 0 -> if (d.scoreHuman > d.scoreAi) "You win the match!" else "The AI wins the match"
                r.humanWon -> "You win"
                else -> "The AI wins"
            }
            val pts = if (r.points == 1) "1 point" else "${r.points} points"
            AlertDialog(
                onDismissRequest = {},
                title = { Text(title) },
                text = {
                    Column {
                        if (kind.isNotEmpty()) Text(kind, fontWeight = FontWeight.Bold)
                        Text("${if (r.humanWon) "You score" else "AI scores"} $pts" + if (r.cube > 1) " (cube ${r.cube})" else "")
                        if (d.target > 0) Text("Score: You ${d.scoreHuman} – ${d.scoreAi} AI", textAlign = TextAlign.Start)
                    }
                },
                confirmButton = {
                    TextButton(onClick = vm::onNextGame) {
                        Text(if (r.matchOver) (if (d.target > 0) "New match" else "Play again") else "Next game")
                    }
                },
                dismissButton = { TextButton(onClick = vm::onGameOverToMenu) { Text("Main menu") } },
            )
        }
        null -> {}
    }
}
