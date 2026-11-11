package com.alexanderlingle.berry.ui.components

import com.alexanderlingle.berry.data.DiaperType
import com.alexanderlingle.berry.data.FeedingType
import com.alexanderlingle.berry.data.SleepType

fun SleepType.label(): String = when (this) {
    SleepType.NAP -> "Nap"
    SleepType.NIGHT -> "Night sleep"
}

fun FeedingType.label(): String = when (this) {
    FeedingType.NURSING_LEFT -> "Left breast"
    FeedingType.NURSING_RIGHT -> "Right breast"
    FeedingType.BOTTLE -> "Bottle"
}

fun DiaperType.label(): String = when (this) {
    DiaperType.WET -> "Wet"
    DiaperType.DIRTY -> "Dirty"
    DiaperType.MIXED -> "Mixed"
}
