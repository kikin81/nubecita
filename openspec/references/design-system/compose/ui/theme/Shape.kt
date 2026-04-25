// ============================================================
// Nubecita — Shape scale
// M3 Expressive is rounder than M3 base; buttons use 999dp pill.
// ============================================================
package app.nubecita.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val NubecitaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object NubecitaShape {
    val Button     = RoundedCornerShape(percent = 50)   // pill
    val Card       = RoundedCornerShape(16.dp)
    val CardLarge  = RoundedCornerShape(28.dp)
    val FAB        = RoundedCornerShape(16.dp)
    val Chip       = RoundedCornerShape(8.dp)
    val SheetTop   = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}
