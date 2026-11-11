package com.alexanderlingle.berry.ai

/**
 * Deterministic month-by-month milestone content, bundled as local data.
 *
 * This is the ONLY source of milestone facts the assistant is allowed to
 * state. The model personalizes the wording; it never invents milestones,
 * sleep totals, or wake windows. The wake-window ranges here deliberately
 * match [com.alexanderlingle.berry.data.PatternEngine.wakeWindowBounds]
 * so the assistant and the v2 predictions never contradict each other.
 *
 * General information only — not medical advice. The UI says so on every
 * answer, and the prompt tells the model to defer health concerns to a
 * pediatrician.
 */
data class MilestoneBand(
    /** Inclusive age range in months this band covers. */
    val minMonths: Int,
    val maxMonths: Int,
    val label: String,
    val sleep: String,
    val feeding: String,
    val development: String
)

object Milestones {
    private val bands = listOf(
        MilestoneBand(
            0, 1, "0–1 month",
            "About 14–17 hours of sleep a day, in short stretches around the clock.",
            "Feeds every 2–3 hours, day and night; wake windows are short, about 35–60 minutes.",
            "Newborn reflexes, brief alert periods, beginning to focus on faces close by."
        ),
        MilestoneBand(
            2, 3, "2–3 months",
            "Around 15 hours of sleep a day; nights slowly start to stretch out.",
            "Feeds still frequent; wake windows grow to roughly 45–105 minutes.",
            "Social smiles, cooing, tracking moving objects, stronger head control."
        ),
        MilestoneBand(
            4, 5, "4–5 months",
            "Usually 3–4 naps a day; wake windows roughly 75–120 minutes.",
            "Feeding intervals stretch a little; many babies are ready to explore tastes near 6 months — ask a pediatrician first.",
            "Rolling over, reaching and grasping, lots of babbling."
        ),
        MilestoneBand(
            6, 8, "6–8 months",
            "Usually 2–3 naps a day; wake windows roughly 2–2.5 hours.",
            "Solids typically start around 6 months alongside milk feeds; textures progress gradually.",
            "Sitting up, babbling with consonants, passing toys hand to hand."
        ),
        MilestoneBand(
            9, 11, "9–11 months",
            "Usually 2 naps a day; wake windows roughly 2.5–3.5 hours.",
            "Three small meals plus milk; finger foods and self-feeding practice.",
            "Crawling, pulling to stand, first words, waving and pointing."
        ),
        MilestoneBand(
            12, 17, "12–17 months",
            "Moving toward 1–2 naps a day; wake windows roughly 3–4.5 hours.",
            "Meals with the family plus milk; cups replace bottles for most toddlers.",
            "First steps, first words, pointing to things they want, simple routines."
        ),
        MilestoneBand(
            18, 24, "18–24 months",
            "Usually 1 nap a day; wake windows roughly 4–6 hours.",
            "Three meals and snacks; growing independence at the table.",
            "Vocabulary burst, two-word phrases, running, stacking, pretend play."
        )
    )

    /** The band for [ageMonths], clamped to the first/last band outside 0–24. */
    fun forAgeMonths(ageMonths: Int): MilestoneBand = when {
        ageMonths < 0 -> bands.first()
        ageMonths > 24 -> bands.last()
        else -> bands.first { ageMonths in it.minMonths..it.maxMonths }
    }
}
