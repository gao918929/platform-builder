package com.pingtai.platformbuilder.screen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.pingtai.platformbuilder.PlatformServices;
import com.pingtai.platformbuilder.blockentity.PlatformBuilderBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class PlatformBuilderScreen extends AbstractContainerScreen<PlatformBuilderMenu> {

    private enum Tool {
        BRUSH(0xFF44AA44, "笔"),
        ERASER(0xFFCC4444, "擦"),
        RECT(0xFF4488CC, "框"),
        CIRCLE(0xFFAA66CC, "圆"),
        LINE(0xFF44AAAA, "线"),
        PIPETTE(0xFFCCAA44, "取");
        final int color; final String label;
        Tool(int c, String l) { color = c; label = l; }
    }

    private enum Mode { NORMAL, COPY, PASTE, REPLACE }
    private Mode mode = Mode.NORMAL;
    private String replaceFrom;

    private static final String[] PATTERN_NAMES = {
        "棋盘格", "横条纹", "竖条纹", "边框", "L↖", "L↗", "L↙", "L↘", "十字", "马路", "随机"
    };
    private int popM1Idx, popM2Idx;
    private int patternIdx;
    private Button offsetResetBtn;
    private Button scanBtn, copyBtn, pasteBtn, genBtn, aiBtn, buildBtn;
    private boolean showPatternPopup;
    private int selectedPattern = -1;

    private static final int TB_Y = 6, TB_H = 22;
    private static final int GRID_X = 8, GRID_Y = 32, GRID_H = 140, GRID_W = 322;
    private static final int BTN_W = 24, BTN_H = 18;
    private static final int DESIGN_H = 220;
    private static final int INVENTORY_H = 188;

    private boolean inventoryMode;

    private Tool tool = Tool.BRUSH;
    private String selectedMaterial;
    private final List<String> materials = new ArrayList<>();
    private final Map<String, TextureAtlasSprite> matSprites = new LinkedHashMap<>();
    private final Map<String, ItemStack> matIcons = new LinkedHashMap<>();
    private final Map<String, Integer> matColors = new LinkedHashMap<>();
    private int matScroll;
    private float panX, panY, zoom = 1f;
    private static final float MIN_ZOOM = 0.2f, MAX_ZOOM = 5f;
    private boolean panning, drawing;
    private BlockPos lastDraw, toolStart, toolEnd;
    private boolean showChunks = true;
    private BlockPos lastClickPos;
    private final Map<BlockPos, String> design = new LinkedHashMap<>();
    private final Map<BlockPos, String> existingBlocks = new LinkedHashMap<>();
    private int lastScannedOffsetY = Integer.MIN_VALUE;
    private Map<BlockPos, String> clipboard;
    private BlockPos clipboardOrigin;
    private int clipCenterX, clipCenterZ;
    private final Deque<Map<BlockPos, String>> undoStack = new ArrayDeque<>();
    private static final int MAX_UNDO = 50;

    private final int[] toolBtnX = new int[Tool.values().length];
    private int undoCX, clearCX, chunkCX, matStartX;
    private int tby;

    public PlatformBuilderScreen(PlatformBuilderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 338;
        imageHeight = DESIGN_H;
    }

    @Override
    protected void init() {
        imageHeight = inventoryMode ? INVENTORY_H : DESIGN_H;
        super.init();

        if (design.isEmpty() && getBE() != null && getBE().getDesign() != null) {
            design.putAll(getBE().getDesign());
        }

        if (inventoryMode) {
            buildInventoryWidgets();
        } else {
            buildDesignWidgets();
            refreshMaterials();
        }
    }

    private void buildDesignWidgets() {
        addRenderableWidget(Button.builder(
                Component.literal("放入"), b -> switchToInventoryMode()
        ).pos(leftPos + 8, topPos + TB_Y + 2).size(36, 18).build());

        int spdY = topPos + 198;
        addRenderableWidget(Button.builder(
                Component.literal("-"), b -> changeSpeed(-10)
        ).pos(leftPos + 8, spdY).size(14, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("+"), b -> changeSpeed(10)
        ).pos(leftPos + 23, spdY).size(14, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("-"), b -> changeOffset(-1)
        ).pos(leftPos + 90, spdY).size(14, 20).build());

        offsetResetBtn = addRenderableWidget(Button.builder(
                Component.literal(String.valueOf(getBuildOffsetY())), b -> changeOffsetTo(0)
        ).pos(leftPos + 105, spdY).size(14, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("+"), b -> changeOffset(1)
        ).pos(leftPos + 120, spdY).size(14, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("扫描"), b -> scanExistingBlocks()
        ).pos(leftPos + 142, spdY).size(24, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("复制"), b -> { mode = Mode.COPY; tool = Tool.RECT; selectedMaterial = null; }
        ).pos(leftPos + 168, spdY).size(24, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("粘贴"), b -> { if (clipboard != null && !clipboard.isEmpty()) mode = Mode.PASTE; }
        ).pos(leftPos + 194, spdY).size(24, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("替换"), b -> { mode = Mode.REPLACE; replaceFrom = null; }
        ).pos(leftPos + 220, spdY).size(24, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("生成"), b -> {
                    showPatternPopup = true; selectedPattern = -1;
                    popM1Idx = 0; popM2Idx = Math.min(1, materials.size() - 1);
                }
        ).pos(leftPos + 246, spdY).size(24, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("AI"), b -> aiGenerate()
        ).pos(leftPos + 272, spdY).size(22, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.platformbuilder.build"), b -> {
                    sendDesign();
                    PlatformServices.PLATFORM.sendBuildPacket(getBEPos());
                }
        ).pos(leftPos + 298, spdY).size(32, 20).build());
    }

    private void changeSpeed(int delta) {
        int newSpeed = getBuildSpeed() + delta;
        setBuildSpeed(newSpeed);
        PlatformServices.PLATFORM.sendSetSpeedPacket(getBEPos(), newSpeed);
    }

    private void changeOffset(int delta) {
        int newOff = getBuildOffsetY() + delta;
        setBuildOffsetY(newOff);
        PlatformServices.PLATFORM.sendSetOffsetPacket(getBEPos(), newOff);
        if (offsetResetBtn != null) offsetResetBtn.setMessage(Component.literal(String.valueOf(newOff)));
    }

    private void changeOffsetTo(int value) {
        setBuildOffsetY(value);
        PlatformServices.PLATFORM.sendSetOffsetPacket(getBEPos(), value);
        if (offsetResetBtn != null) offsetResetBtn.setMessage(Component.literal(String.valueOf(value)));
    }

    private void buildInventoryWidgets() {
        int rowY = topPos + 4;
        addRenderableWidget(Button.builder(
                Component.literal("设计"), b -> switchToDesignMode()
        ).pos(leftPos + 8, rowY).size(36, 18).build());

        addRenderableWidget(Button.builder(
                Component.literal("→ 放入全部"), b ->
                PlatformServices.PLATFORM.sendQuickLoadPacket(getBEPos())
        ).pos(leftPos + 50, rowY).size(80, 18).build());

        addRenderableWidget(Button.builder(
                Component.literal("← 取出全部"), b ->
                PlatformServices.PLATFORM.sendExtractAllPacket(getBEPos())
        ).pos(leftPos + 136, rowY).size(80, 18).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.platformbuilder.build"), b -> {
                    sendDesign();
                    PlatformServices.PLATFORM.sendBuildPacket(getBEPos());
                }
        ).pos(leftPos + 222, rowY).size(100, 18).build());
    }

    private void switchToInventoryMode() {
        sendDesign(); // 同步当前设计到服务器，防止丢失修改
        inventoryMode = true;
        resetWidgets();
        init();
    }

    private void switchToDesignMode() {
        inventoryMode = false;
        resetWidgets();
        init();
    }

    private void resetWidgets() {
        this.renderables.clear();
        this.children().clear();
    }

    private void copySelection() {
        clipboard = null;
        clipboardOrigin = null;
        if (tool == Tool.RECT && toolStart != null && toolEnd != null) {
            int mnX = Math.min(toolStart.getX(), toolEnd.getX());
            int mxX = Math.max(toolStart.getX(), toolEnd.getX());
            int mnZ = Math.min(toolStart.getZ(), toolEnd.getZ());
            int mxZ = Math.max(toolStart.getZ(), toolEnd.getZ());
            clipboard = new LinkedHashMap<>();
            clipboardOrigin = new BlockPos(mnX, 0, mnZ);
            for (int x = mnX; x <= mxX; x++) {
                for (int z = mnZ; z <= mxZ; z++) {
                    BlockPos p = new BlockPos(x, 0, z);
                    String mat = design.get(p);
                    if (mat == null) mat = existingBlocks.get(p);
                    if (mat != null)
                        clipboard.put(new BlockPos(x - mnX, 0, z - mnZ), mat);
                }
            }
            clipCenterX = (mxX - mnX) / 2;
            clipCenterZ = (mxZ - mnZ) / 2;
        }
    }

    private void pasteClipboard() {
        if (clipboard == null || clipboard.isEmpty()) return;
        BlockPos anchor = toolStart != null ? toolStart : lastClickPos;
        if (anchor == null) return;
        saveUndo();
        for (var entry : clipboard.entrySet()) {
            BlockPos target = anchor.offset(entry.getKey().getX() - clipCenterX, 0, entry.getKey().getZ() - clipCenterZ);
            if (!design.containsKey(target))
                design.put(target, entry.getValue());
        }
    }

    private void scanExistingBlocks() {
        existingBlocks.clear();
        if (minecraft == null || minecraft.level == null || getBE() == null) return;

        BlockPos machinePos = getBEPos();
        int offsetY = getBuildOffsetY();
        int scanY = machinePos.getY() + offsetY;
        lastScannedOffsetY = offsetY;

        var level = minecraft.level;
        int range = 48;
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                var state = level.getBlockState(new BlockPos(machinePos.getX() + dx, scanY, machinePos.getZ() + dz));
                if (!state.isAir()) {
                    var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if (id != null) {
                        existingBlocks.put(new BlockPos(dx, 0, dz), id.toString());
                    }
                }
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int bg = 0xC6101010;
        g.fillGradient(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, bg, bg);
        drawBorder(g, leftPos, topPos, imageWidth, imageHeight);

        if (inventoryMode) {
            renderInventoryBg(g);
        } else {
            renderDesignBg(g, mx, my);
        }
    }

    private void renderDesignBg(GuiGraphics g, int mx, int my) {
        tby = topPos + TB_Y;

        g.fill(leftPos + 46, tby - 2, leftPos + imageWidth - 4, tby + TB_H + 2, 0x80222222);
        hline(g, leftPos + 46, leftPos + imageWidth - 4, tby + TB_H + 3, 0xFF555555);

        int gy = topPos + GRID_Y;
        g.fill(leftPos + GRID_X - 1, gy - 1, leftPos + GRID_X + GRID_W + 1, gy + GRID_H + 1, 0xFF222222);
        g.fill(leftPos + GRID_X, gy, leftPos + GRID_X + GRID_W, gy + GRID_H, 0xFFD4CFC9);
        hline(g, leftPos + 4, leftPos + imageWidth - 4, gy + GRID_H + 1, 0xFF555555);

        renderToolbar(g);
        g.enableScissor(leftPos + GRID_X, gy, leftPos + GRID_X + GRID_W, gy + GRID_H);
        renderGrid(g, mx, my);
        g.disableScissor();

        renderMaterialPalette(g, mx, my);

        int spd = getBuildSpeed();
        int off = getBuildOffsetY();
        g.drawString(font, Component.literal("速度:" + spd), leftPos + 40, topPos + 200, 0xFFAAAAAA, false);
        g.drawString(font, Component.literal("Y:" + (off >= 0 ? "+" : "") + off),
                leftPos + 136, topPos + 200, 0xFFAAAAAA, false);
    }

    private void renderMaterialPalette(GuiGraphics g, int mx, int my) {
        int palY = topPos + 176;
        int x = leftPos + 8;

        int maxScroll = Math.max(0, materials.size() - 12);
        if (matScroll > maxScroll) matScroll = maxScroll;
        if (matScroll < 0) matScroll = 0;

        g.drawString(font, Component.literal("材料"), x, palY, 0xFFAAAAAA, false);
        x += 24;

        if (matScroll > 0) {
            g.fill(x, palY, x + 10, palY + 18, 0x80555555);
            g.drawString(font, Component.literal("<"), x + 2, palY + 5, 0xFFFFFFFF, false);
            x += 12;
        }
        matStartX = x;

        for (int i = 0; i < 12 && i + matScroll < materials.size(); i++) {
            String mat = materials.get(i + matScroll);
            boolean sel = mat.equals(selectedMaterial);
            ItemStack icon = matIcons.get(mat);
            if (sel) g.fill(x - 1, palY - 1, x + 21, palY + 19, 0xFFFFFF88);
            if (icon != null) g.renderItem(icon, x + 2, palY + 1);
            x += 22;
        }

        if (matScroll < maxScroll) {
            g.fill(x, palY, x + 10, palY + 18, 0x80555555);
            g.drawString(font, Component.literal(">"), x + 2, palY + 5, 0xFFFFFFFF, false);
        }
    }

    private void renderInventoryBg(GuiGraphics g) {
        int machineTop = topPos + 24;
        int machineBottom = topPos + 90;
        g.fill(leftPos + 6, machineTop, leftPos + imageWidth - 6, machineBottom, 0x80181818);
        hline(g, leftPos + 6, leftPos + imageWidth - 6, machineBottom, 0xFF555555);
        g.drawString(font, Component.literal("材料库存"), leftPos + 8, machineTop + 2, 0xFFAAAAAA, false);

        int playerTop = topPos + 92;
        int playerBottom = topPos + 184;
        g.fill(leftPos + 6, playerTop, leftPos + imageWidth - 6, playerBottom, 0x80181818);
        hline(g, leftPos + 6, leftPos + imageWidth - 6, playerTop, 0xFF555555);
        g.drawString(font, Component.literal("物品栏"), leftPos + 8, playerTop + 2, 0xFFAAAAAA, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!inventoryMode) refreshMaterials();

        if (inventoryMode) {
            super.render(g, mx, my, pt);
            return;
        }

        renderBackground(g, mx, my, pt);
        renderBg(g, pt, mx, my);

        for (var r : this.renderables)
            r.render(g, mx, my, pt);

        if (!this.menu.getCarried().isEmpty()) {
            ItemStack cs = this.menu.getCarried();
            g.renderItem(cs, mx - 8, my - 8);
            g.renderItemDecorations(this.font, cs, mx - 8, my - 8);
        }

        if (isInGrid(mx, my) && tool != Tool.RECT) {
            BlockPos p = screenToGrid(mx, my);
            if (p != null) {
                String dMat = design.get(p);
                if (dMat != null) {
                    g.renderTooltip(font, Component.literal(
                            "X:" + p.getX() + " Z:" + p.getZ() + " - " + shortName(dMat)), mx, my);
                } else {
                    String eMat = existingBlocks.get(p);
                    if (eMat != null) {
                        g.renderTooltip(font, Component.literal(
                                "X:" + p.getX() + " Z:" + p.getZ() + " - " + shortName(eMat)), mx, my);
                    }
                }
            }
        }

        if (mode != Mode.NORMAL)
            g.drawString(font, Component.literal(switch (mode) {
                case COPY -> "复制模式(拖拽框选)";
                case PASTE -> "粘贴模式(点击放置, Esc取消)";
                case REPLACE -> replaceFrom == null ? "替换模式(点击要替换的方块)" : "替换模式(点击新材料完成替换)";
                default -> "";
            }), leftPos + 8, topPos + imageHeight - 12, 0xFFFFFF88, false);

        if (showPatternPopup) renderPopup(g);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}

    private void renderToolbar(GuiGraphics g) {
        int x = leftPos + 52;
        int y = tby + 2;

        for (int i = 0; i < Tool.values().length; i++) {
            Tool t = Tool.values()[i];
            toolBtnX[i] = x;
            drawTbBtn(g, x, y, t.label, tool == t ? t.color : 0xFF3A3A3A,
                    tool == t ? 0xFFFFFFFF : 0xFF888888, tool == t);
            x += BTN_W + 1;
        }
        x += 5;
        g.fill(x, y + 2, x + 1, y + BTN_H - 2, 0xFF555555);
        x += 7;

        undoCX = x;
        boolean canUndo = !undoStack.isEmpty();
        drawTbBtn(g, x, y, "↩", canUndo ? 0xFF446644 : 0xFF333333, canUndo ? 0xFF88FF88 : 0xFF555555, false);
        x += BTN_W + 1;

        clearCX = x;
        drawTbBtn(g, x, y, "✕", 0xFF553333, 0xFFFF8888, false);
        x += BTN_W + 1;

        chunkCX = x;
        drawTbBtn(g, x, y, "C", showChunks ? 0xFF666644 : 0xFF333333, showChunks ? 0xFFCCCC44 : 0xFF666666, false);
    }

    private void drawTbBtn(GuiGraphics g, int x, int y, String text, int bg, int fg, boolean highlight) {
        g.fill(x, y, x + BTN_W, y + BTN_H, bg);
        int bd = highlight ? 0xFFFFFFFF : 0xFF3A3A3A;
        g.fill(x, y, x + BTN_W, y + 1, bd);
        g.fill(x, y, x + 1, y + BTN_H, bd);
        g.fill(x, y + BTN_H - 1, x + BTN_W, y + BTN_H, bd);
        g.fill(x + BTN_W - 1, y, x + BTN_W, y + BTN_H, bd);
        g.drawCenteredString(font, Component.literal(text), x + BTN_W / 2, y + BTN_H / 2 - 4, fg);
    }

    private void renderGrid(GuiGraphics g, int mx, int my) {
        float es = 16f * zoom;
        int cx = leftPos + GRID_X + GRID_W / 2 + (int) panX;
        int cy = topPos + GRID_Y + GRID_H / 2 + (int) panY;

        int sX = (int) Math.floor((leftPos + GRID_X - cx) / es) - 1;
        int eX = (int) Math.ceil((leftPos + GRID_X + GRID_W - cx) / es) + 1;
        int sZ = (int) Math.floor((topPos + GRID_Y - cy) / es) - 1;
        int eZ = (int) Math.ceil((topPos + GRID_Y + GRID_H - cy) / es) + 1;

        BlockPos wp = getBEPos();
        int wx = wp.getX(), wz = wp.getZ();

        int rmx = 0, rMx = -1, rmz = 0, rMz = -1;
        int circleCx = 0, circleCz = 0, circleR2 = -1;
        if (toolStart != null && toolEnd != null) {
            if (tool == Tool.RECT) {
                rmx = Math.min(toolStart.getX(), toolEnd.getX());
                rMx = Math.max(toolStart.getX(), toolEnd.getX());
                rmz = Math.min(toolStart.getZ(), toolEnd.getZ());
                rMz = Math.max(toolStart.getZ(), toolEnd.getZ());
            } else if (tool == Tool.CIRCLE) {
                circleCx = toolStart.getX();
                circleCz = toolStart.getZ();
                int dx = toolEnd.getX() - circleCx;
                int dz = toolEnd.getZ() - circleCz;
                circleR2 = dx * dx + dz * dz;
            }
        }

        for (int gx = sX; gx <= eX; gx++) {
            for (int gz = sZ; gz <= eZ; gz++) {
                int sx = (int) (cx + gx * es);
                int sy = (int) (cy + gz * es);
                int sz = Math.max(1, (int) es);
                BlockPos key = new BlockPos(gx, 0, gz);
                String mat = design.get(key);

                if (mat != null) {
                    TextureAtlasSprite sprite = matSprites.get(mat);
                    if (sprite != null && sz >= 2) {
                        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
                        g.blit(sx, sy, 0, sz, sz, sprite);
                    } else {
                        Integer col = matColors.get(mat);
                        g.fill(sx, sy, sx + sz - 1, sy + sz - 1, col != null ? col : 0xFF44AA44);
                    }
                } else {
                    String existingMat = existingBlocks.get(key);
                    if (existingMat != null) {
                        Integer col = matColors.get(existingMat);
                        if (col == null) {
                            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(existingMat));
                            if (block != null) {
                                col = 0xFF000000 | block.defaultMapColor().col;
                                matColors.put(existingMat, col);
                            }
                        }
                        g.fill(sx, sy, sx + sz - 1, sy + sz - 1,
                            col != null ? (col & 0x00FFFFFF) | 0x80000000 : 0x80448844);
                    } else {
                        g.fill(sx, sy, sx + sz - 1, sy + sz - 1, 0xFFE8E5E0);
                    }
                }

                if (mat != null && mat.equals(selectedMaterial))
                    g.fill(sx, sy, sx + sz - 1, sy + sz - 1, 0x4066CC66);
                if (gx >= rmx && gx <= rMx && gz >= rmz && gz <= rMz)
                    g.fill(sx, sy, sx + sz - 1, sy + sz - 1, 0x604488CC);
                if (circleR2 >= 0) {
                    int dx = gx - circleCx, dz = gz - circleCz;
                    if (dx * dx + dz * dz <= circleR2)
                        g.fill(sx, sy, sx + sz - 1, sy + sz - 1, 0x60AA66CC);
                }
                if (tool == Tool.LINE && toolStart != null && toolEnd != null && isOnLine(gx, gz))
                    g.fill(sx, sy, sx + sz - 1, sy + sz - 1, 0x6044AAAA);

                int cellAlpha = Math.min(0x60, Math.max(0, (int)(sz * 0x18)));
                if (cellAlpha > 3) {
                    int lc = (cellAlpha << 24) | 0xFFFFFF;
                    g.fill(sx, sy, sx + sz, sy + 1, lc);
                    g.fill(sx, sy, sx + 1, sy + sz, lc);
                }
                if (showChunks) {
                    boolean cx2 = Math.floorMod(wx + gx, 16) == 0;
                    boolean cz2 = Math.floorMod(wz + gz, 16) == 0;
                    int ca = Math.min(0x88, Math.max(0x20, (int)(sz * 0x20)));
                    if (cx2) g.fill(sx, sy, sx + 1, sy + Math.max(1, sz), (ca << 24) | 0xFFCC00);
                    if (cz2) g.fill(sx, sy, sx + Math.max(1, sz), sy + 1, (ca << 24) | 0xFFCC00);
                }
            }
        }

        if (showChunks && es < 12) {
            for (int gx = sX; gx <= eX; gx++) {
                if (Math.floorMod(wx + gx, 16) != 0) continue;
                for (int gz = sZ; gz <= eZ; gz++) {
                    if (Math.floorMod(wz + gz, 16) != 0) continue;
                    int dx = (int) (cx + gx * es), dy = (int) (cy + gz * es);
                    int r = Math.max(2, Math.min(4, (int) es / 4));
                    g.fill(dx - r, dy - r, dx + r + 1, dy + r + 1, 0xCCFF8800);
                }
            }
        }

        int oz = Math.max(1, (int) es);
        g.fill(cx, cy, cx + oz, cy + oz, 0x30FF4444);
        g.fill(cx, cy, cx + oz, cy + 2, 0xFFFF4444);
        g.fill(cx, cy, cx + 2, cy + oz, 0xFFFF4444);
        g.fill(cx + oz - 2, cy, cx + oz, cy + oz, 0xFFFF4444);
        g.fill(cx, cy + oz - 2, cx + oz, cy + oz, 0xFFFF4444);
        if (oz >= 12) {
            g.fill(cx + oz / 2 - 1, cy + 4, cx + oz / 2 + 2, cy + oz - 4, 0x90FF4444);
            g.fill(cx + 4, cy + oz / 2 - 1, cx + oz - 4, cy + oz / 2 + 2, 0x90FF4444);
        }

        TextureAtlasSprite previewSprite = (selectedMaterial != null) ? matSprites.get(selectedMaterial) : null;
        Integer previewColor = (selectedMaterial != null) ? matColors.get(selectedMaterial) : null;

        if (isInGrid(mx, my) && (tool == Tool.BRUSH || tool == Tool.ERASER) && !drawing) {
            BlockPos hp = screenToGrid(mx, my);
            if (hp != null) {
                int hx = (int) (cx + hp.getX() * es), hy = (int) (cy + hp.getZ() * es);
                int sz = Math.max(1, (int) es);
                if (tool == Tool.BRUSH && sz >= 4 && previewSprite != null) {
                    RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
                    g.blit(hx, hy, 0, sz, sz, previewSprite);
                } else if (tool == Tool.BRUSH && previewColor != null) {
                    g.fill(hx, hy, hx + sz - 1, hy + sz - 1, (previewColor & 0x00FFFFFF) | 0xB0000000);
                }
                int brd = (tool == Tool.ERASER) ? 0xFFFF4444 : 0xFFFFFFFF;
                g.fill(hx, hy, hx + sz, hy + 1, brd);
                g.fill(hx, hy, hx + 1, hy + sz, brd);
                g.fill(hx, hy + sz - 1, hx + sz, hy + sz, brd);
                g.fill(hx + sz - 1, hy, hx + sz, hy + sz, brd);
            }
        }

        if (tool == Tool.RECT && toolStart != null && toolEnd != null && previewSprite != null) {
            int rmnX = Math.min(toolStart.getX(), toolEnd.getX());
            int rmX = Math.max(toolStart.getX(), toolEnd.getX());
            int rmnZ = Math.min(toolStart.getZ(), toolEnd.getZ());
            int rmZ = Math.max(toolStart.getZ(), toolEnd.getZ());
            if ((rmX - rmnX + 1) * (rmZ - rmnZ + 1) <= 2500) {
                RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
                for (int gx = rmnX; gx <= rmX; gx++) {
                    for (int gz = rmnZ; gz <= rmZ; gz++) {
                        int sx = (int) (cx + gx * es), sy = (int) (cy + gz * es);
                        g.blit(sx, sy, 0, Math.max(1, (int) es), Math.max(1, (int) es), previewSprite);
                    }
                }
            }
        }

        if (tool == Tool.CIRCLE && toolStart != null) {
            int scx = (int)(cx + toolStart.getX() * es), scy = (int)(cy + toolStart.getZ() * es);
            int sz = Math.max(1, (int)es);
            g.fill(scx + sz/2 - 1, scy + 2, scx + sz/2 + 2, scy + sz - 2, 0xCCAA66CC);
            g.fill(scx + 2, scy + sz/2 - 1, scx + sz - 2, scy + sz/2 + 2, 0xCCAA66CC);
        }

        if (tool == Tool.LINE && toolStart != null) {
            int lx = (int)(cx + toolStart.getX() * es), ly = (int)(cy + toolStart.getZ() * es);
            int lsz = Math.max(1, (int)es);
            g.fill(lx, ly, lx + lsz, ly + 2, 0xCC44AAAA);
            g.fill(lx, ly, lx + 2, ly + lsz, 0xCC44AAAA);
            g.fill(lx + lsz - 2, ly, lx + lsz, ly + lsz, 0xCC44AAAA);
            g.fill(lx, ly + lsz - 2, lx + lsz, ly + lsz, 0xCC44AAAA);
        }

        // Paste preview
        if (clipboard != null && !clipboard.isEmpty() && isInGrid(mx, my)) {
            BlockPos hover = screenToGrid(mx, my);
            if (hover != null) {
                for (var entry : clipboard.entrySet()) {
                    BlockPos tp = hover.offset(entry.getKey().getX() - clipCenterX, 0, entry.getKey().getZ() - clipCenterZ);
                    if (design.containsKey(tp)) continue;
                    int px = (int) (cx + tp.getX() * es);
                    int py = (int) (cy + tp.getZ() * es);
                    int psz = Math.max(1, (int) es);
                    TextureAtlasSprite sprite = matSprites.get(entry.getValue());
                    if (sprite != null && psz >= 2) {
                        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
                        RenderSystem.setShaderColor(1f, 1f, 1f, 0.35f);
                        g.blit(px, py, 0, psz, psz, sprite);
                        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                    } else {
                        Integer col = matColors.get(entry.getValue());
                        if (col == null) {
                            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(entry.getValue()));
                            if (block != null) {
                                col = 0xFF000000 | block.defaultMapColor().col;
                                matColors.put(entry.getValue(), col);
                            }
                        }
                        g.fill(px, py, px + psz - 1, py + psz - 1,
                            col != null ? (col & 0x00FFFFFF) | 0x50000000 : 0x50448844);
                    }
                    g.fill(px, py, px + psz, py + 1, 0xCCFFD700);
                    g.fill(px, py, px + 1, py + psz, 0xCCFFD700);
                    g.fill(px, py + psz - 1, px + psz, py + psz, 0xCCFFD700);
                    g.fill(px + psz - 1, py, px + psz, py + psz, 0xCCFFD700);
                }
            }
        }
    }

    // === INPUT ===

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int modeBtnX = leftPos + 8, modeBtnY = topPos + TB_Y + 2;
        if (!inventoryMode && btn == 0
                && mx >= modeBtnX && mx < modeBtnX + 36
                && my >= modeBtnY && my <= modeBtnY + 18) {
            return super.mouseClicked(mx, my, btn);
        }

        if (showPatternPopup && btn == 0) { handlePopupClick((int)mx, (int)my); return true; }
        if (inventoryMode || (btn != 0 && btn != 2)) return super.mouseClicked(mx, my, btn);

        int toolbarY = tby + 2;
        if (btn == 0 && my >= toolbarY && my <= toolbarY + BTN_H) {
            for (int i = 0; i < toolBtnX.length; i++) {
                if (mx >= toolBtnX[i] && mx < toolBtnX[i] + BTN_W) { tool = Tool.values()[i]; return true; }
            }
            if (mx >= undoCX && mx < undoCX + BTN_W) { undo(); return true; }
            if (mx >= clearCX && mx < clearCX + BTN_W) { saveUndo(); design.clear(); return true; }
            if (mx >= chunkCX && mx < chunkCX + BTN_W) { showChunks = !showChunks; return true; }
            return true;
        }

        int palY = topPos + 176;
        if (btn == 0 && my >= palY && my <= palY + 18) {
            int ix = matStartX;
            if (matScroll > 0 && mx >= leftPos + 32 && mx < leftPos + 42) {
                matScroll--; return true;
            }
            for (int i = 0; i < 12 && i + matScroll < materials.size(); i++) {
                if (mx >= ix && mx < ix + 20) {
                    String mat = materials.get(i + matScroll);
                    if (mode == Mode.REPLACE && replaceFrom != null) {
                        doReplace(mat); mode = Mode.NORMAL; return true;
                    }
                    selectedMaterial = mat.equals(selectedMaterial) ? null : mat;
                    return true;
                }
                ix += 22;
            }
            int maxScroll = Math.max(0, materials.size() - 12);
            if (matScroll < maxScroll && mx >= ix && mx < ix + 10) { matScroll++; return true; }
            return true;
        }

        if (isInGrid((int) mx, (int) my)) {
            lastClickPos = screenToGrid((int) mx, (int) my);
            if (mode == Mode.PASTE && btn == 0) {
                pasteClipboard();
                return true;
            }
            if (mode == Mode.REPLACE && btn == 0) {
                String mat = design.get(lastClickPos);
                if (mat != null && replaceFrom == null) { replaceFrom = mat; return true; }
                mode = Mode.NORMAL; return true;
            }
            if (btn == 0) {
                switch (tool) {
                    case BRUSH, ERASER -> {
                        if (mode == Mode.COPY || mode == Mode.REPLACE) break;
                        drawing = true; lastDraw = null; applyTool((int) mx, (int) my);
                    }
                    case RECT, CIRCLE, LINE -> { toolStart = screenToGrid((int) mx, (int) my); toolEnd = toolStart; }
                    case PIPETTE -> { if (mode == Mode.NORMAL) pickMaterial((int) mx, (int) my); }
                }
                return true;
            }
            if (btn == 2) { panning = true; return true; }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (inventoryMode) return super.mouseDragged(mx, my, btn, dx, dy);
        if (panning) { panX += (float) dx; panY += (float) dy; return true; }
        if (drawing && isInGrid((int) mx, (int) my)) { applyTool((int) mx, (int) my); return true; }
        if ((tool == Tool.RECT || tool == Tool.CIRCLE || tool == Tool.LINE)
                && toolStart != null && isInGrid((int) mx, (int) my)) {
            toolEnd = screenToGrid((int) mx, (int) my); return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (inventoryMode) return super.mouseReleased(mx, my, btn);
        if (btn == 2) { panning = false; return true; }
        if (btn == 0) {
            if (drawing) { drawing = false; lastDraw = null; return true; }
            if (toolStart != null) {
                if (mode == Mode.COPY && tool == Tool.RECT) {
                    copySelection();
                    mode = Mode.PASTE;
                    toolStart = null; toolEnd = null;
                    return true;
                }
                switch (tool) {
                    case RECT -> finishRect();
                    case CIRCLE -> finishCircle();
                    case LINE -> finishLine();
                }
                return true;
            }
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (inventoryMode) return super.mouseScrolled(mx, my, scrollX, scrollY);
        if (isInGrid((int) mx, (int) my)) {
            float nz = zoom * (scrollY > 0 ? 1.10f : 0.91f);
            nz = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, nz));
            float cx = leftPos + GRID_X + GRID_W / 2f + panX;
            float cy = topPos + GRID_Y + GRID_H / 2f + panY;
            float oe = 16f * zoom, ne = 16f * nz;
            float wx = ((float) mx - cx) / oe, wy = ((float) my - cy) / oe;
            panX += wx * (oe - ne); panY += wy * (oe - ne);
            zoom = nz; return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (showPatternPopup || mode != Mode.NORMAL) {
            if (k == 256) { mode = Mode.NORMAL; showPatternPopup = false; return true; }
        }
        if (!inventoryMode && k == 90 && hasControlDown()) { undo(); return true; }
        return super.keyPressed(k, s, m);
    }

    // === ACTIONS ===

    private void applyTool(int mx, int my) {
        BlockPos p = screenToGrid(mx, my);
        if (p == null || (lastDraw != null && lastDraw.equals(p))) return;
        if (lastDraw == null) saveUndo();
        lastDraw = p;
        if (tool == Tool.BRUSH) {
            if (selectedMaterial == null && !materials.isEmpty()) selectedMaterial = materials.get(0);
            if (selectedMaterial != null) design.put(p, selectedMaterial);
        } else if (tool == Tool.ERASER) {
            design.remove(p);
        }
    }

    private void pickMaterial(int mx, int my) {
        BlockPos p = screenToGrid(mx, my);
        if (p == null) return;
        String mat = design.get(p);
        if (mat != null) { selectedMaterial = mat; tool = Tool.BRUSH; }
    }

    private void finishRect() {
        if (toolStart == null || toolEnd == null) return;
        if (selectedMaterial == null) return; // keep selection for copy
        saveUndo();
        int mnX = Math.min(toolStart.getX(), toolEnd.getX());
        int mxX = Math.max(toolStart.getX(), toolEnd.getX());
        int mnZ = Math.min(toolStart.getZ(), toolEnd.getZ());
        int mxZ = Math.max(toolStart.getZ(), toolEnd.getZ());
        for (int x = mnX; x <= mxX; x++)
            for (int z = mnZ; z <= mxZ; z++)
                design.put(new BlockPos(x, 0, z), selectedMaterial);
        toolStart = null; toolEnd = null;
    }

    private void finishCircle() {
        if (toolStart == null || toolEnd == null) return;
        if (selectedMaterial == null && !materials.isEmpty()) selectedMaterial = materials.get(0);
        if (selectedMaterial == null) { toolStart = null; toolEnd = null; return; }
        saveUndo();
        int cx = toolStart.getX(), cz = toolStart.getZ();
        int dx = toolEnd.getX() - cx, dz = toolEnd.getZ() - cz;
        int r2 = dx * dx + dz * dz;
        int r = (int) Math.ceil(Math.sqrt(r2));
        for (int x = cx - r; x <= cx + r; x++)
            for (int z = cz - r; z <= cz + r; z++)
                if ((x - cx) * (x - cx) + (z - cz) * (z - cz) <= r2)
                    design.put(new BlockPos(x, 0, z), selectedMaterial);
        toolStart = null; toolEnd = null;
    }

    private void finishLine() {
        if (toolStart == null || toolEnd == null) return;
        if (selectedMaterial == null && !materials.isEmpty()) selectedMaterial = materials.get(0);
        if (selectedMaterial == null) { toolStart = null; toolEnd = null; return; }
        saveUndo();
        int x1 = toolStart.getX(), z1 = toolStart.getZ();
        int x2 = toolEnd.getX(), z2 = toolEnd.getZ();
        int dx = Math.abs(x2 - x1), dz = Math.abs(z2 - z1);
        int sx = x1 < x2 ? 1 : -1, sz = z1 < z2 ? 1 : -1;
        int err = dx - dz;
        while (true) {
            design.put(new BlockPos(x1, 0, z1), selectedMaterial);
            if (x1 == x2 && z1 == z2) break;
            int e2 = 2 * err;
            if (e2 > -dz) { err -= dz; x1 += sx; }
            if (e2 < dx)  { err += dx; z1 += sz; }
        }
        toolStart = null; toolEnd = null;
    }

    private boolean isOnLine(int gx, int gz) {
        int x1 = toolStart.getX(), z1 = toolStart.getZ();
        int x2 = toolEnd.getX(), z2 = toolEnd.getZ();
        int dx = Math.abs(x2 - x1), dz = Math.abs(z2 - z1);
        int sx = x1 < x2 ? 1 : -1, sz = z1 < z2 ? 1 : -1;
        int err = dx - dz;
        while (true) {
            if (x1 == gx && z1 == gz) return true;
            if (x1 == x2 && z1 == z2) break;
            int e2 = 2 * err;
            if (e2 > -dz) { err -= dz; x1 += sx; }
            if (e2 < dx)  { err += dx; z1 += sz; }
        }
        return false;
    }

    private void saveUndo() {
        if (!design.isEmpty()) { undoStack.push(new LinkedHashMap<>(design)); if (undoStack.size() > MAX_UNDO) undoStack.removeLast(); }
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        design.clear(); design.putAll(undoStack.pop());
    }

    // === UTILS ===

    private void refreshMaterials() {
        if (getBE() == null) return;
        var inv = getBE().getInventory();
        Set<String> current = new LinkedHashSet<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && st.getItem() instanceof BlockItem bi) {
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(bi.getBlock());
                if (id != null) current.add(id.toString());
            }
        }
        for (String m : design.values()) current.add(m);

        if (getBE().hasPigCertificate()) {
            for (var entry : BuiltInRegistries.BLOCK.entrySet()) {
                ResourceLocation id = entry.getKey().location();
                String idStr = id.toString();
                if (idStr.endsWith("_concrete")) {
                    current.add(idStr);
                }
            }
        }

        if (current.size() == materials.size() && current.containsAll(materials)) return;

        materials.clear(); matSprites.clear(); matIcons.clear(); matColors.clear();
        for (String id : current) {
            materials.add(id);
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
            if (block != null) {
                matIcons.put(id, new ItemStack(block.asItem()));
                matColors.put(id, 0xFF000000 | block.defaultMapColor().col);
                TextureAtlasSprite sprite = getBlockFaceSprite(block);
                if (sprite != null) matSprites.put(id, sprite);
            }
        }
    }

    /** Get the top-face or representative sprite of a block for flat preview. */
    private static TextureAtlasSprite getBlockFaceSprite(Block block) {
        try {
            var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(block.defaultBlockState());
            var quads = model.getQuads(block.defaultBlockState(), Direction.UP, RandomSource.create());
            if (!quads.isEmpty()) return quads.get(0).getSprite();
            // fallback: try null direction (general quads)
            quads = model.getQuads(block.defaultBlockState(), null, RandomSource.create());
            if (!quads.isEmpty()) return quads.get(0).getSprite();
            return model.getParticleIcon();
        } catch (Exception e) {
            return null;
        }
    }

    private void generatePattern() {
        int mnX, mxX, mnZ, mxZ;
        if (tool == Tool.RECT && toolStart != null && toolEnd != null) {
            mnX = Math.min(toolStart.getX(), toolEnd.getX());
            mxX = Math.max(toolStart.getX(), toolEnd.getX());
            mnZ = Math.min(toolStart.getZ(), toolEnd.getZ());
            mxZ = Math.max(toolStart.getZ(), toolEnd.getZ());
        } else {
            mnX = 0; mxX = 15; mnZ = 0; mxZ = 15;
        }
        String m1 = selectedMaterial;
        if (m1 == null && !materials.isEmpty()) m1 = materials.get(0);
        if (m1 == null) return;
        String m2 = m1;
        for (String m : materials) { if (!m.equals(m1)) { m2 = m; break; } }

        saveUndo();
        String name = PATTERN_NAMES[patternIdx];
        patternIdx = (patternIdx + 1) % PATTERN_NAMES.length;

        for (int x = mnX; x <= mxX; x++) {
            for (int z = mnZ; z <= mxZ; z++) {
                boolean useM1 = switch (name) {
                    case "棋盘格" -> (x + z) % 2 == 0;
                    case "横条纹" -> z % 2 == 0;
                    case "竖条纹" -> x % 2 == 0;
                    case "边框" -> x == mnX || x == mxX || z == mnZ || z == mxZ;
                    case "L↖" -> x == mnX || z == mnZ;
                    case "L↗" -> x == mxX || z == mnZ;
                    case "L↙" -> x == mnX || z == mxZ;
                    case "L↘" -> x == mxX || z == mxZ;
                    case "十字" -> (x == (mnX + mxX) / 2 || x == (mnX + mxX + 1) / 2) || (z == (mnZ + mxZ) / 2 || z == (mnZ + mxZ + 1) / 2);
                    case "马路" -> { int cz = (mnZ + mxZ) / 2; yield z >= cz - 1 && z <= cz + 1; }
                    default -> new Random().nextBoolean();
                };
                design.put(new BlockPos(x, 0, z), useM1 ? m1 : m2);
            }
        }
    }

    private void aiGenerate() {
        try {
            Path cfgPath = Minecraft.getInstance().gameDirectory.toPath().resolve("config/platformbuilder.json");
            if (!Files.exists(cfgPath)) return;
            JsonObject cfg = JsonParser.parseString(Files.readString(cfgPath)).getAsJsonObject();
            String url = cfg.get("api_url").getAsString();
            String key = cfg.get("api_key").getAsString();
            String model = cfg.get("model").getAsString();

            int mnX = -5, mxX = 5, mnZ = -5, mxZ = 5;
            if (tool == Tool.RECT && toolStart != null && toolEnd != null) {
                mnX = Math.min(toolStart.getX(), toolEnd.getX());
                mxX = Math.max(toolStart.getX(), toolEnd.getX());
                mnZ = Math.min(toolStart.getZ(), toolEnd.getZ());
                mxZ = Math.max(toolStart.getZ(), toolEnd.getZ());
            }

            StringBuilder mats = new StringBuilder();
            for (int i = 0; i < Math.min(materials.size(), 20); i++)
                mats.append(materials.get(i)).append(", ");

            String prompt = String.format(
                "You are a Minecraft floor pattern generator. Available blocks: [%s]. " +
                "Generate a creative floor design for area X:%d..%d Z:%d..%d. " +
                "Output ONLY a JSON object mapping \"x,z\" to block IDs. Example: {\"0,0\":\"minecraft:stone\",\"0,1\":\"minecraft:dirt\"}",
                mats, mnX, mxX, mnZ, mxZ);

            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            var msgs = new com.google.gson.JsonArray();
            var msg = new JsonObject();
            msg.addProperty("role", "user");
            msg.addProperty("content", prompt);
            msgs.add(msg);
            body.add("messages", msgs);
            body.addProperty("temperature", 0.7);

            var client = HttpClient.newHttpClient();
            var req = HttpRequest.newBuilder()
                .uri(URI.create(url + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
            var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            JsonObject result = JsonParser.parseString(resp.body()).getAsJsonObject();
            String content = result.getAsJsonArray("choices").get(0)
                .getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
            content = content.replaceAll("```json|```", "").trim();
            JsonObject map = JsonParser.parseString(content).getAsJsonObject();

            saveUndo();
            for (var entry : map.entrySet()) {
                String[] parts = entry.getKey().split(",");
                int x = Integer.parseInt(parts[0].trim());
                int z = Integer.parseInt(parts[1].trim());
                BlockPos p = new BlockPos(x, 0, z);
                if (!design.containsKey(p))
                    design.put(p, entry.getValue().getAsString());
            }
        } catch (Exception ignored) {}
    }

    private void handlePopupClick(int mx, int my) {
        int pw = 340, ph = 266;
        int px = (width - pw) / 2, py = (height - ph) / 2;
        if (mx >= px + pw - 20 && mx < px + pw - 4 && my >= py + 4 && my < py + 20) {
            showPatternPopup = false; return;
        }
        // Material selectors
        if (my >= py + 24 && my < py + 44) {
            if (mx >= px + 27 && mx < px + 49) { popM1Idx = (popM1Idx + 1) % Math.max(1, materials.size()); return; }
            if (mx >= px + 75 && mx < px + 97) { popM2Idx = (popM2Idx + 1) % Math.max(1, materials.size()); return; }
        }
        int rowH = 34, cols = 2;
        int colW = (pw - 24) / cols;
        for (int i = 0; i < PATTERN_NAMES.length; i++) {
            int col = i % cols, row = i / cols;
            int cx = px + 8 + col * colW;
            int ry = py + 48 + row * rowH;
            if (mx >= cx && mx < cx + colW && my >= ry && my < ry + rowH) {
                selectedPattern = i; return;
            }
        }
        int btnX = px + pw - 50, btnY = py + ph - 24;
        if (mx >= btnX && mx < btnX + 40 && my >= btnY && my < btnY + 18) {
            if (selectedPattern >= 0) {
                generatePatternToClipboard(selectedPattern);
                mode = Mode.PASTE;
            }
            showPatternPopup = false;
        }
    }

    private void renderPopup(GuiGraphics g) {
        int pw = 340, ph = 266;
        int px = (width - pw) / 2, py = (height - ph) / 2;
        g.fill(px, py, px + pw, py + ph, 0xE8202020);
        drawBorder(g, px, py, pw, ph);
        g.drawCenteredString(font, Component.literal("选择图案"), px + pw / 2, py + 6, 0xFFFFFFFF);
        g.drawString(font, Component.literal("✕"), px + pw - 18, py + 6, 0xFFFF6666, false);

        if (popM1Idx >= materials.size()) popM1Idx = 0;
        if (popM2Idx >= materials.size()) popM2Idx = materials.isEmpty() ? 0 : Math.min(1, materials.size() - 1);
        String m1 = materials.isEmpty() ? "minecraft:white_concrete" : materials.get(popM1Idx);
        String m2 = materials.size() > 1 ? materials.get(popM2Idx) : m1;
        int c1 = matColors.getOrDefault(m1, 0xFFFFFFFF);
        int c2 = matColors.getOrDefault(m2, 0xFF888888);

        g.drawString(font, Component.literal("主:"), px + 8, py + 26, 0xFFAAAAAA, false);
        g.fill(px + 28, py + 24, px + 48, py + 44, c1);
        g.fill(px + 27, py + 23, px + 49, py + 45, 0xFFFFFFFF);
        g.fill(px + 28, py + 24, px + 48, py + 44, c1);
        g.drawString(font, Component.literal("副:"), px + 56, py + 26, 0xFFAAAAAA, false);
        g.fill(px + 76, py + 24, px + 96, py + 44, c2);
        g.fill(px + 75, py + 23, px + 97, py + 45, 0xFFFFFFFF);
        g.fill(px + 76, py + 24, px + 96, py + 44, c2);

        int rowH = 34, cellSz = 2, cols = 2;
        int colW = (pw - 24) / cols;
        for (int i = 0; i < PATTERN_NAMES.length; i++) {
            int col = i % cols, row = i / cols;
            int cx = px + 8 + col * colW;
            int ry = py + 48 + row * rowH;
            if (i == selectedPattern)
                g.fill(cx - 2, ry - 2, cx + colW - 4, ry + rowH - 2, 0x40FFFFFF);

            String name = PATTERN_NAMES[i];
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    boolean useM1 = switch (name) {
                        case "棋盘格" -> (x + z) % 2 == 0;
                        case "横条纹" -> z % 2 == 0;
                        case "竖条纹" -> x % 2 == 0;
                        case "边框" -> x == 0 || x == 15 || z == 0 || z == 15;
                        case "L↖" -> x == 0 || z == 0;
                        case "L↗" -> x == 15 || z == 0;
                        case "L↙" -> x == 0 || z == 15;
                        case "L↘" -> x == 15 || z == 15;
                        case "十字" -> x == 7 || x == 8 || z == 7 || z == 8;
                        case "马路" -> z >= 7 && z <= 8;
                        default -> (x * 7 + z * 13) % 3 != 0;
                    };
                    g.fill(cx + x * cellSz, ry + 2 + z * cellSz,
                        cx + x * cellSz + cellSz - 1, ry + 2 + z * cellSz + cellSz - 1,
                        useM1 ? c1 : c2);
                }
            }
            g.drawString(font, Component.literal(name), cx + 36, ry + 8, 0xFFFFFFFF, false);
            int bX = cx + colW - 40, bY = ry + 6;
            g.fill(bX, bY, bX + 32, bY + 18, i == selectedPattern ? 0xFF668866 : 0xFF444444);
            g.drawCenteredString(font, Component.literal(i == selectedPattern ? "已选" : "选择"),
                bX + 16, bY + 5, 0xFFFFFFFF);
        }

        int btnX = px + pw - 50, btnY = py + ph - 24;
        g.fill(btnX, btnY, btnX + 40, btnY + 18, 0xFF446644);
        g.drawCenteredString(font, Component.literal("确定"), btnX + 20, btnY + 5, 0xFFFFFFFF);
    }

    private void doReplace(String to) {
        if (replaceFrom == null || replaceFrom.equals(to)) return;
        saveUndo();
        int mnX = Integer.MIN_VALUE, mxX = Integer.MAX_VALUE;
        int mnZ = Integer.MIN_VALUE, mxZ = Integer.MAX_VALUE;
        if (tool == Tool.RECT && toolStart != null && toolEnd != null) {
            mnX = Math.min(toolStart.getX(), toolEnd.getX());
            mxX = Math.max(toolStart.getX(), toolEnd.getX());
            mnZ = Math.min(toolStart.getZ(), toolEnd.getZ());
            mxZ = Math.max(toolStart.getZ(), toolEnd.getZ());
        }
        var toChange = new ArrayList<BlockPos>();
        for (var e : design.entrySet()) {
            BlockPos p = e.getKey();
            if (e.getValue().equals(replaceFrom)
                && p.getX() >= mnX && p.getX() <= mxX && p.getZ() >= mnZ && p.getZ() <= mxZ)
                toChange.add(p);
        }
        for (BlockPos p : toChange) design.put(p, to);
    }

    private void generatePatternToClipboard(int idx) {
        int mnX = 0, mxX = 15, mnZ = 0, mxZ = 15;
        if (tool == Tool.RECT && toolStart != null && toolEnd != null) {
            mnX = Math.min(toolStart.getX(), toolEnd.getX());
            mxX = Math.max(toolStart.getX(), toolEnd.getX());
            mnZ = Math.min(toolStart.getZ(), toolEnd.getZ());
            mxZ = Math.max(toolStart.getZ(), toolEnd.getZ());
        }
        if (popM1Idx >= materials.size()) popM1Idx = 0;
        String m1 = materials.isEmpty() ? null : materials.get(popM1Idx);
        if (m1 == null) return;
        if (popM2Idx >= materials.size()) popM2Idx = Math.min(popM1Idx + 1, materials.size() - 1);
        String m2 = materials.size() > 1 ? materials.get(popM2Idx) : m1;

        clipboard = new LinkedHashMap<>();
        String name = PATTERN_NAMES[idx];
        for (int x = mnX; x <= mxX; x++) {
            for (int z = mnZ; z <= mxZ; z++) {
                boolean useM1 = switch (name) {
                    case "棋盘格" -> (x + z) % 2 == 0;
                    case "横条纹" -> z % 2 == 0;
                    case "竖条纹" -> x % 2 == 0;
                    case "边框" -> x == mnX || x == mxX || z == mnZ || z == mxZ;
                    case "L↖" -> x == mnX || z == mnZ;
                    case "L↗" -> x == mxX || z == mnZ;
                    case "L↙" -> x == mnX || z == mxZ;
                    case "L↘" -> x == mxX || z == mxZ;
                    case "十字" -> (x == (mnX + mxX) / 2 || x == (mnX + mxX + 1) / 2) || (z == (mnZ + mxZ) / 2 || z == (mnZ + mxZ + 1) / 2);
                    case "马路" -> { int cz = (mnZ + mxZ) / 2; yield z >= cz - 1 && z <= cz + 1; }
                    default -> new Random().nextBoolean();
                };
                clipboard.put(new BlockPos(x - mnX, 0, z - mnZ), useM1 ? m1 : m2);
            }
        }
        clipCenterX = (mxX - mnX) / 2;
        clipCenterZ = (mxZ - mnZ) / 2;
    }

    private boolean isInGrid(int mx, int my) {
        return mx >= leftPos + GRID_X && mx <= leftPos + GRID_X + GRID_W
            && my >= topPos + GRID_Y && my <= topPos + GRID_Y + GRID_H;
    }

    private BlockPos screenToGrid(int mx, int my) {
        float es = 16f * zoom;
        float cx = leftPos + GRID_X + GRID_W / 2f + panX;
        float cy = topPos + GRID_Y + GRID_H / 2f + panY;
        return new BlockPos((int) Math.floor((mx - cx) / es), 0, (int) Math.floor((my - cy) / es));
    }

    private static String shortName(String id) {
        return id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
    }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + 2, 0xFF3A3A3A);
        g.fill(x, y + h - 2, x + w, y + h, 0xFF3A3A3A);
        g.fill(x, y, x + 2, y + h, 0xFF3A3A3A);
        g.fill(x + w - 2, y, x + w, y + h, 0xFF3A3A3A);
    }

    private static void hline(GuiGraphics g, int x1, int x2, int y, int color) {
        g.fill(x1, y, x2, y + 1, color);
    }

    // === NETWORK ===

    private void sendDesign() {
        PlatformServices.PLATFORM.sendSyncDesignPacket(getBEPos(), new HashMap<>(design));
    }

    private PlatformBuilderBlockEntity getBE() {
        return menu.blockEntity;
    }

    private BlockPos getBEPos() {
        return getBE() != null ? getBE().getBlockPos() : BlockPos.ZERO;
    }

    private int getBuildSpeed() {
        return getBE() != null ? getBE().getBuildSpeed() : 50;
    }

    private void setBuildSpeed(int speed) {
        if (getBE() != null) getBE().setBuildSpeed(speed);
    }

    private int getBuildOffsetY() {
        return getBE() != null ? getBE().getBuildOffsetY() : 0;
    }

    private void setBuildOffsetY(int offset) {
        if (getBE() != null) getBE().setBuildOffsetY(offset);
    }

    public void onClose() { sendDesign(); super.onClose(); }

    public void updateDesign(Map<BlockPos, String> d) {
        design.clear(); if (d != null) design.putAll(d);
    }
}
