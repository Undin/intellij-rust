/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapiext.isUnitTestMode
import com.intellij.util.io.exists
import com.intellij.util.text.SemVer
import org.rust.openapiext.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

data class RustToolchain(val location: Path, val name: String?) {

    fun looksLikeValidToolchain(): Boolean =
        hasExecutable(CARGO) && hasExecutable(RUSTC)

    fun queryVersions(): VersionInfo {
        if (!isUnitTestMode) {
            checkIsBackgroundThread()
        }
        val rustcVersion = GeneralCommandLine(pathToExecutable(RUSTC))
            .apply { if (name != null) withParameters("+$name") }
            .withParameters("--version", "--verbose")
            .execute()
            ?.stdoutLines
            ?.let { RustcVersion.parseRustcVersion(it) }
        return VersionInfo(rustcVersion)
    }

    fun getSysroot(): String? {
        if (!isUnitTestMode) {
            checkIsBackgroundThread()
        }
        val timeoutMs = 10000
        val output = GeneralCommandLine(pathToExecutable(RUSTC))
            .withCharset(Charsets.UTF_8)
            .apply { if (name != null) withParameters("+$name") }
            .withParameters("--print", "sysroot")
            .execute(timeoutMs)
        return if (output?.isSuccess == true) output.stdout.trim() else null
    }

    fun getStdlibFromSysroot(): VirtualFile? {
        val sysroot = getSysroot() ?: return null
        val fs = LocalFileSystem.getInstance()
        return fs.refreshAndFindFileByPath(FileUtil.join(sysroot, "lib/rustlib/src/rust/src"))
    }

    fun getCfgOptions(projectDirectory: Path): List<String>? {
        val timeoutMs = 10000
        val output = GeneralCommandLine(pathToExecutable(RUSTC))
            .withCharset(Charsets.UTF_8)
            .withWorkDirectory(projectDirectory)
            .apply { if (name != null) withParameters("+$name") }
            .withParameters("--print", "cfg")
            .execute(timeoutMs)
        return if (output?.isSuccess == true) output.stdoutLines else null
    }

    fun rawCargo(): Cargo = Cargo(pathToExecutable(CARGO), name)

    fun cargoOrWrapper(cargoProjectDirectory: Path?): Cargo {
        val hasXargoToml = cargoProjectDirectory?.resolve(XARGO_TOML)?.let { Files.isRegularFile(it) } == true
        val cargoWrapper = if (hasXargoToml && hasExecutable(XARGO)) XARGO else CARGO
        return Cargo(pathToExecutable(cargoWrapper), name)
    }

    fun rustup(): Rustup? =
        if (isRustupAvailable)
            Rustup(this, pathToExecutable(RUSTUP))
        else
            null

    fun rustfmt(): Rustfmt = Rustfmt(pathToExecutable(RUSTFMT))

    fun grcov(): Grcov? = if (hasCargoExecutable(GRCOV)) Grcov(pathToCargoExecutable(GRCOV)) else null

    fun evcxr(): Evcxr? = if (hasCargoExecutable(EVCXR)) Evcxr(pathToCargoExecutable(EVCXR)) else null

    val isRustupAvailable: Boolean get() = hasExecutable(RUSTUP)

    val presentableLocation: String = pathToExecutable(CARGO).toString()

    // for executables from toolchain
    private fun pathToExecutable(toolName: String): Path {
        val exeName = if (SystemInfo.isWindows) "$toolName.exe" else toolName
        return location.resolve(exeName).toAbsolutePath()
    }

    // for executables installed using `cargo install`
    private fun pathToCargoExecutable(toolName: String): Path {
        // Binaries installed by `cargo install` (e.g. Grcov, Evcxr) are placed in ~/.cargo/bin by default:
        // https://doc.rust-lang.org/cargo/commands/cargo-install.html
        // But toolchain root may be different (e.g. on Arch Linux it is usually /usr/bin)
        val path = pathToExecutable(toolName)
        if (path.exists()) return path

        val exeName = if (SystemInfo.isWindows) "$toolName.exe" else toolName
        val cargoBinPath = File(FileUtil.expandUserHome("~/.cargo/bin")).toPath()
        return cargoBinPath.resolve(exeName).toAbsolutePath()
    }

    private fun hasExecutable(exec: String): Boolean =
        Files.isExecutable(pathToExecutable(exec))

    private fun hasCargoExecutable(exec: String): Boolean =
        Files.isExecutable(pathToCargoExecutable(exec))

    data class VersionInfo(
        val rustc: RustcVersion?
    )

    companion object {
        const val RUSTC = "rustc"
        const val CARGO = "cargo"
        const val XARGO = "xargo"
        const val RUSTUP = "rustup"

        private const val RUSTFMT = "rustfmt"
        private const val GRCOV = "grcov"
        private const val EVCXR = "evcxr"

        const val CARGO_TOML = "Cargo.toml"
        const val CARGO_LOCK = "Cargo.lock"
        const val XARGO_TOML = "Xargo.toml"

        val MIN_SUPPORTED_TOOLCHAIN = SemVer.parseFromText("1.32.0")!!
    }
}
