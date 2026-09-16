package fr.tropimon.battleui;

import java.util.LinkedHashMap;
import java.util.List;

/** Navigation uses rendered line anchors, so wrapped messages never shift a turn jump. */
final class BattleHistoryNavigation {
    record Turn(int number, int firstLine) { }

    private List<Turn> turns = List.of();
    private int lineCount;
    private int visibleLines = 1;
    private int startLine;
    private boolean followingLatest = true;

    static List<Turn> indexTurns(List<Integer> lineTurns) {
        var firstLines = new LinkedHashMap<Integer, Turn>();
        for (int line = 0; line < lineTurns.size(); line++) {
            int turn = Math.max(0, lineTurns.get(line));
            firstLines.putIfAbsent(turn, new Turn(turn, line));
        }
        return List.copyOf(firstLines.values());
    }

    void update(List<Turn> nextTurns, int nextLineCount, int nextVisibleLines) {
        if (turns == nextTurns && lineCount == nextLineCount && visibleLines == nextVisibleLines) return;
        Turn oldTurn = turnAt(startLine);
        int offset = oldTurn == null ? 0 : startLine - oldTurn.firstLine();
        turns = nextTurns;
        lineCount = Math.max(0, nextLineCount);
        visibleLines = Math.max(1, nextVisibleLines);
        if (followingLatest) {
            startLine = maximumStart();
        } else {
            Turn sameTurn = oldTurn == null ? null : findTurn(oldTurn.number());
            startLine = sameTurn == null ? 0 : sameTurn.firstLine() + offset;
            startLine = clamp(startLine, 0, maximumStart());
        }
    }

    boolean jumpToTurn(int number) {
        Turn target = findTurn(number);
        if (target == null) return false;
        startLine = Math.min(target.firstLine(), maximumStart());
        // Even the latest turn stays paused until the user explicitly resumes live reading.
        followingLatest = false;
        return true;
    }

    void scroll(int rows) {
        if (rows == 0) return;
        startLine = clamp(startLine + rows, 0, maximumStart());
        followingLatest = startLine == maximumStart();
    }

    void followLatest() {
        followingLatest = true;
        startLine = maximumStart();
    }

    void reset() {
        turns = List.of();
        lineCount = 0;
        visibleLines = 1;
        startLine = 0;
        followingLatest = true;
    }

    int startLine() { return startLine; }
    int endLine() { return Math.min(lineCount, startLine + visibleLines); }
    int maximumStart() { return Math.max(0, lineCount - visibleLines); }
    boolean followingLatest() { return followingLatest; }
    List<Turn> turns() { return turns; }

    int viewedTurn() {
        Turn value = turnAt(startLine);
        return value == null ? 0 : value.number();
    }

    private Turn turnAt(int line) {
        Turn result = null;
        for (Turn value : turns) {
            if (value.firstLine() > line) break;
            result = value;
        }
        return result;
    }

    private Turn findTurn(int number) {
        for (Turn value : turns) if (value.number() == number) return value;
        return null;
    }

    static int menuOffset(int offset, int total, int visible) {
        return clamp(offset, 0, Math.max(0, total - Math.max(1, visible)));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
