package com.alexanderlingle.berry.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.alexanderlingle.berry.data.Confidence
import com.alexanderlingle.berry.data.PatternEngine
import com.alexanderlingle.berry.data.Prediction
import com.alexanderlingle.berry.data.PredictionKind

/**
 * Picks the single prediction worth showing on the dashboard: the nearest
 * upcoming one, or one that's only recently overdue. Null when there is
 * nothing fresh to say — the dashboard then skips the banner item.
 */
fun nextUp(predictions: List<Prediction>, now: Long): Prediction? =
    predictions
        .filter { it.expectedAtMillis > now - 45 * 60_000L }
        .minByOrNull { it.expectedAtMillis }

private fun Prediction.icon(): ImageVector = when (kind) {
    PredictionKind.NAP -> Icons.Filled.Bedtime
    PredictionKind.FEED -> Icons.Filled.Restaurant
    PredictionKind.DIAPER -> Icons.Filled.BabyChangingStation
}

private fun Prediction.kindLabel(): String = when (kind) {
    PredictionKind.NAP -> "Nap"
    PredictionKind.FEED -> "Feeding"
    PredictionKind.DIAPER -> "Diaper change"
}

/**
 * "Next up" prediction banner for the dashboard, e.g.
 * "Feeding usually due in ~20 min" / "Nap was due ~10 min ago".
 * Renders nothing when [prediction] is null.
 */
@Composable
fun NextUpBanner(prediction: Prediction?, modifier: Modifier = Modifier) {
    if (prediction == null) return
    val now = System.currentTimeMillis()
    val rel = PatternEngine.relativeText(prediction.expectedAtMillis, now)
    val headline = if (prediction.expectedAtMillis >= now) {
        "${prediction.kindLabel()} usually due $rel"
    } else {
        "${prediction.kindLabel()} was due $rel"
    }
    val sub = when (prediction.confidence) {
        Confidence.LOW -> "Still learning — keep logging"
        else -> "Learned from ${prediction.basedOnDays} days of logs"
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = prediction.icon(),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
        }
    }
}
