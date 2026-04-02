package com.range.phoneLinuxer.data.executor

import android.content.Context
import android.system.Os
import timber.log.Timber
import java.io.File

class LibLinker(private val context: Context) {

    private val TAG = "LibLinker"

    val injectLibDir = File(context.filesDir, "injected_libs")

    private val systemBlacklistExact = setOf(
        "libEGL.so", "libGLESv1_CM.so", "libGLESv2.so", "libGLESv3.so",
        "libvulkan.so", "libgui.so", "libui.so", "libandroid.so",
        "libutils.so", "libc++.so", "libm.so", "libc.so", "libdl.so",
        "libqemu_system.so", "liblzma.so", "liblz4.so", "libz.so",
        "libssl.so", "libcrypto.so", "libexpat.so", "libxml2.so",
        "libsqlite3.so"
    )

    private val desktopOnlyPrefixes = listOf(
        "libgst",
        "libGL.so",
        "libGLX",
        "libGLdispatch",
        "libX11",
        "libxcb",
        "libX11-xcb",
        "libxcb-glx",
        "libxcb-dri",
        "libxcb-randr",
        "libxkb",
        "libXext",
        "libdrm",
        "libgbm"
    )

    fun injectAndLink() {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir

        try {
            if (!injectLibDir.exists()) injectLibDir.mkdirs()
            injectLibDir.listFiles()?.forEach { it.delete() }

            fun processFile(file: File) {
                val fileName = file.name
                if (systemBlacklistExact.contains(fileName)) return
                if (desktopOnlyPrefixes.any { fileName.startsWith(it) }) return
                
                if (fileName == "libz.so.1" || fileName == "libz.so") {
                    try {
                        val originalBytes = file.readBytes()
                        
                        val libz1Dest = File(injectLibDir, "libz.so.1")
                        if (!libz1Dest.exists()) {
                            libz1Dest.writeBytes(originalBytes)
                            libz1Dest.setExecutable(true, false)
                        }
                        
                        val patchedBytes = originalBytes.copyOf()
                        val searchBytes = "libz.so.1\u0000".toByteArray()
                        val replaceBytes = "libz.so\u0000\u0000\u0000".toByteArray()
                        var patched = false
                        for (i in 0..patchedBytes.size - searchBytes.size) {
                            var match = true
                            for (j in searchBytes.indices) {
                                if (patchedBytes[i + j] != searchBytes[j]) {
                                    match = false; break
                                }
                            }
                            if (match) {
                                for (j in replaceBytes.indices) patchedBytes[i + j] = replaceBytes[j]
                                patched = true
                            }
                        }
                        
                        val libzDest = File(injectLibDir, "libz.so")
                        if (!libzDest.exists()) {
                            libzDest.writeBytes(if (patched) patchedBytes else originalBytes)
                            libzDest.setExecutable(true, false)
                        }
                        return
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Patch error")
                    }
                }

                if (fileName.endsWith(".so")) {
                    var clean = fileName.removeSuffix(".so")
                    
                    clean = clean.replace("_so_", ".so.")
                    if (clean.endsWith("_so")) {
                        clean = clean.substringBeforeLast("_so") + ".so"
                    }
                    
                    val vRegex = "_(\\d+)".toRegex()
                    clean = vRegex.replace(clean) { ".${it.groupValues[1]}" }
                    
                    var finalName = if (clean.contains(".so")) clean else "$clean.so"
                    finalName = finalName.replace("..", ".").removeSuffix(".")

                    if (finalName != fileName) {
                        createSymlink(file, finalName)
                    }
                    if (finalName.contains(".so.")) {
                        val unversionedName = finalName.substringBeforeLast(".so.") + ".so"
                        if (!systemBlacklistExact.contains(unversionedName)) {
                            createSymlink(file, unversionedName)
                        }
                    }
                }

                createSymlink(file, fileName)
            }

            File(nativeLibDir).listFiles()?.forEach { processFile(it) }

            context.filesDir.listFiles()?.filter { it.isFile && (it.name.endsWith(".so") || it.name.contains(".so.")) }
                ?.forEach { processFile(it) }

            systemBlacklistExact.forEach { name ->
                val f = File(context.filesDir, name)
                if (f.exists()) { try { f.delete() } catch (_: Exception) {} }
            }

            val libzInInject = File(injectLibDir, "libz.so")
            val libz1InInject = File(injectLibDir, "libz.so.1")
            
            if (!libzInInject.exists() && !libz1InInject.exists()) {
                Timber.tag(TAG).e("CRITICAL: Custom libz.so is missing from injected libs!")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Linker loop failure")
        }
    }

    private fun createSymlink(target: File, linkName: String) {
        val linkFile = File(injectLibDir, linkName)
        try {
            if (linkName.startsWith("libz.so")) {
                if (target.absolutePath.startsWith(context.filesDir.absolutePath)) {
                    target.copyTo(linkFile, overwrite = true)
                    linkFile.setReadable(true, false)
                    return
                }
            }
            Os.symlink(target.absolutePath, linkFile.absolutePath)
        } catch (e: Exception) {
            Timber.tag(TAG).v("Link fail: $linkName")
        }
    }
}
