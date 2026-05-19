# Scripts Directory

This directory contains all scripts and tools for the Haval MMI project.

## Directory Structure

```
scripts/
├── README.md                          # This file
├── Deploy-To-Car.ps1                  # Impulse app (Haval Shisuku) deployment
├── Share-Internet-And-Connect.ps1     # PC → Car internet sharing via Wi-Fi
│
├── aa-patches/                        # ★ Android Auto patching pipeline
│   ├── README.md                      #   Full build/deploy documentation
│   ├── patch_v19_focus.py             #   Focus-bypass patch script
│   ├── deploy_to_car.py              #   Push + mount deployment
│   └── stock/                         #   Stock APK cache
│       └── AndroidAutoApp_stock.apk
│
├── milestones/                        # Validated, car-confirmed versions
│   └── (promoted after car validation)
│
├── experimental/                      # Historical experiments & future work
│   ├── README.md                      #   Index with resume instructions
│   ├── patches/                       #   V9–V18 patch history
│   ├── resize/                        #   Display resize experiments
│   ├── frida/                         #   Legacy Frida hooks (deprecated)
│   └── telnet/                        #   Telnet deployment & diagnostics
│
└── utils/                             # Car diagnostics helpers
    ├── check_app_maps.py
    ├── check_service_maps.py
    ├── dump_maps_telnet.py
    ├── dumpsys_telnet.py
    ├── extract_vdex.py
    └── poll_app_maps.py
```

## Key Workflows

### Build & Deploy Patched Android Auto
See [`aa-patches/README.md`](aa-patches/README.md) for the full pipeline.

### Deploy Impulse App to Car
```powershell
.\scripts\Deploy-To-Car.ps1
```

### Share Internet with Car MMI
```powershell
.\scripts\Share-Internet-And-Connect.ps1
```

## Tools (in `tools/` at project root)
| Tool | File | Purpose |
|------|------|---------|
| apktool | `tools/apktool_3.0.2.jar` | Disassemble/reassemble APKs |

## Car Connection
- **IP**: Auto-detected (typically `192.168.33.x`)
- **ADB**: Port 5555
- **Telnet**: Port 23 (root shell)
