package fr.tropimon.battleui;

import net.minecraft.client.gui.DrawContext;

/** Generic translated scope for optional buttons appended to Cobblemon's battle screen. */
public final class BattleActionExtensionScope {
    private static boolean open;

    private BattleActionExtensionScope() { }

    public static void begin(DrawContext context) {
        open = false;
        if (!BattleActionPanel.active()) return;
        context.getMatrices().push();
        context.getMatrices().translate(BattleActionPanel.offsetX(), BattleActionPanel.offsetY(), 0);
        BattleUiTheme.beginNativeTint();
        open = true;
    }

    public static void end(DrawContext context) {
        if (!open) return;
        try { BattleUiTheme.endNativeTint(); }
        finally {
            context.getMatrices().pop();
            open = false;
        }
    }

    public static boolean open() { return open; }
}
