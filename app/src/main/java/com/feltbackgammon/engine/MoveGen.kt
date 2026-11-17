package com.feltbackgammon.engine

/**
 * Legal move generation following standard backgammon rules:
 *  - checkers on the bar must enter first
 *  - a point holding 2+ opponent checkers is blocked; landing on a single one hits it
 *  - bearing off only once all 15 are home; a higher die may bear off from the
 *    highest occupied point
 *  - as many dice as possible must be used; if only one die can be used, the larger
 *    must be used when possible
 */
object MoveGen {

    /** Target for moving from [from] with [die], or -1 if illegal. 0 = bear off. */
    fun target(p: Pos, from: Int, die: Int): Int {
        val me = p.me
        if (from !in 1..25 || me[from] <= 0) return -1
        if (me[BAR] > 0 && from != BAR) return -1
        val t = from - die
        if (t >= 1) return if (p.op[25 - t] >= 2) -1 else t
        // Bearing off: everything must be home (points 1..6).
        for (i in 7..25) if (me[i] > 0) return -1
        if (t == 0) return 0
        // Over-shooting die: only from the highest occupied point.
        for (i in from + 1..6) if (me[i] > 0) return -1
        return 0
    }

    fun singleMoves(p: Pos, die: Int): List<SubMove> {
        val out = ArrayList<SubMove>(8)
        val from = if (p.me[BAR] > 0) BAR..BAR else 1..24
        for (f in from.reversed()) {
            if (p.me[f] <= 0) continue
            val t = target(p, f, die)
            if (t < 0) continue
            val hit = t >= 1 && p.op[25 - t] == 1
            out.add(SubMove(f, t, die, hit))
        }
        return out
    }

    /** Mutates [p] by playing [m]. */
    fun apply(p: Pos, m: SubMove) {
        p.me[m.from]--
        if (m.to == OFF) {
            p.me[OFF]++
        } else {
            val oi = 25 - m.to
            if (p.op[oi] == 1) {
                p.op[oi] = 0
                p.op[BAR]++
            }
            p.me[m.to]++
        }
    }

    fun applied(p: Pos, m: SubMove): Pos = p.copy().also { apply(it, m) }

    fun diceList(d1: Int, d2: Int): List<Int> =
        if (d1 == d2) listOf(d1, d1, d1, d1) else listOf(d1, d2)

    private fun minus(list: List<Int>, d: Int): List<Int> {
        val r = list.toMutableList()
        r.remove(d)
        return r
    }

    /** Most dice that can still be played from [p] with the [remaining] dice. */
    fun maxMoves(p: Pos, remaining: List<Int>): Int {
        if (remaining.isEmpty()) return 0
        var best = 0
        for (d in remaining.distinct()) {
            for (m in singleMoves(p, d)) {
                val r = 1 + maxMoves(applied(p, m), minus(remaining, d))
                if (r == remaining.size) return r
                if (r > best) best = r
            }
        }
        return best
    }

    /** Facts about a turn needed to validate human input step by step. */
    class TurnRules(val maxLen: Int, val forcedDie: Int?)

    fun turnRules(start: Pos, d1: Int, d2: Int): TurnRules {
        val dice = diceList(d1, d2)
        val maxLen = maxMoves(start, dice)
        var forced: Int? = null
        if (maxLen == 1 && d1 != d2) {
            val big = maxOf(d1, d2)
            if (singleMoves(start, big).isNotEmpty()) forced = big
        }
        return TurnRules(maxLen, forced)
    }

    /**
     * Sub-moves the human may make next, given the position after [made] moves and the
     * dice still [remaining]. Only moves that still allow a full-length play are offered.
     */
    fun allowedNext(cur: Pos, remaining: List<Int>, made: Int, rules: TurnRules): List<SubMove> {
        if (made >= rules.maxLen) return emptyList()
        val out = ArrayList<SubMove>()
        for (d in remaining.distinct()) {
            if (rules.forcedDie != null && d != rules.forcedDie) continue
            for (m in singleMoves(cur, d)) {
                val after = applied(cur, m)
                if (made + 1 + maxMoves(after, minus(remaining, d)) == rules.maxLen) out.add(m)
            }
        }
        return out
    }

    /** All distinct legal plays (by resulting position). Never empty: a blocked roll gives one empty play. */
    fun legalPlays(p: Pos, d1: Int, d2: Int): List<Play> {
        val leaves = ArrayList<Pair<List<SubMove>, Pos>>()
        var maxLen = 0
        val orders = if (d1 == d2) listOf(diceList(d1, d2)) else listOf(listOf(d1, d2), listOf(d2, d1))

        fun rec(pos: Pos, rem: List<Int>, seq: List<SubMove>, visited: HashSet<String>) {
            var any = false
            if (rem.isNotEmpty()) {
                val d = rem[0]
                for (m in singleMoves(pos, d)) {
                    any = true
                    val q = applied(pos, m)
                    val rest = rem.subList(1, rem.size)
                    if (!visited.add(q.key() + rest.size)) continue
                    rec(q, rest, seq + m, visited)
                }
            }
            if (!any) {
                if (seq.size > maxLen) {
                    maxLen = seq.size
                    leaves.clear()
                }
                if (seq.size == maxLen) leaves.add(seq to pos)
            }
        }

        for (o in orders) rec(p, o, emptyList(), HashSet())

        var filtered: List<Pair<List<SubMove>, Pos>> = leaves
        if (maxLen == 1 && d1 != d2) {
            val big = maxOf(d1, d2)
            val withBig = leaves.filter { it.first[0].die == big }
            if (withBig.isNotEmpty()) filtered = withBig
        }
        val seen = HashSet<String>()
        val plays = ArrayList<Play>()
        for ((seq, pos) in filtered) {
            if (seen.add(pos.key())) plays.add(Play(seq, pos))
        }
        if (plays.isEmpty()) plays.add(Play(emptyList(), p.copy()))
        return plays
    }
}
