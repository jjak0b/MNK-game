package player;

public final class Debug {
    public static final boolean DEBUG_ENABLED = false;
    public static final boolean DEBUG_USE_COLORS = true;

    public static void println(String msg) {
        if( DEBUG_ENABLED )
            System.out.println(msg);
    }

    public static void println(Object o) {
        if( DEBUG_ENABLED )
            System.out.println(o);
    }

    // singleton class used by players for debug
    public static final class Player {
        // DEBUG
        public static final boolean DEBUG_START_FIXED_MOVE = false;
        public static final boolean DEBUG_SHOW_INFO = true;
        public static final boolean DEBUG_SHOW_DECISION_INFO = true;
        public static final boolean DEBUG_SHOW_BOARD = true;
        public static final boolean DEBUG_SHOW_STATS = true;
        public static final boolean DEBUG_SHOW_STREAKS = true;
        public static final boolean DEBUG_SHOW_USEFUL = true;
        public static final boolean DEBUG_SHOW_WEIGHTS = true;
        public static final boolean DEBUG_SHOW_CANDIDATES = true;
        public static final boolean DEBUG_SHOW_MOVES_RESULT_ON_ROOT = false;
    }
}