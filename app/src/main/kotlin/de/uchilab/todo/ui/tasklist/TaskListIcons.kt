package de.uchilab.todo.ui.tasklist

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Own plain signs (no material-icons dependency); purely decorative, the tint comes from the caller. */
internal object TaskListIcons {

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 2 * r, dy1 = 0f)
        arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -2 * r, dy1 = 0f)
        close()
    }

    private fun PathBuilder.rect(left: Float, top: Float, width: Float, height: Float) {
        moveTo(left, top)
        horizontalLineToRelative(width)
        verticalLineToRelative(height)
        horizontalLineToRelative(-width)
        close()
    }

    /** A sheet with lines. */
    val Sheet: ImageVector by lazy {
        ImageVector.Builder("TaskSheet", 64.dp, 64.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Unspecified), pathFillType = PathFillType.EvenOdd) {
                rect(5f, 3f, 14f, 18f)
                rect(6.5f, 4.5f, 11f, 15f)
            }
            path(fill = SolidColor(Color.Unspecified)) {
                rect(9f, 8f, 6f, 1.5f)
                rect(9f, 11f, 6f, 1.5f)
                rect(9f, 14f, 6f, 1.5f)
            }
        }.build()
    }

    /** A circle with an exclamation mark. */
    val Alert: ImageVector by lazy {
        ImageVector.Builder("TaskAlert", 64.dp, 64.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Unspecified), pathFillType = PathFillType.EvenOdd) {
                circle(12f, 12f, 10f)
                circle(12f, 12f, 8.5f)
            }
            path(fill = SolidColor(Color.Unspecified)) {
                rect(11f, 6.5f, 2f, 7f)
                circle(12f, 16.5f, 1.25f)
            }
        }.build()
    }
}
