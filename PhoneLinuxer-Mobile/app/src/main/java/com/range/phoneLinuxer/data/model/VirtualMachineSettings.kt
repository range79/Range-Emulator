package com.range.phoneLinuxer.data.model

import com.range.phoneLinuxer.data.enums.CpuModel
import com.range.phoneLinuxer.data.enums.NetworkMode
import com.range.phoneLinuxer.data.enums.ScreenType
import com.range.phoneLinuxer.data.enums.VmState
import kotlinx.serialization.Serializable


@Serializable
data class VirtualMachineSettings(
    val id: String = java.util.UUID.randomUUID().toString(),
    val vmName: String,
    val cpuModel: CpuModel = CpuModel.MAX,
    val cpuCores: Int = 4,
    val ramMB: Int = 2048,
    val screenType: ScreenType = ScreenType.VNC,
    val screenResolution: String = "1024x768",
    val isGpuEnabled: Boolean = true,
    val rdpPort: Int = 3389,
    val vncPort: Int = 5900,
    val isoUri: String? = null,
    val diskImgPath: String? = null,
    val diskSizeGB: Int = 20,
    val easyInstall: Boolean = false,
    val easyInstallSettings: EasyInstallSettings? = null,
    val networkMode: NetworkMode = NetworkMode.USER,
    val createdAt: Long = System.currentTimeMillis(),
    val state: VmState= VmState.INACTIVE,
)

fun VirtualMachineSettings.buildFullCommand(): List<String> {
    val cmd = mutableListOf<String>()

    cmd.add("qemu-system-x86_64")

    cmd.add("-name")
    cmd.add(vmName)

    cmd.add("-cpu")
    cmd.add(if (cpuModel == CpuModel.HOST) "host" else "max")

    cmd.add("-smp")
    cmd.add(cpuCores.toString())

    cmd.add("-accel")
    cmd.add("tcg,thread=multi")

    cmd.add("-m")
    cmd.add(ramMB.toString())

    isoUri?.let {
        cmd.add("-cdrom")
        cmd.add(it)
    }

    diskImgPath?.let {
        cmd.add("-drive")
        cmd.add("file=$it,format=qcow2,if=virtio,cache=writeback")
    }

    cmd.addAll(getDisplayArgs())
    cmd.addAll(getNetworkArgs())

    cmd.add("-usb")
    cmd.add("-device")
    cmd.add("usb-tablet")

    return cmd
}

private fun VirtualMachineSettings.getNetworkArgs(): List<String> {
    return when (networkMode) {
        NetworkMode.USER -> listOf(
            "-netdev", "user,id=net0",
            "-device", "virtio-net-pci,netdev=net0"
        )
        NetworkMode.NONE -> listOf("-net", "none")
    }
}

private fun VirtualMachineSettings.getDisplayArgs(): List<String> {
    val args = mutableListOf<String>()

    if (screenType == ScreenType.VNC) {
        val vncDisplayIndex = vncPort - 5900
        args.add("-vnc")
        args.add(":$vncDisplayIndex")

        if (isGpuEnabled) {
            args.addAll(listOf("-vga", "virtio", "-display", "vnc"))
        } else {
            args.addAll(listOf("-vga", "std"))
        }
    } else {
        args.addAll(listOf(
            "-vga", "std",
            "-netdev", "user,id=net1,hostfwd=tcp::$rdpPort-:3389"
        ))
    }
    return args
}