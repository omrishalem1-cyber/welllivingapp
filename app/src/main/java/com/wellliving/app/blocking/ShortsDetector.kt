package com.wellliving.app.blocking

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Recognises the YouTube Shorts player by the view ids YouTube uses for it.
 *
 * This is the fragile part of the app (architecture.md, section 4): YouTube may rename these ids
 * in any update, and then Shorts stop being blocked until the list below is updated.
 */
object ShortsDetector {

    private val SHORTS_VIEW_IDS = listOf(
        "com.google.android.youtube:id/reel_recycler",
        "com.google.android.youtube:id/reel_player_page_container",
        "com.google.android.youtube:id/reel_watch_player",
        "com.google.android.youtube:id/shorts_player",
    )

    fun isShowingShorts(root: AccessibilityNodeInfo): Boolean =
        SHORTS_VIEW_IDS.any { id ->
            root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser }
        }
}
