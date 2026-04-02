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
    cmd.add("virt")

    cmd.add("-cpu")
    cmd.add(if (cpuModel == CpuModel.HOST) "host" else cpuModel.toQemuParam())

    cmd.add("-accel")
    cmd.add("tcg,thread=multi")

    // Use virtio-scsi-pci for all optical and disk drives for better EFI compatibility
    cmd.add("-device")
    cmd.add("virtio-scsi-pci,id=scsi0")

    cmd.add("-smp")
    cmd.add(cpuCores.toString())
    cmd.add("-m")
    cmd.add(ramMB.toString())

    // Always include ISOs if present, or if in setup mode
    if (isoUris.isNotEmpty() || isSetupMode || easyInstall) {
        isoUris.forEachIndexed { index, uri ->
            cmd.add("-drive")
            cmd.add("file=$uri,format=raw,if=none,id=cd$index,readonly=on")
            cmd.add("-device")
            // Use bootindex for AArch64 virt machine instead of -boot order
            cmd.add("scsi-cd,drive=cd$index,bus=scsi0.0,bootindex=${index}")
        }
    }

    diskImgPath?.let {
        val formatName = diskFormat.name.lowercase()
        cmd.add("-drive")
        cmd.add("file=$it,format=$formatName,if=none,id=drive0,cache=writeback")
        cmd.add("-device")
        // Hard disk gets a lower priority boot index than CD-ROMs
        cmd.add("scsi-hd,drive=drive0,bus=scsi0.0,bootindex=${isoUris.size + 10}")
    }

    cmd.addAll(getDisplayArgs())

    cmd.addAll(getNetworkArgs())

    // Add XHCI USB controller for input devices and better peripheral support
    cmd.add("-device")
    cmd.add("qemu-xhci,id=usb")
    
    // USB-based input devices are more universally supported than VirtIO-PCI in early boot/installers
    cmd.add("-device")
    cmd.add("usb-tablet,bus=usb.0")
    cmd.add("-device")
    cmd.add("usb-kbd,bus=usb.0")

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

    if (easyInstall && screenType != ScreenType.VNC) {
        args.add("-display")
        args.add("none")
        if (isGpuEnabled) {
            args.add("-device")
            args.add("virtio-gpu-pci,xres=$screenWidth,yres=$screenHeight")
        }
    } else {
        if (screenType == ScreenType.VNC) {
            // Switch back to -display vnc syntax which often bridges device heads more effectively
            args.add("-display")
            args.add("vnc=0.0.0.0:$vncIndex")

            // Explicitly disable default VGA to avoid conflicts on ARM virt machine
            args.add("-vga")
            args.add("none")

            // Add BIOS for ARM virt machine boot support
            args.add("-bios")
            args.add("edk2-aarch64-code.fd")

            if (isGpuEnabled) {
                args.add("-device")
                args.add("virtio-gpu-pci,xres=$screenWidth,yres=$screenHeight,edid=on")
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