package net.kikin.nubecita.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withParentOf
import com.lemonappdev.konsist.api.verify.assertTrue
import net.kikin.nubecita.ui.mvi.UiEffect
import net.kikin.nubecita.ui.mvi.UiEvent
import net.kikin.nubecita.ui.mvi.UiState
import org.junit.Test

// These rules target DIRECT implementers of the three marker interfaces. Terminal data
// classes or data objects nested inside sealed hierarchies (e.g. `MainScreenEffect.ShowError`,
// `MainScreenEvent.Refresh`) are not checked here — their validity comes from the sealed
// root, not from re-asserting their own shape.
//
// Scans include classes, interfaces, AND objects so an ill-advised direct implementer
// like `data object FooState : UiState` can't slip past the convention by being an object.
class MviFoundationRulesTest {
    @Test
    fun `classes directly implementing UiState are data classes`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withParentOf(UiState::class)
            .assertTrue { it.hasDataModifier }
    }

    // `data object` is not valid as a UiState — `copy()` isn't generated for data objects,
    // so `setState { copy(...) }` reducers in MviViewModel would not compile.
    @Test
    fun `no objects directly implement UiState`() {
        Konsist
            .scopeFromProduction()
            .objects()
            .withParentOf(UiState::class)
            .assertTrue { false }
    }

    // UiState is already a marker interface — a second-level marker adds indirection without
    // payoff. Screens should implement UiState directly via a data class.
    @Test
    fun `no interfaces directly implement UiState`() {
        Konsist
            .scopeFromProduction()
            .interfaces()
            .withParentOf(UiState::class)
            .assertTrue { false }
    }

    @Test
    fun `types directly implementing UiEvent are sealed`() {
        Konsist
            .scopeFromProduction()
            .classesAndInterfaces()
            .withParentOf(UiEvent::class)
            .assertTrue { it.hasSealedModifier }
    }

    // `sealed object` isn't valid in Kotlin; an object as a direct UiEvent implementer can
    // never satisfy the "sealed root" convention. Reject outright so the pattern is caught
    // before it propagates.
    @Test
    fun `no objects directly implement UiEvent`() {
        Konsist
            .scopeFromProduction()
            .objects()
            .withParentOf(UiEvent::class)
            .assertTrue { false }
    }

    @Test
    fun `types directly implementing UiEffect are sealed`() {
        Konsist
            .scopeFromProduction()
            .classesAndInterfaces()
            .withParentOf(UiEffect::class)
            .assertTrue { it.hasSealedModifier }
    }

    @Test
    fun `no objects directly implement UiEffect`() {
        Konsist
            .scopeFromProduction()
            .objects()
            .withParentOf(UiEffect::class)
            .assertTrue { false }
    }
}
