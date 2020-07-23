/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject

/**
 * Validates package name while new project creation.
 */
object RsPackageNameValidator {

    /**
     * Keywords + "test"
     * https://github.com/rust-lang/cargo/blob/2c6711155232b2d6271bb7147610077c3a8cee65/src/cargo/util/restricted_names.rs#L13
     * https://github.com/rust-lang/cargo/blob/eb7bc688aef827651fefb0faa70d88717f55d59d/src/cargo/ops/cargo_new.rs#L184
     */
    private val BLACKLIST = setOf(
        "Self", "abstract", "as", "async", "await", "become", "box", "break", "const", "continue",
        "crate", "do", "dyn", "else", "enum", "extern", "false", "final", "fn", "for", "if",
        "impl", "in", "let", "loop", "macro", "match", "mod", "move", "mut", "override", "priv",
        "pub", "ref", "return", "self", "static", "struct", "super", "trait", "true", "try",
        "type", "typeof", "unsafe", "unsized", "use", "virtual", "where", "while", "yield", "test"
    )

    /**
     * https://github.com/rust-lang/cargo/blob/2c6711155232b2d6271bb7147610077c3a8cee65/src/cargo/util/restricted_names.rs#L35
     */
    private val BINARY_BLACKLIST = setOf("deps", "examples", "build", "incremental")

    fun validate(name: String, isBinary: Boolean): String? = when {
        name.isEmpty() -> "Package name can't be empty"
        name in BLACKLIST -> "The name `$name` cannot be used as a crate name"
        isBinary && name in BINARY_BLACKLIST -> "The name `$name` cannot be used as a crate name"
        name[0].isDigit() -> "Package names starting with a digit cannot be used as a crate name"
        !name.all { it.isLetterOrDigit() || it == '-' || it == '_' } ->
            "Package names should contain only letters, digits, `-` and `_`"
        else -> null
    }
}
