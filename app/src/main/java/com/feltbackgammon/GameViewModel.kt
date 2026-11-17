package com.feltbackgammon

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.feltbackgammon.engine.Ai
import com.feltbackgammon.engine.BAR
import com.feltbackgammon.engine.CubeOwner
import com.feltbackgammon.engine.Difficulty
import com.feltbackgammon.engine.GameResult
import com.feltbackgammon.engine.MoveGen
import com.feltbackgammon.engine.OFF
import com.feltbackgammon.engine.Phase
import com.feltbackgammon.engine.Pos
import com.feltbackgammon.engine.Session
import com.feltbackgammon.engine.Stats
import com.feltbackgammon.engine.SubMove
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom

enum class Screen { HOME, NEW_GAME, GAME, STATS, SETTINGS }

enum class UndoMode(val label: String, val blurb: String) {
    OFF("Off", "Moves are final"),
    TURN("This turn", "Undo checker moves until you tap Done"),
    FULL("Full take-back", "Also take back your previous turn"),
}

enum class AnimSpeed(val label: String, val factor: Float) {
    SLOW("Slow", 1.5f), NORMAL("Normal", 1f), FAST("Fast", 0.55f)
}

data class Settings(
    val undo: UndoMode = UndoMode.TURN,
    val speed: AnimSpeed = AnimSpeed.NORMAL,
    val showPips: Boolean = true,
    val highlight: Boolean = true,
    val autoRoll: Boolean = false,
)

/** A board location for animation. Points use absolute numbering (human's view, 1..24). */
sealed interface Loc {
    data class Point(val abs: Int, val slot: Int) : Loc
    data class Bar(val human: Boolean, val slot: Int) : Loc
    data class Off(val human: Boolean, val slot: Int) : Loc
}

data class Flight(val id: Long, val human: Boolean, val from: Loc, val to: Loc, val durationMs: Int)

data class DieView(val value: Int, val used: Boolean, val human: Boolean)

sealed interface GameDialog {
    data object AiDoubles : GameDialog
    data class GameOver(val result: GameResult, val scoreHuman: Int, val scoreAi: Int, val target: Int) : GameDialog
}

data class UiState(
    val human: IntArray = Pos.start(),
    val ai: IntArray = Pos.start(),
    val flight: Flight? = null,
    val dice: List<DieView> = emptyList(),
    val rollId: Long = 0,
    val rolling: Boolean = false,
    val rollMs: Int = 700,
    val selected: Int? = null,
    val targets: Set<Int> = emptySet(),
    val sources: Set<Int> = emptySet(),
    val cubeValue: Int = 1,
    val cubeOwner: CubeOwner = CubeOwner.CENTER,
    val cubeVisible: Boolean = true,
    val canRoll: Boolean = false,
    val canDouble: Boolean = false,
    val canUndo: Boolean = false,
    val canDone: Boolean = false,
    val canTakeback: Boolean = false,
    val message: String? = null,
    val dialog: GameDialog? = null,
    val scoreHuman: Int = 0,
    val scoreAi: Int = 0,
    val target: Int = 0,
    val crawford: Boolean = false,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val humanPips: Int = 167,
    val aiPips: Int = 167,
    val gameNumber: Int = 1,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("felt_backgammon", Context.MODE_PRIVATE)
    private val rng = SecureRandom()

    var screen by mutableStateOf(Screen.HOME)
        private set
    var settings by mutableStateOf(loadSettings())
        private set
    var stats by mutableStateOf(Stats.fromSave(prefs.getString(KEY_STATS, null)))
        private set
    /** Bumped whenever [stats] changes in place, so Compose re-reads it. */
    var statsVersion by mutableStateOf(0)
        private set
    var ui by mutableStateOf(UiState())
        private set

    private var session: Session? = Session.fromSave(prefs.getString(KEY_SESSION, null) ?: "")
    private var aiPlayer: Ai = Ai(session?.difficulty ?: Difficulty.MEDIUM)

    // Display copy of the board; differs from the session only while checkers are flying.
    private var dispHuman = Pos.start()
    private var dispAi = Pos.start()
    private var flight: Flight? = null
    private var flightDone: CompletableDeferred<Unit>? = null
    private var nextId = 1L
    private var selected: Int? = null
    private var message: String? = null
    private var dialog: GameDialog? = null
    private var openingDice: Pair<Int, Int>? = null
    private var rolling = false
    private var rollId = 0L
    private var job: Job? = null
    private var running = false
    private var runToken = 0
    private var rollingDice: List<DieView>? = null
    private val history = ArrayList<String>()

    val hasResumableGame: Boolean
        get() = session?.let { !(it.phase == Phase.GAME_OVER && it.matchOver) } ?: false

    val lastDifficulty: Difficulty
        get() = prefs.getString(KEY_LAST_LEVEL, null)?.let { runCatching { Difficulty.valueOf(it) }.getOrNull() } ?: Difficulty.MEDIUM
    val lastTarget: Int get() = prefs.getInt(KEY_LAST_TARGET, 0)

    private val busy get() = running

    // ---------------- Navigation ----------------

    fun go(s: Screen) {
        screen = s
    }

    fun goHome() {
        selected = null
        screen = Screen.HOME
    }

    fun continueGame() {
        val s = session ?: return
        screen = Screen.GAME
        if (busy) return
        syncDisplay()
        message = null
        when (s.phase) {
            Phase.OPENING -> runOpening()
            Phase.AI_TURN -> runAi()
            Phase.HUMAN_DOUBLED -> runAiAnswer()
            Phase.AI_DOUBLED -> dialog = GameDialog.AiDoubles
            Phase.GAME_OVER -> s.lastResult?.let { dialog = GameDialog.GameOver(it, s.scoreHuman, s.scoreAi, s.target) }
            Phase.HUMAN_MOVE -> if (history.isEmpty()) history.add(s.toSave())
            Phase.HUMAN_PRE_ROLL -> maybeAutoRoll()
        }
        publish()
    }

    fun startNewMatch(level: Difficulty, target: Int) {
        job?.cancel()
        running = false
        openingDice = null
        rollingDice = null
        rolling = false
        prefs.edit().putString(KEY_LAST_LEVEL, level.name).putInt(KEY_LAST_TARGET, target).apply()
        val s = Session(level, target)
        session = s
        aiPlayer = Ai(level)
        history.clear()
        dialog = null
        message = null
        selected = null
        flight = null
        syncDisplay()
        save()
        screen = Screen.GAME
        publish()
        runOpening()
    }

    // ---------------- Settings / stats ----------------

    fun updateSettings(s: Settings) {
        settings = s
        prefs.edit()
            .putString("undo", s.undo.name)
            .putString("speed", s.speed.name)
            .putBoolean("pips", s.showPips)
            .putBoolean("highlight", s.highlight)
            .putBoolean("autoRoll", s.autoRoll)
            .apply()
        publish()
    }

    private fun loadSettings(): Settings = Settings(
        undo = runCatching { UndoMode.valueOf(prefs.getString("undo", null)!!) }.getOrDefault(UndoMode.TURN),
        speed = runCatching { AnimSpeed.valueOf(prefs.getString("speed", null)!!) }.getOrDefault(AnimSpeed.NORMAL),
        showPips = prefs.getBoolean("pips", true),
        highlight = prefs.getBoolean("highlight", true),
        autoRoll = prefs.getBoolean("autoRoll", false),
    )

    fun resetStats() {
        stats = Stats()
        saveStats()
    }

    private fun saveStats() {
        prefs.edit().putString(KEY_STATS, stats.toSave()).apply()
        statsVersion++
    }

    private fun save() {
        val s = session ?: return
        prefs.edit().putString(KEY_SESSION, s.toSave()).apply()
    }

    // ---------------- Human input ----------------

    fun onBoardTap(rel: Int?) {
        val s = session ?: return
        if (busy || s.phase != Phase.HUMAN_MOVE || dialog != null) return
        val allowed = s.humanAllowed()
        val sel = selected
        if (sel != null && rel != null) {
            val m = allowed.filter { it.from == sel && it.to == rel }.minByOrNull { it.die }
            if (m != null) {
                doHumanMove(m)
                return
            }
        }
        selected = when {
            rel != null && rel == sel -> null
            rel != null && allowed.any { it.from == rel } -> rel
            else -> null
        }
        publish()
    }

    private fun doHumanMove(m: SubMove) = launchGame {
        val s = session!!
        selected = null
        s.humanMove(m)
        animateMove(true, m)
        syncDisplay()
        if (s.humanTurnComplete()) {
            if (settings.undo == UndoMode.OFF) {
                publish()
                delay(ms(250))
                commitHuman()
            }
        } else {
            autoSelect()
        }
    }

    /** Pre-select the only movable checker (e.g. a single checker on the bar). */
    private fun autoSelect() {
        val s = session ?: return
        val sources = s.humanAllowed().map { it.from }.toSet()
        if (sources.size == 1) selected = sources.first()
    }

    fun onRoll() {
        val s = session ?: return
        if (busy || s.phase != Phase.HUMAN_PRE_ROLL) return
        launchGame { humanRollFlow() }
    }

    private suspend fun humanRollFlow() {
        val s = session!!
        val a = die(); val b = die()
        stats.recordRoll(a, b)
        rollingDice = listOf(DieView(a, false, true), DieView(b, false, true))
        rollAnimation()
        rollingDice = null
        s.humanRoll(a, b)
        history.add(s.toSave())
        save()
        syncDisplay()
        if (s.humanHasNoMoves()) {
            message = "No legal moves"
            publish()
            delay(ms(1400))
            message = null
            s.commitHumanTurn()
            save()
            afterHumanCommit()
        } else {
            autoSelect()
        }
    }

    fun onDone() {
        val s = session ?: return
        if (busy || !s.humanTurnComplete()) return
        launchGame { commitHuman() }
    }

    private suspend fun commitHuman() {
        val s = session!!
        s.commitHumanTurn()
        selected = null
        save()
        afterHumanCommit()
    }

    private suspend fun afterHumanCommit() {
        val s = session!!
        if (s.phase == Phase.GAME_OVER) handleGameOver() else aiTurn()
    }

    fun onUndo() {
        val s = session ?: return
        if (busy || settings.undo == UndoMode.OFF || s.phase != Phase.HUMAN_MOVE || s.made.isEmpty()) return
        launchGame {
            selected = null
            val m = s.humanUndoStep() ?: return@launchGame
            animateUndo(m)
            syncDisplay()
            autoSelect()
        }
    }

    fun onTakeback() {
        val s = session ?: return
        if (busy || !canTakeback(s)) return
        // In the move phase the newest snapshot is this turn's start: drop it to reach the previous turn.
        if (s.phase == Phase.HUMAN_MOVE) history.removeAt(history.size - 1)
        val restored = Session.fromSave(history.last()) ?: return
        restored.takebacksUsed = s.takebacksUsed + 1
        session = restored
        selected = null
        message = "Took back your last turn"
        syncDisplay()
        save()
        publish()
        viewModelScope.launch {
            delay(ms(1300))
            if (message == "Took back your last turn") {
                message = null
                publish()
            }
        }
    }

    private fun canTakeback(s: Session): Boolean =
        settings.undo == UndoMode.FULL &&
            ((s.phase == Phase.HUMAN_PRE_ROLL && history.size >= 1) ||
                (s.phase == Phase.HUMAN_MOVE && s.made.isEmpty() && history.size >= 2))

    fun onDouble() {
        val s = session ?: return
        if (busy || !s.canHumanDouble()) return
        s.humanDoubles()
        save()
        runAiAnswer()
    }

    private fun runAiAnswer() = launchGame {
        val s = session!!
        message = "AI is considering your double…"
        publish()
        delay(ms(1100))
        val take = s.aiShouldTake(aiPlayer)
        stats.recordDouble(s.difficulty, !take)
        saveStats()
        s.aiAnswersDouble(take)
        save()
        if (s.phase == Phase.GAME_OVER) {
            message = "AI passes"
            publish()
            delay(ms(900))
            message = null
            handleGameOver()
        } else {
            message = "AI takes — cube now at ${s.cubeValue}"
            publish()
            delay(ms(1100))
            message = null
            maybeAutoRollInline()
        }
    }

    fun onAnswerDouble(take: Boolean) {
        val s = session ?: return
        if (s.phase != Phase.AI_DOUBLED) return
        dialog = null
        s.humanAnswersDouble(take)
        save()
        if (s.phase == Phase.GAME_OVER) launchGame { handleGameOver() } else runAi()
    }

    fun onNextGame() {
        val s = session ?: return
        dialog = null
        if (s.matchOver) {
            startNewMatch(s.difficulty, s.target)
            return
        }
        s.nextGame()
        history.clear()
        syncDisplay()
        save()
        publish()
        runOpening()
    }

    fun onGameOverToMenu() {
        dialog = null
        val s = session
        if (s != null && !s.matchOver && s.phase == Phase.GAME_OVER) {
            // Keep the match: next time "Continue" starts the next game.
            s.nextGame()
            history.clear()
            syncDisplay()
            save()
        }
        goHome()
        publish()
    }

    private fun maybeAutoRoll() {
        val s = session ?: return
        if (settings.autoRoll && s.phase == Phase.HUMAN_PRE_ROLL && !s.canHumanDouble() && !busy) {
            launchGame {
                delay(ms(350))
                if (s.phase == Phase.HUMAN_PRE_ROLL) humanRollFlow()
            }
        }
    }

    // ---------------- Opening / AI ----------------

    private fun runOpening() = launchGame {
        val s = session!!
        delay(ms(500))
        while (true) {
            val h = die(); val a = die()
            openingDice = h to a
            rollAnimation()
            if (s.resolveOpening(h, a)) {
                message = if (h > a) "You go first" else "AI goes first"
                publish()
                delay(ms(1000))
                break
            }
            message = "Tie — rolling again"
            publish()
            delay(ms(900))
        }
        openingDice = null
        message = null
        if (s.phase == Phase.HUMAN_MOVE) {
            history.add(s.toSave())
            save()
            autoSelect()
        } else {
            save()
            aiTurn()
        }
    }

    private fun runAi() = launchGame { aiTurn() }

    private suspend fun aiTurn() {
        val s = session!!
        if (s.phase != Phase.AI_TURN) return
        if (s.d1 == 0) {
            publish()
            delay(ms(350))
            if (s.aiConsidersDouble(aiPlayer)) {
                save()
                dialog = GameDialog.AiDoubles
                publish()
                return
            }
            val a = die(); val b = die()
            rollingDice = listOf(DieView(a, false, false), DieView(b, false, false))
            rollAnimation()
            rollingDice = null
            s.aiRoll(a, b)
        }
        publish()
        val started = System.currentTimeMillis()
        val play = withContext(Dispatchers.Default) { aiPlayer.choosePlay(s.aiPos().copy(), s.d1, s.d2) }
        val waited = System.currentTimeMillis() - started
        val minThink = ms(450)
        if (waited < minThink) delay(minThink - waited)
        if (play.moves.isEmpty()) {
            message = "AI has no legal moves"
            publish()
            delay(ms(1200))
            message = null
        }
        for (m in play.moves) {
            s.aiApply(m)
            animateMove(false, m)
            delay(ms(90))
        }
        syncDisplay()
        s.finishAiTurn()
        save()
        if (s.phase == Phase.GAME_OVER) {
            handleGameOver()
        } else {
            publish()
            maybeAutoRollInline()
        }
    }

    private suspend fun maybeAutoRollInline() {
        val s = session ?: return
        if (settings.autoRoll && s.phase == Phase.HUMAN_PRE_ROLL && !s.canHumanDouble()) {
            delay(ms(350))
            humanRollFlow()
        }
    }

    private suspend fun handleGameOver() {
        val s = session!!
        val r = s.lastResult ?: return
        syncDisplay()
        stats.recordGame(s.difficulty, r, s.target, s.scoreHuman, s.scoreAi)
        saveStats()
        history.clear()
        if (r.matchOver) prefs.edit().remove(KEY_SESSION).apply() else save()
        publish()
        delay(ms(500))
        dialog = GameDialog.GameOver(r, s.scoreHuman, s.scoreAi, s.target)
        publish()
    }

    // ---------------- Animation plumbing ----------------

    fun onFlightFinished(id: Long) {
        if (flight?.id == id) flightDone?.complete(Unit)
    }

    private fun ms(base: Int): Long = (base * settings.speed.factor).toLong()

    private suspend fun fly(human: Boolean, from: Loc, to: Loc, base: Int = 380) {
        val d = CompletableDeferred<Unit>()
        val f = Flight(nextId++, human, from, to, ms(base).toInt())
        flightDone = d
        flight = f
        publish()
        withTimeoutOrNull(f.durationMs + 800L) { d.await() }
        flight = null
    }

    private fun loc(human: Boolean, rel: Int, slot: Int): Loc = when (rel) {
        BAR -> Loc.Bar(human, slot)
        OFF -> Loc.Off(human, slot)
        else -> Loc.Point(if (human) rel else 25 - rel, slot)
    }

    private suspend fun animateMove(human: Boolean, m: SubMove) {
        val mine = if (human) dispHuman else dispAi
        val theirs = if (human) dispAi else dispHuman
        val from = loc(human, m.from, (mine[m.from] - 1).coerceAtLeast(0))
        mine[m.from]--
        val toSlot = if (m.to == OFF) mine[OFF] else if (m.hit) 0 else mine[m.to]
        fly(human, from, loc(human, m.to, toSlot))
        if (m.hit) {
            val tp = 25 - m.to
            theirs[tp]--
            mine[m.to]++
            fly(!human, loc(!human, tp, 0), loc(!human, BAR, theirs[BAR]), base = 330)
            theirs[BAR]++
        } else {
            mine[m.to]++
        }
    }

    private suspend fun animateUndo(m: SubMove) {
        val mine = dispHuman
        val theirs = dispAi
        mine[m.to]--
        val fromSlot = if (m.to == OFF) mine[OFF] else mine[m.to]
        fly(true, loc(true, m.to, fromSlot), loc(true, m.from, mine[m.from]), base = 300)
        mine[m.from]++
        if (m.hit) {
            val tp = 25 - m.to
            theirs[BAR]--
            fly(false, loc(false, BAR, theirs[BAR]), loc(false, tp, 0), base = 300)
            theirs[tp]++
        }
    }

    private suspend fun rollAnimation() {
        rolling = true
        rollId++
        publish()
        delay(ms(ROLL_MS))
        rolling = false
        publish()
    }

    private fun die() = rng.nextInt(6) + 1

    private fun syncDisplay() {
        val s = session ?: return
        dispHuman = s.human.copyOf()
        dispAi = s.ai.copyOf()
    }

    private fun launchGame(block: suspend () -> Unit) {
        val token = ++runToken
        running = true
        job = viewModelScope.launch {
            try {
                block()
            } finally {
                if (runToken == token) {
                    running = false
                    publish()
                }
            }
        }
    }

    // ---------------- UI state ----------------

    private fun publish() {
        val s = session
        if (s == null) {
            ui = UiState()
            return
        }
        val inMove = s.phase == Phase.HUMAN_MOVE && !busy
        val allowed = if (s.phase == Phase.HUMAN_MOVE) s.humanAllowed() else emptyList()
        val sel = selected
        val targets = if (sel != null) allowed.filter { it.from == sel }.map { it.to }.toSet() else emptySet()

        val dice: List<DieView> = when {
            rollingDice != null -> rollingDice!!
            openingDice != null -> listOf(
                DieView(openingDice!!.first, false, true),
                DieView(openingDice!!.second, false, false),
            )
            s.d1 != 0 -> {
                val human = s.phase == Phase.HUMAN_MOVE
                val all = MoveGen.diceList(s.d1, s.d2)
                if (human) {
                    val rem = s.remaining.toMutableList()
                    // Show dice in rolled order; mark those no longer remaining as used.
                    all.map { v -> val left = rem.remove(v); DieView(v, !left, true) }
                } else all.map { DieView(it, false, false) }
            }
            else -> emptyList()
        }
        val noDialog = dialog == null
        ui = UiState(
            human = dispHuman.copyOf(),
            ai = dispAi.copyOf(),
            flight = flight,
            dice = dice,
            rollId = rollId,
            rolling = rolling,
            rollMs = ms(ROLL_MS).toInt(),
            selected = sel,
            targets = targets,
            sources = if (settings.highlight && inMove) allowed.map { it.from }.toSet() else emptySet(),
            cubeValue = s.cubeValue,
            cubeOwner = s.cubeOwner,
            cubeVisible = s.target != 1 && !s.isCrawfordGame,
            canRoll = noDialog && !busy && s.phase == Phase.HUMAN_PRE_ROLL,
            canDouble = noDialog && !busy && s.canHumanDouble(),
            canUndo = noDialog && !busy && settings.undo != UndoMode.OFF && s.phase == Phase.HUMAN_MOVE && s.made.isNotEmpty(),
            canDone = noDialog && !busy && settings.undo != UndoMode.OFF && s.humanTurnComplete(),
            canTakeback = noDialog && !busy && canTakeback(s),
            message = message ?: when {
                s.phase == Phase.HUMAN_PRE_ROLL && !busy -> if (s.canHumanDouble()) "Your turn — roll or double" else "Your turn — roll"
                s.phase == Phase.HUMAN_MOVE && s.humanTurnComplete() && settings.undo != UndoMode.OFF && !busy -> "Tap Done to finish your turn"
                s.phase == Phase.AI_TURN -> "AI is thinking…"
                else -> null
            },
            dialog = dialog,
            scoreHuman = s.scoreHuman,
            scoreAi = s.scoreAi,
            target = s.target,
            crawford = s.isCrawfordGame,
            difficulty = s.difficulty,
            humanPips = Pos.pips(dispHuman),
            aiPips = Pos.pips(dispAi),
            gameNumber = s.gameNumber,
        )
    }

    init {
        syncDisplay()
        publish()
    }

    companion object {
        private const val KEY_SESSION = "session"
        private const val KEY_STATS = "stats"
        private const val KEY_LAST_LEVEL = "lastLevel"
        private const val KEY_LAST_TARGET = "lastTarget"
        private const val ROLL_MS = 650
    }
}
