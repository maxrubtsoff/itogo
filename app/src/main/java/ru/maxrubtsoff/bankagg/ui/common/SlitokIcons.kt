package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/** Icons Material doesn't have, drawn in its 24×24 grid. */
object SlitokIcons {

    /** Two fingertips and an arrow down — the "hide amounts" swipe (B-13). */
    val TwoFingerSwipeDown: ImageVector by lazy {
        materialIcon(name = "Slitok.TwoFingerSwipeDown") {
            materialPath {
                moveTo(8f, 2.5f)
                arcToRelative(2.5f, 2.5f, 0f, true, true, 0f, 5f)
                arcToRelative(2.5f, 2.5f, 0f, true, true, 0f, -5f)
                close()
                moveTo(16f, 2.5f)
                arcToRelative(2.5f, 2.5f, 0f, true, true, 0f, 5f)
                arcToRelative(2.5f, 2.5f, 0f, true, true, 0f, -5f)
                close()
                moveTo(11f, 9f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(7.2f)
                lineToRelative(2.6f, -2.6f)
                lineToRelative(1.4f, 1.4f)
                lineTo(12f, 20f)
                lineToRelative(-5f, -5f)
                lineToRelative(1.4f, -1.4f)
                lineToRelative(2.6f, 2.6f)
                close()
            }
        }
    }
}
