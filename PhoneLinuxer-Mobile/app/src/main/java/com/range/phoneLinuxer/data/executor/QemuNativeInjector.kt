package com.range.phoneLinuxer.data.executor

import android.content.Context
import timber.log.Timber
import java.io.File

object QemuNativeInjector {
    private var isLoaded = false

    fun injectAllLibs(context: Context) {
        if (isLoaded) return

        try {
            // Android'in kütüphaneleri sakladığı sistem dizini
            val libDir = File(context.applicationInfo.nativeLibraryDir)

            if (libDir.exists() && libDir.isDirectory) {
                val libs = libDir.listFiles { file ->
                    file.extension == "so"
                }?.map { it.name.removePrefix("lib").removeSuffix(".so") } ?: emptyList()

                // Kritik: QEMU kütüphanesi en son yüklenmelidir çünkü diğerlerine bağımlıdır.
                // Önce bağımlılıkları (glib, pixman, ffi vb.) yüklüyoruz.
                val sortedLibs = libs.sortedWith(compareBy {
                    if (it.contains("qemu")) 1 else 0
                })

                sortedLibs.forEach { libName ->
                    try {
                        System.loadLibrary(libName)
                        Timber.Forest.tag("Injector").d("Injected: lib$libName.so")
                    } catch (e: UnsatisfiedLinkError) {
                        // Bazı kütüphaneler o an yüklenemezse (bağımlılık sırası)
                        // bir sonraki turda tekrar denenebilir veya loglanabilir.
                        Timber.Forest.tag("Injector").w("Pending or Failed: $libName - ${e.message}")
                    }
                }

                isLoaded = true
                Timber.Forest.tag("Injector").i("Tüm jniLibs içeriği başarıyla inject edildi.")
            }
        } catch (e: Exception) {
            Timber.Forest.tag("Injector").e(e, "Toplu injection işlemi başarısız!")
        }
    }
}