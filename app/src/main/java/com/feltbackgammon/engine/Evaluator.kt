package com.feltbackgammon.engine

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Hand-built positional evaluator. Scores are in rough "pip" units.
 * [evaluate] scores a position for [Pos.me] who has JUST moved (the opponent is on roll).
 */
object Evaluator {

    /** The 21 distinct rolls with their weight out of 36. */
    val ROLLS: List<Triple<Int, Int, Int>> = buildList {
        for (a in 1..6) for (b in a..6) add(Triple(a, b, if (a == b) 1 else 2))
    }

    private val POINT_VALUE = doubleArrayOf(
        0.0,
        1.5, 2.5, 3.5, 5.5, 8.0, 7.0,   // home board 1..6
        6.0, 3.0, 1.5, 1.0, 1.0, 1.0,   // outer board 7..12
        0.5, 0.5, 0.5, 0.5, 0.5,         // 13..17
        3.5, 4.0, 5.0, 5.5, 3.5, 3.0, 2.5 // 18..24 = anchors in opponent's board
    )

    fun evaluate(p: Pos): Double {
        if (p.me[OFF] == 15) return 1000.0
        if (p.op[OFF] == 15) return -1000.0
        return if (p.hasContact()) contactEval(p) else raceEval(p)
    }

    private fun raceEval(p: Pos): Double {
        // Opponent is on roll, worth about 4 pips to them.
        return effectivePips(p.op) - effectivePips(p.me) - 4.0 + gammonEdge(p)
    }

    private fun effectivePips(a: IntArray): Double {
        var e = Position.pipsOf(a).toDouble()
        val left = 15 - a[OFF]
        for (i in 1..6) {
            if (a[i] > 2) e += (a[i] - 2) * 0.6           // stacking wastes rolls
            if (a[i] == 0 && left > 5 && i >= 4) e += 1.0  // gaps on high points
        }
        e += a[1] * 0.8 + a[2] * 0.4                       // wastage on low points
        return e
    }

    /** Small push towards winning gammons / saving them. */
    private fun gammonEdge(p: Pos): Double {
        var g = 0.0
        if (p.op[OFF] == 0 && p.me[OFF] > 0) g += 2.0 + p.me[OFF] * 0.4
        if (p.me[OFF] == 0 && p.op[OFF] > 0) g -= 2.0 + p.op[OFF] * 0.4
        return g
    }

    private fun madeHomePoints(a: IntArray): Int {
        var c = 0
        for (i in 1..6) if (a[i] >= 2) c++
        return c
    }

    private fun contactEval(p: Pos): Double {
        val me = p.me
        val op = p.op
        var s = (p.opPips - p.myPips).toDouble()

        // Does the opponent still have checkers that must pass through my board?
        var opBack = op[BAR]
        for (j in 13..24) opBack += op[j]  // their points 13..24 = my 1..12 region
        val homeFactor = if (opBack > 0) 1.0 else 0.25

        for (i in 1..24) {
            if (me[i] >= 2) {
                val v = POINT_VALUE[i]
                s += if (i <= 12) v * homeFactor else v * 0.6
                if (me[i] > 4) s -= (me[i] - 4) * 0.8
            }
        }

        // Prime: consecutive made points in front of opponent back checkers.
        var run = 0
        var best = 0
        var bestLow = 0
        for (i in 1..12) {
            if (me[i] >= 2) {
                run++
                if (run > best) { best = run; bestLow = i - run + 1 }
            } else run = 0
        }
        if (best >= 3) {
            // Opponent checkers behind it sit on my points below bestLow (their high points) or on the bar.
            var trapped = op[BAR]
            for (x in 1 until bestLow) trapped += op[25 - x]
            if (trapped > 0) s += best * best * 1.1 + trapped * best * 0.4
        }

        val myHome = madeHomePoints(me)
        val opHome = madeHomePoints(op)

        // Checkers on the bar.
        s += op[BAR] * (2.0 + myHome * 1.6)
        s -= me[BAR] * (2.0 + opHome * 1.6)

        // My back checkers stuck in front of a strong opponent board.
        var myBackCount = 0
        for (i in 19..24) myBackCount += me[i]
        s -= myBackCount * opHome * 0.35

        // Blot exposure.
        s -= shotLoss(p) * (1.0 + 0.3 * opHome)

        // Mirror: the opponent's blots are a chance for me next turn (smaller weight, I'm not on roll).
        s += blotTargets(p) * 0.6

        s += gammonEdge(p) * 0.5
        return s
    }

    /** Count of opponent blots inside my reach zone (cheap proxy for my next-turn shots). */
    private fun blotTargets(p: Pos): Double {
        val myBack = Pos.backmost(p.me)
        var t = 0.0
        for (j in 1..24) {
            if (p.op[j] != 1) continue
            val myPoint = 25 - j
            // I can only hit it if I have checkers behind it.
            if (myBack <= myPoint) continue
            // Being hit costs them (25 - j) pips.
            t += (25 - j) / 24.0 * 2.0
        }
        return t
    }

    /**
     * Expected pips I lose to the opponent hitting my blots on their next roll.
     * Accounts for direct and combination shots blocked by my made points.
     */
    fun shotLoss(p: Pos): Double {
        val me = p.me
        val op = p.op
        val blots = ArrayList<Int>()
        for (b in 1..24) if (me[b] == 1) blots.add(b)
        if (blots.isEmpty()) return 0.0

        // Opponent shooters in MY numbering: x = 0 means bar.
        val sources = ArrayList<Int>()
        if (op[BAR] > 0) sources.add(0)
        val opOnBar = op[BAR] > 0
        for (x in 1..23) if (op[25 - x] > 0) sources.add(x)
        if (sources.isEmpty()) return 0.0

        fun blocked(pt: Int) = pt in 1..24 && me[pt] >= 2

        var total = 0.0
        for ((a, b, w) in ROLLS) {
            var worst = 0
            for (blot in blots) {
                if (25 - blot <= worst) continue
                var hit = false
                for (x in sources) {
                    if (x >= blot) break
                    // With a checker on the bar, only the bar checker can move first.
                    if (opOnBar && x != 0 && op[BAR] >= (if (a == b) 4 else 2)) continue
                    val dist = blot - x
                    if (canReach(x, dist, a, b, ::blocked)) { hit = true; break }
                }
                if (hit) worst = 25 - blot
            }
            total += worst * w
        }
        return total / 36.0
    }

    private inline fun canReach(x: Int, dist: Int, a: Int, b: Int, blocked: (Int) -> Boolean): Boolean {
        if (a == b) {
            var pos = x
            for (step in 1..4) {
                pos += a
                if (pos - x == dist) return true
                if (pos - x > dist || blocked(pos)) return false
            }
            return false
        }
        if (dist == a || dist == b) return true
        if (dist == a + b) return !blocked(x + a) || !blocked(x + b)
        return false
    }

    private fun sigmoid(z: Double) = 1.0 / (1.0 + exp(-z))

    /** Estimated probability that [Pos.me] wins, with [Pos.me] ON ROLL. */
    fun winProbOnRoll(p: Pos): Double {
        if (p.me[OFF] == 15) return 1.0
        if (p.op[OFF] == 15) return 0.0
        if (!p.hasContact()) {
            val mine = effectivePips(p.me)
            val theirs = effectivePips(p.op)
            val lead = theirs - mine + 4.0
            val avg = max(4.0, (mine + theirs) / 2.0)
            val sd = 2.1 * sqrt(avg)
            return normalCdf(lead / sd).coerceIn(0.001, 0.999)
        }
        // The opponent "just moved" in the swapped view.
        val v = -evaluate(p.swapped())
        return sigmoid(v / 14.0).coerceIn(0.01, 0.99)
    }

    private fun normalCdf(z: Double): Double {
        // Abramowitz-Stegun approximation.
        val t = 1.0 / (1.0 + 0.2316419 * kotlin.math.abs(z))
        val d = 0.3989423 * exp(-z * z / 2.0)
        val pr = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))))
        return if (z > 0) 1.0 - pr else pr
    }
}

/** Tiny helper so Evaluator can reuse the pip count without a Pos. */
object Position {
    fun pipsOf(a: IntArray): Int = Pos.pips(a)
}
