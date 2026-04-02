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
    val spicePort: Int = 5901,
    val vncPort: Int = 5900,

    val isoUris: List<String> = emptyList(),
    val diskImgPath: String? = null,
    val diskFormat: DiskFormat = DiskFormat.QCOW2,
    val diskSizeGB: Int = 20,

    val easyInstall: Boolean = false,
    val easyInstallSettings: EasyInstallSettings? = null,
    val networkMode: NetworkMode = NetworkMode.USER,
    val diskAioMode: DiskAioMode = DiskAioMode.THREADS,
    val createdAt: Long = System.currentTimeMillis(),
    val state: VmState = VmState.INACTIVE,
)

fun VirtualMachineSettings.buildFullCommand(isSetupMode: Boolean = false): List<String> {
    val cmd = mutableListOf<String>()

    cmd.add("qemu_executable")

    cmd.add("-machine")
    cmd.add("virt,gic-version=max,its=on,highmem=on")

    cmd.add("-cpu")
    cmd.add(if (cpuModel == CpuModel.HOST) "host" else "${cpuModel.toQemuParam()},pauth-impdef=on,lse=on")

    cmd.add("-accel")
    if (cpuModel == CpuModel.HOST) {
        cmd.add("kvm:tcg,thread=multi,tb-size=2048")
    } else {
        cmd.add("tcg,thread=multi,tb-size=2048")
    }

    cmd.add("-object")
    cmd.add("iothread,id=iothread0")

    cmd.add("-smp")
    cmd.add(cpuCores.toString())
    cmd.add("-m")
    cmd.add("${ramMB}M")

    if (isoUris.isNotEmpty() || isSetupMode || easyInstall) {
        isoUris.forEachIndexed { index, uri ->
            val aio = diskAioMode.name.lowercase()
            val cache = if (diskAioMode == DiskAioMode.IO_URING) "none" else "writeback"
            cmd.add("-drive")
            cmd.add("file=$uri,format=raw,if=none,id=cd$index,readonly=on,cache=$cache,aio=$aio")
            cmd.add("-device")
            cmd.add("virtio-blk-pci,drive=cd$index,bootindex=${index},iothread=iothread0,disable-legacy=on,disable-modern=off")
        }
    }

    diskImgPath?.let {
        val formatName = diskFormat.name.lowercase()
        val aio = diskAioMode.name.lowercase()
        val cache = if (diskAioMode == DiskAioMode.IO_URING) "none" else "writeback"
        cmd.add("-drive")
        cmd.add("file=$it,format=$formatName,if=none,id=drive0,cache=$cache,discard=on,detect-zeroes=on,aio=$aio")
        cmd.add("-device")
        cmd.add("virtio-blk-pci,drive=drive0,bootindex=${isoUris.size + 10},iothread=iothread0,disable-legacy=on,disable-modern=off")
    }

    cmd.addAll(getDisplayArgs())

    cmd.addAll(getNetworkArgs())

    cmd.add("-device")
    cmd.add("qemu-xhci,id=usb")
    
    cmd.add("-device")
    cmd.add("usb-tablet,bus=usb.0")
    cmd.add("-device")
    cmd.add("usb-kbd,bus=usb.0")

    cmd.add("-device")
    cmd.add("virtio-tablet-pci")
    cmd.add("-device")
    cmd.add("virtio-keyboard-pci")
    cmd.add("-device")
    cmd.add("virtio-rng-pci")

    cmd.add("-nodefaults")
    cmd.add("-no-user-config")
    cmd.add("-rtc")
    cmd.add("base=utc,clock=rt,driftfix=slew")

    cmd.add("-serial")
    cmd.add("stdio")

    return cmd
}

private fun VirtualMachineSettings.getNetworkArgs(): List<String> {
    val args = mutableListOf<String>()
    when (networkMode) {
        NetworkMode.USER -> {
            args.add("-netdev")
            args.add("user,id=net0,hostfwd=tcp::2222-:22")
            args.add("-device")
            args.add("virtio-net-pci,netdev=net0,disable-legacy=on,disable-modern=off")
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

    args.add("-device")
    args.add("ramfb")

    if (isGpuEnabled) {
        args.add("-device")
        val resParams = if (screenWidth > 0 && screenHeight > 0) ",xres=$screenWidth,yres=$screenHeight" else ""
        args.add("virtio-gpu-pci${resParams},edid=on,disable-legacy=on,disable-modern=off")
    }

    args.add("-display")
    args.add("none")

    args.add("-vga")
    args.add("none")

    args.add("-bios")
    args.add("edk2-aarch64-code.fd")

    val vncIndex = vncPort - 5900
    args.add("-vnc")
    args.add("0.0.0.0:$vncIndex")

    if (screenType == ScreenType.SPICE) {
        args.add("-spice")
        args.add("port=$spicePort,addr=0.0.0.0,disable-ticketing=on")

        args.add("-device")
        args.add("virtio-serial-pci,disable-legacy=on,disable-modern=off")
        args.add("-device")
        args.add("virtserialport,chardev=spicechannel0,name=com.redhat.spice.0")
        args.add("-chardev")
        args.add("spicevmc,id=spicechannel0,name=vdagent")
    }

    return args
}