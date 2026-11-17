package com.feltbackgammon.engine

/** Index of the bar in a side's array. */
const val BAR = 25

/** Index of the borne-off tray in a side's array. */
const val OFF = 0

/**
 * A position seen from the player who owns [me].
 *
 * Each array has 26 slots: 1..24 are points counted from that player's own home
 * (1 = deepest home point, 24 = farthest away), 25 is the bar, 0 is borne off.
 * [op] uses the opponent's own numbering, so the opponent's checker on their point j
 * sits on my point 25 - j.
 */
class Pos(val me: IntArray, val op: IntArray) {
    init {
        require(me.size == 26 && op.size == 26)
    }

    fun copy() = Pos(me.copyOf(), op.copyOf())

    /** Same position seen from the other side. */
    fun swapped() = Pos(op.copyOf(), me.copyOf())

    fun key(): String {
        val sb = StringBuilder(52)
        for (v in me) sb.append((v + 48).toChar())
        for (v in op) sb.append((v + 48).toChar())
        return sb.toString()
    }

    /** Opponent checkers on MY point [myPoint] (1..24). */
    fun opOnMyPoint(myPoint: Int): Int = op[25 - myPoint]

    val myPips: Int get() = pips(me)
    val opPips: Int get() = pips(op)

    /** True while the two armies can still hit or block each other. */
    fun hasContact(): Boolean {
        val myBack = backmost(me)
        val opBack = backmost(op)
        if (myBack == 0 || opBack == 0) return false
        return myBack + opBack > 25
    }

    companion object {
        fun pips(a: IntArray): Int {
            var s = 0
            for (i in 1..25) s += i * a[i]
            return s
        }

        /** Highest occupied index (bar counts as 25); 0 if everything is off. */
        fun backmost(a: IntArray): Int {
            for (i in 25 downTo 1) if (a[i] > 0) return i
            return 0
        }

        fun start(): IntArray {
            val a = IntArray(26)
            a[24] = 2; a[13] = 5; a[8] = 3; a[6] = 5
            return a
        }

        fun startPos() = Pos(start(), start())

        fun checkerCount(a: IntArray): Int = a.sum()
    }
}

/** One checker moved by one die, in the mover's own numbering. to == 0 means borne off. */
data class SubMove(val from: Int, val to: Int, val die: Int, val hit: Boolean)

/** A complete legal play for one roll and the position it leads to. */
class Play(val moves: List<SubMove>, val result: Pos)
