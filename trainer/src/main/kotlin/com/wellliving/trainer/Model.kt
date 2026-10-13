package com.wellliving.trainer

/** A kind of focused work the user trains separately, e.g. reading an article. */
data class Activity(
    val id: String,
    val name: String,
    val archived: Boolean = false,
)

/** One finished deep-work session. Sessions shorter than [MIN_COUNTED_SEC] are never stored. */
data class Session(
    val id: String,
    val activityId: String,
    val startMs: Long,
    val targetMin: Int,
    val actualSec: Int,
    val completed: Boolean,
    val distractions: Int,
    val focusRating: Int? = null,
)

/** Sessions abandoned before this many seconds are treated as false starts and discarded. */
const val MIN_COUNTED_SEC = 120

val DEFAULT_ACTIVITIES = listOf(
    "קריאת מאמר",
    "קריאת ספר",
    "כתיבת עבודה",
    "כתיבת דוח תהליכי",
    "בניית הרצאה",
    "ניתוח ראיונות",
)
