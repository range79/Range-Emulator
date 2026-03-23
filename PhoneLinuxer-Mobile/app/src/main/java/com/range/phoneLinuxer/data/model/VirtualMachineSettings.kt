package com.range.phoneLinuxer.data.model

import com.range.phoneLinuxer.data.enums.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class VirtualMachineSettings(
    val id: String = UUID.randomUUID().toString(),
    val vmName: String,
    val cpuModel: CpuModel = CpuModel.MAX,
    val cpuCores: Int = 4,
    val ramMB: Int = 2048,
    val screenType: ScreenType = ScreenType.VNC,

    val screenWidth: Int = 1280,
    val screenHeight: Int = 720,

    val isGpuEnabled: Boolean = true,
    val rdpPort: Int = 3389,
    val vncPort: Int = 5900,

    val isoUris: List<String> = emptyList(),
    val diskImgPath: String? = null,
    val diskFormat: DiskFormat = DiskFormat.QCOW2,
    val diskSizeGB: Int = 20,

    val easyInstall: Boolean = false,
    val easyInstallSettings: EasyInstallSettings? = null,
    val networkMode: NetworkMode = NetworkMode.USER,
    val createdAt: Long = System.currentTimeMillis(),
    val state: VmState = VmState.INACTIVE,
)

fun VirtualMachineSettings.buildFullCommand(isSetupMode: Boolean = false): List<String> {
    val cmd = mutableListOf<String>()

    cmd.add("qemu_executable")

    cmd.add("-machine")
    cmd.add("virt,highmem=off")

    cmd.add("-cpu")
    cmd.add(if (cpuModel == CpuModel.HOST) "host" else cpuModel.toQemuParam())

    cmd.add("-accel")
    cmd.add("tcg,thread=multi")

    cmd.add("-smp")
    cmd.add(cpuCores.toString())
    cmd.add("-m")
    cmd.add(ramMB.toString())

    if (isSetupMode || easyInstall) {
        isoUris.forEachIndexed { index, uri ->
            if (index == 0) {
                cmd.add("-cdrom")
                cmd.add(uri)
            } else {
                cmd.add("-drive")
                cmd.add("file=$uri,media=cdrom,if=none,id=cd$index")
                cmd.add("-device")
                cmd.add("virtio-blk-pci,drive=cd$index")
            }
        }
    }

    diskImgPath?.let {
        val formatName = diskFormat.name.lowercase()
        cmd.add("-drive")
        cmd.add("file=$it,format=$formatName,if=virtio,cache=writeback")
    }

    cmd.addAll(getDisplayArgs())

    cmd.addAll(getNetworkArgs())

    cmd.add("-device")
    cmd.add("virtio-tablet-pci")
    cmd.add("-device")
    cmd.add("virtio-keyboard-pci")

    cmd.add("-serial")
    cmd.add("stdio")

    return cmd
}

private fun VirtualMachineSettings.getNetworkArgs(): List<String> {
    val args = mutableListOf<String>()
    when (networkMode) {
        NetworkMode.USER -> {
            val rdpForward = if (easyInstall || screenType == ScreenType.RDP) ",hostfwd=tcp::$rdpPort-:3389" else ""
            args.add("-netdev")
            args.add("user,id=net0$rdpForward")
            args.add("-device")
            args.add("virtio-net-pci,netdev=net0")
        }
        NetworkMode.NONE -> {
            args.add("-net")
            args.add("none")
        }
    }
    return args
}

private fun VirtualMachineSettings.getDisplayArgs(): List<String> {
    val args = mutableListOf<String>()

    val vncIndex = vncPort - 5900

    if (easyInstall) {
        args.add("-display")
        args.add("none")
        if (isGpuEnabled) {
            args.add("-device")
            args.add("virtio-gpu-pci,xres=$screenWidth,yres=$screenHeight")
        }
    } else {
        if (screenType == ScreenType.VNC) {
            args.add("-display")
            args.add("vnc=127.0.0.1:$vncIndex")

            if (isGpuEnabled) {
                args.add("-device")
                args.add("virtio-gpu-pci,xres=$screenWidth,yres=$screenHeight")
            } else {
                args.add("-device")
                args.add("ramfb")
            }
        } else {
            args.add("-display")
            args.add("none")
            if (isGpuEnabled) {
                args.add("-device")
                args.add("virtio-gpu-pci")
            }
        }
    }
    return args
}