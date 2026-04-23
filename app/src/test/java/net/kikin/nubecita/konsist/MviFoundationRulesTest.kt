package net.kikin.nubecita.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withParentOf
import com.lemonappdev.konsist.api.verify.assertTrue
import net.kikin.nubecita.ui.mvi.UiEffect
import net.kikin.nubecita.ui.mvi.UiEvent
import net.kikin.nubecita.ui.mvi.UiState
import org.junit.Test

// These rules target DIRECT implementers of the three marker interfaces. Terminal data classes
// or data objects nested inside sealed hierarchies (e.g. `MainScreenEffect.ShowError`) are
// deliberately not checked here — their validity comes from the sealed root, not from re-asserting
// their own shape.
class MviFoundationRulesTest {
    @Test
    fun `classes directly implementing UiState are data classes`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withParentOf(UiState::class)
            .assertTrue { it.hasDataModifier }
    }

    @Test
    fun `types directly implementing UiEvent are sealed`() {
        Konsist
            .scopeFromProduction()
            .classesAndInterfaces()
            .withParentOf(UiEvent::class)
            .assertTrue { it.hasSealedModifier }
    }

    @Test
    fun `types directly implementing UiEffect are sealed`() {
        Konsist
            .scopeFromProduction()
            .classesAndInterfaces()
            .withParentOf(UiEffect::class)
            .assertTrue { it.hasSealedModifier }
    }
}
