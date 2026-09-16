package fr.tropimon.battleui;

import java.util.HashSet;
import java.util.Set;

final class BattleCalcDiagnostics {
    static final org.slf4j.Logger LOGGER = TropimonUIBattleClient.LOGGER;
    private static final Set<String> WARNED = new HashSet<>();
    static synchronized void warnOnce(String key, String message, Throwable error) {
        if (WARNED.add(key)) LOGGER.warn(message, error);
    }
    private BattleCalcDiagnostics() { }
}
