package fr.tropimon.battleui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.Locale;
import java.util.function.Supplier;

final class BattleUiSettingsScreen extends Screen {
    private final Screen parent;
    private boolean tooltips;
    private int previewY;
    private int optionHeight = 18;

    BattleUiSettingsScreen(Screen parent) { super(label("title")); this.parent = parent; }
    private static Text label(String key, Object... values) {
        return Text.translatable("text.tropimon_ui_battle.settings." + key, values);
    }
    @Override protected void init() {
        int total = Math.min(380, width - 20), left = (width - total) / 2, column = (total - 6) / 2;
        addDrawableChild(ButtonWidget.builder(label("chat"), b -> { tooltips = false; clearAndInit(); })
                .dimensions(left, 28, column, 20).build());
        addDrawableChild(ButtonWidget.builder(label("tooltips"), b -> { tooltips = true; clearAndInit(); })
                .dimensions(left + column + 6, 28, column, 20).build());
        int rowHeight = Math.min(24, Math.max(13, (height - 86) / 6));
        optionHeight = Math.min(18, rowHeight - 2);
        if (tooltips) {
            option(left, 53, total, () -> label("tooltip_size", BattleUiPreferences.tooltipPercent()), BattleUiPreferences::cycleTooltipSize);
            int index = 0;
            for (var detail : BattleUiPreferences.Detail.values()) {
                final var current = detail;
                option(left + (index % 2) * (column + 6), 53 + (1 + index / 2) * rowHeight, column,
                        () -> label("toggle", label("detail." + current.name().toLowerCase(Locale.ROOT)),
                                label(BattleUiPreferences.show(current) ? "on" : "off")),
                        () -> BattleUiPreferences.toggle(current));
                index++;
            }
        } else {
            option(left, 53, total, () -> label("chat_size", BattleUiPreferences.chatPercent()), BattleUiPreferences::cycleChatSize);
            option(left, 53 + rowHeight, total, () -> label("palette", label("palette." +
                    BattleUiPreferences.palette().name().toLowerCase(Locale.ROOT))), BattleUiPreferences::cyclePalette);
            option(left, 53 + rowHeight * 2, total, () -> label("rows", label(BattleUiPreferences.rowBackgrounds() ? "on" : "off")), BattleUiPreferences::toggleRows);
            previewY = 56 + rowHeight * 3;
        }
        addDrawableChild(ButtonWidget.builder(label("reset"), b -> { BattleUiPreferences.reset(); clearAndInit(); })
                .dimensions(left, height - 26, column, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
                .dimensions(left + column + 6, height - 26, column, 20).build());
    }
    private void option(int x, int y, int width, Supplier<Text> text, Runnable action) {
        addDrawableChild(ButtonWidget.builder(text.get(), b -> { action.run(); b.setMessage(text.get()); })
                .dimensions(x, y, width, optionHeight).build());
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xEF111923);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
        if (!tooltips && previewY + 24 < height - 30) {
            int left = (width - Math.min(380, width - 20)) / 2;
            context.fill(left, previewY, width - left, height - 32, BattleUiTheme.historyBackground());
            context.enableScissor(left + 4, previewY, width - left - 4, height - 32);
            float scale = BattleUiPreferences.chatScale();
            context.getMatrices().push();
            try {
                context.getMatrices().translate(left + 6, previewY + 5, 0);
                context.getMatrices().scale(scale, scale, 1);
                context.drawText(textRenderer, BattleUiTheme.historyText(label("preview").copy()
                        .styled(s -> s.withColor(BattleLogTextFormatter.BODY_COLOR))), 0, 0, 0xFFFFFFFF, false);
                context.drawText(textRenderer, BattleUiTheme.historyText(label("preview_sides").copy()
                        .styled(s -> s.withColor(0x17616B))), 0, 11, 0xFFFFFFFF, false);
            } finally {
                context.getMatrices().pop();
                context.disableScissor();
            }
        }
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(BattleUiState.active() ? parent : null); }
}
