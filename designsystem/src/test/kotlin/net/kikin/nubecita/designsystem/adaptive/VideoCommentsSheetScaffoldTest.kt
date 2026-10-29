@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package net.kikin.nubecita.designsystem.adaptive

import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class VideoCommentsSheetScaffoldTest {
    @Test
    fun `isSettledHidden returns true only when currentValue is Hidden and not animating`() {
        val sheetState = mockk<SheetState>()

        every { sheetState.currentValue } returns SheetValue.Hidden
        every { sheetState.isAnimationRunning } returns false
        assertTrue(sheetState.isSettledHidden)

        every { sheetState.currentValue } returns SheetValue.Hidden
        every { sheetState.isAnimationRunning } returns true
        assertFalse(sheetState.isSettledHidden)

        every { sheetState.currentValue } returns SheetValue.PartiallyExpanded
        every { sheetState.isAnimationRunning } returns false
        assertFalse(sheetState.isSettledHidden)

        every { sheetState.currentValue } returns SheetValue.Expanded
        every { sheetState.isAnimationRunning } returns false
        assertFalse(sheetState.isSettledHidden)
    }

    @Test
    fun `isCommentsSheetSettledHidden in compact returns true when comments are closed`() {
        val sheetState = mockk<SheetState>()
        val result =
            isCommentsSheetSettledHidden(
                isCompact = true,
                isCommentsOpen = false,
                sheetState = sheetState,
            )
        assertTrue(result)
    }

    @Test
    fun `isCommentsSheetSettledHidden in compact gates on sheet settled hidden when open`() {
        val sheetState = mockk<SheetState>()

        // Open and animating towards hidden -> false
        every { sheetState.currentValue } returns SheetValue.Hidden
        every { sheetState.isAnimationRunning } returns true
        assertFalse(
            isCommentsSheetSettledHidden(
                isCompact = true,
                isCommentsOpen = true,
                sheetState = sheetState,
            ),
        )

        // Open and settled hidden -> true
        every { sheetState.currentValue } returns SheetValue.Hidden
        every { sheetState.isAnimationRunning } returns false
        assertTrue(
            isCommentsSheetSettledHidden(
                isCompact = true,
                isCommentsOpen = true,
                sheetState = sheetState,
            ),
        )

        // Open and partially expanded -> false
        every { sheetState.currentValue } returns SheetValue.PartiallyExpanded
        every { sheetState.isAnimationRunning } returns false
        assertFalse(
            isCommentsSheetSettledHidden(
                isCompact = true,
                isCommentsOpen = true,
                sheetState = sheetState,
            ),
        )
    }

    @Test
    fun `isCommentsSheetSettledHidden in medium or expanded gates directly on isCommentsOpen`() {
        val sheetState = mockk<SheetState>()

        assertTrue(
            isCommentsSheetSettledHidden(
                isCompact = false,
                isCommentsOpen = false,
                sheetState = sheetState,
            ),
        )

        assertFalse(
            isCommentsSheetSettledHidden(
                isCompact = false,
                isCommentsOpen = true,
                sheetState = sheetState,
            ),
        )
    }
}
