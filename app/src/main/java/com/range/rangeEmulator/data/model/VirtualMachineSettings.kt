package com.range.rangeEmulator.data.model

import com.range.rangeEmulator.data.enums.*
import kotlinx.serialization.Serializable
import java.util.UUID

enum class DiskInterface { VIRTIO, NVME }

@Serializable
data class DiskConfig(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val path: String,
    val format: DiskFormat = DiskFormat.QCOW2,
    val sizeGB: Int = 20
)

@Serializable
data class VirtualMachineSettings(
    val id: String = UUID.randomUUID().toString(),
    val vmName: String,
    val osType: OsType = OsType.LINUX,
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
    val disks: List<DiskConfig> = emptyList(),
    val diskInterface: DiskInterface = DiskInterface.VIRTIO,

    val easyInstall: Boolean = false,
    val easyInstallSettings: EasyInstallSettings? = null,
    val networkMode: NetworkMode = NetworkMode.USER,
    val tbSizeMB: Int = 512,
    val isTurboEnabled: Boolean = true,
    val isTitanModeEnabled: Boolean = false,
    val sshPort: Int = 2222,
    val createdAt: Long = System.currentTimeMillis(),
    val state: VmState = VmState.INACTIVE,
)

fun VirtualMachineSettings.buildFullCommand(isSetupMode: Boolean = false): List<String> {
    val cmd = mutableListOf<String>()

    cmd.add("qemu_executable")

    if (isTurboEnabled) {
        cmd.add("-overcommit")
        cmd.add("cpu-pm=on")
    }

    cmd.add("-machine")
    if (cpuModel.getArch() == "aarch64") {
        val gic = if (isTitanModeEnabled) "3" else "max"
        cmd.add("virt,gic-version=$gic,its=on,highmem=on")
    } else {
        cmd.add("q35")
    }

    cmd.add("-boot")
    cmd.add("menu=on,strict=on")

    cmd.add("-cpu")
    val cpuParam = if (cpuModel == CpuModel.HOST) "host" else cpuModel.toQemuParam()
    if (cpuModel == CpuModel.HOST && isTurboEnabled) {
        cmd.add("$cpuParam,pmu=on,lse=on,host-cache-info=on,l3-cache=on")
    } else if (cpuModel.getArch() == "aarch64" && cpuModel == CpuModel.MAX) {
        val pauth = if (osType == OsType.WINDOWS) ",pauth=on" else ""
        cmd.add("$cpuParam$pauth")
    } else {
        cmd.add(cpuParam)
    }

    cmd.add("-accel")
    val titanMultiplier = if (isTitanModeEnabled) 2 else 1
    val finalTbSize = if (isTurboEnabled) tbSizeMB.coerceAtLeast(1024 * titanMultiplier) else tbSizeMB
    if (cpuModel == CpuModel.HOST) {
        cmd.add("kvm:tcg,thread=multi,tb-size=${finalTbSize}")
    } else {
        cmd.add("tcg,thread=multi,tb-size=${finalTbSize}")
    }

    cmd.add("-object")
    cmd.add("iothread,id=iothread0")

    cmd.add("-device")
    cmd.add("qemu-xhci,id=usb")
    cmd.add("-device")
    cmd.add("virtio-scsi-pci,id=scsi0")

    if (cpuCores > 1) {
        cmd.add("-smp")
        cmd.add("cores=$cpuCores,threads=1,sockets=1")
    } else {
        cmd.add("-smp")
        cmd.add("1")
    }

    cmd.add("-m")
    cmd.add("${ramMB}M")
    if (isTitanModeEnabled) {
        cmd.add("-mem-prealloc")
    }

    if (isoUris.isNotEmpty() || isSetupMode || easyInstall) {
        isoUris.forEachIndexed { index, uri ->
            cmd.add("-drive")
            cmd.add("file=$uri,format=raw,if=none,id=cd$index,readonly=on,cache=unsafe,aio=threads")
            cmd.add("-device")
            cmd.add("scsi-cd,drive=cd$index,bootindex=$index")
        }
    }

    disks.forEachIndexed { index, disk ->
        val formatName = disk.format.name.lowercase()
        val cacheMode = if (isTitanModeEnabled) "unsafe" else "writeback"
        val driveId = "drive$index"
        cmd.add("-drive")
        cmd.add("file=${disk.path},format=$formatName,if=none,id=$driveId,cache=$cacheMode,discard=on,detect-zeroes=on,aio=threads")
        
        cmd.add("-device")
        val isArm = cpuModel.getArch() == "aarch64"
        val isWindows = osType == OsType.WINDOWS

        if (isWindows && diskInterface == DiskInterface.NVME) {
            cmd.add("nvme,drive=$driveId,serial=virtio-disk$index,bootindex=${isoUris.size + 10 + index}")
        } else {
            val vectors = cpuCores * 2 + 2
            val packed = if (isTitanModeEnabled) ",packed=on" else ""
            val blockSize = if (isArm) 4096 else 512
            cmd.add("virtio-blk-pci,drive=$driveId,bootindex=${isoUris.size + 10 + index},iothread=iothread0,num-queues=$cpuCores,vectors=$vectors,logical_block_size=$blockSize,physical_block_size=$blockSize$packed,disable-legacy=on,disable-modern=off")
        }
    }

    cmd.addAll(getDisplayArgs())

    cmd.addAll(getNetworkArgs())

    cmd.add("-device")
    cmd.add("usb-tablet,bus=usb.0")
    cmd.add("-device")
    cmd.add("usb-kbd,bus=usb.0")

    cmd.add("-device")
    cmd.add("virtio-tablet-pci,disable-legacy=on,disable-modern=off")
    cmd.add("-device")
    cmd.add("virtio-keyboard-pci,disable-legacy=on,disable-modern=off")
    cmd.add("-device")
    cmd.add("virtio-rng-pci,disable-legacy=on,disable-modern=off")

    cmd.add("-nodefaults")
    cmd.add("-no-user-config")
    cmd.add("-rtc")
    val rtcArgs = if (isTitanModeEnabled) {
        "base=utc,clock=host,driftfix=none"
    } else if (isTurboEnabled) {
        "base=utc,clock=host"
    } else if (cpuModel.getArch() == "aarch64") {
        "base=utc,clock=rt"
    } else {
        "base=utc,clock=rt,driftfix=slew"
    }
    cmd.add(rtcArgs)

    cmd.add("-serial")
    cmd.add("stdio")

    return cmd
}

private fun VirtualMachineSettings.getNetworkArgs(): List<String> {
    val args = mutableListOf<String>()
    when (networkMode) {
        NetworkMode.USER -> {
            args.add("-netdev")
            args.add("user,id=net0,hostfwd=tcp::$sshPort-:22,dns=8.8.8.8,dns=1.1.1.1")
            args.add("-device")
            
            val netDevice = if (osType == OsType.WINDOWS) {
                "e1000-82545em,netdev=net0"
            } else {
                "virtio-net-pci,netdev=net0,disable-legacy=on,disable-modern=off"
            }
            args.add(netDevice)
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

    val isArm = cpuModel.getArch() == "aarch64"
    
    if (isGpuEnabled) {
        if (osType == OsType.WINDOWS && isArm) {
            args.add("-device")
            args.add("ramfb")
        }
        
        args.add("-device")
        val resParams = if (screenWidth > 0 && screenHeight > 0) ",xres=$screenWidth,yres=$screenHeight" else ""
        
        val gpuDevice = if (isArm) "virtio-gpu-pci" else "virtio-vga"
        args.add("${gpuDevice}${resParams},edid=on,disable-legacy=on,disable-modern=off")
    } else {
        if (osType == OsType.WINDOWS && isArm) {
            args.add("-device")
            args.add("ramfb")
        }
        args.add("-device")
        val gpuDevice = if (isArm) "virtio-gpu-pci" else "virtio-vga"
        args.add("${gpuDevice},edid=on,disable-legacy=on,disable-modern=off")
    }

    args.add("-display")
    args.add("none")

    val arch = cpuModel.getArch()
    val biosFile = when (arch) {
        "aarch64" -> "edk2-aarch64-code.fd"
        "x86_64" -> "edk2-x86_64-code.fd"
        else -> "edk2-i386-code.fd"
    }
    args.add("-bios")
    args.add(biosFile)

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