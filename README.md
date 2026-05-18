# Platform Builder / 平台建造器

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green)](https://www.minecraft.net)
[![Forge](https://img.shields.io/badge/Forge-47.3.0-orange)](https://files.minecraftforge.net)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

Minecraft Forge mod — a visual building machine that lets you design and auto-build structures on a 2D grid, with infinite concrete supply via the **Pig Certificate**.

---

## Features / 功能

### Visual Design / 可视化设计
- **6 tools**: Brush, Eraser, Rectangle, Circle, Line, Pipette
- **6 种工具**：笔、擦、框、圆、线、取色
- Infinite zoom & pan with scroll / middle-click
- Undo (Ctrl+Z) and chunk-grid overlay

### Dual-Mode UI / 双模式界面
- **Design Mode**: grid + toolbar + material palette
- **放入模式**: material storage + player inventory, drag-and-drop item transfer

### Pig Certificate / 猪猪之证
- Crafted with Fishing Rod + Golden Carrot
- Place it in the machine → **infinite concrete** (all 32 variants, no materials consumed)

### Adjustable Settings / 可调参数
- Build speed: 1–500 blocks/tick
- Y-offset: −64 to +64 (build above or below the machine)

---

## Recipes / 合成表

| Item | Recipe |
|------|--------|
| **Platform Builder** | 8× Iron Ingot surrounding Piston + Redstone Block |
| **Pig Certificate** | Fishing Rod + Golden Carrot (shapeless) |

---

## Keyboard Shortcuts / 快捷键

| Key | Tool |
|-----|------|
| `B` | Brush / 笔 |
| `E` | Eraser / 擦 |
| `R` | Rectangle / 框 |
| `C` | Circle / 圆 |
| `L` | Line / 线 |
| `P` | Pipette / 取色 |
| `G` | Chunk grid / 区块线 |
| `Ctrl+Z` | Undo / 撤销 |

---

## Installation / 安装

1. Download the latest `.jar` from [Releases](https://github.com/gao918929/platform-builder-1.20.1/releases)
2. Place it in `mods/` folder
3. Requires **Minecraft 1.20.1** + **Forge 47.3.0+**

### Build from source / 从源码构建
```bash
git clone https://github.com/gao918929/platform-builder-1.20.1.git
cd platform-builder-1.20.1
./gradlew build
# Output: build/libs/platformbuilder-1.0.0.jar
```

---

## License / 许可

MIT License
