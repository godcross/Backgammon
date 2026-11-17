import com.feltbackgammon.engine.*
import kotlin.random.Random

var failures = 0
fun check(cond: Boolean, msg: String) {
    if (!cond) { failures++; println("FAIL: $msg") }
}

fun empty() = IntArray(26)

fun rulesTests() {
    // Opening 3-1: standard count of distinct legal plays is 16.
    val start = Pos.startPos()
    val p31 = MoveGen.legalPlays(start, 3, 1)
    println("Opening 3-1 plays: ${p31.size}")
    check(p31.all { it.moves.size == 2 }, "3-1 uses both dice")

    // Bar entry blocked: me on bar, opponent owns my 24..19 entry points for dice 1 & 2.
    run {
        val me = empty(); me[BAR] = 1; me[6] = 14
        val op = empty(); op[1] = 2; op[2] = 2; op[13] = 11 // op's points 1,2 = my 24,23
        val plays = MoveGen.legalPlays(Pos(me, op), 1, 2)
        check(plays.size == 1 && plays[0].moves.isEmpty(), "blocked entry => no moves")
        val p2 = MoveGen.legalPlays(Pos(me.copyOf(), op.copyOf()), 1, 3)
        check(p2.all { it.moves.first().from == BAR && it.moves.first().to == 22 }, "must enter with 3 first")
    }

    // Larger die rule: only one die can be played -> must play the larger.
    run {
        // My single checker on 8, everything else borne off is not allowed (would be home). Use a blocking setup:
        val me = empty(); me[13] = 1; me[OFF] = 0; me[6] = 14
        val op = empty()
        // Block my 13-6=7 (op point 18) and 13-5=8 (op 17)... we want: die 6 playable alone, die 5 playable alone, not both.
        // 13 -> 7 (6) open; 13 -> 8 (5) open; 7 -> 2 blocked, 8 -> 2 blocked; 6-pt checkers: 6->1 / 6->0 not allowed (not all home)
        op[23] = 2  // my point 2 blocked
        op[24] = 2  // my point 1 blocked
        op[13] = 11
        val pos = Pos(me, op)
        val plays = MoveGen.legalPlays(pos, 6, 5)
        val lens = plays.map { it.moves.size }.toSet()
        println("larger-die case plays=${plays.size} lens=$lens dice=${plays.map { it.moves.map { m -> m.die } }}")
        // 6->1 blocked, 6->... with 5: 6->1 blocked. So only 13 can move. 13->7->2 blocked, 13->8->2 blocked. Single die only.
        check(lens == setOf(1), "only one die playable")
        check(plays.all { it.moves[0].die == 6 }, "must use larger die 6")
        val rules = MoveGen.turnRules(pos, 6, 5)
        check(rules.maxLen == 1 && rules.forcedDie == 6, "turn rules force 6")
    }

    // Bear-off: higher die from highest point only.
    run {
        val me = empty(); me[3] = 1; me[1] = 1; me[OFF] = 13
        val op = empty(); op[6] = 15
        val plays = MoveGen.legalPlays(Pos(me, op), 6, 5)
        check(plays.size == 1 && plays[0].result.me[OFF] == 15, "bear off both with 6-5")
        val me2 = empty(); me2[5] = 1; me2[2] = 1; me2[OFF] = 13
        val pl2 = MoveGen.legalPlays(Pos(me2, op.copyOf()), 6, 1)
        check(pl2.any { it.result.me[OFF] == 14 && it.result.me[1] == 1 }, "6 bears off from 5, 1 moves 2->1")
        // Cannot bear off with a checker outside home.
        val me3 = empty(); me3[7] = 1; me3[2] = 1; me3[OFF] = 13
        val pl3 = MoveGen.legalPlays(Pos(me3, op.copyOf()), 6, 2)
        check(pl3.all { it.moves[0].from == 7 || it.moves.any { m -> m.from == 7 } }, "must bring 7 in")
    }

    // Hitting.
    run {
        val me = empty(); me[13] = 15
        val op = empty(); op[25 - 10] = 1; op[6] = 14
        val plays = MoveGen.legalPlays(Pos(me, op), 3, 3)
        check(plays.any { it.result.op[BAR] == 1 }, "can hit blot on my 10")
    }

    // Human step validation equals full play set.
    val rnd = Random(1)
    repeat(300) {
        val pos = randomPosition(rnd)
        val a = rnd.nextInt(1, 7); val b = rnd.nextInt(1, 7)
        val plays = MoveGen.legalPlays(pos, a, b).map { it.result.key() }.toSet()
        val rules = MoveGen.turnRules(pos, a, b)
        val reached = HashSet<String>()
        fun dfs(cur: Pos, rem: List<Int>, made: Int) {
            val nxt = MoveGen.allowedNext(cur, rem, made, rules)
            if (made == rules.maxLen) { reached.add(cur.key()); return }
            check(nxt.isNotEmpty(), "dead end in allowedNext")
            for (m in nxt) {
                val r = rem.toMutableList(); r.remove(m.die)
                dfs(MoveGen.applied(cur, m), r, made + 1)
            }
        }
        dfs(pos, MoveGen.diceList(a, b), 0)
        check(reached == plays, "human step reachability == legal plays (dice $a-$b) ${reached.size} vs ${plays.size}")
    }
}

fun randomPosition(rnd: Random): Pos {
    // Play random moves from start to get varied positions.
    var p = Pos.startPos()
    val n = rnd.nextInt(0, 40)
    repeat(n) {
        val plays = MoveGen.legalPlays(p, rnd.nextInt(1, 7), rnd.nextInt(1, 7))
        val pl = plays[rnd.nextInt(plays.size)]
        p = pl.result.swapped()
        if (p.me[OFF] == 15 || p.op[OFF] == 15) return Pos.startPos()
    }
    return p
}

fun simulateMatches(levelA: Difficulty, levelB: Difficulty, games: Int, seed: Int): Pair<Int, Int> {
    val rnd = Random(seed)
    val a = Ai(levelA, Random(seed + 1))
    val b = Ai(levelB, Random(seed + 2))
    var winsA = 0; var winsB = 0
    repeat(games) {
        var p = Pos.startPos()
        var aToMove = rnd.nextBoolean()
        var turns = 0
        while (true) {
            val ai = if (aToMove) a else b
            val play = ai.choosePlay(p, rnd.nextInt(1, 7), rnd.nextInt(1, 7))
            val r = play.result
            check(r.me.sum() == 15 && r.op.sum() == 15, "checker count")
            if (r.me[OFF] == 15) { if (aToMove) winsA++ else winsB++; break }
            p = r.swapped(); aToMove = !aToMove
            turns++
            if (turns > 2000) { check(false, "game too long"); break }
        }
    }
    return winsA to winsB
}

fun sessionTests() {
    // Full session: human side plays via allowedNext randomly, AI via Ai; check scoring, save/load.
    val rnd = Random(42)
    repeat(30) { iter ->
        val target = listOf(0, 3, 5, 7)[iter % 4]
        var s = Session(Difficulty.values()[iter % 3], target)
        val ai = Ai(s.difficulty, Random(iter))
        var steps = 0
        while (steps < 20000) {
            steps++
            // Random save/load round trip.
            if (rnd.nextInt(10) == 0) {
                val saved = s.toSave()
                val loaded = Session.fromSave(saved)
                check(loaded != null, "load ok")
                check(loaded!!.toSave() == saved, "save roundtrip stable")
                s = loaded
            }
            when (s.phase) {
                Phase.OPENING -> { while (!s.resolveOpening(rnd.nextInt(1, 7), rnd.nextInt(1, 7))) {} }
                Phase.HUMAN_PRE_ROLL -> {
                    if (s.canHumanDouble() && rnd.nextInt(8) == 0) s.humanDoubles()
                    else s.humanRoll(rnd.nextInt(1, 7), rnd.nextInt(1, 7))
                }
                Phase.HUMAN_DOUBLED -> s.aiAnswersDouble(s.aiShouldTake(ai))
                Phase.HUMAN_MOVE -> {
                    if (s.humanTurnComplete()) {
                        if (rnd.nextInt(6) == 0 && s.made.isNotEmpty()) s.humanUndoStep() else s.commitHumanTurn()
                    } else {
                        val allowed = s.humanAllowed()
                        check(allowed.isNotEmpty(), "human has allowed moves when incomplete")
                        s.humanMove(allowed[rnd.nextInt(allowed.size)])
                    }
                }
                Phase.AI_TURN -> {
                    if (s.d1 == 0) {
                        if (s.aiConsidersDouble(ai)) continue
                        s.aiRoll(rnd.nextInt(1, 7), rnd.nextInt(1, 7))
                    }
                    val play = ai.choosePlay(s.aiPos(), s.d1, s.d2)
                    for (m in play.moves) s.aiApply(m)
                    check(s.aiPos().key() == play.result.key(), "sub-move replay matches play result")
                    s.finishAiTurn()
                }
                Phase.AI_DOUBLED -> s.humanAnswersDouble(rnd.nextBoolean())
                Phase.GAME_OVER -> {
                    val r = s.lastResult!!
                    if (s.matchOver) {
                        if (target > 0) check(s.scoreHuman >= target || s.scoreAi >= target, "match over has winner")
                        break
                    }
                    check(target > 0, "single game ends match")
                    s.nextGame()
                    if (s.isCrawfordGame) check(!s.cubeAvailableTo(CubeOwner.HUMAN), "no cube in Crawford")
                }
            }
            check(s.human.sum() == 15 && s.ai.sum() == 15, "15 each")
        }
        println("session $iter target=$target final ${s.scoreHuman}-${s.scoreAi} games=${s.gameNumber} cube=${s.lastResult?.cube} type=${s.lastResult?.type}")
    }
}

fun statsTests() {
    val st = Stats()
    st.recordGame(Difficulty.HARD, GameResult(true, WinType.GAMMON, 2, 4, false, true), 5, 5, 2)
    st.recordGame(Difficulty.EASY, GameResult(false, WinType.SINGLE, 1, 1, true, false), 0, 0, 1)
    val back = Stats.fromSave(st.toSave())
    check(back.toSave() == st.toSave(), "stats roundtrip")
    check(back.levels[Difficulty.HARD]!!.gammonsWon == 1 && back.levels[Difficulty.HARD]!!.matchesWon == 1, "stats values")
    check(back.streak == -1 && back.bestStreak == 1, "streaks")
}

fun main() {
    rulesTests()
    statsTests()
    sessionTests()
    val t0 = System.currentTimeMillis()
    // Timing for HARD move choice
    val rnd = Random(9)
    val hard = Ai(Difficulty.HARD, Random(3))
    var worst = 0L
    repeat(60) {
        val p = randomPosition(rnd)
        val s = System.nanoTime()
        hard.choosePlay(p, rnd.nextInt(1, 7), rnd.nextInt(1, 7))
        worst = maxOf(worst, (System.nanoTime() - s) / 1_000_000)
    }
    println("HARD: 60 decisions in ${System.currentTimeMillis() - t0} ms, worst $worst ms")
    val n = (System.getenv("SIMS") ?: "200").toInt()
    println("Medium vs Easy: ${simulateMatches(Difficulty.MEDIUM, Difficulty.EASY, n, 1)}")
    println("Hard vs Medium: ${simulateMatches(Difficulty.HARD, Difficulty.MEDIUM, n / 2, 2)}")
    println(if (failures == 0) "ALL TESTS PASSED" else "$failures FAILURES")
}
