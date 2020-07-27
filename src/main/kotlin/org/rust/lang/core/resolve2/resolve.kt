/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.containingCrate
import org.rust.lang.core.psi.ext.superMods
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve2.Visibility.Invisible

class DefDatabase(
    // defMaps for some crate and all its dependencies (including transitive)
    val allDefMaps: Map<CratePersistentId, CrateDefMap>
) {
    private fun getModData(modPath: ModPath): ModData? {
        val defMap = allDefMaps[modPath.crate]
            ?: error("todo")
        return defMap.getModData(modPath)
    }

    fun tryCastToModData(perNs: PerNs): ModData? {
        val types = perNs.types ?: return null
        return tryCastToModData(types)
    }

    fun tryCastToModData(types: VisItem): ModData? {
        if (!types.isModOrEnum) return null
        return getModData(types.path)
    }
}

// todo вынести поля нужные только на этапе построения в collector ?
class CrateDefMap(
    val crate: CratePersistentId,
    val edition: CargoWorkspace.Edition,
    val root: ModData,

    val externPrelude: MutableMap<String, ModData>,
    // used only by `extern crate crate_name;` declarations
    val directDependenciesDefMaps: Map<String, CrateDefMap>,
    allDependenciesDefMaps: Map<CratePersistentId, CrateDefMap>,
    var prelude: ModData?
) {
    val defDatabase: DefDatabase = DefDatabase(allDependenciesDefMaps + (crate to this))

    fun getModData(modPath: ModPath): ModData? {
        check(crate == modPath.crate)
        return modPath.segments
            // todo assert not null ?
            .fold(root as ModData?) { modData, segment -> modData?.childModules?.get(segment) }
    }

    fun getModData(mod: RsMod): ModData? {
        mod.containingCrate?.id?.let { check(it == crate) }  // todo
        val modPath = ModPath.fromMod(mod, crate) ?: return null
        return getModData(modPath)
    }
}

class ModData(
    // val fileId: Int,  // todo ?
    val childModules: MutableMap<String, ModData>,
    // todo concurrency ?
    // todo три мапы ?
    val visibleItems: MutableMap<String, PerNs>,
    val parent: ModData?,  // todo rename `superMod` ?
    val crate: CratePersistentId,
    val path: ModPath
) {
    val isCrateRoot: Boolean get() = parent == null
    val parents: Sequence<ModData> get() = generateSequence(this) { it.parent }

    // Traits imported via `use Trait as _;`
    val unnamedTraitImports: MutableMap<ModPath, Visibility> = hashMapOf()

    operator fun get(name: String): PerNs = visibleItems.getOrDefault(name, PerNs.Empty)

    fun getVisibleItems(filterVisibility: (Visibility) -> Boolean): List<Pair<String, PerNs>> {
        val usualItems = visibleItems.entries
            .map { (name, visItem) -> name to visItem.filterVisibility(filterVisibility) }
            .filterNot { (_, visItem) -> visItem.isEmpty }
        val traitItems = unnamedTraitImports
            .mapNotNull { (path, visibility) ->
                if (!filterVisibility(visibility)) return@mapNotNull null
                val trait = VisItem(path, visibility, isModOrEnum = false)
                "_" to PerNs(types = trait)
            }
        return usualItems + traitItems
    }

    fun asVisItem(): VisItem = VisItem(path, Visibility.Public /* todo */, true)

    fun asPerNs(): PerNs = PerNs(types = asVisItem())

    fun getNthParent(n: Int): ModData? {
        check(n >= 0)
        return parents.drop(n).firstOrNull()
    }

    override fun toString(): String = "ModData(path=$path, crate=$crate)"
}

data class PerNs(
    // todo var ?
    var types: VisItem? = null,
    var values: VisItem? = null,
    var macros: VisItem? = null
    // todo
    // val invalid: List<ModPath>
) {
    val isEmpty: Boolean get() = types == null && values == null && macros == null

    constructor(visItem: VisItem, ns: Set<Namespace>) :
        this(
            visItem.takeIf { Namespace.Types in ns },
            visItem.takeIf { Namespace.Values in ns },
            visItem.takeIf { Namespace.Macros in ns }
        )

    fun update(other: PerNs) {
        other.types?.let {
            check(types == null)
            types = it
        }
        other.values?.let {
            check(values == null)
            values = it
        }
        other.macros?.let {
            check(macros == null)
            macros = it
        }
    }

    fun withVisibility(visibility: Visibility): PerNs =
        PerNs(
            types?.let { if (it.visibility === Invisible) it else it.copy(visibility = visibility) },
            values?.let { if (it.visibility === Invisible) it else it.copy(visibility = visibility) },
            macros?.let { if (it.visibility === Invisible) it else it.copy(visibility = visibility) }
        )

    fun filterVisibility(filter: (Visibility) -> Boolean): PerNs =
        PerNs(
            types?.takeIf { filter(it.visibility) },
            values?.takeIf { filter(it.visibility) },
            macros?.takeIf { filter(it.visibility) }
        )

    fun or(other: PerNs): PerNs =
        PerNs(
            types ?: other.types,
            values ?: other.values,
            macros ?: other.macros
        )

    fun mapItems(f: (VisItem) -> VisItem): PerNs =
        PerNs(
            types?.let { f(it) },
            values?.let { f(it) },
            macros?.let { f(it) }
        )

    companion object {
        val Empty: PerNs = PerNs()
    }
}

// The item which can be visible in the module (either directly declared or imported)
// Could be `RsEnumVariant` (because it can be imported)
data class VisItem(
    // full path to item, including its name
    // can't store `containingMod` and `name` separately,
    // because `VisItem` could be used for crate root
    val path: ModPath,
    val visibility: Visibility,
    val isModOrEnum: Boolean
) {
    val containingMod: ModPath get() = path.parent  // mod where item is explicitly declared
    val name: String get() = path.name
    val crate: CratePersistentId get() = containingMod.crate
}

sealed class Visibility {
    fun isVisibleFromOtherCrate(): Boolean = this === Public

    fun isVisibleFromMod(mod: ModData): Boolean =
        when (this) {
            Public -> true
            is Restricted -> mod.parents.contains(inMod)
            Invisible -> false
        }

    object Public : Visibility()

    // includes Private
    data class Restricted(val inMod: ModData) : Visibility()

    // Means that we have import to private item
    // So normally we should ignore such VisItem (it is not accessible)
    // But we record it for completion, etc
    object Invisible : Visibility()

    override fun toString(): String =
        when (this) {
            Public -> "Public"
            is Restricted -> "in ${inMod.path}"
            Invisible -> "Invisible"
        }

    fun isStrictMorePermissive(other: Visibility): Boolean {
        return if (this is Restricted && other is Restricted) {
            inMod.crate == other.inMod.crate
                && inMod !== other.inMod
                && other.inMod.parents.contains(inMod)
        } else {
            this === Public && other !== Public
        }
    }
}

/** Path to a module or an item in module */
data class ModPath(
    val crate: CratePersistentId,
    // val path: String  // foo::bar::baz
    val segments: List<String>
) {
    val path = segments.joinToString("::")
    // val segments: List<String> get() = path.split("::")

    val name: String get() = segments.last()

    val parent: ModPath get() = ModPath(crate, segments.subList(0, segments.size - 1))

    fun append(segment: String): ModPath = ModPath(crate, segments + segment)

    override fun toString(): String = "'$path'"

    companion object {
        fun fromMod(mod: RsMod, crate: CratePersistentId): ModPath? {
            val segments = mod.superMods
                .asReversed().drop(1)
                .map { it.modName ?: return null }
            return ModPath(crate, segments)
        }
    }
}
