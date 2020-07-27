/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import org.rust.cargo.project.workspace.CargoWorkspace

enum class ResolveMode { IMPORT, OTHER }

// Returns `reachedFixedPoint=true` if we are sure that additions to `visibleItems` wouldn't change the result
fun CrateDefMap.resolvePathFp(containingMod: ModData, path: String, mode: ResolveMode): ResolvePathResult {
    val (pathKind, segments) = getPathKind(path)
        .run { first to second.toMutableList() }
    // we use PerNs and not ModData for first segment,
    // because path could be one-segment: `use crate as foo;` and `use func as func2;`
    //                                         ~~~~~ path              ~~~~ path
    val firstSegmentPerNs = when {
        // todo $crate
        pathKind == PathKind.Crate -> root.asPerNs()
        pathKind is PathKind.Super -> {
            val modData = containingMod.getNthParent(pathKind.level)
                ?: return ResolvePathResult.empty(reachedFixedPoint = true)
            modData.asPerNs()
        }
        // plain import or absolute path in 2015:
        // crate-relative with fallback to extern prelude
        // (with the simplification in https://github.com/rust-lang/rust/issues/57745)
        edition == CargoWorkspace.Edition.EDITION_2015
            && (pathKind is PathKind.Absolute || pathKind is PathKind.Plain && mode == ResolveMode.IMPORT) -> {
            val firstSegment = segments.removeAt(0)
            resolveNameInCrateRootOrExternPrelude(firstSegment)
        }
        pathKind == PathKind.Absolute -> {
            val crateName = segments.removeAt(0)
            externPrelude[crateName]?.asPerNs()
            // extern crate declarations can add to the extern prelude
                ?: return ResolvePathResult.empty(reachedFixedPoint = false)
        }
        pathKind == PathKind.Plain -> {
            val firstSegment = segments.removeAt(0)
            resolveNameInModule(containingMod, firstSegment)
                ?: return ResolvePathResult.empty(reachedFixedPoint = false)
        }
        else -> error("unreachable")
    }

    var currentPerNs = firstSegmentPerNs
    var visitedOtherCrate = false
    for (segment in segments) {
        // we still have path segments left, but the path so far
        // didn't resolve in the types namespace => no resolution
        val currentModAsVisItem = currentPerNs.types
            ?: return ResolvePathResult.empty(reachedFixedPoint = false)

        val currentModData = defDatabase.tryCastToModData(currentModAsVisItem)
        // could be an inherent method call in UFCS form
        // (`Struct::method`), or some other kind of associated item
            ?: return ResolvePathResult.empty(reachedFixedPoint = true)
        if (currentModData.crate != crate) visitedOtherCrate = true

        currentPerNs = currentModData[segment]
    }
    return ResolvePathResult(currentPerNs, reachedFixedPoint = true, visitedOtherCrate = visitedOtherCrate)
}

fun CrateDefMap.resolveNameInExternPrelude(name: String): PerNs {
    val root = externPrelude[name] ?: return PerNs.Empty
    return root.asPerNs()
}

// only when resolving `name` in `extern crate name;`
//                                             ~~~~
fun CrateDefMap.resolveExternCrateName(name: String): PerNs? {
    val externCrateDefMap = if (name == "self") {
        this
    } else {
        directDependenciesDefMaps[name] ?: return null
    }
    return externCrateDefMap.root.asPerNs()
}

private fun CrateDefMap.resolveNameInModule(modData: ModData, name: String): PerNs? {
    // Resolve in:
    //  - legacy scope of macro
    //  - current module / scope
    //  - extern prelude
    //  - std prelude
    val fromScope = modData[name]
    val fromExternPrelude = resolveNameInExternPrelude(name)
    val fromPrelude = resolveNameInPrelude(name)
    return fromScope.or(fromExternPrelude).or(fromPrelude)
}

private fun CrateDefMap.resolveNameInCrateRootOrExternPrelude(name: String): PerNs {
    val fromCrateRoot = root[name]
    val fromExternPrelude = resolveNameInExternPrelude(name)

    return fromCrateRoot.or(fromExternPrelude)
}

private fun CrateDefMap.resolveNameInPrelude(name: String): PerNs {
    val prelude = prelude ?: return PerNs.Empty
    return prelude[name]
}

sealed class PathKind {
    object Plain : PathKind()

    // `self` is `Super(0)`
    class Super(val level: Int) : PathKind()

    // starts with crate
    object Crate : PathKind()

    // starts with ::
    object Absolute : PathKind()
}

fun getPathKind(path: String): Pair<PathKind, List<String> /* remaining segments */> {
    check(path.isNotEmpty())
    val segments = path.split("::")
    val (kind, segmentsToSkip) = when (segments.first()) {
        "crate" -> PathKind.Crate to 1
        "super" -> {
            val level = segments.takeWhile { it == "super" }.size
            PathKind.Super(level) to level
        }
        "self" -> {
            if (segments.getOrNull(1) == "super") return getPathKind(path.removePrefix("self::"))
            PathKind.Super(0) to 1
        }
        "" -> PathKind.Absolute to 1
        else -> PathKind.Plain to 0
    }
    return kind to segments.subList(segmentsToSkip, segments.size)
}

data class ResolvePathResult(
    val resolvedDef: PerNs,
    val reachedFixedPoint: Boolean,
    val visitedOtherCrate: Boolean
) {
    companion object {
        fun empty(reachedFixedPoint: Boolean): ResolvePathResult =
            ResolvePathResult(PerNs.Empty, reachedFixedPoint, false)
    }
}
