package net.kikin.nubecita.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withAnnotationOf
import com.lemonappdev.konsist.api.verify.assertTrue
import dagger.Module
import dagger.hilt.InstallIn
import org.junit.Test

class HiltRulesTest {
    // Only applies to `class` declarations. Objects and interfaces are already abstract by
    // construction (Kotlin objects are singletons; interfaces are implicitly abstract), so they
    // don't need this check.
    @Test
    fun `@Module classes (not objects or interfaces) must be abstract`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withAnnotationOf(Module::class)
            .assertTrue { it.hasAbstractModifier }
    }

    // Dagger allows `@Module` on classes, abstract classes, objects, AND interfaces (the last for
    // @Binds-only modules). Scan all three so the "every @Module has @InstallIn" guard can't be
    // bypassed by an interface-based module slipping in.
    @Test
    fun `all @Module declarations are annotated with @InstallIn`() {
        Konsist
            .scopeFromProduction()
            .classesAndInterfacesAndObjects()
            .withAnnotationOf(Module::class)
            .assertTrue { it.hasAnnotationOf(InstallIn::class) }
    }
}
