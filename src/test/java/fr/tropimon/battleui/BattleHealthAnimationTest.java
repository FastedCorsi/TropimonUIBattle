package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.animations.HealthChangeAnimation;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleHealthAnimationTest {
    @BeforeAll static void bootstrap() { DamageCacheParityTest.bootstrap(); }

    @Test void nativeDamageStaysSmoothDespiteStaleAndPrematureFinalSnapshots() {
        for (boolean flat : List.of(true, false)) {
            var pokemon = pokemon(UUID.randomUUID(), flat, flat ? 300F : 1F, 300F);
            var slot = slot(pokemon);
            var animation = new HealthChangeAnimation(flat ? 120F : 0.4F, 1F);
            float previous = 100;
            for (int frame = 0; frame < 65; frame++) {
                animation.invoke(slot, 20F / 60F);
                // A party scan can be stale or already contain the packet's final value.
                var snapshot = member(pokemon.getUuid(), frame % 2 == 0 ? 100F : 40F);
                float displayed = BattleHealthFormatting.displayedMember(snapshot, pokemon).hpPercent();
                String counter = BattlePercentageFormatting.animated(displayed, 40F);
                assertTrue(counter.endsWith("%"));
                assertTrue(displayed <= previous + 0.001F, "damage must never rebound");
                assertTrue(previous - displayed < 1.01F, "read animation every frame, not every team scan");
                previous = displayed;
            }
            assertEquals(40F, previous, 0.001F);
        }
    }

    @Test void healingMultiHitsAndKnockoutsFollowNativeAnimationWithoutAnExtraDelay() {
        var pokemon = pokemon(UUID.randomUUID(), false, 1F, 100F);
        var slot = slot(pokemon);
        var snapshot = member(pokemon.getUuid(), 100F);
        for (float target : new float[]{0.8F, 0.65F, 0.72F, 0F}) {
            float initial = pokemon.getHpValue() * 100;
            float previous = initial;
            var animation = new HealthChangeAnimation(target, 1F);
            for (int frame = 0; frame < 64; frame++) {
                animation.invoke(slot, 20F / 60F);
                var displayed = BattleHealthFormatting.displayedMember(snapshot, pokemon);
                if (target * 100 > initial) assertTrue(displayed.hpPercent() >= previous - 0.001F);
                else assertTrue(displayed.hpPercent() <= previous + 0.001F);
                assertEquals(displayed.hpPercent() <= 0, displayed.fainted());
                previous = displayed.hpPercent();
            }
            assertEquals(target * 100, previous, 0.001F);
        }
        assertEquals(100F, snapshot.hpPercent(), "display never writes back to combat snapshots");
    }

    @Test void lowHealthMaxHealthChangesAndBenchIdentityRemainExact() {
        var id = UUID.randomUUID();
        var snapshot = member(id, 100F);
        var pokemon = pokemon(id, true, 1F, 2500F);
        var low = BattleHealthFormatting.displayedMember(snapshot, pokemon);
        assertEquals(0.04F, low.hpPercent(), 0.00001F);
        assertFalse(low.fainted());
        assertEquals("0.04%", BattlePercentageFormatting.format(low.hpPercent()));
        pokemon.setMaxHp(5000);
        assertEquals(0.02F, BattleHealthFormatting.displayedMember(snapshot, pokemon).hpPercent(), 0.00001F);
        assertSame(snapshot, BattleHealthFormatting.displayedMember(snapshot, null));
        assertSame(snapshot, BattleHealthFormatting.displayedMember(snapshot, pokemon(UUID.randomUUID(), false, 0.1F, 100F)),
                "another doubles slot or an Illusion identity must not change this card");
    }

    private static ClientBattlePokemon pokemon(UUID id, boolean flat, float hp, float max) {
        return new ClientBattlePokemon(id, Text.literal("Fixture"), new PokemonProperties(), Set.of(),
                hp, max, flat, null, new HashMap<>());
    }

    private static ActiveClientBattlePokemon slot(ClientBattlePokemon pokemon) {
        var actor = new ClientBattleActor("p1", Text.literal("Trainer"), UUID.randomUUID(), ActorType.PLAYER);
        pokemon.setActor(actor);
        return new ActiveClientBattlePokemon(actor, pokemon);
    }

    private static TeamMemberView member(UUID id, float hp) {
        return new TeamMemberView(id, "Fixture", 100, hp, "", hp <= 0, true, ItemStack.EMPTY,
                null, List.of(), null, List.of(), List.of(), true);
    }
}
