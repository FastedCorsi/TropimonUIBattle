package fr.tropimon.battleui;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.categories.DamageCategory;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.battles.MoveTarget;

import java.lang.reflect.Constructor;

/** Test fixture bridge for the extra MoveTemplate parameter introduced by Cobblemon 1.8. */
final class TestMoveTemplates {
    private static final Constructor<MoveTemplate> CONSTRUCTOR = resolveConstructor();

    private TestMoveTemplates() {
    }

    static MoveTemplate create(String name, int displayName, ElementalType type, DamageCategory category,
                               double power, MoveTarget target, double accuracy, int pp, int priority,
                               double criticalHitRate, Double[] hitTimes) {
        try {
            if (CONSTRUCTOR.getParameterCount() == 12) {
                return CONSTRUCTOR.newInstance(name, displayName, type, category, power, target, accuracy,
                        pp, priority, criticalHitRate, hitTimes, 0.0F);
            }
            return CONSTRUCTOR.newInstance(name, displayName, type, category, power, target, accuracy,
                    pp, priority, criticalHitRate, hitTimes);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Unable to create a Cobblemon move test fixture", error);
        }
    }

    private static Constructor<MoveTemplate> resolveConstructor() {
        try {
            return MoveTemplate.class.getConstructor(String.class, int.class, ElementalType.class,
                    DamageCategory.class, double.class, MoveTarget.class, double.class, int.class,
                    int.class, double.class, Double[].class, float.class);
        } catch (NoSuchMethodException modernMissing) {
            try {
                return MoveTemplate.class.getConstructor(String.class, int.class, ElementalType.class,
                        DamageCategory.class, double.class, MoveTarget.class, double.class, int.class,
                        int.class, double.class, Double[].class);
            } catch (NoSuchMethodException legacyMissing) {
                legacyMissing.addSuppressed(modernMissing);
                throw new ExceptionInInitializerError(legacyMissing);
            }
        }
    }
}
