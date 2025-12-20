package br.com.cactus.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Test

class ArchitectureTest {

    @Test
    fun `hexagonal architecture layers are respected`() {
        Konsist.scopeFromProduction()
            .assertArchitecture {
                val core = Layer("Core", "br.com.cactus.core..")
                val adapters = Layer("Adapters", "br.com.cactus.adapter..")
                val config = Layer("Config", "br.com.cactus.config..")

                core.dependsOnNothing()
                adapters.dependsOn(core)
                config.dependsOn(core, adapters)
            }
    }

    @Test
    fun `use cases should be annotated with Service`() {
        Konsist.scopeFromPackage("br.com.cactus.core.usecase..")
            .classes()
            .withNameEndingWith("Impl")
            .assertTrue { klass ->
                klass.hasAnnotationWithName("Service")
            }
    }

    @Test
    fun `controllers should be annotated with RestController`() {
        Konsist.scopeFromPackage("br.com.cactus.adapter.inbound.rest.controller..")
            .classes()
            .withNameEndingWith("Controller")
            .assertTrue { klass ->
                klass.hasAnnotationWithName("RestController")
            }
    }

    @Test
    fun `adapters should be annotated with Component`() {
        Konsist.scopeFromPackage("br.com.cactus.adapter.outbound..")
            .classes()
            .withNameEndingWith("Adapter")
            .assertTrue { klass ->
                klass.hasAnnotationWithName("Component")
            }
    }

    @Test
    fun `ports should be interfaces`() {
        Konsist.scopeFromPackage("br.com.cactus.core.ports..")
            .interfaces()
            .assertTrue { it.resideInPackage("br.com.cactus.core.ports..") }
    }
}
