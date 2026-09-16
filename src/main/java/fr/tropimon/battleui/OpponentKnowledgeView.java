package fr.tropimon.battleui;

import net.minecraft.item.ItemStack;

import java.util.List;

record OpponentKnowledgeView(
        List<AbilityView> possibleAbilities,
        SpeedRangeView speedRange,
        List<ItemHistoryView> itemHistory,
        List<FormHistoryView> formHistory
) {
    OpponentKnowledgeView {
        possibleAbilities = possibleAbilities == null ? List.of() : List.copyOf(possibleAbilities);
        itemHistory = itemHistory == null ? List.of() : List.copyOf(itemHistory);
        formHistory = formHistory == null ? List.of() : List.copyOf(formHistory);
    }

    static OpponentKnowledgeView empty() {
        return new OpponentKnowledgeView(List.of(), SpeedRangeView.unknown(), List.of(), List.of());
    }
}

record SpeedRangeView(int minimum, int maximum, int effectiveMinimum, int effectiveMaximum) {
    SpeedRangeView {
        minimum = Math.max(0, minimum);
        maximum = Math.max(minimum, maximum);
        effectiveMinimum = Math.max(0, effectiveMinimum);
        effectiveMaximum = Math.max(effectiveMinimum, effectiveMaximum);
    }

    static SpeedRangeView unknown() {
        return new SpeedRangeView(0, 0, 0, 0);
    }

    boolean known() {
        return maximum > 0;
    }

    boolean modified() {
        return known() && (minimum != effectiveMinimum || maximum != effectiveMaximum);
    }
}

record ItemHistoryView(ItemStack item, ItemEventKind kind, int turn) {
    ItemHistoryView {
        item = item == null ? ItemStack.EMPTY : item.copy();
        kind = kind == null ? ItemEventKind.REVEALED : kind;
        turn = Math.max(0, turn);
    }

    @Override
    public ItemStack item() {
        return item.copy();
    }
}

enum ItemEventKind {
    REVEALED("text.tropimon_ui_battle.item_history.revealed"),
    CONSUMED("text.tropimon_ui_battle.item_history.consumed"),
    KNOCKED_OFF("text.tropimon_ui_battle.item_history.knocked_off"),
    DESTROYED("text.tropimon_ui_battle.item_history.destroyed"),
    STOLEN("text.tropimon_ui_battle.item_history.stolen"),
    GIVEN("text.tropimon_ui_battle.item_history.given"),
    SWAPPED("text.tropimon_ui_battle.item_history.swapped");

    private final String translationKey;

    ItemEventKind(String translationKey) {
        this.translationKey = translationKey;
    }

    String translationKey() {
        return translationKey;
    }
}

record FormHistoryView(String from, String to, FormEventKind kind, int turn) {
    FormHistoryView {
        from = from == null ? "" : from;
        to = to == null ? "" : to;
        kind = kind == null ? FormEventKind.FORM_CHANGE : kind;
        turn = Math.max(0, turn);
    }
}

enum FormEventKind {
    FORM_CHANGE("text.tropimon_ui_battle.form_history.change"),
    TRANSFORM("text.tropimon_ui_battle.form_history.transform"),
    TRANSFORM_REVERTED("text.tropimon_ui_battle.form_history.transform_reverted"),
    ILLUSION_REVEALED("text.tropimon_ui_battle.form_history.illusion"),
    TERASTALLIZED("text.tropimon_ui_battle.form_history.tera");

    private final String translationKey;

    FormEventKind(String translationKey) {
        this.translationKey = translationKey;
    }

    String translationKey() {
        return translationKey;
    }
}
