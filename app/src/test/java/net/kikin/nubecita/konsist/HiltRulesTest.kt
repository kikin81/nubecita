package net.kikin.nubecita.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withAnnotationOf
import com.lemonappdev.konsist.api.verify.assertTrue
import dagger.Module
import dagger.hilt.InstallIn
import org.junit.Test

class HiltRulesTest {
    // Objects don't need an explicit abstract-modifier check — Kotlin doesn't allow abstract objects,
    // and object-as-module is valid for @Provides-only Hilt modules.
    @Test
    fun `@Module classes (not objects) must be abstract`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withAnnotationOf(Module::class)
            .assertTrue { it.hasAbstractModifier }
    }

    @Test
    fun `@Module class declarations are annotated with @InstallIn`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withAnnotationOf(Module::class)
            .assertTrue { it.hasAnnotationOf(InstallIn::class) }
    }

    @Test
    fun `@Module object declarations are annotated with @InstallIn`() {
        Konsist
            .scopeFromProduction()
            .objects()
            .withAnnotationOf(Module::class)
            .assertTrue { it.hasAnnotationOf(InstallIn::class) }
    }
}
