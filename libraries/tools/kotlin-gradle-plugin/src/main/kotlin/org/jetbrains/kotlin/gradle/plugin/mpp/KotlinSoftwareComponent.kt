/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Usage
import org.gradle.api.capabilities.Capability
import org.gradle.api.component.ComponentWithCoordinates
import org.gradle.api.component.ComponentWithVariants
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.ProjectLocalConfigurations

abstract class KotlinSoftwareComponent(
    private val name: String,
    protected val kotlinTargets: Iterable<KotlinTarget>
) : SoftwareComponentInternal, ComponentWithVariants {

    private val metadataTarget: KotlinMetadataTarget
        get() = kotlinTargets.filterIsInstance<KotlinMetadataTarget>().single()

    override fun getUsages(): Set<UsageContext> = (metadataTarget.components.single() as SoftwareComponentInternal).usages

    override fun getVariants(): Set<SoftwareComponent> =
        kotlinTargets.minus(metadataTarget).flatMap { it.components }.toSet()

    override fun getName(): String = name

    // This property is declared in the parent type to allow the usages to reference it without forcing the subtypes to load,
    // which is needed for compatibility with older Gradle versions
    var publicationDelegate: MavenPublication? = null
}

class KotlinSoftwareComponentWithCoordinatesAndPublication(name: String, kotlinTargets: Iterable<KotlinTarget>) :
    KotlinSoftwareComponent(name, kotlinTargets), ComponentWithCoordinates {

    override fun getCoordinates(): ModuleVersionIdentifier = getCoordinatesFromPublicationDelegateAndProject(
        publicationDelegate, kotlinTargets.first().project, null
    )
}

// At the moment all KN artifacts have JAVA_API usage.
// TODO: Replace it with a specific usage
object NativeUsage {
    const val KOTLIN_KLIB = "kotlin-klib"
}

interface KotlinUsageContext : UsageContext {
    val compilation: KotlinCompilation<*>
    val dependencyConfigurationName: String
    val includeIntoProjectStructureMetadata: Boolean
}

class DefaultKotlinUsageContext(
    override val compilation: KotlinCompilation<*>,
    private val usage: Usage,
    override val dependencyConfigurationName: String,
    private val overrideConfigurationArtifacts: Set<PublishArtifact>? = null,
    private val overrideConfigurationAttributes: AttributeContainer? = null,
    override val includeIntoProjectStructureMetadata: Boolean = true
) : KotlinUsageContext {

    private val kotlinTarget: KotlinTarget get() = compilation.target
    private val project: Project get() = kotlinTarget.project

    override fun getUsage(): Usage = usage

    override fun getName(): String = dependencyConfigurationName

    private val configuration: Configuration
        get() = project.configurations.getByName(dependencyConfigurationName)

    override fun getDependencies(): MutableSet<out ModuleDependency> =
        configuration.incoming.dependencies.withType(ModuleDependency::class.java)

    override fun getDependencyConstraints(): MutableSet<out DependencyConstraint> =
        configuration.incoming.dependencyConstraints

    override fun getArtifacts(): Set<PublishArtifact> =
        overrideConfigurationArtifacts ?:
        // TODO Gradle Java plugin does that in a different way; check whether we can improve this
        configuration.artifacts

    override fun getAttributes(): AttributeContainer {
        val configurationAttributes = overrideConfigurationAttributes ?: configuration.attributes

        /** TODO Using attributes of a detached configuration is a small and 'conservative' fix for KT-29758, [HierarchyAttributeContainer]
         * being rejected by Gradle 5.2+; we may need to either not filter the attributes, which will lead to
         * [ProjectLocalConfigurations.ATTRIBUTE] being published in the Gradle module metadata, which will potentially complicate our
         * attributes schema migration, or create proper, non-detached configurations for publishing that are separated from the
         * configurations used for project-to-project dependencies
         */
        val result = project.configurations.detachedConfiguration().attributes

        // Capture type parameter T:
        fun <T> copyAttribute(attribute: Attribute<T>, from: AttributeContainer, to: AttributeContainer) {
            to.attribute<T>(attribute, from.getAttribute(attribute)!!)
        }

        configurationAttributes.keySet()
            .filter { it != ProjectLocalConfigurations.ATTRIBUTE }
            .forEach { copyAttribute(it, configurationAttributes, result) }

        return result
    }

    override fun getCapabilities(): Set<Capability> = emptySet()

    override fun getGlobalExcludes(): Set<ExcludeRule> = emptySet()
}