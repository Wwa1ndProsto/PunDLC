package itz.async.feature.ui.screen.csgui;

import itz.async.Async;
import itz.async.feature.module.api.Category;
import itz.async.feature.module.api.Module;
import itz.async.feature.module.api.setting.Setting;
import itz.async.feature.module.api.setting.impl.*;
import itz.async.utils.animation.Animation;
import itz.async.utils.animation.Easing;
import itz.async.utils.client.IMinecraft;
import itz.async.utils.client.Keyboard;
import itz.async.utils.render.ColorRGBA;
import itz.async.utils.render.Fonts;
import itz.async.utils.render.RenderContext;
import itz.async.utils.render.text.Font;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.*;
import java.util.List;

public class CsGui extends Screen implements IMinecraft {
    private final Animation openAnim = new Animation(350, Easing.QUAD_IN_OUT);
    private final Animation alphaAnim = new Animation(300, Easing.QUAD_IN_OUT);

    private final Set<Category> categories = EnumSet.of(Category.COMBAT, Category.MOVEMENT, Category.RENDER, Category.PLAYER, Category.MISC);
    private static Category lastSelected = Category.COMBAT;
    private Category selected = lastSelected;

    private boolean isClosing;
    private boolean binding = false;
    private Module bindingTarget = null;

    private static final int GUI_W = 400;
    private static final int GUI_H = 280;

    private static final int TOP_H = 40;

    private boolean lightTheme = false;
    private boolean closeHovered = false;
    private boolean themeToggleHovered = false;

    private final Map<Module, Float> expandProgress = new HashMap<>();
    private final Map<Module, Float> toggleProgress = new HashMap<>();

    private final int ITEM_H = 24;
    private float listScroll = 0f;
    private float listScrollTarget = 0f;


    private String searchText = "";
    private boolean searchFocused = false;
    private int searchCursor = 0;


    private Setting activeSetting = null;
    private boolean draggingSlider = false;

    public CsGui() {
        super(Text.of("CsGui"));
        openAnim.setStartValue(0);
        openAnim.animate(1);
        alphaAnim.setStartValue(0);
        alphaAnim.animate(1);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingSlider) {
            draggingSlider = false;
            activeSetting = null;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {
        if (draggingSlider && activeSetting instanceof NumberSetting ns) {

            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dX, dY);
    }

    @Override
    public void init() {
        super.init();
        isClosing = false;
        openAnim.reset(0);
        openAnim.animate(1);
        alphaAnim.reset(0);
        alphaAnim.animate(1);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        super.close();
        lastSelected = selected;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { searchFocused = false; return true; }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && searchCursor > 0 && !searchText.isEmpty()) {
                searchText = searchText.substring(0, searchCursor - 1) + searchText.substring(searchCursor);
                searchCursor = Math.max(0, searchCursor - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE && searchCursor < searchText.length()) {
                searchText = searchText.substring(0, searchCursor) + searchText.substring(searchCursor + 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) { searchCursor = Math.max(0, searchCursor - 1); return true; }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) { searchCursor = Math.min(searchText.length(), searchCursor + 1); return true; }
            if (keyCode == GLFW.GLFW_KEY_HOME) { searchCursor = 0; return true; }
            if (keyCode == GLFW.GLFW_KEY_END) { searchCursor = searchText.length(); return true; }
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !binding && !searchFocused) {
            isClosing = true;
            openAnim.animate(0);
            alphaAnim.animate(0);
            return true;
        }

        if (binding && bindingTarget != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                binding = false;
                bindingTarget = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                bindingTarget.setKey(GLFW.GLFW_KEY_UNKNOWN);
            } else {
                bindingTarget.setKey(keyCode);
            }
            binding = false;
            bindingTarget = null;
            return true;
        }
        

        if (activeSetting instanceof KeySetting ks) {
             if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                activeSetting = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                ks.setKeyCode(GLFW.GLFW_KEY_UNKNOWN);
                activeSetting = null;
                return true;
            }
            ks.setKeyCode(keyCode);
            activeSetting = null;
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocused && !Character.isISOControl(chr)) {
            searchText = searchText.substring(0, searchCursor) + chr + searchText.substring(searchCursor);
            searchCursor++;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        float cx = width / 2f - GUI_W / 2f;
        float cy = height / 2f - GUI_H / 2f;

        float listX = cx + 14;
        float listYView = cy + TOP_H + 30;
        float listW = GUI_W - 28;
        float listH = GUI_H - TOP_H - 40;

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listYView && mouseY <= listYView + listH) {
            listScrollTarget -= vertical * 16f;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float cx = width / 2f - GUI_W / 2f;
        float cy = height / 2f - GUI_H / 2f;


        if (button == 0) {
            float closeX = cx + 12;
            float closeY = cy + 12;
            float closeSize = 16;
            if (isIn(mouseX, mouseY, closeX - closeSize / 2f, closeY - closeSize / 2f, closeSize, closeSize)) {
                isClosing = true;
                openAnim.animate(0);
                alphaAnim.animate(0);
                return true;
            }

            float themeX = cx + GUI_W - 12;
            float themeY = cy + 12;
            float themeSize = 16;
            if (isIn(mouseX, mouseY, themeX - themeSize / 2f, themeY - themeSize / 2f, themeSize, themeSize)) {
                lightTheme = !lightTheme;
                return true;
            }
        }

        float tabStartX = cx + 50;
        float tabY = cy + 10;
        float tabW = 55;
        float tabH = 20;
        int idx = 0;
        for (Category t : categories) {
            float tabX = tabStartX + idx * (tabW + 3);
            if (button == 0 && isIn(mouseX, mouseY, tabX, tabY, tabW, tabH)) {
                selected = t;
                lastSelected = selected;
                listScroll = listScrollTarget = 0f;
                searchText = "";
                searchFocused = false;
                return true;
            }
            idx++;
        }

        float searchXf = cx + 14;
        float searchYf = cy + TOP_H + 8;
        float searchWf = GUI_W - 28;
        int searchHf = 18;
        if (button == 0) {
            if (isIn(mouseX, mouseY, searchXf, searchYf, searchWf, searchHf)) {
                searchFocused = true;
                return true;
            } else {
                searchFocused = false;
            }
        }

        float listX = cx + 14;
        float listYView = cy + TOP_H + 30;
        float listW = GUI_W - 28;
        float listH = GUI_H - TOP_H - 40;

        if (!isIn(mouseX, mouseY, listX, listYView, listW, listH)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        List<Module> funcs = getModules(selected);
        if (searchText != null && !searchText.isEmpty()) {
            String q = searchText.toLowerCase(Locale.ROOT);
            List<Module> filtered = new ArrayList<>();
            for (Module f : funcs) {
                if (f.getName().toLowerCase(Locale.ROOT).contains(q)) filtered.add(f);
            }
            funcs = filtered;
        }

        int gutter = 8;
        float itemW = (listW - gutter) / 2f;
        float listY = listYView - listScroll;

        for (int i = 0; i < funcs.size(); i += 2) {
            Module fL = funcs.get(i);
            Module fR = (i + 1 < funcs.size()) ? funcs.get(i + 1) : null;

            int fullL = computeSettingsHeight(fL, (int) itemW - 12);
            float progL = updateExpand(fL);
            int animL = (int) (fullL * progL);
            float totHL = ITEM_H + animL + (animL > 0 ? 8 : 0);

            int fullR = 0, animR = 0;
            float totHR = 0;
            if (fR != null) {
                fullR = computeSettingsHeight(fR, (int) itemW - 12);
                float progR = updateExpand(fR);
                animR = (int) (fullR * progR);
                totHR = ITEM_H + animR + (animR > 0 ? 8 : 0);
            }
            float rowH = Math.max(totHL, totHR);


            if (handleModuleClick(fL, mouseX, mouseY, button, listX, listY, itemW, totHL)) return true;


            if (fR != null) {
                if (handleModuleClick(fR, mouseX, mouseY, button, listX + itemW + gutter, listY, itemW, totHR)) return true;
            }

            listY += rowH + 6;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private RenderContext renderContext = new RenderContext(null);

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderContext.setContext(ctx);
        renderContext.setMouseX(mouseX);
        renderContext.setMouseY(mouseY);

        openAnim.update();
        alphaAnim.update();
        
        float open = openAnim.getValue();
        float a = alphaAnim.getValue();

        if (isClosing && openAnim.isFinished() && open == 0) {
            super.close();
            return;
        }


        
        MatrixStack ms = ctx.getMatrices();
        ms.push();

        int cx = width / 2;
        int cy = height / 2;
        

        ms.translate(cx, cy, 0);
        float scale = Math.max(0.0001f, open);
        ms.scale(scale, scale, 1);
        ms.translate(-cx, -cy, 0);

        float x = cx - GUI_W / 2f;
        float y = cy - GUI_H / 2f;
        

        closeHovered = isIn(mouseX, mouseY, x + 12 - 8, y + 12 - 8, 16, 16);
        themeToggleHovered = isIn(mouseX, mouseY, x + GUI_W - 12 - 8, y + 12 - 8, 16, 16);


        int bgColor = lightTheme ? ColorRGBA.of(255, 255, 255, (int)(120 * a)).getRGB() : ColorRGBA.of(0, 0, 0, (int)(120 * a)).getRGB();
        renderContext.drawBlur(x, y, GUI_W, GUI_H, 12, 35, ColorRGBA.of(255,255,255,200));
        renderContext.drawRect(x, y, GUI_W, GUI_H, 12, new ColorRGBA(bgColor));
        

        float closeX = x + 12;
        float closeY = y + 12;
        int closeColor = closeHovered ? ColorRGBA.of(220, 50, 50, (int)(255 * a)).getRGB() : ColorRGBA.of(200, 60, 60, (int)(200 * a)).getRGB();

        renderContext.drawRect(closeX - 4, closeY - 4, 8, 8, 4, new ColorRGBA(closeColor));
        

        float themeX = x + GUI_W - 12;
        float themeY = y + 12;
        int themeColor = themeToggleHovered ? ColorRGBA.of(255, 200, 80, (int)(255 * a)).getRGB() : ColorRGBA.of(230, 180, 70, (int)(200 * a)).getRGB();
        if (lightTheme) {

            renderContext.drawRect(themeX - 3, themeY - 3, 6, 6, 3, new ColorRGBA(themeColor));
        } else {

            renderContext.drawRect(themeX - 3.5f, themeY - 3.5f, 7, 7, 3.5f, new ColorRGBA(themeColor));
            int darkColor = lightTheme ? ColorRGBA.of(255, 255, 255, (int)(35 * a)).getRGB() : ColorRGBA.of(0, 0, 0, (int)(35 * a)).getRGB();
            renderContext.drawRect(themeX - 3.5f + 3, themeY - 3.5f - 2, 6, 6, 3, new ColorRGBA(darkColor));
        }
        

        float tabStartX = x + 50;
        float tabY = y + 10;
        float tabW = 55;
        float tabH = 20;
        int idx = 0;
        for (Category t : categories) {
            float tabX = tabStartX + idx * (tabW + 3);
            boolean sel = (t == selected);
            
            int tabBg = sel 
                ? (lightTheme ? ColorRGBA.of(255, 255, 255, (int)(80 * a)).getRGB() : ColorRGBA.of(255, 255, 255, (int)(25 * a)).getRGB())
                : (lightTheme ? ColorRGBA.of(200, 200, 200, (int)(40 * a)).getRGB() : ColorRGBA.of(255, 255, 255, (int)(10 * a)).getRGB());
            
            if (sel) {
                renderContext.drawBlur(tabX, tabY, tabW, tabH, 5, 8, ColorRGBA.of(255,255,255,100));
            }
            renderContext.drawRect(tabX, tabY, tabW, tabH, 5, new ColorRGBA(tabBg));
            
            int textColor = sel
                ? (lightTheme ? ColorRGBA.of(30, 30, 30, (int)(230 * a)).getRGB() : ColorRGBA.of(255, 255, 255, (int)(230 * a)).getRGB())
                : (lightTheme ? ColorRGBA.of(100, 100, 100, (int)(180 * a)).getRGB() : ColorRGBA.of(180, 180, 180, (int)(150 * a)).getRGB());
            
            float textW = Fonts.sf_pro.getWidth(t.getName(), 6.5f);
            renderContext.drawText(t.getName(), Fonts.sf_pro, tabX + (tabW - textW) / 2, tabY + 6.5f, 6.5f, new ColorRGBA(textColor));
            idx++;
        }


        float listX = x + 14;
        float searchY = y + TOP_H + 8;
        float listW = GUI_W - 28;
        int searchH = 18;
        
        int searchBg = lightTheme ? ColorRGBA.of(255, 255, 255, (int)(50 * a)).getRGB() : ColorRGBA.of(255, 255, 255, (int)(15 * a)).getRGB();
        renderContext.drawBlur(listX, searchY, listW, searchH, 5, 12, ColorRGBA.of(255,255,255,120));
        renderContext.drawRect(listX, searchY, listW, searchH, 5, new ColorRGBA(searchBg));
        
        String toDraw = searchText.isEmpty() && !searchFocused ? "Search..." : searchText;
        int searchTextColor = (searchText.isEmpty() && !searchFocused) 
            ? (lightTheme ? ColorRGBA.of(120,120,120,(int)(180*a)).getRGB() : ColorRGBA.of(170,170,170,(int)(180*a)).getRGB())
            : (lightTheme ? ColorRGBA.of(30,30,30,(int)(230*a)).getRGB() : ColorRGBA.of(230,230,230,(int)(230*a)).getRGB());
        renderContext.drawText(toDraw, Fonts.sf_pro, listX + 8, searchY + 6, 7f, new ColorRGBA(searchTextColor));


        float listYView = y + TOP_H + 30;
        float listH = GUI_H - TOP_H - 40;
        
        ctx.enableScissor((int)listX, (int)listYView, (int)(listX + listW), (int)(listYView + listH));
        float yCursor = listYView - listScroll;
        
        List<Module> funcs = getModules(selected);
        if (searchText != null && !searchText.isEmpty()) {
            String q = searchText.toLowerCase(Locale.ROOT);
            List<Module> filtered = new ArrayList<>();
            for (Module f : funcs) { if (f.getName().toLowerCase(Locale.ROOT).contains(q)) filtered.add(f); }
            funcs = filtered;
        }
        

        int gutter = 8;
        float itemW = (listW - gutter) / 2f;
        

        float totalH = 0f;
        for (int i = 0; i < funcs.size(); i += 2) {
            Module fL = funcs.get(i);
            Module fR = (i + 1 < funcs.size()) ? funcs.get(i + 1) : null;
            int animL = (int)(computeSettingsHeight(fL, (int)itemW - 12) * updateExpand(fL));
            int animR = fR != null ? (int)(computeSettingsHeight(fR, (int)itemW - 12) * updateExpand(fR)) : 0;
            totalH += Math.max(ITEM_H + animL + (animL > 0 ? 8 : 0), ITEM_H + animR + (animR > 0 ? 8 : 0)) + 6;
        }
        int maxScroll = (int)Math.max(0, totalH - listH);
        listScrollTarget = MathHelperClamp(listScrollTarget, 0, maxScroll);
        listScroll += (listScrollTarget - listScroll) * 0.2f;

        for (int i = 0; i < funcs.size(); i += 2) {
            Module fL = funcs.get(i);
            Module fR = (i + 1 < funcs.size()) ? funcs.get(i + 1) : null;
            
            int fullL = computeSettingsHeight(fL, (int)itemW - 12);
            float progL = updateExpand(fL);
            int animL = (int)(fullL * progL);
            float totHL = ITEM_H + animL + (animL > 0 ? 8 : 0);
            
            int fullR = 0, animR = 0;
            float totHR = 0;
            if (fR != null) {
                fullR = computeSettingsHeight(fR, (int)itemW - 12);
                float progR = updateExpand(fR);
                animR = (int)(fullR * progR);
                totHR = ITEM_H + animR + (animR > 0 ? 8 : 0);
            }
            float rowH = Math.max(totHL, totHR);
            

            renderModuleCard(ctx, fL, listX, yCursor, itemW, totHL, a);
            

            if (fR != null) {
                renderModuleCard(ctx, fR, listX + itemW + gutter, yCursor, itemW, totHR, a);
            }
            
            yCursor += rowH + 6;
        }
        
        ctx.disableScissor();
        ms.pop();
    }

    private boolean handleModuleClick(Module f, double mouseX, double mouseY, int button, float x, float y, float w, float h) {
        if (mouseY < y || mouseY > y + h) return false;
        

        if (isIn(mouseX, mouseY, x, y, w, ITEM_H)) {
            if (button == 0) { f.cToggle(); return true; }
            else if (button == 1 && !f.getSettings().isEmpty()) { 
                float current = expandProgress.getOrDefault(f, 0f);
                float target = current > 0.5f ? 0f : 1f;

                toggleExpanded(f);
                return true; 
            }
            else if (button == 2) { binding = true; bindingTarget = f; return true; }
        }
        

        if (isExpanded(f)) {
            float sY = y + ITEM_H;
            for (Setting setting : f.getSettings()) {
                if (!setting.isVisible()) continue;
                int sh = getSettingHeight(setting, (int)w - 16);
                if (handleSettingClick(setting, mouseX, mouseY, button, (int)x + 8, (int)sY, (int)w - 16)) return true;
                sY += sh + 2;
            }
        }
        
        return false;
    }

    private Set<Module> expandedModules = new HashSet<>();
    private void toggleExpanded(Module m) {
        if (expandedModules.contains(m)) expandedModules.remove(m);
        else expandedModules.add(m);
    }
    private boolean isExpanded(Module m) {
        return expandedModules.contains(m);
    }

    private void renderModuleCard(DrawContext ctx, Module f, float x, float y, float w, float h, float a) {

        int cardBg = lightTheme ? ColorRGBA.of(255, 255, 255, (int)(60 * a)).getRGB() : ColorRGBA.of(255, 255, 255, (int)(18 * a)).getRGB();
        renderContext.drawBlur(x, y, w, h, 6, 14, ColorRGBA.of(255,255,255,130));
        renderContext.drawRect(x, y, w, h, 6, new ColorRGBA(cardBg));
        

        int nameColor = lightTheme ? ColorRGBA.of(30, 30, 30, (int)(230 * a)).getRGB() : ColorRGBA.of(230, 230, 230, (int)(230 * a)).getRGB();
        renderContext.drawText(f.getName(), Fonts.sf_pro, x + 8, y + 8, 7.5f, new ColorRGBA(nameColor));
        

        float progress = toggleProgress.getOrDefault(f, f.isEnabled() ? 1f : 0f);
        progress += ((f.isEnabled() ? 1f : 0f) - progress) * 0.15f;
        toggleProgress.put(f, progress);
        
        int WIDTH = 22;
        int SWITCH_HEIGHT = 12;
        int KNOB_RADIUS = 8;
        
        float toggleX = x + w - WIDTH - 4;
        float toggleY = y + (ITEM_H - SWITCH_HEIGHT) / 2f;

        ColorRGBA offColor = new ColorRGBA(50, 50, 50, 200);
        ColorRGBA onColor = ColorRGBA.of(100, 200, 100, 255); // Greenish for on

        int r = (int)(offColor.getR() + (onColor.getR() - offColor.getR()) * progress);
        int g = (int)(offColor.getG() + (onColor.getG() - offColor.getG()) * progress);
        int b = (int)(offColor.getB() + (onColor.getB() - offColor.getB()) * progress);
        int alpha = (int)(offColor.getA() + (onColor.getA() - offColor.getA()) * progress);
        
        renderContext.drawRect(toggleX, toggleY, WIDTH, SWITCH_HEIGHT, 5, new ColorRGBA(r, g, b, alpha));
        

        float knobX = toggleX + 2 + (WIDTH - KNOB_RADIUS - 4) * progress;
        float knobY = toggleY + (SWITCH_HEIGHT - KNOB_RADIUS) / 2f;
        renderContext.drawRect(knobX, knobY, KNOB_RADIUS, KNOB_RADIUS, KNOB_RADIUS/2f, ColorRGBA.of(255, 255, 255, 255));
        

        if (!f.getSettings().isEmpty()) {
            float currentProgress = expandProgress.getOrDefault(f, isExpanded(f) ? 1f : 0f);
            float arrowX = x + w - WIDTH - 12;
            float arrowY = y + ITEM_H / 2f;
            
            MatrixStack ms = ctx.getMatrices();
            ms.push();
            ms.translate(arrowX, arrowY, 0);
            float angleRad = (float) Math.toRadians(90.0f * currentProgress);
            ms.multiply(new Quaternionf().fromAxisAngleRad(new Vector3f(0,0,1), angleRad));
            int arrowColor = lightTheme ? ColorRGBA.of(100, 100, 100, (int)(200 * a)).getRGB() : ColorRGBA.of(180, 180, 180, (int)(200 * a)).getRGB();
            renderContext.drawText(">", Fonts.sf_pro, -2, -3, 6f, new ColorRGBA(arrowColor));
            ms.pop();
        }
        

        if (h > ITEM_H) {
            float sY = y + ITEM_H;
            ctx.enableScissor((int)x, (int)(y + ITEM_H), (int)(x + w), (int)(y + h));
            for (Setting setting : f.getSettings()) {
                if (!setting.isVisible()) continue;
                int sh = renderSetting(ctx, setting, (int)x + 8, (int)sY, (int)w - 16);
                sY += sh + 2;
            }
            ctx.disableScissor();
        }
    }

    private int renderSetting(DrawContext ctx, Setting setting, int x, int y, int w) {
        if (setting instanceof BooleanSetting bs) {
            renderContext.drawText(bs.getName(), Fonts.sf_pro, x, y + 2, 6f, ColorRGBA.of(255, 255, 255, 255));

            float checkSize = 10;
            float checkX = x + w - checkSize;
            float checkY = y + 1;
            renderContext.drawRect(checkX, checkY, checkSize, checkSize, 2, bs.isEnabled() ? ColorRGBA.of(100, 200, 100, 255) : ColorRGBA.of(60, 60, 60, 255));
            return 14;
        } else if (setting instanceof NumberSetting ns) {
            renderContext.drawText(ns.getName(), Fonts.sf_pro, x, y, 6f, ColorRGBA.of(255, 255, 255, 255));
            renderContext.drawText(String.format("%.2f", ns.getCurrent()), Fonts.sf_pro, x + w - Fonts.sf_pro.getWidth(String.format("%.2f", ns.getCurrent()), 6f), y, 6f, ColorRGBA.of(180, 180, 180, 255));
            
            float sliderH = 4;
            float sliderY = y + 10;
            float sliderW = w;
            renderContext.drawRect(x, sliderY, sliderW, sliderH, 2, ColorRGBA.of(60, 60, 60, 255));
            
            float range = ns.getMax() - ns.getMin();
            float fill = (ns.getCurrent() - ns.getMin()) / range;
            renderContext.drawRect(x, sliderY, sliderW * fill, sliderH, 2, ColorRGBA.of(100, 150, 255, 255));
            

            if (activeSetting == ns && draggingSlider) {
                 float mouseX = (float)renderContext.getMouseX();
                 float val = (mouseX - x) / sliderW;
                 val = Math.max(0, Math.min(1, val));
                 float newVal = ns.getMin() + val * range;

                 float inc = ns.getIncrement();
                 if (inc > 0) {
                     newVal = Math.round(newVal / inc) * inc;
                 }
                 ns.setCurrent(newVal);
            }
            
            return 18;
        } else if (setting instanceof ModeSetting ms) {
            renderContext.drawText(ms.getName(), Fonts.sf_pro, x, y + 2, 6f, ColorRGBA.of(255, 255, 255, 255));
            renderContext.drawText(ms.get(), Fonts.sf_pro, x + w - Fonts.sf_pro.getWidth(ms.get(), 6f), y + 2, 6f, ColorRGBA.of(150, 150, 150, 255));
            return 14;
        } else if (setting instanceof KeySetting ks) {
            renderContext.drawText(ks.getName(), Fonts.sf_pro, x, y + 2, 6f, ColorRGBA.of(255, 255, 255, 255));
            String keyName = activeSetting == ks ? "..." : (ks.getKeyCode() == -1 ? "None" : Keyboard.getKeyName(ks.getKeyCode()));
            renderContext.drawText(keyName, Fonts.sf_pro, x + w - Fonts.sf_pro.getWidth(keyName, 6f), y + 2, 6f, ColorRGBA.of(150, 150, 150, 255));
            return 14;
        }
        return 0;
    }

    private int getSettingHeight(Setting setting, int w) {
        if (setting instanceof BooleanSetting) return 14;
        if (setting instanceof NumberSetting) return 18;
        if (setting instanceof ModeSetting) return 14;
        if (setting instanceof KeySetting) return 14;
        return 0;
    }

    private boolean handleSettingClick(Setting setting, double mouseX, double mouseY, int button, int x, int y, int w) {
        if (setting instanceof BooleanSetting bs) {
            if (isIn(mouseX, mouseY, x, y, w, 14)) {
                bs.toggle();
                return true;
            }
        } else if (setting instanceof NumberSetting ns) {
            if (isIn(mouseX, mouseY, x, y, w, 18)) {
                activeSetting = ns;
                draggingSlider = true;
                return true;
            }
        } else if (setting instanceof ModeSetting ms) {
             if (isIn(mouseX, mouseY, x, y, w, 14)) {
                 if (button == 0) {

                     List<ModeSetting.Value> values = ms.getValues();
                     int index = values.indexOf(ms.getValue());
                     if (index != -1) {
                         int next = (index + 1) % values.size();
                         ms.setValue(values.get(next));
                     }
                 } else if (button == 1) {

                     List<ModeSetting.Value> values = ms.getValues();
                     int index = values.indexOf(ms.getValue());
                     if (index != -1) {
                         int prev = (index - 1 + values.size()) % values.size();
                         ms.setValue(values.get(prev));
                     }
                 }
                 return true;
             }
        } else if (setting instanceof KeySetting ks) {
            if (isIn(mouseX, mouseY, x, y, w, 14)) {
                activeSetting = ks;
                return true;
            }
        }
        return false;
    }

    private float updateExpand(Module f) {
        float target = isExpanded(f) ? 1f : 0f;
        float prog = expandProgress.getOrDefault(f, target);

        prog += (target - prog) * 0.15f;
        if (Math.abs(target - prog) < 0.001f) prog = target;
        expandProgress.put(f, prog);
        return prog;
    }

    private int computeSettingsHeight(Module f, int w) {
        int h = 0;
        for (Setting s : f.getSettings()) {
            if (!s.isVisible()) continue;
            h += getSettingHeight(s, w) + 2;
        }
        return h;
    }

    private List<Module> getModules(Category c) {
        return Async.getInstance().moduleManager.getModules().stream().filter(m -> m.getCategory() == c).toList();
    }

    private static boolean isIn(double mx, double my, double x, double y, double w, double h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static float MathHelperClamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}