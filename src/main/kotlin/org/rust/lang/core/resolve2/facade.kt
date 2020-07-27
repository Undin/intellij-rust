/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.RecursionManager
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.crateGraph
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.ItemProcessingMode.WITHOUT_PRIVATE_IMPORTS

fun buildCrateDefMap(crate: Crate): CrateDefMap? {
    val crateId = crate.id ?: return null
    val crateRoot = crate.rootMod ?: return null
    val result = RecursionManager.doPreventingRecursion(crateId, false /* todo */) {
        // todo read action
        val (defMap, imports) = buildCrateDefMapContainingExplicitItems(crate, crateId, crateRoot)
        DefCollector(defMap, imports).collect()
        defMap
    }
    checkNotNull(result) { "recursion when computing CrateDefMap for $crate" }
    return result
}

fun processItemDeclarations2(
    scope: RsMod,
    ns: Set<Namespace>,
    originalProcessor: RsResolveProcessor,
    ipm: ItemProcessingMode  // todo
): Boolean {
    val project = scope.project
    val crate = scope.containingCrate ?: return false
    val defMap = crate.defMap ?: return false
    val modData = defMap.getModData(scope) ?: return false

    // todo optimization: попробовать избавиться от цикла и передавать name как параметр
    val namesInTypesNamespace = hashSetOf<String>()
    for ((name, perNs) in modData.visibleItems) {
        /* todo inline */ fun VisItem.tryConvertToPsi(namespace: Namespace): RsNamedElement? {
            if (namespace !in ns) return null
            if (visibility === Visibility.Invisible && ipm === WITHOUT_PRIVATE_IMPORTS) return null
            return toPsi(project, namespace)
        }

        // todo refactor ?
        // todo iterate over `ns` ?
        val types = perNs.types?.tryConvertToPsi(Namespace.Types)
        val values = perNs.values?.tryConvertToPsi(Namespace.Values)
        val macros = perNs.macros?.tryConvertToPsi(Namespace.Macros)
        // we need setOf here because item could belong to multiple namespaces (e.g. unit struct)
        for (element in setOf(types, values, macros)) {
            if (element == null) continue
            val entry = SimpleScopeEntry(name, element)
            originalProcessor(entry) && return true
        }

        if (types != null) namesInTypesNamespace += name
    }

    // todo не обрабатывать отдельно, а использовать `getVisibleItems` ?
    if (Namespace.Types in ns) {
        for ((traitPath, traitVisibility) in modData.unnamedTraitImports) {
            val trait = VisItem(traitPath, traitVisibility, isModOrEnum = false)
            val traitPsi = trait.toPsi(project, Namespace.Types) ?: continue
            val entry = SimpleScopeEntry("_", traitPsi)
            originalProcessor(entry) && return true
        }
    }

    if (ipm.withExternCrates && Namespace.Types in ns) {
        for ((name, externCrateModData) in defMap.externPrelude) {
            if (name in namesInTypesNamespace) continue
            val externCratePsi = externCrateModData.asVisItem().toPsi(project, Namespace.Types)!!  // todo
            val entry = SimpleScopeEntry(name, externCratePsi)
            originalProcessor(entry) && return true
        }
    }

    return false
}

private fun VisItem.toPsi(project: Project, ns: Namespace): RsNamedElement? {
    if (isModOrEnum) return path.toRsModOrEnum(project)
    val containingModOrEnum = containingMod.toRsModOrEnum(project) ?: return null
    return when (containingModOrEnum) {
        is RsMod -> containingModOrEnum.expandedItemsExceptImplsAndUses
            .filterIsInstance<RsNamedElement>()
            // todo multiresolve
            .singleOrNull { it.name == name && ns in it.namespaces }
        is RsEnumItem -> containingModOrEnum.variants.find { it.name == name && ns in it.namespaces }
        else -> error("unreachable")
    }
}

private fun ModPath.toRsModOrEnum(project: Project): RsNamedElement? /* RsMod or RsEnumItem */ {
    val crate = project.crateGraph.findCrateById(crate) ?: return null
    val crateRoot = crate.rootMod ?: return null
    if (segments.isEmpty()) return crateRoot
    val parentMod = segments
        .subList(0, segments.size - 1)
        .fold(crateRoot as RsMod) { mod, segment ->
            // todo multiresolve
            val childMod = mod.childModules.singleOrNull { it.modName == segment }
            childMod ?: return null
        }

    val lastSegment = segments.last()
    val mod = parentMod.childModules.find { it.modName == lastSegment }
    // todo performance
    val enum = parentMod.expandedItemsExceptImplsAndUses
        // todo multiresolve
        .singleOrNull { it.name == lastSegment && it is RsEnumItem }
        as RsNamedElement?
    return mod ?: enum
}
