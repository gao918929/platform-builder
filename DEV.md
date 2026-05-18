# Platform Builder 项目文档

## 项目结构

```
pingtai/
├── src/main/          # Forge 1.20.1 独立版（原始代码，非多平台构建）
├── common/            # 多平台共享代码（Forge + NeoForge 共用）
├── forge/             # Forge 1.20.1 多平台版（引用 common/）
├── neoforge/          # NeoForge 1.21.1 多平台版（引用 common/）
├── build/             # 构建输出（src/main）
├── gradle/            # Gradle wrapper
├── settings.gradle    # 当前仅 include 'neoforge'
├── build.gradle       # 根构建（src/main 用）
├── mcmod_description.md  # MCMOD 页面描述
└── USAGE.md           # 用户操作说明
```

## 构建

```bash
# NeoForge 1.21.1（当前激活）
./gradlew :neoforge:build

# Forge 1.20.1（需先改 settings.gradle：include 'forge'，注释 neoforge）
./gradlew :forge:build

# 离线构建（网络有问题时）
./gradlew :neoforge:build --offline
```

输出 jar 在 `neoforge/build/libs/` 或 `forge/build/libs/`。

## 核心文件

| 文件 | 职责 |
|------|------|
| `common/.../PlatformBuilderScreen.java` | 主界面：设计网格、工具栏、模式系统、弹窗、AI、图案生成 |
| `common/.../PlatformBuilderMenu.java` | 容器菜单：槽位定义、快速移动逻辑 |
| `common/.../PlatformBuilderBlockEntity.java` | 方块实体：库存、设计存储、建造逻辑、猪猪之证 |
| `common/.../PlatformHelper.java` | 平台抽象接口 |
| `common/.../PlatformServices.java` | 平台服务注册 |
| `neoforge/.../NeoForgePlatformHelper.java` | NeoForge 平台实现（网络发包） |
| `forge/.../ForgePlatformHelper.java` | Forge 平台实现 |
| `neoforge/.../NeoForgeNetworking.java` | NeoForge 网络注册 |
| `forge/.../ForgeNetworking.java` | Forge 网络注册 |

## PlatformBuilderScreen 关键字段

```java
// 状态
private boolean inventoryMode;          // 当前模式：设计/放入
private Mode mode = Mode.NORMAL;       // NORMAL / COPY / PASTE

// 设计数据
private final Map<BlockPos, String> design;      // 已设计的方块
private final Map<BlockPos, String> existingBlocks; // 扫描到的世界方块
private Map<BlockPos, String> clipboard;          // 剪贴板
private int clipCenterX, clipCenterZ;              // 剪贴板中心偏移

// 工具
private Tool tool = Tool.BRUSH;
private String selectedMaterial;       // null = 未选中

// 视图
private float panX, panY, zoom = 1f;
private boolean showChunks = true;

// UI
private boolean showPatternPopup;
private int selectedPattern = -1;
```

## 模式系统

```
NORMAL → 点[复制] → COPY模式（自动切RECT工具，禁用选材）
                    框选完成 → 自动复制到剪贴板 → PASTE模式
NORMAL → 点[粘贴] → PASTE模式（预览跟随鼠标，点击放置）
PASTE → 点击网格 → 粘贴 → NORMAL

COPY/PASTE → Esc/切工具 → NORMAL
```

## 渲染架构（common/ 1.21.1版）

由于 1.21.1 的 `Slot.x/y` 是 `final`，不能动态重定位槽位：

- **设计模式**：手动渲染（renderBackground → renderBg → widgets → 工具提示 → 模式指示器 → 弹窗），不渲染槽位
- **放入模式**：调用 `super.render()` 走标准 Minecraft 渲染

src/main/ (1.20.1) 版则用 `repositionSlotsForInventory()` / `hideSlots()` 动态改槽位 Y。

## 网络协议

| 包 | 方向 | 用途 |
|----|------|------|
| SyncDesignPacket | C→S | 同步设计数据到服务器 |
| BuildPlatformPacket | C→S | 触发建造 |
| SetSpeedPacket | C→S | 修改建造速度 |
| SetOffsetPacket | C→S | 修改Y轴偏移 |
| QuickLoadPacket | C→S | 一键放入全部 |
| ExtractAllPacket | C→S | 一键取出全部 |

## AI 配置

配置文件：`.minecraft/config/platformbuilder.json`
```json
{"api_url":"https://api.openai.com","api_key":"sk-xxx","model":"gpt-4o-mini"}
```

调用流程：读配置 → 构建 prompt（含材料列表+选区范围）→ POST /v1/chat/completions → 解析 JSON 响应 → 填入设计

## 版本号

当前 v1.3.0。修改版本需同时更新：
- `forge/build.gradle` 第8行
- `neoforge/build.gradle` 第9行

## 待办/改进方向

- [ ] AI 失败时给用户反馈（目前静默忽略异常）
- [ ] 粘贴预览支持超出网格范围时自动裁剪
- [ ] 图案弹窗可翻页支持更多预设
- [ ] 设计可保存/加载到文件
- [ ] 多层 Y 轴设计支持
