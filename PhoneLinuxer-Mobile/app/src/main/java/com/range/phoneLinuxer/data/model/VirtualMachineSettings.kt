package com.range.phoneLinuxer.data.model

import com.range.phoneLinuxer.data.enums.CpuModel
import com.range.phoneLinuxer.data.enums.NetworkMode
import com.range.phoneLinuxer.data.enums.ScreenType
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
    val createdAt: Long = System.currentTimeMillis()
)

fun VirtualMachineSettings.buildFullCommand(): List<String> {
    val cmd = mutableListOf<String>()

    cmd.add("-name")
    cmd.add(vmName)

    cmd.add("-cpu")
    cmd.add(if (cpuModel == CpuModel.HOST) "host" else cpuModel.toString().lowercase())
    cmd.add("-smp")
    cmd.add(cpuCores.toString())

    cmd.add("-m")
    cmd.add(ramMB.toString())

    isoUri?.let {
        cmd.add("-cdrom")
        cmd.add(it)
    }

    diskImgPath?.let {
        cmd.add("-drive")
        cmd.add("file=$it,format=qcow2")
    }

    cmd.addAll(getDisplayArgs())
    cmd.addAll(getNetworkArgs())

    return cmd
}

private fun VirtualMachineSettings.getNetworkArgs(): List<String> {
    return when (networkMode) {
        NetworkMode.USER -> listOf("-netdev", "user,id=net0", "-device", "virtio-net-pci,netdev=net0")
        NetworkMode.NONE -> listOf("-net", "none")
    }
}

private fun VirtualMachineSettings.getDisplayArgs(): List<String> {
    return if (screenType == ScreenType.VNC) {
        listOf("-vnc", ":${vncPort - 5900}", "-vga", "std")
    } else {
        listOf("-vga", "std", "-netdev", "user,id=net1,hostfwd=tcp::$rdpPort-:3389")
    }
}