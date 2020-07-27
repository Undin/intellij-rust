/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.psi.util.parentOfType
import org.rust.cargo.project.workspace.CargoWorkspace.Edition.EDITION_2015
import org.rust.cargo.util.AutoInjectedCrates.CORE
import org.rust.cargo.util.AutoInjectedCrates.STD
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.ext.RsCachedItems.CachedNamedImport
import org.rust.lang.core.psi.ext.RsCachedItems.CachedStarImport
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve.namespaces

fun buildCrateDefMapContainingExplicitItems(
    crate: Crate,
    // have to pass `crate.id` and `crate.rootModule` as parameters,
    // because we want check them for null earlier
    crateId: CratePersistentId,
    crateRoot: RsFile
): Pair<CrateDefMap, List<Import>> {
    // todo: Prelude module is always considered to be `#[macro_use]`

    val (rootModuleData, imports) = ModCollector(crateId).collect(crateRoot)

    val externPrelude = getInitialExternPrelude(crate)
    val directDependenciesDefMaps = crate.dependencies
        .mapNotNull {
            val defMap = it.crate.defMap ?: return@mapNotNull null
            it.normName to defMap
        }
        .toMap()
    // todo вынести в отдельный метод
    val allDependenciesDefMaps = crate.flatDependencies
        .mapNotNull {
            val id = it.id ?: return@mapNotNull null
            val defMap = it.defMap ?: return@mapNotNull null
            id to defMap
        }
        .toMap()
    // look for the prelude
    // If the dependency defines a prelude, we overwrite an already defined
    // prelude. This is necessary to import the "std" prelude if a crate
    // depends on both "core" and "std".
    // todo should find prelude in all dependencies or only direct ones ?
    // todo check that correct prelude is always selected (core vs std)
    val prelude: ModData? = allDependenciesDefMaps.values.map { it.prelude }.firstOrNull()
    val defMap = CrateDefMap(
        crateId,
        crate.edition,
        rootModuleData,
        externPrelude,
        directDependenciesDefMaps,
        allDependenciesDefMaps,
        prelude
    )

    return Pair(defMap, imports)
}

private fun getInitialExternPrelude(crate: Crate): MutableMap<String, ModData> {
    return crate.dependencies
        .mapNotNull {
            val defMap = it.crate.defMap ?: return@mapNotNull null
            it.normName to defMap.root
        }
        .toMap(hashMapOf())
}

class ModCollector(private val crate: CratePersistentId) {

    private lateinit var crateRoot: ModData
    private val imports: MutableList<Import> = mutableListOf()

    fun collect(crateRoot: RsFile): Pair<ModData, List<Import>> {
        val crateRootData = collectRecursively(crateRoot, parent = null)
        addExternCrateStdImport(crateRoot, crateRootData)
        return Pair(crateRootData, imports)
    }

    private fun collectRecursively(mod: RsMod, parent: ModData?): ModData {
        val modPath = parent?.path?.append(mod.modName!! /* todo */) ?: ModPath(crate, emptyList())
        val modData = ModData(hashMapOf(), hashMapOf(), parent, crate, modPath)
        if (parent == null) crateRoot = modData

        val expandedItems = mod.expandedItemsCached
        // todo use `expandedItems.rest` directly ?
        val childModules = mod.childModules
        val expandedItemsAndChildModules: List<RsElement> = childModules +
            // we should filter `RsModDeclItem` from `expandedItems`
            // because there may not be corresponding `RsMod` in `childModules`
            // (e.g. in case `#[cfg_attr(..., path="other.rs")]` - `cfg_attr` is currently unsupported)
            expandedItems.rest.filterNot { it is RsModDeclItem || it is RsMod }

        modData.visibleItems += collectVisibleItems(expandedItemsAndChildModules, modData)
        collectImports(modData, expandedItems)

        // todo maybe ignore mods and enums in `collectVisibleItems` and add `childModules.map { it.asPerNs() }` to `visibleItems` ?
        //  непонятно как тогда обрабатывать multiresolve
        modData.childModules += childModules
            .associate { it.modName!! /* todo */ to collectRecursively(it, modData) }
        modData.childModules += expandedItemsAndChildModules
            .filterIsInstance<RsEnumItem>()
            .associate { it.name!! /* todo */ to collectEnumAsModData(it, modData) }
        return modData
    }

    private fun collectVisibleItems(expandedItems: List<RsElement>, modData: ModData): MutableMap<String, PerNs> {
        // `items` have same name and namespace
        fun mergeItemsToVisItem(items: List<RsNamedElement>): VisItem? {
            items.singleOrNull()?.let { return convertToVisItem(it, modData, crateRoot) }

            return if (items.distinctBy { it.elementType }.size == 1) {
                // multiple items of same type (e.g. all functions)
                //  -> can peek any of them (will handle multiresolve when converting back to PSI)
                // todo take item with widest visibility ?
                convertToVisItem(items.first(), modData, crateRoot)
            } else {
                // todo implement proper multiresolve
                //  it is complicated because there can be e.g. mod and struct with same name
                null
            }
        }

        val visibleItems = hashMapOf<String, PerNs>()
        val expandedItemsByName = expandedItems
            .filterIsInstance<RsNamedElement>()
            .filter { it !is RsExternCrateItem }
            .groupBy { it.normName }
        for ((name, items) in expandedItemsByName) {
            if (name == null) continue
            val itemsByNs = hashMapOf<Namespace, MutableList<RsNamedElement>>()
            for (item in items) {
                for (ns in item.namespaces) {
                    itemsByNs.getOrPut(ns, { mutableListOf() }) += item
                }
            }
            val visItemsByNs = itemsByNs.mapValues { (_, items) -> mergeItemsToVisItem(items) }
            val perNs = PerNs(
                types = visItemsByNs[Namespace.Types],
                values = visItemsByNs[Namespace.Values],
                macros = visItemsByNs[Namespace.Macros]
            )
            visibleItems[name] = perNs
        }
        return visibleItems
    }

    private fun collectImports(modData: ModData, expandedItems: RsCachedItems) {
        imports += expandedItems.namedImports.map { it.convertToImport(modData, crateRoot) }
        imports += expandedItems.starImports.mapNotNull { it.convertToImport(modData, crateRoot) }

        val externCrates = expandedItems.rest.filterIsInstance<RsExternCrateItem>()
        imports += externCrates.map { it.convertToImport(modData, crateRoot) }
    }

    private fun collectEnumAsModData(enum: RsEnumItem, parent: ModData): ModData {
        val enumPath = parent.path.append(enum.name!! /* todo */)
        val visibleItems = enum.variants
            .mapNotNull { variant ->
                val variantName = variant.name ?: return@mapNotNull null
                val variantPath = enumPath.append(variantName)
                val visItem = VisItem(variantPath, Visibility.Public, isModOrEnum = false)
                variantName to PerNs(visItem, variant.namespaces)
            }
            .toMap(hashMapOf())
        return ModData(hashMapOf(), visibleItems, parent, crate, enumPath)
    }

    private fun addExternCrateStdImport(crateRoot: RsFile, crateRootData: ModData) {
        if (crateRoot.edition != EDITION_2015) return
        // Rust injects implicit `extern crate std` in every crate root module unless it is
        // a `#![no_std]` crate, in which case `extern crate core` is injected. However, if
        // there is a (unstable?) `#![no_core]` attribute, nothing is injected.
        //
        // https://doc.rust-lang.org/book/using-rust-without-the-standard-library.html
        // The stdlib lib itself is `#![no_std]`, and the core is `#![no_core]`
        val name = when (crateRoot.attributes) {
            RsFile.Attributes.NONE -> STD
            RsFile.Attributes.NO_STD -> CORE
            RsFile.Attributes.NO_CORE -> return
        }
        imports += Import(
            crateRootData,
            name,
            name,
            Visibility.Restricted(crateRootData),
            isExternCrate = true,
            isMacroUse = true
        )
    }
}

private fun convertToVisItem(item: RsNamedElement, containingMod: ModData, crateRoot: ModData): VisItem? {
    val visibility = (item as? RsVisibilityOwner).getVisibility(containingMod, crateRoot)
    val itemName = item.normName ?: return null
    val itemPath = containingMod.path.append(itemName)
    val isModOrEnum = item is RsMod || item is RsModDeclItem || item is RsEnumItem
    return VisItem(itemPath, visibility, isModOrEnum)
}

private fun CachedNamedImport.convertToImport(containingMod: ModData, crateRoot: ModData): Import {
    val visibility = path.parentOfType<RsUseItem>().getVisibility(containingMod, crateRoot)
    return Import(containingMod, path.fullPath, nameInScope, visibility, isGlob = false)
}

private fun CachedStarImport.convertToImport(containingMod: ModData, crateRoot: ModData): Import? {
    val usePath = speck.path?.fullPath
        ?: when (val parent = speck.parent) {
            // `use ::*;`  (2015 edition)
            //        ^ speck
            is RsUseItem -> "crate"
            // `use aaa::{self, *};`
            //                  ^ speck
            is RsUseGroup -> (parent.parent as? RsUseSpeck)?.path?.fullPath ?: return null
            else -> return null
        }
    val useItem = speck.parentOfType<RsUseItem>()
    return Import(
        containingMod,
        usePath,
        "_" /* todo */,
        useItem.getVisibility(containingMod, crateRoot),
        isGlob = true,
        isPrelude = useItem?.hasPreludeImport ?: false
    )
}

private fun RsExternCrateItem.convertToImport(containingMod: ModData, crateRoot: ModData): Import {
    return Import(
        containingMod,
        referenceName,
        nameWithAlias,
        getVisibility(containingMod, crateRoot),
        isExternCrate = true,
        isMacroUse = hasMacroUse
    )
}

private fun RsVisibilityOwner?.getVisibility(containingMod: ModData, crateRoot: ModData): Visibility {
    if (this == null) return Visibility.Public
    val vis = vis ?: return Visibility.Restricted(containingMod)
    return when (vis.stubKind) {
        RsVisStubKind.PUB -> Visibility.Public
        RsVisStubKind.CRATE -> Visibility.Restricted(crateRoot)
        RsVisStubKind.RESTRICTED -> {
            // https://doc.rust-lang.org/reference/visibility-and-privacy.html#pubin-path-pubcrate-pubsuper-and-pubself
            val path = vis.visRestriction!!.path
            val pathText = path.fullPath.removePrefix("::")  // 2015 edition, absolute paths
            if (pathText.isEmpty() || pathText == "crate") return Visibility.Restricted(crateRoot)

            val segments = pathText.split("::")
            val initialModData = when (segments.first()) {
                "super", "self" -> containingMod
                else -> crateRoot
            }
            val pathTarget = segments
                .fold(initialModData) { modData, segment ->
                    val nextModData = when (segment) {
                        "self" -> modData
                        "super" -> modData.parent
                        else -> modData.childModules[segment]
                    }
                    nextModData ?: return Visibility.Restricted(crateRoot)
                }
            Visibility.Restricted(pathTarget)
        }
    }
}

private val RsNamedElement.normName: String?
    get() = if (this is RsFile) modName else name
