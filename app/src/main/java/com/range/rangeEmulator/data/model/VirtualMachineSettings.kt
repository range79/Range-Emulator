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
    val isTpmEnabled: Boolean = false,
    val arch: Architecture = Architecture.AARCH64,

    val isCacheUnsafe: Boolean = false,
    val isIoThreadEnabled: Boolean = true,
    val isDiscardEnabled: Boolean = true,
    val is4kAlignmentEnabled: Boolean = false,
    val isDetectZeroesEnabled: Boolean = true,
    val isMemPreallocEnabled: Boolean = false,
    val isGicV3Enabled: Boolean = true,

    val sshPort: Int = 2222,
    val createdAt: Long = System.currentTimeMillis(),
    val state: VmState = VmState.INACTIVE,
)

fun VirtualMachineSettings.buildFullCommand(tpmSockPath: String? = null, isSetupMode: Boolean = false): List<String> {
    val cmd = mutableListOf<String>()

    cmd.add("qemu_executable")

    // Optimized performance is now standard
    cmd.add("-overcommit")
    cmd.add("cpu-pm=on")

    // Consolidated Machine Configuration
    cmd.add("-machine")
    if (arch == Architecture.AARCH64) {
        val gic = if (isTitanModeEnabled || isGicV3Enabled) "3" else "max"
        // Windows ARM requires virtualization=on (EL2), ACPI and ITS for stable boot
        val machineBase = "virt,gic-version=$gic,its=on,highmem=on,acpi=on"
        val machineArgs = if (osType == OsType.WINDOWS || isTitanModeEnabled) {
            "$machineBase,virtualization=on"
        } else {
            machineBase
        }
        cmd.add(machineArgs)
    } else {
        cmd.add("q35")
    }

    cmd.add("-boot")
    cmd.add("menu=on,strict=on")

    cmd.add("-cpu")
    val cpuParam = if (cpuModel == CpuModel.HOST) "host" else cpuModel.toQemuParam()
    if (cpuModel == CpuModel.HOST) {
        cmd.add("$cpuParam,pmu=on,lse=on,host-cache-info=on,l3-cache=on")
    } else if (arch == Architecture.AARCH64 && cpuModel == CpuModel.MAX) {
        // Disable pauth for Windows ARM to avoid TCG exception conflicts in some UEFI versions
        val pauth = if (osType == OsType.WINDOWS) ",pauth=off" else ",pauth=on"
        cmd.add("$cpuParam$pauth")
    } else {
        cmd.add(cpuParam)
    }

    cmd.add("-accel")
    val titanMultiplier = if (isTitanModeEnabled) 2 else 1
    val finalTbSize = tbSizeMB.coerceAtLeast(1024 * titanMultiplier)
    if (cpuModel == CpuModel.HOST) {
        cmd.add("kvm:tcg,thread=multi,tb-size=${finalTbSize}")
    } else {
        cmd.add("tcg,thread=multi,tb-size=${finalTbSize}")
    }

    // IOThreads and performance flags are now default
    cmd.add("-object")
    val pollArgs = ",poll-max-ns=32768,poll-grow=2,poll-shrink=2"
    cmd.add("iothread,id=iothread0$pollArgs")

    cmd.add("-device")
    cmd.add("qemu-xhci,id=usb,rombar=0")
    cmd.add("-device")
    cmd.add("virtio-scsi-pci,id=scsi0,iothread=iothread0,disable-legacy=on,disable-modern=off,rombar=0")
    
    // Mouse Support: Use USB Tablet for absolute coordinate sync in SPICE/Windows
    // Now safe since 4K block size conflict is resolved
    if (arch == Architecture.AARCH64 && osType == OsType.WINDOWS) {
        cmd.add("-device")
        cmd.add("usb-tablet,bus=usb.0")
    }

    if (cpuCores > 1) {
        cmd.add("-smp")
        cmd.add("cores=$cpuCores,threads=1,sockets=1")
    } else {
        cmd.add("-smp")
        cmd.add("1")
    }

    cmd.add("-m")
    cmd.add("${ramMB}M")
    if (isTitanModeEnabled || isMemPreallocEnabled) {
        cmd.add("-mem-prealloc")
    }

    if (isoUris.isNotEmpty() || isSetupMode || easyInstall) {
        isoUris.forEachIndexed { index, uri ->
            val driveId = "cd$index"
            // Restore missing -drive flag and use high performance parameters
            cmd.add("-drive")
            cmd.add("file=$uri,format=raw,if=none,id=$driveId,readonly=on,cache=unsafe,aio=threads,discard=on")
            
            val is4kSafe = if (osType == OsType.WINDOWS && isTitanModeEnabled) false else (isTitanModeEnabled || is4kAlignmentEnabled)
            val blockArgs = if (is4kSafe) ",logical_block_size=4096,physical_block_size=4096" else ""
            
            cmd.add("-device")
            if (arch == Architecture.AARCH64 && osType == OsType.WINDOWS) {
                // Use modernized SCSI-CD for best compatibility with Windows/ARM installers
                cmd.add("scsi-cd,drive=$driveId,bus=scsi0.0,bootindex=$index")
            } else if (arch == Architecture.AARCH64) {
                // Use scsi-cd on Windows/ARM for standard CD-ROM emulation
                cmd.add("scsi-cd,drive=$driveId,bus=scsi0.0,bootindex=$index")
            } else {
                val vectors = cpuCores * 2 + 2
                cmd.add("virtio-blk-pci,drive=$driveId,bootindex=$index,iothread=iothread0,num-queues=$cpuCores,vectors=$vectors,disable-legacy=on,disable-modern=off$blockArgs")
            }
        }
    }

    if (isTpmEnabled && tpmSockPath != null) {
        cmd.add("-chardev")
        cmd.add("socket,id=chrtpm,path=$tpmSockPath")
        cmd.add("-tpmdev")
        cmd.add("emulator,id=tpm0,chardev=chrtpm")
        cmd.add("-device")
        val tpmDevice = if (arch == Architecture.AARCH64) "tpm-tis-device" else "tpm-tis"
        cmd.add("$tpmDevice,tpmdev=tpm0")
    }

    disks.forEachIndexed { index, disk ->
        val formatName = disk.format.name.lowercase()
        // Force unsafe cache for all virtual disks to maximize throughput
        val cacheMode = "unsafe"
        val discard = "on" 
        val detectZeroes = "on" 
        
        val driveId = "drive$index"
        cmd.add("-drive")
        cmd.add("file=${disk.path},format=$formatName,if=none,id=$driveId,cache=$cacheMode,discard=$discard,detect-zeroes=$detectZeroes,aio=threads")
        
        cmd.add("-device")

        val is4kSafe = if (osType == OsType.WINDOWS && isTitanModeEnabled) false else (isTitanModeEnabled || is4kAlignmentEnabled)

        if (diskInterface == DiskInterface.NVME) {
            // Use single-device NVMe for maximum compatibility with Windows built-in drivers
            val blockArgs = if (is4kSafe) ",logical_block_size=4096,physical_block_size=4096" else ""
            cmd.add("nvme,drive=$driveId,serial=vdisk$index,bootindex=${isoUris.size + 10 + index},rombar=0$blockArgs")
        } else {
            val vectors = cpuCores * 2 + 2
            val blockSize = if (is4kSafe) 4096 else 512
            val ioThreadArg = ",iothread=iothread0" 
            cmd.add("virtio-blk-pci,drive=$driveId,bootindex=${isoUris.size + 10 + index}$ioThreadArg,num-queues=$cpuCores,vectors=$vectors,logical_block_size=$blockSize,physical_block_size=$blockSize,disable-legacy=on,disable-modern=off,rombar=0")
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
    } else {
        "base=utc,clock=host"
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
            args.add("user,id=net0,net=10.0.2.0/24,dhcpstart=10.0.2.15,dns=1.1.1.1,dns=8.8.8.8,hostfwd=tcp::$sshPort-:22")
            args.add("-device")
            
            val netDevice = if (osType == OsType.WINDOWS) {
                // Windows ARM likes modern virtio-net
                "virtio-net-pci,netdev=net0,disable-legacy=on,disable-modern=off,rombar=0"
            } else {
                val turboNet = ",mrg_rxbuf=on" 
                "virtio-net-pci,netdev=net0,disable-legacy=on,disable-modern=off,rombar=0$turboNet"
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

    val isArm = arch == Architecture.AARCH64
    // Deterministic Graphics Configuration
    args.add("-vga")
    args.add("none")

    // For AArch64 Windows, ramfb is a sysbus device for early boot
    if (osType == OsType.WINDOWS && isArm) {
        args.add("-device")
        args.add("ramfb")
    }

    if (isGpuEnabled) {
        val resParams = if (screenWidth > 0 && screenHeight > 0) ",xres=$screenWidth,yres=$screenHeight" else ""
        if (isArm) {
            // Explicitly use PCI version for GPU on ARM virt machine
            args.add("-device")
            args.add("virtio-gpu-pci${resParams},edid=on,disable-legacy=on,disable-modern=off,rombar=0")
        } else {
            args.add("-device")
            args.add("virtio-vga${resParams},edid=on,disable-legacy=on,disable-modern=off,rombar=0")
        }
    } else {
        if (isArm) {
            args.add("-device")
            args.add("virtio-gpu-pci,edid=on,disable-legacy=on,disable-modern=off,rombar=0")
        } else {
            args.add("-device")
            args.add("virtio-vga,edid=on,disable-legacy=on,disable-modern=off,rombar=0")
        }
    }

    args.add("-display")
    args.add("none")

    val biosFile = when (arch) {
        Architecture.AARCH64 -> "pc-bios/edk2-aarch64-code.fd"
        Architecture.X86_64 -> "pc-bios/edk2-x86_64-code.fd"
    }
    
    // For AArch64 virt machine, pflash is the modern way to load UEFI
    if (arch == Architecture.AARCH64) {
        args.add("-drive")
        args.add("if=pflash,format=raw,unit=0,readonly=on,file=$biosFile")
        // We add a variables store if it exists or can be created in the app's logic
        // For now, we use the code fd as unit 0. 
    } else {
        args.add("-bios")
        args.add(biosFile)
    }

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