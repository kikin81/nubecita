package net.kikin.nubecita.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withAnnotationOf
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertTrue
import dagger.Module
import dagger.hilt.InstallIn
import org.junit.Test

class PackageLayoutRulesTest {
    @Test
    fun `ViewModel, State, Event, and Effect classes reside under net_kikin_nubecita_ui`() {
        Konsist
            .scopeFromProduction()
            .classesAndInterfaces()
            .withNameEndingWith("ViewModel", "State", "Event", "Effect")
            .assertTrue { it.resideInPackage("net.kikin.nubecita.ui..") }
    }

    @Test
    fun `Hilt modules reside under net_kikin_nubecita_data`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withAnnotationOf(Module::class, InstallIn::class)
            .assertTrue { it.resideInPackage("net.kikin.nubecita.data..") }
    }
}
