package com.simulens.app.domain.bsmas

enum class BsmasItem(val label: String) {
    SALIENCE("Preoccupation"), TOLERANCE("Needing more"), MOOD("Escape"),
    RELAPSE("Failed cutting down"), WITHDRAWAL("Restlessness"), CONFLICT("Study impact")
}

enum class BsmasBand { LOWER, MODERATE, ELEVATED }

data class BsmasResult(
    val items: List<Int>,          // 6 scores, 1..5, in BsmasItem order
    val total: Int,                // 6..30
    val band: BsmasBand,
    val meetsPolythetic: Boolean,  // 3+ on at least 4 of 6 items
    val nudgeMultiplier: Double
) {
    fun score(item: BsmasItem) = items[item.ordinal]
}

object BsmasScoring {
    // Placeholder cut-offs. Confirm against the source your project cites.
    fun score(answers: List<Int>): BsmasResult? {
        if (answers.size != 6 || answers.any { it !in 1..5 }) return null
        val total = answers.sum()
        val band = when {
            total <= 12 -> BsmasBand.LOWER
            total <= 18 -> BsmasBand.MODERATE
            else -> BsmasBand.ELEVATED
        }
        return BsmasResult(
            items = answers,
            total = total,
            band = band,
            meetsPolythetic = answers.count { it >= 3 } >= 4,
            nudgeMultiplier = when (band) {
                BsmasBand.LOWER -> 2.0
                BsmasBand.MODERATE -> 1.75
                BsmasBand.ELEVATED -> 1.5
            }
        )
    }
}