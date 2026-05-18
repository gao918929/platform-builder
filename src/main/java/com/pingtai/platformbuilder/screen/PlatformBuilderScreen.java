package com.pingtai.platformbuilder.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.pingtai.platformbuilder.network.ModMessages;
import com.pingtai.platformbuilder.network.BuildPlatformPacket;
import com.pingtai.platformbuilder.network.ExtractAllPacket;
import com.pingtai.platformbuilder.network.QuickLoadPacket;
import com.pingtai.platformbuilder.network.SetOffsetPacket;
import com.pingtai.platformbuilder.network.SetSpeedPacket;
import com.pingtai.platformbuilder.network.SyncDesignPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

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

    private static final int TB_Y = 6, TB_H = 22;
    private static final int GRID_X = 8, GRID_Y = 32, GRID_H = 140, GRID_W = 322;
    private static final int BTN_W = 24, BTN_H = 18;
    private static final int DESIGN_H = 220; // slots start at 230, hidden in this mode
    private static final int INVENTORY_H = 390;

    // State
    private boolean inventoryMode;

    // Design-mode state
    private Tool tool = Tool.BRUSH;
    private String selectedMaterial;
    private final List<String> materials = new ArrayList<>();
    private final Map<String, ItemStack> matIcons = new LinkedHashMap<>();
    private final Map<String, Integer> matColors = new LinkedHashMap<>();
    private int matScroll;
    private float panX, panY, zoom = 1f;
    private static final float MIN_ZOOM = 0.2f, MAX_ZOOM = 5f;
    private boolean panning, drawing;
    private BlockPos lastDraw, toolStart, toolEnd;
    private boolean showChunks = true;
    private final Map<BlockPos, String> design = new LinkedHashMap<>();
    private final Deque<Map<BlockPos, String>> undoStack = new ArrayDeque<>();
    private static final int MAX_UNDO = 50;

    // Toolbar hit areas
    private final int[] toolBtnX = new int[Tool.values().length];
    private int undoCX, clearCX, chunkCX, matStartX;
    private int tby;

    public PlatformBuilderScreen(PlatformBuilderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 338;
        imageHeight = DESIGN_H;
    }

    // ==================== INIT ====================

    @Override
    protected void init() {
        imageHeight = inventoryMode ? INVENTORY_H : DESIGN_H;
        super.init();

        if (menu.blockEntity.getDesign() != null)
            design.putAll(menu.blockEntity.getDesign());

        if (inventoryMode) {
            buildInventoryWidgets();
        } else {
            buildDesignWidgets();
            refreshMaterials();
        }
    }

    private void buildDesignWidgets() {
        // Mode toggle
        addRenderableWidget(Button.builder(
                Component.literal("放入"), b -> switchToInventoryMode()
        ).pos(leftPos + 8, topPos + TB_Y + 2).size(36, 18).build());

        // Speed control
        int spdY = topPos + 198;
        addRenderableWidget(Button.builder(
                Component.literal("-"), b -> changeSpeed(-10)
        ).pos(leftPos + 8, spdY).size(14, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("+"), b -> changeSpeed(10)
        ).pos(leftPos + 23, spdY).size(14, 20).build());

        // Y-offset control
        addRenderableWidget(Button.builder(
                Component.literal("-"), b -> changeOffset(-1)
        ).pos(leftPos + 104, spdY).size(14, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("0"), b -> changeOffsetTo(0)
        ).pos(leftPos + 119, spdY).size(14, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("+"), b -> changeOffset(1)
        ).pos(leftPos + 134, spdY).size(14, 20).build());

        // Build button
        addRenderableWidget(Button.builder(
                Component.translatable("gui.platformbuilder.build"), b -> {
                    sendDesign();
                    ModMessages.sendToServer(new BuildPlatformPacket(menu.blockEntity.getBlockPos()));
                }
        ).pos(leftPos + 230, spdY).size(100, 20).build());
    }

    private void changeSpeed(int delta) {
        int newSpeed = menu.blockEntity.getBuildSpeed() + delta;
        menu.blockEntity.setBuildSpeed(newSpeed);
        ModMessages.sendToServer(new SetSpeedPacket(menu.blockEntity.getBlockPos(), newSpeed));
    }

    private void changeOffset(int delta) {
        int newOff = menu.blockEntity.getBuildOffsetY() + delta;
        menu.blockEntity.setBuildOffsetY(newOff);
        ModMessages.sendToServer(new SetOffsetPacket(menu.blockEntity.getBlockPos(), newOff));
    }

    private void changeOffsetTo(int value) {
        menu.blockEntity.setBuildOffsetY(value);
        ModMessages.sendToServer(new SetOffsetPacket(menu.blockEntity.getBlockPos(), value));
    }

    private void buildInventoryWidgets() {
        // Mode toggle
        addRenderableWidget(Button.builder(
                Component.literal("设计"), b -> switchToDesignMode()
        ).pos(leftPos + 8, topPos + 6).size(36, 18).build());

        // Quick load
        addRenderableWidget(Button.builder(
                Component.literal("→ 放入全部"), b ->
                ModMessages.sendToServer(new QuickLoadPacket(menu.blockEntity.getBlockPos()))
        ).pos(leftPos + 8, topPos + 290).size(80, 20).build());

        // Extract all
        addRenderableWidget(Button.builder(
                Component.literal("← 取出全部"), b ->
                ModMessages.sendToServer(new ExtractAllPacket(menu.blockEntity.getBlockPos()))
        ).pos(leftPos + 100, topPos + 290).size(80, 20).build());

        // Build button
        addRenderableWidget(Button.builder(
                Component.translatable("gui.platformbuilder.build"), b -> {
                    sendDesign();
                    ModMessages.sendToServer(new BuildPlatformPacket(menu.blockEntity.getBlockPos()));
                }
        ).pos(leftPos + 230, topPos + 290).size(100, 20).build());
    }

    private void switchToInventoryMode() {
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

    // ==================== BG RENDER ====================

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

        // Toolbar bg (tools only, no materials)
        g.fill(leftPos + 46, tby - 2, leftPos + imageWidth - 4, tby + TB_H + 2, 0x80222222);
        hline(g, leftPos + 46, leftPos + imageWidth - 4, tby + TB_H + 3, 0xFF555555);

        // Grid bg
        int gy = topPos + GRID_Y;
        g.fill(leftPos + GRID_X - 1, gy - 1, leftPos + GRID_X + GRID_W + 1, gy + GRID_H + 1, 0xFF222222);
        g.fill(leftPos + GRID_X, gy, leftPos + GRID_X + GRID_W, gy + GRID_H, 0x80101010);
        hline(g, leftPos + 4, leftPos + imageWidth - 4, gy + GRID_H + 1, 0xFF555555);

        renderToolbar(g);
        g.enableScissor(leftPos + GRID_X, gy, leftPos + GRID_X + GRID_W, gy + GRID_H);
        renderGrid(g, mx, my);
        g.disableScissor();

        // Material palette below grid
        renderMaterialPalette(g, mx, my);

        // Speed & offset display
        int spd = menu.blockEntity.getBuildSpeed();
        int off = menu.blockEntity.getBuildOffsetY();
        g.drawString(font, Component.literal("速度:" + spd), leftPos + 40, topPos + 200, 0xFFAAAAAA, false);
        g.drawString(font, Component.literal("Y:" + (off >= 0 ? "+" : "") + off),
                leftPos + 152, topPos + 200, 0xFFAAAAAA, false);
    }

    private void renderMaterialPalette(GuiGraphics g, int mx, int my) {
        int palY = topPos + 176;
        int x = leftPos + 8;

        int maxScroll = Math.max(0, materials.size() - 12);
        if (matScroll > maxScroll) matScroll = maxScroll;
        if (matScroll < 0) matScroll = 0;

        // Label
        g.drawString(font, Component.literal("材料"), x, palY, 0xFFAAAAAA, false);
        x += 24;

        // Scroll left arrow
        if (matScroll > 0) {
            g.fill(x, palY, x + 10, palY + 18, 0x80555555);
            g.drawString(font, Component.literal("<"), x + 2, palY + 5, 0xFFFFFFFF, false);
            x += 12;
        }
        matStartX = x;

        // Material icons
        for (int i = 0; i < 12 && i + matScroll < materials.size(); i++) {
            String mat = materials.get(i + matScroll);
            boolean sel = mat.equals(selectedMaterial);
            ItemStack icon = matIcons.get(mat);
            if (sel) g.fill(x - 1, palY - 1, x + 21, palY + 19, 0xFFFFFF88);
            if (icon != null) g.renderItem(icon, x + 2, palY + 1);
            x += 22;
        }

        // Scroll right arrow
        if (matScroll < maxScroll) {
            g.fill(x, palY, x + 10, palY + 18, 0x80555555);
            g.drawString(font, Component.literal(">"), x + 2, palY + 5, 0xFFFFFFFF, false);
        }
    }

    private void renderInventoryBg(GuiGraphics g) {
        // Machine inventory section — slots at 230,248,266 (bottom 284)
        g.fill(leftPos + 6, topPos + 198, leftPos + imageWidth - 6, topPos + 286, 0x80181818);
        hline(g, leftPos + 6, leftPos + imageWidth - 6, topPos + 286, 0xFF555555);
        g.drawString(font, Component.literal("材料库存"), leftPos + 8, topPos + 202, 0xFFAAAAAA, false);

        // Player inventory section — slots at 300,318,336 + hotbar 362 (bottom 380)
        g.fill(leftPos + 6, topPos + 288, leftPos + imageWidth - 6, topPos + 384, 0x80181818);
        hline(g, leftPos + 6, leftPos + imageWidth - 6, topPos + 288, 0xFF555555);
        g.drawString(font, Component.literal("物品栏"), leftPos + 8, topPos + 292, 0xFFAAAAAA, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!inventoryMode) refreshMaterials();
        renderBackground(g);

        // Clip slots in design mode — machine/player slots are below imageHeight
        if (!inventoryMode) {
            g.enableScissor(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        }
        super.render(g, mx, my, pt);
        if (!inventoryMode) {
            g.disableScissor();
        }

        if (!inventoryMode && isInGrid(mx, my) && tool != Tool.RECT) {
            BlockPos p = screenToGrid(mx, my);
            if (p != null && design.containsKey(p)) {
                g.renderTooltip(font, Component.literal(
                        "X:" + p.getX() + " Z:" + p.getZ() + " - " + shortName(design.get(p))), mx, my);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}

    // ==================== TOOLBAR (design mode only) ====================

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

    // ==================== GRID (design mode only) ====================

    private void renderGrid(GuiGraphics g, int mx, int my) {
        float es = 16f * zoom;
        int cx = leftPos + GRID_X + GRID_W / 2 + (int) panX;
        int cy = topPos + GRID_Y + GRID_H / 2 + (int) panY;

        int sX = (int) Math.floor((leftPos + GRID_X - cx) / es) - 1;
        int eX = (int) Math.ceil((leftPos + GRID_X + GRID_W - cx) / es) + 1;
        int sZ = (int) Math.floor((topPos + GRID_Y - cy) / es) - 1;
        int eZ = (int) Math.ceil((topPos + GRID_Y + GRID_H - cy) / es) + 1;

        BlockPos wp = menu.blockEntity.getBlockPos();
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
                String mat = design.get(new BlockPos(gx, 0, gz));

                if (mat != null) {
                    if (es >= 8) {
                        ItemStack icon = matIcons.get(mat);
                        if (icon != null)
                            g.renderItem(icon, sx + (sz - 16) / 2, sy + (sz - 16) / 2);
                        else {
                            Integer col = matColors.get(mat);
                            g.fill(sx, sy, sx + sz - 1, sy + sz - 1, col != null ? col : 0xFF44AA44);
                        }
                    } else {
                        Integer col = matColors.get(mat);
                        g.fill(sx, sy, sx + sz - 1, sy + sz - 1, col != null ? col : 0xFF44AA44);
                    }
                } else {
                    g.fill(sx, sy, sx + sz - 1, sy + sz - 1, 0x40181818);
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

                boolean cx2 = showChunks && Math.floorMod(wx + gx, 16) == 0;
                boolean cz2 = showChunks && Math.floorMod(wz + gz, 16) == 0;
                int ml = Math.min(sz, 2);
                g.fill(sx, sy, sx + sz, sy + Math.min(sz, cz2 ? ml : 1), cz2 ? 0xCCFFCC00 : 0x60FFFFFF);
                g.fill(sx, sy, sx + Math.min(sz, cx2 ? ml : 1), sy + sz, cx2 ? 0xCCFFCC00 : 0x60FFFFFF);
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

        ItemStack previewIcon = (selectedMaterial != null) ? matIcons.get(selectedMaterial) : null;
        Integer previewColor = (selectedMaterial != null) ? matColors.get(selectedMaterial) : null;

        // Brush / Eraser hover cursor
        if (isInGrid(mx, my) && (tool == Tool.BRUSH || tool == Tool.ERASER) && !drawing) {
            BlockPos hp = screenToGrid(mx, my);
            if (hp != null) {
                int hx = (int) (cx + hp.getX() * es), hy = (int) (cy + hp.getZ() * es);
                int sz = Math.max(1, (int) es);
                if (tool == Tool.BRUSH && sz >= 10 && previewIcon != null) {
                    RenderSystem.setShaderColor(1f, 1f, 1f, 0.55f);
                    g.renderItem(previewIcon, hx + (sz - 16) / 2, hy + (sz - 16) / 2);
                    RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
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

        // RECT fill preview
        if (tool == Tool.RECT && toolStart != null && toolEnd != null && previewIcon != null) {
            int rmnX = Math.min(toolStart.getX(), toolEnd.getX());
            int rmX = Math.max(toolStart.getX(), toolEnd.getX());
            int rmnZ = Math.min(toolStart.getZ(), toolEnd.getZ());
            int rmZ = Math.max(toolStart.getZ(), toolEnd.getZ());
            if ((rmX - rmnX + 1) * (rmZ - rmnZ + 1) <= 2500) {
                for (int gx = rmnX; gx <= rmX; gx++) {
                    for (int gz = rmnZ; gz <= rmZ; gz++) {
                        int sx = (int) (cx + gx * es), sy = (int) (cy + gz * es);
                        if (es >= 10) {
                            RenderSystem.setShaderColor(1f, 1f, 1f, 0.5f);
                            g.renderItem(previewIcon, sx + 1, sy + 1);
                            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                        } else if (previewColor != null) {
                            g.fill(sx, sy, sx + Math.max(1, (int) es) - 1, sy + Math.max(1, (int) es) - 1,
                                    (previewColor & 0x00FFFFFF) | 0xB0000000);
                        }
                    }
                }
            }
        }

        // Circle center marker
        if (tool == Tool.CIRCLE && toolStart != null) {
            int scx = (int)(cx + toolStart.getX() * es), scy = (int)(cy + toolStart.getZ() * es);
            int sz = Math.max(1, (int)es);
            // Crosshair at center
            g.fill(scx + sz/2 - 1, scy + 2, scx + sz/2 + 2, scy + sz - 2, 0xCCAA66CC);
            g.fill(scx + 2, scy + sz/2 - 1, scx + sz - 2, scy + sz/2 + 2, 0xCCAA66CC);
        }

        // Line endpoints
        if (tool == Tool.LINE && toolStart != null) {
            int lx = (int)(cx + toolStart.getX() * es), ly = (int)(cy + toolStart.getZ() * es);
            int lsz = Math.max(1, (int)es);
            g.fill(lx, ly, lx + lsz, ly + 2, 0xCC44AAAA);
            g.fill(lx, ly, lx + 2, ly + lsz, 0xCC44AAAA);
            g.fill(lx + lsz - 2, ly, lx + lsz, ly + lsz, 0xCC44AAAA);
            g.fill(lx, ly + lsz - 2, lx + lsz, ly + lsz, 0xCC44AAAA);
        }
    }

    // ==================== INPUT ====================

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Mode toggle button area — let the widget system handle it
        int modeBtnX = leftPos + 8, modeBtnY = topPos + TB_Y + 2;
        if (!inventoryMode && btn == 0
                && mx >= modeBtnX && mx < modeBtnX + 36
                && my >= modeBtnY && my <= modeBtnY + 18) {
            return super.mouseClicked(mx, my, btn);
        }

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

        // Material palette below grid
        int palY = topPos + 176;
        if (btn == 0 && my >= palY && my <= palY + 18) {
            int ix = matStartX;
            // Left arrow
            if (matScroll > 0 && mx >= leftPos + 32 && mx < leftPos + 42) {
                matScroll--; return true;
            }
            for (int i = 0; i < 12 && i + matScroll < materials.size(); i++) {
                if (mx >= ix && mx < ix + 20) { selectedMaterial = materials.get(i + matScroll); return true; }
                ix += 22;
            }
            int maxScroll = Math.max(0, materials.size() - 12);
            if (matScroll < maxScroll && mx >= ix && mx < ix + 10) { matScroll++; return true; }
            return true;
        }

        if (isInGrid((int) mx, (int) my)) {
            if (btn == 0) {
                switch (tool) {
                    case BRUSH, ERASER -> { drawing = true; lastDraw = null; applyTool((int) mx, (int) my); }
                    case RECT, CIRCLE, LINE -> { toolStart = screenToGrid((int) mx, (int) my); toolEnd = toolStart; }
                    case PIPETTE -> pickMaterial((int) mx, (int) my);
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
    public boolean mouseScrolled(double mx, double my, double d) {
        if (inventoryMode) return super.mouseScrolled(mx, my, d);
        if (isInGrid((int) mx, (int) my)) {
            float nz = zoom * (d > 0 ? 1.10f : 0.91f);
            nz = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, nz));
            float cx = leftPos + GRID_X + GRID_W / 2f + panX;
            float cy = topPos + GRID_Y + GRID_H / 2f + panY;
            float oe = 16f * zoom, ne = 16f * nz;
            float wx = ((float) mx - cx) / oe, wy = ((float) my - cy) / oe;
            panX += wx * (oe - ne); panY += wy * (oe - ne);
            zoom = nz; return true;
        }
        return super.mouseScrolled(mx, my, d);
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (inventoryMode) return super.keyPressed(k, s, m);
        if (k == 90 && hasControlDown()) { undo(); return true; }
        if (k == 66) { tool = Tool.BRUSH; return true; }
        if (k == 69) { tool = Tool.ERASER; return true; }
        if (k == 82) { tool = Tool.RECT; return true; }
        if (k == 67) { tool = Tool.CIRCLE; return true; }
        if (k == 76) { tool = Tool.LINE; return true; }
        if (k == 80) { tool = Tool.PIPETTE; return true; }
        if (k == 71 && !hasControlDown()) { showChunks = !showChunks; return true; }
        return super.keyPressed(k, s, m);
    }

    // ==================== ACTIONS ====================

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
        if (selectedMaterial == null && !materials.isEmpty()) selectedMaterial = materials.get(0);
        if (selectedMaterial == null) { toolStart = null; toolEnd = null; return; }
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
        // Iterate bounding box of circle
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

    /** Bresenham check: is (gx, gz) on the line between toolStart and toolEnd? */
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

    // ==================== UTILS ====================

    private void refreshMaterials() {
        var handler = menu.blockEntity.getItemHandler();
        Set<String> current = new LinkedHashSet<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack st = handler.getStackInSlot(i);
            if (!st.isEmpty() && st.getItem() instanceof BlockItem bi) {
                ResourceLocation id = ForgeRegistries.BLOCKS.getKey(bi.getBlock());
                if (id != null) current.add(id.toString());
            }
        }
        for (String m : design.values()) current.add(m);

        // Pig Certificate: add all concrete types to material palette
        if (menu.blockEntity.hasPigCertificate()) {
            for (var entry : ForgeRegistries.BLOCKS.getEntries()) {
                ResourceLocation id = entry.getKey().location();
                String idStr = id.toString();
                if (idStr.endsWith("_concrete") || idStr.endsWith("_concrete_powder")) {
                    current.add(idStr);
                }
            }
        }

        if (current.size() == materials.size() && current.containsAll(materials)) return;

        materials.clear(); matIcons.clear(); matColors.clear();
        for (String id : current) {
            materials.add(id);
            var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
            if (block != null) {
                matIcons.put(id, new ItemStack(block.asItem()));
                matColors.put(id, 0xFF000000 | block.defaultMapColor().col);
            }
        }
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

    // ==================== NETWORK ====================

    private void sendDesign() {
        ModMessages.sendToServer(new SyncDesignPacket(menu.blockEntity.getBlockPos(), new HashMap<>(design)));
    }

    @Override
    public void onClose() { sendDesign(); super.onClose(); }

    public void updateDesign(Map<BlockPos, String> d) {
        design.clear(); if (d != null) design.putAll(d);
    }
}
