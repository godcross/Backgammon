package com.feltbackgammon.engine

import kotlin.random.Random

enum class Difficulty(val label: String) {
    EASY("Easy"), MEDIUM("Medium"), HARD("Hard")
}

/**
 * Computer opponent.
 *  EASY   – greedy 1-ply with heavy noise and occasional random plays; loose cube.
 *  MEDIUM – greedy 1-ply with light noise; reasonable cube.
 *  HARD   – 2-ply look-ahead (averages the opponent's best reply over all 21 rolls); sound cube.
 */
class Ai(val level: Difficulty, private val rnd: Random = Random.Default) {

    fun choosePlay(p: Pos, d1: Int, d2: Int): Play {
        val plays = MoveGen.legalPlays(p, d1, d2)
        if (plays.size == 1) return plays[0]
        return when (level) {
            Difficulty.EASY -> {
                if (rnd.nextDouble() < 0.22) plays[rnd.nextInt(plays.size)]
                else plays.maxBy { Evaluator.evaluate(it.result) + gauss() * 9.0 }
            }
            Difficulty.MEDIUM -> plays.maxBy { Evaluator.evaluate(it.result) + gauss() * 1.5 }
            Difficulty.HARD -> twoPly(plays)
        }
    }

    private fun twoPly(plays: List<Play>): Play {
        val ranked = plays.sortedByDescending { Evaluator.evaluate(it.result) }
        val candidates = ranked.take(if (ranked[0].result.hasContact()) 12 else 4)
        var best = candidates[0]
        var bestVal = Double.NEGATIVE_INFINITY
        for (c in candidates) {
            val v = expectedAfterReply(c.result)
            if (v > bestVal) { bestVal = v; best = c }
        }
        return best
    }

    /** Average value for the side that just moved, assuming the opponent replies greedily. */
    private fun expectedAfterReply(afterMine: Pos): Double {
        if (afterMine.me[OFF] == 15) return 1000.0
        val theirView = afterMine.swapped()
        var total = 0.0
        for ((a, b, w) in Evaluator.ROLLS) {
            val replies = MoveGen.legalPlays(theirView, a, b)
            var bestReply = Double.NEGATIVE_INFINITY
            for (r in replies) {
                val v = Evaluator.evaluate(r.result)
                if (v > bestReply) bestReply = v
            }
            total += -bestReply * w
        }
        return total / 36.0
    }

    // ---------------- Doubling cube ----------------

    /**
     * Should the AI (on roll, holding access to the cube) offer a double?
     * [aiPos] is the position from the AI's side.
     */
    fun shouldDouble(aiPos: Pos, ctx: CubeContext): Boolean {
        if (!ctx.cubeUseful) return false
        // Post-Crawford: trailer should double at once.
        if (ctx.opponentAtMatchPoint && !ctx.crawfordGame) return true
        val pw = Evaluator.winProbOnRoll(aiPos)
        return when (level) {
            Difficulty.EASY -> pw > 0.80 && rnd.nextDouble() < 0.5
            Difficulty.MEDIUM -> pw > 0.71
            Difficulty.HARD -> pw > 0.68 && !(pw > 0.88 && gammonish(aiPos))
        }
    }

    /**
     * Should the AI accept the human's double? [humanPos] is from the human's side (human on roll).
     */
    fun shouldTake(humanPos: Pos, ctx: CubeContext): Boolean {
        if (ctx.selfAtMatchPoint) return true // nothing to lose by taking
        val aiWin = 1.0 - Evaluator.winProbOnRoll(humanPos)
        return when (level) {
            Difficulty.EASY -> aiWin > 0.18 || rnd.nextDouble() < 0.3
            Difficulty.MEDIUM -> aiWin > 0.24
            Difficulty.HARD -> aiWin > (if (gammonish(humanPos)) 0.27 else 0.23)
        }
    }

    /** The side on roll in [p] has real gammon chances (opponent has nothing off, checkers back). */
    private fun gammonish(p: Pos): Boolean {
        if (p.op[OFF] > 0) return false
        var back = p.op[BAR]
        for (j in 13..24) back += p.op[j]
        return back >= 3
    }

    private fun gauss(): Double {
        // Box-Muller
        val u1 = rnd.nextDouble().coerceAtLeast(1e-12)
        val u2 = rnd.nextDouble()
        return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2 * kotlin.math.PI * u2)
    }
}

/** Match context for cube decisions, always from the deciding AI's point of view. */
class CubeContext(
    val cubeUseful: Boolean,
    val crawfordGame: Boolean,
    val opponentAtMatchPoint: Boolean,
    val selfAtMatchPoint: Boolean,
)
