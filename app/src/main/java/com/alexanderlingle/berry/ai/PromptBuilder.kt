package com.alexanderlingle.berry.ai

import com.alexanderlingle.berry.data.Confidence
import com.alexanderlingle.berry.data.PatternEngine
import com.alexanderlingle.berry.data.Prediction
import com.alexanderlingle.berry.data.PredictionKind
import com.alexanderlingle.berry.util.TimeUtils

/**
 * Berry v3 prompt construction.
 *
 * Hard rule: RAW LOG ENTRIES NEVER REACH THE MODEL. The only inputs are
 * the v2 PatternEngine's aggregated facts (median intervals, confidence
 * tiers, next-due times), today's totals, and the bundled milestone band.
 * This keeps prompts to a few hundred tokens, keeps baby data summarized
 * rather than enumerated, and keeps the model inside its competence zone:
 * personalizing short tips, not reasoning over raw timelines.
 */
data class AssistantContext(
    val childName: String,
    /** Completed months of age, null when no birthdate is set. */
    val ageMonths: Int?,
    val milestoneBand: MilestoneBand?,
    /** One line per v2 prediction, e.g. "Nap usually due in ~20 min (medium confidence, learned from 6 days)". */
    val predictionLines: List<String>,
    val todaySleepText: String,
    val todayFeedCount: Int,
    val todayDiaperCount: Int,
    /** True when the pattern engine had enough history for at least one prediction. */
    val hasPatterns: Boolean
)

object PromptBuilder {

    fun buildContext(
        childName: String,
        birthdateMillis: Long?,
        predictions: List<Prediction>,
        todaySleepMillis: Long,
        todayFeedCount: Int,
        todayDiaperCount: Int,
        now: Long
    ): AssistantContext {
        val ageMonths = birthdateMillis?.let {
            val birth = TimeUtils.zoned(it).toLocalDate()
            val today = TimeUtils.zoned(now).toLocalDate()
            java.time.Period.between(birth, today).let { p ->
                (p.years * 12 + p.months).coerceAtLeast(0)
            }
        }
        return AssistantContext(
            childName = childName,
            ageMonths = ageMonths,
            milestoneBand = ageMonths?.let { Milestones.forAgeMonths(it) },
            predictionLines = predictions.map { lineFor(it, now) },
            todaySleepText = TimeUtils.formatDurationShort(todaySleepMillis),
            todayFeedCount = todayFeedCount,
            todayDiaperCount = todayDiaperCount,
            hasPatterns = predictions.isNotEmpty()
        )
    }

    private fun lineFor(prediction: Prediction, now: Long): String {
        val what = when (prediction.kind) {
            PredictionKind.NAP -> "Nap"
            PredictionKind.FEED -> "Feed"
            PredictionKind.DIAPER -> "Diaper change"
        }
        val confidence = when (prediction.confidence) {
            Confidence.HIGH -> "high"
            Confidence.MEDIUM -> "medium"
            Confidence.LOW -> "low"
        }
        return "$what usually due ${PatternEngine.relativeText(prediction.expectedAtMillis, now)} " +
            "($confidence confidence, learned from ${prediction.basedOnDays} days of logs)"
    }

    /** Suggested questions, shaped by what the pattern engine actually knows. */
    fun suggestedQuestions(context: AssistantContext): List<String> {
        val out = mutableListOf<String>()
        if (context.ageMonths != null) {
            out += "What should I expect this month?"
        }
        if (context.predictionLines.any { it.startsWith("Nap") }) {
            out += "When is the next nap usually due?"
        }
        if (context.predictionLines.any { it.startsWith("Feed") }) {
            out += "How are feeding gaps looking lately?"
        }
        out += "Any sleep tips for this age?"
        if (!context.hasPatterns) {
            out += "We don't have much logged yet — what should I track first?"
        }
        return out.distinct().take(4)
    }

    /**
     * One short prompt: system rules + facts + the parent's question.
     * Answers are capped at ~200 tokens by [AiConfig.MAX_TOKENS], so the
     * prompt itself stays well under 1K tokens end to end.
     */
    fun buildPrompt(context: AssistantContext, question: String): String {
        val facts = StringBuilder()
        facts.appendLine("Child: ${context.childName}.")
        if (context.ageMonths != null && context.milestoneBand != null) {
            val band = context.milestoneBand
            facts.appendLine("Age: ${context.ageMonths} months (milestone band: ${band.label}).")
            facts.appendLine("Typical for this band — sleep: ${band.sleep}")
            facts.appendLine("Typical for this band — feeding: ${band.feeding}")
            facts.appendLine("Typical for this band — development: ${band.development}")
        } else {
            facts.appendLine("Age: not set (no birthdate on the profile). Do not guess an age.")
        }
        facts.appendLine(
            "Today so far: ${context.todaySleepText} asleep, " +
                "${context.todayFeedCount} feeds, ${context.todayDiaperCount} diapers."
        )
        if (context.predictionLines.isNotEmpty()) {
            facts.appendLine("Learned patterns:")
            context.predictionLines.forEach { facts.appendLine("- $it") }
        } else {
            facts.appendLine(
                "No learned patterns yet: not enough logged days. " +
                    "Say so plainly instead of inventing a routine."
            )
        }
        return """
            You are Berry, a warm, concise baby-care assistant. Answer in 2–4 short sentences, under 150 words.
            Use ONLY the facts below. Do not invent numbers, milestones, or routines. If a fact is missing, say so.
            This is general information, not medical advice; for any health concern, tell the parent to contact their pediatrician.
            Answer directly with no reasoning trace and no preamble.

            FACTS:
            $facts
            PARENT'S QUESTION: $question
        """.trimIndent()
    }
}
