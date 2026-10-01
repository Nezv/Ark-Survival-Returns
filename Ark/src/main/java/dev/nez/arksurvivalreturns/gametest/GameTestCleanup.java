package dev.nez.arksurvivalreturns.gametest;

import net.minecraft.gametest.framework.*;

/** Restore shared state even when a delayed assertion fails or a test times out. */
final class GameTestCleanup {
    static void onFinish(GameTestHelper h, Runnable cleanup) {
        h.testInfo.addListener(new GameTestListener() {
            private boolean done;
            private void finish() { if (!done) { done = true; cleanup.run(); } }
            public void testStructureLoaded(GameTestInfo info) {}
            public void testPassed(GameTestInfo info, GameTestRunner runner) { finish(); }
            public void testFailed(GameTestInfo info, GameTestRunner runner) { finish(); }
            public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {}
        });
    }
    private GameTestCleanup() {}
}
