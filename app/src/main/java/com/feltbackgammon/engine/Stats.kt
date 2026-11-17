package com.feltbackgammon.engine

class LevelStats {
    var games = 0
    var wins = 0
    var gammonsWon = 0
    var gammonsLost = 0
    var backgammonsWon = 0
    var backgammonsLost = 0
    var pointsFor = 0
    var pointsAgainst = 0
    var matchesPlayed = 0
    var matchesWon = 0
    var doublesOffered = 0
    var doublesPassedByAi = 0

    val losses get() = games - wins
    val winPct get() = if (games == 0) 0.0 else wins * 100.0 / games

    fun add(o: LevelStats) {
        games += o.games; wins += o.wins
        gammonsWon += o.gammonsWon; gammonsLost += o.gammonsLost
        backgammonsWon += o.backgammonsWon; backgammonsLost += o.backgammonsLost
        pointsFor += o.pointsFor; pointsAgainst += o.pointsAgainst
        matchesPlayed += o.matchesPlayed; matchesWon += o.matchesWon
        doublesOffered += o.doublesOffered; doublesPassedByAi += o.doublesPassedByAi
    }

    internal fun fields() = intArrayOf(
        games, wins, gammonsWon, gammonsLost, backgammonsWon, backgammonsLost,
        pointsFor, pointsAgainst, matchesPlayed, matchesWon, doublesOffered, doublesPassedByAi
    )

    internal fun load(f: List<Int>) {
        games = f.getOrElse(0) { 0 }; wins = f.getOrElse(1) { 0 }
        gammonsWon = f.getOrElse(2) { 0 }; gammonsLost = f.getOrElse(3) { 0 }
        backgammonsWon = f.getOrElse(4) { 0 }; backgammonsLost = f.getOrElse(5) { 0 }
        pointsFor = f.getOrElse(6) { 0 }; pointsAgainst = f.getOrElse(7) { 0 }
        matchesPlayed = f.getOrElse(8) { 0 }; matchesWon = f.getOrElse(9) { 0 }
        doublesOffered = f.getOrElse(10) { 0 }; doublesPassedByAi = f.getOrElse(11) { 0 }
    }
}

/** Lifetime player statistics, kept per difficulty level. */
class Stats {
    val levels: Map<Difficulty, LevelStats> = Difficulty.entries.associateWith { LevelStats() }
    /** Positive = current winning streak, negative = losing streak. */
    var streak = 0
    var bestStreak = 0
    var totalRolls = 0
    var doublets = 0

    fun total(): LevelStats = LevelStats().also { t -> levels.values.forEach { t.add(it) } }

    fun recordGame(level: Difficulty, r: GameResult, matchTarget: Int, humanScore: Int, aiScore: Int) {
        val s = levels.getValue(level)
        s.games++
        if (r.humanWon) {
            s.wins++
            s.pointsFor += r.points
            if (!r.byPass && r.type == WinType.GAMMON) s.gammonsWon++
            if (!r.byPass && r.type == WinType.BACKGAMMON) s.backgammonsWon++
            streak = if (streak >= 0) streak + 1 else 1
            if (streak > bestStreak) bestStreak = streak
        } else {
            s.pointsAgainst += r.points
            if (!r.byPass && r.type == WinType.GAMMON) s.gammonsLost++
            if (!r.byPass && r.type == WinType.BACKGAMMON) s.backgammonsLost++
            streak = if (streak <= 0) streak - 1 else -1
        }
        if (matchTarget > 0 && r.matchOver) {
            s.matchesPlayed++
            if (humanScore > aiScore) s.matchesWon++
        }
    }

    fun recordDouble(level: Difficulty, aiPassed: Boolean) {
        val s = levels.getValue(level)
        s.doublesOffered++
        if (aiPassed) s.doublesPassedByAi++
    }

    fun recordRoll(a: Int, b: Int) {
        totalRolls++
        if (a == b) doublets++
    }

    fun toSave(): String = buildString {
        appendLine("v=1")
        for ((d, s) in levels) appendLine("${d.name}=${s.fields().joinToString(",")}")
        appendLine("streak=$streak")
        appendLine("best=$bestStreak")
        appendLine("rolls=$totalRolls")
        appendLine("doublets=$doublets")
    }

    companion object {
        fun fromSave(s: String?): Stats {
            val st = Stats()
            if (s.isNullOrBlank()) return st
            try {
                val m = s.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
                for (d in Difficulty.entries) {
                    m[d.name]?.let { v -> st.levels.getValue(d).load(v.split(",").map { it.trim().toInt() }) }
                }
                st.streak = m["streak"]?.toInt() ?: 0
                st.bestStreak = m["best"]?.toInt() ?: 0
                st.totalRolls = m["rolls"]?.toInt() ?: 0
                st.doublets = m["doublets"]?.toInt() ?: 0
            } catch (_: Exception) {
            }
            return st
        }
    }
}
