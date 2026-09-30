# WiringUno

**WiringUno** is a high-performance hardware control engine and companion interface designed for the **Arduino Uno Q** (`imola`).

It provides real-time, zero-firmware-dependency control of the on-board STM32 microcontroller's physical pin headers (`D0`–`D13`, `A0`–`A5`) and status RGB indicators (`LED3`, `LED4`).

---

## How It Works: Direct Silicon AHB-AP Control

Traditional microcontroller companion solutions (such as StandardFirmata or custom serial RPC sketches) rely on cooperative software running inside the MCU's SRAM or Flash. That architecture has significant drawbacks:
* If the MCU sketch crashes, hangs in an infinite loop, or is erased during flashing, host control is instantly lost.
* Firmware polling over UART or SPI adds latency and jitter.
* The host must constantly coordinate with whatever sketch is currently loaded.

**WiringUno bypasses microcontroller firmware entirely by manipulating the hardware directly at the silicon bus level.**

```
┌────────────────────────────────────────────────────────┐
│                   WiringUno App                        │
│          (Interactive Material 3 UI / Control)         │
└───────────────────────────┬────────────────────────────┘
                            │ UNIX Domain Socket
                            ▼ (/dev/socket/arduino-router.sock)
┌────────────────────────────────────────────────────────┐
│                  arduino-router                        │
│          (High-Performance RPC Multiplexer)            │
└───────────────────────────┬────────────────────────────┘
                            │ MessagePack-RPC
                            ▼
┌────────────────────────────────────────────────────────┐
│                   imola-bridge                         │
│       (RPC-to-Memory Address Translation Daemon)       │
└───────────────────────────┬────────────────────────────┘
                            │ Local TCP Loopback (127.0.0.1:4444)
                            ▼
┌────────────────────────────────────────────────────────┐
│                  OpenOCD Daemon                        │
│        (ARM64 Native CoreSight SWD Driver)             │
└───────────────────────────┬────────────────────────────┘
                            │ Physical SWD Lines (SWCLK, SWDIO, SRST)
════════════════════════════╪═════════════════════════════
   Physical Boundary        │ Silicon Access
════════════════════════════╪═════════════════════════════
                            ▼
┌────────────────────────────────────────────────────────┐
│        STM32U585 CoreSight DAP / AHB-AP                │
│       (Autonomous Internal System Bus Master)          │
└───────────────────────────┬────────────────────────────┘
                            │ Internal 32-bit AHB Matrix
                            ▼
┌────────────────────────────────────────────────────────┐
│             STM32 Peripheral Registers                 │
│   • GPIOH / GPIOB BSRR (Atomic Bit Set / Reset)        │
│   • GPIOx MODER (Direction Control)                    │
│   • RCC AHB2ENR1 (Peripheral Clock Gating)             │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│                   Physical Hardware                    │
│   • RGB Status LEDs (LED3: PH10-12, LED4: PH13-15)     │
│   • Digital & Analog Header Pins (D0-D13, A0-A5)       │
└────────────────────────────────────────────────────────┘
```

---

### The CoreSight AHB-AP Advantage

1. **Hardware Bus Mastering**:
   The ARM Cortex-M33 inside the STM32U585 includes an integrated **CoreSight Debug Access Port (DAP)** featuring an **AHB-AP (Advanced High-performance Bus Access Port)**. The AHB-AP functions as an independent hardware bus master directly connected to the STM32 internal crossbar matrix.

2. **Zero MCU Firmware Dependency**:
   When the host issues a pin state change, OpenOCD commands the AHB-AP to write directly to peripheral register physical addresses over the 2-wire Serial Wire Debug (SWD) interface.
   * The Cortex-M33 CPU does **not** need to execute any code or interrupt service routines.
   * The MCU does not need to be halted; AHB-AP transactions occur concurrently with normal CPU instruction execution without disturbing running firmware.
   * Control works regardless of whether the MCU is running FreeRTOS, a custom Arduino sketch, a bootloader, or is sitting in an unprogrammed state.

3. **Atomic Register Manipulation**:
   Pin toggles utilize the dedicated **Bit Set/Reset Registers (`GPIOx_BSRR`)**:
   * Setting bit `n` (`1 << n`) atomically asserts pin `n` HIGH.
   * Setting bit `n + 16` (`1 << (n + 16)`) atomically resets pin `n` LOW.
   * Because BSRR writes are inherently atomic at the silicon level, there is zero risk of read-modify-write race conditions with any firmware concurrently running on the MCU.

4. **Clocks and Pin Protection**:
   * Peripheral clocks for GPIO ports are verified through the Reset and Clock Control register (`RCC_AHB2ENR1` at address `0x46020c8c`).
   * The debug interface lines (`PA13` / SWDIO and `PA14` / SWCLK) on `GPIOA` are strictly preserved in alternate function mode to ensure debug link integrity is never severed during pin mode reconfiguration.

---

## Hardware Pin Mapping (Arduino Uno Q)

| Logical Pin | STM32 Pin | Register Base | Physical Description |
| :--- | :--- | :--- | :--- |
| **LED3_R** | `PH10` | `0x42021c00` (GPIOH) | Red channel of user RGB LED 3 (Active Low) |
| **LED3_G** | `PH11` | `0x42021c00` (GPIOH) | Green channel of user RGB LED 3 (Active Low) |
| **LED3_B** | `PH12` | `0x42021c00` (GPIOH) | Blue channel of user RGB LED 3 (Active Low) |
| **LED4_R** | `PH13` | `0x42021c00` (GPIOH) | Red channel of user RGB LED 4 (Active Low) |
| **LED4_G** | `PH14` | `0x42021c00` (GPIOH) | Green channel of user RGB LED 4 (Active Low) |
| **LED4_B** | `PH15` | `0x42021c00` (GPIOH) | Blue channel of user RGB LED 4 (Active Low) |
| **D13** | `PB13` | `0x42020400` (GPIOB) | Standard Uno header pin 13 (Active High 3.3V) |
| **D0 – D12** | STM32 GPIO | Varies | Standard Uno digital header pins |
| **A0 – A5** | STM32 GPIO | Varies | Standard Uno analog / GPIO header pins |

> **Note on D13 vs. On-board LEDs**: On the Arduino Uno Q, header pin `D13` (`PB13`) is strictly routed to the physical header row and does not have an on-board surface-mount LED. On-board visual feedback is provided by the dual RGB LEDs (`LED3` and `LED4`) and the 13×8 LED matrix.

---

## Software Components

* **Application UI**:
  A clean, responsive Material 3 interface allowing individual or batch pin toggling, state visualization, categorized pin filtering (Digital, Analog, LED), and instant hardware diagnostics.
* **`arduino-router`**:
  System daemon exposing a high-throughput UNIX domain socket (`/dev/socket/arduino-router.sock`), multiplexing MessagePack-RPC requests between client applications and hardware backends.
* **`imola-bridge`**:
  Lightweight native daemon that registers the hardware RPC methods (e.g. `set_pin_by_name`), resolves logical pin identifiers to hardware memory offsets, and coordinates with OpenOCD.
* **`openocd`**:
  ARM64 native OpenOCD service utilizing Linux GPIO bitbang transport to speak SWD directly to the STM32 Cortex-M33 CoreSight DAP.

---

## Building

Build the APK using the Android build environment:

```bash
source build/envsetup.sh
lunch lineage_imola-userdebug
m WiringUno
```

The compiled APK will be generated at:
```
out/target/product/imola/system/app/WiringUno/WiringUno.apk
```

Install via ADB:
```bash
adb install -r out/target/product/imola/system/app/WiringUno/WiringUno.apk
```

---

## License

Copyright (C) 2026 Yajat Kumar.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
