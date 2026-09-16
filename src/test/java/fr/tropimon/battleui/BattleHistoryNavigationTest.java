package fr.tropimon.battleui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleHistoryNavigationTest {
    @Test
    void indexesTheFirstWrappedLineOfEachActualTurn() {
        var turns = BattleHistoryNavigation.indexTurns(List.of(0, 0, 1, 1, 1, 1, 3, 3, 4));
        assertEquals(List.of(new BattleHistoryNavigation.Turn(0, 0),
                new BattleHistoryNavigation.Turn(1, 2), new BattleHistoryNavigation.Turn(3, 6),
                new BattleHistoryNavigation.Turn(4, 8)), turns);
    }

    @Test
    void jumpsToTurnThenKeepsReadingPositionAsMessagesArrive() {
        var navigation = withTurns(8, 5, 4);
        assertEquals(36, navigation.startLine());
        assertTrue(navigation.jumpToTurn(2));
        assertEquals(10, navigation.startLine());
        assertEquals(14, navigation.endLine());
        navigation.update(turns(9, 5), 45, 4);
        assertEquals(10, navigation.startLine());
        assertFalse(navigation.followingLatest());
    }

    @Test
    void keepsTurnAnchorWhenGuiScaleChangesLineWrapping() {
        var navigation = withTurns(8, 5, 4);
        navigation.jumpToTurn(2);
        navigation.scroll(2);
        navigation.update(turns(8, 9), 72, 3);
        assertEquals(20, navigation.startLine());
        assertEquals(2, navigation.viewedTurn());
    }

    @Test
    void selectingNewestTurnStillPausesEvenIfAllTextFits() {
        var navigation = withTurns(2, 2, 10);
        assertTrue(navigation.jumpToTurn(1));
        assertFalse(navigation.followingLatest());
        navigation.update(turns(5, 4), 20, 10);
        assertEquals(0, navigation.startLine());
        navigation.followLatest();
        assertEquals(10, navigation.startLine());
        navigation.update(turns(6, 4), 24, 10);
        assertEquals(14, navigation.startLine());
        assertTrue(navigation.followingLatest());
    }

    @Test
    void wheelReachingBottomResumesAutomaticScrolling() {
        var navigation = withTurns(10, 5, 4);
        navigation.scroll(-3);
        assertFalse(navigation.followingLatest());
        navigation.scroll(1000);
        assertEquals(46, navigation.startLine());
        assertTrue(navigation.followingLatest());
        navigation.scroll(-1000);
        assertEquals(0, navigation.startLine());
    }

    @Test
    void missingTurnsDoNotMoveTheViewportOrInventEntries() {
        var navigation = withTurns(3, 5, 4);
        assertFalse(navigation.jumpToTurn(99));
        assertTrue(navigation.followingLatest());
        assertEquals(11, navigation.startLine());
    }

    @Test
    void handlesOldEntriesPrunedFromLongBattle() {
        var navigation = withTurns(8, 5, 4);
        navigation.jumpToTurn(3);
        var pruned = List.of(new BattleHistoryNavigation.Turn(3, 0), new BattleHistoryNavigation.Turn(4, 5));
        navigation.update(pruned, 10, 4);
        assertEquals(0, navigation.startLine());
        assertEquals(3, navigation.viewedTurn());
        navigation.update(List.of(new BattleHistoryNavigation.Turn(4, 0)), 5, 4);
        assertEquals(0, navigation.startLine());
        assertFalse(navigation.followingLatest());
    }

    @Test
    void newBattleClearsNavigationAndRestoresLiveMode() {
        var navigation = withTurns(3, 5, 4);
        navigation.jumpToTurn(1);
        navigation.reset();
        assertTrue(navigation.turns().isEmpty());
        assertEquals(0, navigation.startLine());
        assertEquals(0, navigation.endLine());
        assertTrue(navigation.followingLatest());
        navigation.update(turns(2, 4), 8, 4);
        assertEquals(4, navigation.startLine());
    }

    @Test
    void dropdownScrollBoundsSupportHundredsOfTurns() {
        assertEquals(0, BattleHistoryNavigation.menuOffset(-3, 300, 8));
        assertEquals(120, BattleHistoryNavigation.menuOffset(120, 300, 8));
        assertEquals(292, BattleHistoryNavigation.menuOffset(999, 300, 8));
        assertEquals(0, BattleHistoryNavigation.menuOffset(10, 2, 8));
        assertEquals(0, BattleHistoryNavigation.menuOffset(10, 0, 8));
    }

    private static BattleHistoryNavigation withTurns(int count, int perTurn, int visible) {
        var navigation = new BattleHistoryNavigation();
        navigation.update(turns(count, perTurn), count * perTurn, visible);
        return navigation;
    }

    private static List<BattleHistoryNavigation.Turn> turns(int count, int perTurn) {
        var turns = new ArrayList<BattleHistoryNavigation.Turn>();
        for (int turn = 0; turn < count; turn++) turns.add(new BattleHistoryNavigation.Turn(turn, turn * perTurn));
        return List.copyOf(turns);
    }
}
