package net.kikin.nubecita.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.Test

class ComposableRulesTest {
    /**
     * Forbid the non-Hilt `viewModel()` Compose extension so ViewModels are always
     * resolved through the Hilt graph. Consumers must use one of the Hilt-provided
     * `hiltViewModel()` entry points (either `androidx.hilt.navigation.compose` or
     * `androidx.hilt.lifecycle.viewmodel.compose`).
     */
    @Test
    fun `production code does not import the non-Hilt 'viewModel()' Compose extension`() {
        Konsist
            .scopeFromProduction()
            .files
            .assertFalse { file ->
                file.hasImport { it.name == "androidx.lifecycle.viewmodel.compose.viewModel" }
            }
    }
}
