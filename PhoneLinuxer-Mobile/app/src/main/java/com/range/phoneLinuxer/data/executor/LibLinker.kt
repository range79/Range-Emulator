package com.range.phoneLinuxer.data.executor

import android.content.Context
import android.system.Os
import timber.log.Timber
import java.io.File

class LibLinker(private val context: Context) {

    private val TAG = "LibLinker"

    val injectLibDir = File(context.filesDir, "injected_libs")

    // Android-owned libs: Linux versions with same unversioned SONAME must never be injected
    private val systemBlacklistExact = setOf(
        "libEGL.so", "libGLESv1_CM.so", "libGLESv2.so", "libGLESv3.so",
        "libvulkan.so", "libgui.so", "libui.so", "libandroid.so",
        "libutils.so", "libc++.so", "libm.so", "libc.so", "libdl.so",
        "libqemu_system.so", "liblzma.so", "liblz4.so", "libz.so",
        "libssl.so", "libcrypto.so", "libexpat.so", "libxml2.so",
        "libsqlite3.so"
    )

    // Libs that need a full desktop X11/GL stack — must NOT be in LD_PRELOAD
    // QEMU will dlopen them at runtime instead; they stay accessible via LD_LIBRARY_PATH
    private val desktopOnlyPrefixes = listOf(
        "libgst",       // All GStreamer plugins (libgstgl, libgstvideo, etc.)
        "libGL.so",     // Desktop OpenGL (not GLES)
        "libGLX",       // GLX (X11 OpenGL)
        "libGLdispatch",// OpenGL dispatch layer
        "libX11",       // X11 display system
        "libxcb",       // XCB (X11 protocol)
        "libX11-xcb",   // X11/XCB bridge
        "libxcb-glx",   // GLX-XCB extension
        "libxcb-dri",   // DRI XCB extensions
        "libxcb-randr", // RandR XCB extension
        "libxkb",       // XKB keyboard
        "libXext",      // X11 extensions
        "libdrm",       // Direct Rendering Manager
        "libgbm"        // Generic Buffer Manager
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
                
                // Active dual SONAME patching for libz
                if (fileName == "libz.so.1" || fileName == "libz.so") {
                    try {
                        val originalBytes = file.readBytes()
                        
                        // 1. Write the ORIGINAL unpatched bytes to libz.so.1 (SONAME stays libz.so.1)
                        val libz1Dest = File(injectLibDir, "libz.so.1")
                        if (!libz1Dest.exists()) {
                            libz1Dest.writeBytes(originalBytes)
                            libz1Dest.setExecutable(true, false)
                        }
                        
                        // 2. Clone bytes and patch the clone for libz.so (SONAME becomes libz.so)
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
                        return // Fully processed
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

            // Remove unversioned symlinks created by EngineExtractor that shadow Android system libs.
            // e.g. filesDir/libEGL.so → libEGL.so.1 would intercept libgui.so's NEEDED via LD_LIBRARY_PATH.
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
