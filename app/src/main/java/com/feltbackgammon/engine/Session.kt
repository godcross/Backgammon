package com.feltbackgammon.engine

enum class Phase {
    /** Both players roll one die to decide who starts. */
    OPENING,
    /** Human to act: may double, then rolls. */
    HUMAN_PRE_ROLL,
    /** Human has dice and is moving checkers. */
    HUMAN_MOVE,
    /** AI's turn (cube decision, roll, move). If dice are set they're already rolled. */
    AI_TURN,
    /** Human offered a double, AI must answer. */
    HUMAN_DOUBLED,
    /** AI offered a double, human must answer. */
    AI_DOUBLED,
    GAME_OVER,
}

enum class CubeOwner { CENTER, HUMAN, AI }

enum class WinType(val mult: Int, val label: String) {
    SINGLE(1, "Single game"), GAMMON(2, "Gammon"), BACKGAMMON(3, "Backgammon")
}

data class GameResult(
    val humanWon: Boolean,
    val type: WinType,
    val cube: Int,
    val points: Int,
    val byPass: Boolean,
    val matchOver: Boolean,
)

/**
 * One match (or single game when [target] == 0) between the human (White, moves 24 -> 1)
 * and the AI (Black). All board arrays use each side's own numbering (see [Pos]).
 * Dice come from the caller so the session itself is deterministic and testable.
 */
class Session(val difficulty: Difficulty, val target: Int) {
    var human: IntArray = Pos.start()
    var ai: IntArray = Pos.start()
    var phase: Phase = Phase.OPENING
    var d1 = 0
    var d2 = 0
    var cubeValue = 1
    var cubeOwner = CubeOwner.CENTER
    var scoreHuman = 0
    var scoreAi = 0
    /** 0 = Crawford not reached yet, 1 = this game is the Crawford game, 2 = post-Crawford. */
    var crawford = 0
    private var crawfordPending = false
    var gameNumber = 1
    var lastResult: GameResult? = null
    var aiCubeChecked = false
    var takebacksUsed = 0

    // Human turn bookkeeping (rebuilt on load).
    private var turnStartHuman: IntArray = human.copyOf()
    private var turnStartAi: IntArray = ai.copyOf()
    val made = ArrayList<SubMove>()
    var remaining: List<Int> = emptyList()
        private set
    var rules = MoveGen.TurnRules(0, null)
        private set

    val isSingleGame get() = target == 0
    val matchOver get() = lastResult?.matchOver == true
    val isCrawfordGame get() = crawford == 1

    fun humanPos() = Pos(human, ai)
    fun aiPos() = Pos(ai, human)

    // ---------------- Game lifecycle ----------------

    fun newGame() {
        human = Pos.start()
        ai = Pos.start()
        cubeValue = 1
        cubeOwner = CubeOwner.CENTER
        d1 = 0; d2 = 0
        phase = Phase.OPENING
        aiCubeChecked = false
        lastResult = null
        made.clear()
        remaining = emptyList()
        if (crawfordPending) {
            crawford = 1
            crawfordPending = false
        }
    }

    /** Resolve the opening roll. Returns false on a tie (roll again). */
    fun resolveOpening(humanDie: Int, aiDie: Int): Boolean {
        check(phase == Phase.OPENING)
        if (humanDie == aiDie) return false
        if (humanDie > aiDie) {
            d1 = humanDie; d2 = aiDie
            beginHumanMove()
        } else {
            d1 = aiDie; d2 = humanDie
            phase = Phase.AI_TURN
            aiCubeChecked = true
        }
        return true
    }

    fun nextGame() {
        check(phase == Phase.GAME_OVER && !matchOver)
        gameNumber++
        newGame()
    }

    // ---------------- Cube ----------------

    fun cubeAvailableTo(side: CubeOwner): Boolean {
        if (target == 1) return false
        if (crawford == 1) return false
        if (cubeValue >= 64) return false
        if (cubeOwner != CubeOwner.CENTER && cubeOwner != side) return false
        return true
    }

    fun canHumanDouble() = phase == Phase.HUMAN_PRE_ROLL && cubeAvailableTo(CubeOwner.HUMAN)

    fun humanDoubles() {
        check(canHumanDouble())
        phase = Phase.HUMAN_DOUBLED
    }

    fun aiAnswersDouble(take: Boolean) {
        check(phase == Phase.HUMAN_DOUBLED)
        if (take) {
            cubeValue *= 2
            cubeOwner = CubeOwner.AI
            phase = Phase.HUMAN_PRE_ROLL
        } else {
            endGame(humanWon = true, byPass = true)
        }
    }

    /** Context for AI cube decisions. */
    fun cubeContextForAi(): CubeContext {
        val aiNeeds = if (isSingleGame) Int.MAX_VALUE else target - scoreAi
        val humanNeeds = if (isSingleGame) Int.MAX_VALUE else target - scoreHuman
        return CubeContext(
            // Doubling is pointless when the current cube already wins the match.
            cubeUseful = cubeAvailableTo(CubeOwner.AI) && cubeValue < aiNeeds,
            crawfordGame = crawford == 1,
            opponentAtMatchPoint = !isSingleGame && humanNeeds == 1,
            selfAtMatchPoint = !isSingleGame && aiNeeds == 1,
        )
    }

    /** Records that the AI considered the cube this turn; returns true if it doubles. */
    fun aiConsidersDouble(ai: Ai): Boolean {
        check(phase == Phase.AI_TURN)
        if (aiCubeChecked || d1 != 0) return false
        aiCubeChecked = true
        val ctx = cubeContextForAi()
        if (!ctx.cubeUseful) return false
        val doubles = ai.shouldDouble(aiPos(), ctx)
        if (doubles) phase = Phase.AI_DOUBLED
        return doubles
    }

    fun aiShouldTake(ai: Ai): Boolean = ai.shouldTake(humanPos(), cubeContextForAi())

    fun humanAnswersDouble(take: Boolean) {
        check(phase == Phase.AI_DOUBLED)
        if (take) {
            cubeValue *= 2
            cubeOwner = CubeOwner.HUMAN
            phase = Phase.AI_TURN
        } else {
            endGame(humanWon = false, byPass = true)
        }
    }

    // ---------------- Human turn ----------------

    fun humanRoll(a: Int, b: Int) {
        check(phase == Phase.HUMAN_PRE_ROLL)
        d1 = a; d2 = b
        beginHumanMove()
    }

    private fun beginHumanMove() {
        turnStartHuman = human.copyOf()
        turnStartAi = ai.copyOf()
        made.clear()
        remaining = MoveGen.diceList(d1, d2)
        rules = MoveGen.turnRules(humanPos(), d1, d2)
        phase = Phase.HUMAN_MOVE
    }

    fun humanAllowed(): List<SubMove> =
        if (phase != Phase.HUMAN_MOVE) emptyList()
        else MoveGen.allowedNext(humanPos(), remaining, made.size, rules)

    fun humanHasNoMoves() = phase == Phase.HUMAN_MOVE && rules.maxLen == 0

    fun humanMove(m: SubMove) {
        check(phase == Phase.HUMAN_MOVE)
        require(humanAllowed().contains(m)) { "Illegal move $m" }
        MoveGen.apply(humanPos(), m)
        made.add(m)
        val r = remaining.toMutableList(); r.remove(m.die); remaining = r
    }

    fun humanTurnComplete() = phase == Phase.HUMAN_MOVE && made.size >= rules.maxLen

    /** Undo the last sub-move of this turn. Returns the move undone, or null. */
    fun humanUndoStep(): SubMove? {
        if (phase != Phase.HUMAN_MOVE || made.isEmpty()) return null
        val last = made.removeAt(made.size - 1)
        val replay = made.toList()
        human = turnStartHuman.copyOf()
        ai = turnStartAi.copyOf()
        made.clear()
        remaining = MoveGen.diceList(d1, d2)
        for (m in replay) humanMove(m)
        return last
    }

    fun commitHumanTurn() {
        check(humanTurnComplete())
        made.clear()
        if (human[OFF] == 15) {
            endGame(humanWon = true, byPass = false)
            return
        }
        d1 = 0; d2 = 0
        phase = Phase.AI_TURN
        aiCubeChecked = false
    }

    // ---------------- AI turn ----------------

    fun aiRoll(a: Int, b: Int) {
        check(phase == Phase.AI_TURN && d1 == 0)
        d1 = a; d2 = b
    }

    fun aiApply(m: SubMove) {
        check(phase == Phase.AI_TURN)
        MoveGen.apply(aiPos(), m)
    }

    fun finishAiTurn() {
        check(phase == Phase.AI_TURN)
        if (ai[OFF] == 15) {
            endGame(humanWon = false, byPass = false)
            return
        }
        d1 = 0; d2 = 0
        phase = Phase.HUMAN_PRE_ROLL
    }

    // ---------------- Scoring ----------------

    private fun winType(loser: IntArray): WinType {
        if (loser[OFF] > 0) return WinType.SINGLE
        if (loser[BAR] > 0) return WinType.BACKGAMMON
        for (i in 19..24) if (loser[i] > 0) return WinType.BACKGAMMON
        return WinType.GAMMON
    }

    private fun endGame(humanWon: Boolean, byPass: Boolean) {
        val type = if (byPass) WinType.SINGLE else winType(if (humanWon) ai else human)
        val points = cubeValue * type.mult
        if (humanWon) scoreHuman += points else scoreAi += points
        val over = isSingleGame || scoreHuman >= target || scoreAi >= target
        if (!isSingleGame && !over) {
            if (crawford == 1) crawford = 2
            else if (crawford == 0 && (scoreHuman == target - 1 || scoreAi == target - 1)) crawfordPending = true
        }
        lastResult = GameResult(humanWon, type, cubeValue, points, byPass, over)
        d1 = 0; d2 = 0
        phase = Phase.GAME_OVER
    }

    // ---------------- Save / load ----------------

    fun toSave(): String {
        val inMove = phase == Phase.HUMAN_MOVE
        val h = if (inMove) turnStartHuman else human
        val a = if (inMove) turnStartAi else ai
        val r = lastResult
        return buildString {
            appendLine("v=1")
            appendLine("difficulty=${difficulty.name}")
            appendLine("target=$target")
            appendLine("human=${h.joinToString(",")}")
            appendLine("ai=${a.joinToString(",")}")
            appendLine("phase=${phase.name}")
            appendLine("d1=$d1")
            appendLine("d2=$d2")
            appendLine("cube=$cubeValue")
            appendLine("owner=${cubeOwner.name}")
            appendLine("sh=$scoreHuman")
            appendLine("sa=$scoreAi")
            appendLine("crawford=$crawford")
            appendLine("crawfordPending=$crawfordPending")
            appendLine("game=$gameNumber")
            appendLine("aiCube=$aiCubeChecked")
            appendLine("takebacks=$takebacksUsed")
            if (r != null) appendLine("result=${r.humanWon},${r.type.name},${r.cube},${r.points},${r.byPass},${r.matchOver}")
        }
    }

    companion object {
        fun fromSave(s: String): Session? = try {
            val m = s.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
            val sess = Session(Difficulty.valueOf(m.getValue("difficulty")), m.getValue("target").toInt())
            sess.human = parseArr(m.getValue("human"))
            sess.ai = parseArr(m.getValue("ai"))
            sess.d1 = m.getValue("d1").toInt()
            sess.d2 = m.getValue("d2").toInt()
            sess.cubeValue = m.getValue("cube").toInt()
            sess.cubeOwner = CubeOwner.valueOf(m.getValue("owner"))
            sess.scoreHuman = m.getValue("sh").toInt()
            sess.scoreAi = m.getValue("sa").toInt()
            sess.crawford = m.getValue("crawford").toInt()
            sess.crawfordPending = m["crawfordPending"].toBoolean()
            sess.gameNumber = m.getValue("game").toInt()
            sess.aiCubeChecked = m["aiCube"].toBoolean()
            sess.takebacksUsed = m["takebacks"]?.toInt() ?: 0
            m["result"]?.split(",")?.let {
                sess.lastResult = GameResult(
                    it[0].toBoolean(), WinType.valueOf(it[1]), it[2].toInt(), it[3].toInt(), it[4].toBoolean(), it[5].toBoolean()
                )
            }
            val phase = Phase.valueOf(m.getValue("phase"))
            if (phase == Phase.HUMAN_MOVE) {
                sess.phase = Phase.HUMAN_MOVE
                sess.beginHumanMove()
            } else {
                sess.phase = phase
            }
            require(sess.human.sum() == 15 && sess.ai.sum() == 15)
            sess
        } catch (e: Exception) {
            null
        }

        private fun parseArr(s: String): IntArray {
            val a = s.split(",").map { it.trim().toInt() }.toIntArray()
            require(a.size == 26)
            return a
        }
    }
}
