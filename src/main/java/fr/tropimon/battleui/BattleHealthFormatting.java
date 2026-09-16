package fr.tropimon.battleui;

import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;

final class BattleHealthFormatting {
    private BattleHealthFormatting() {
    }

    static float percent(boolean flatHealth, float health, float maxHealth) {
        float value;
        if (flatHealth) {
            value = maxHealth <= 0.0F ? 0.0F : 100.0F * health / maxHealth;
        } else {
            value = 100.0F * health;
        }
        return Math.max(0.0F, Math.min(100.0F, value));
    }

    /** Read Cobblemon's animation each frame, without changing battle/calculator snapshots. */
    static TeamMemberView displayedMember(TeamMemberView member, ClientBattlePokemon active) {
        if (member == null || active == null || !member.uuid().equals(active.getUuid())) return member;
        return member.withHealth(percent(active.isHpFlat(), active.getHpValue(), active.getMaxHp()));
    }
}
