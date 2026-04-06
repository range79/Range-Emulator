<p align="center">
 <img width="200" height="200" alt="Image" src="https://github.com/user-attachments/assets/4f4eb2b3-e367-49eb-b7a9-b8e390760840" /> 
</p>

# RangeEmulator: Desktop-Class Virtualization on Your Android

**RangeEmulator** is a powerful yet simple tool that lets you run full desktop operating systems like Linux and Windows directly on your Android phone. By using modern virtualization technology, it turns your mobile device into a portable workstation.

---

## Why Use Your Phone?

Modern flagship Android devices (equipped with Snapdragon 8 Gen 2 / Gen 3 or similar) are often **more powerful** than older desktop computers or budget laptops. 

A high-end phone running RangeEmulator can outperform a 1st Generation Intel Core i3 PC. If you have a powerful mobile device, why settle for a slow old computer? RangeEmulator lets you harness that power for programming, professional tools, or lightweight gaming anywhere you go.

---

## Pro Features

### Zero-Copy Direct Access
Don't waste time and storage copying multi-GB disk images. Use **Direct Path Resolution** to mount `.qcow2` and `.iso` files directly from your phone's internal storage. Immediate boot, zero overhead.

### Multi-Disk Management
- **Custom Labels:** Organize your storage with custom names like "System", "Games", or "Workspace".
- **Dynamic Addition:** Add or remove disks on the fly during VM configuration.

### Concurrent Multi-VM Engine
Run multiple virtual machines **at the same time**. RangeEmulator automatically discovers open ports for SPICE/VNC and network interfaces to prevent conflicts.

### Performance Tweak Engine
- **Disk Interface Selection:** Choose **NVMe** for Windows stability or **VirtIO** for Linux speed.
- **Optimized CPU Flags:** Host-passthrough support for maximum instruction set access.

---

## What Can You Do With It?

- **Programming on the Go:** Run a full Linux environment with Visual Studio Code, Docker, and compilers. Code and build your projects anywhere.
- **Run PC Software:** Use Windows ARM64 to run essential professional tools and legacy applications that aren't available on Android.
- **Mobile Server:** Host local websites, development databases, or network tools right from your pocket.
- **Retro Gaming:** Experience classic PC titles or specialized ARM Windows games.

---

## Quick Start & Performance Tips

### Getting the Best Linux Experience
For the smoothest graphical performance with GPU acceleration enabled:
> [!TIP]
> **Use Wayland:** In your guest OS (like Ubuntu or Debian), select **Wayland** as the display compositor. It provides significantly lower latency and better frame rates than X11 when using the `virtio-gpu-gl` driver.

### Windows Optimization
> [!IMPORTANT]
> **NVMe Interface:** When creating a Windows VM, ensure the **NVMe** disk interface is selected in the settings to avoid boot-loop issues or "Disk not found" errors during installation.

### Troubleshooting with Isolated Logs
Each VM now has its own isolated log file. If something goes wrong, check the **System Logs** in the VM settings to find the exact QEMU output for that specific instance.

---
## Technical Overview
- **Firmware:** Modern UEFI (EDK2) shell.
- **Accelerated Graphics:** `virtio-gpu-pci` with OpenGL ES 3.0+ support.
- **Connectivity:** Low-latency **SPICE** protocol (recommended) or VNC.
- **Architecture:** Optimized for AArch64 (ARM64) host/guest parity.


## Dependencies & Built-in Engine

RangeEmulator automatically downloads and updates its core virtualization engine and required system libraries from our dedicated dependencies repository to ensure compatibility and performance.

- **Engine Source:** [Range-Emulator-Dependencies](https://github.com/range79x/Range-Emulator-Dependencies)
- **Included Components:** Optimized QEMU binaries, EDK2 UEFI firmware, and patched system libraries (`libz`, etc.) for Android environments.

---

*Developed by Range Development. Optimized for the future of mobile-desktop convergence.*
