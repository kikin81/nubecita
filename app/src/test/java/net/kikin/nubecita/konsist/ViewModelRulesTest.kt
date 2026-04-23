package net.kikin.nubecita.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.modifierprovider.withoutAbstractModifier
import com.lemonappdev.konsist.api.ext.list.properties
import com.lemonappdev.konsist.api.ext.list.withAnnotationOf
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import dagger.hilt.android.lifecycle.HiltViewModel
import org.junit.Test
import javax.inject.Inject

// Note: parent-class filters use name-based matching (`parent.name == "ViewModel"`) rather than
// `KClass`-based lookups. Konsist's `scopeFromProduction()` only parses source files in the scan
// scope, so external types like `androidx.lifecycle.ViewModel` don't resolve reliably through
// `hasParentClassOf(ViewModel::class, ...)`. Name-matching sidesteps that.
class ViewModelRulesTest {
    @Test
    fun `classes with 'ViewModel' suffix extend a ViewModel (directly or transitively)`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withNameEndingWith("ViewModel")
            .assertTrue { c -> c.parents(indirectParents = true).any { it.name == "ViewModel" } }
    }

    @Test
    fun `concrete ViewModel subclasses are annotated with @HiltViewModel`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withoutAbstractModifier()
            .withNameEndingWith("ViewModel")
            .assertTrue { it.hasAnnotationOf(HiltViewModel::class) }
    }

    @Test
    fun `HiltViewModel-annotated classes inject through their primary constructor`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withAnnotationOf(HiltViewModel::class)
            .assertTrue { c -> c.primaryConstructor?.hasAnnotationOf(Inject::class) == true }
    }

    @Test
    fun `no @Inject field or property injection in production code`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .properties()
            .assertFalse { it.hasAnnotationOf(Inject::class) }
    }
}
