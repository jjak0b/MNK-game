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
        public static final boolean DEBUG_START_FIXED_MOVE = DEBUG_ENABLED && false;
        public static final boolean DEBUG_SHOW_INFO = DEBUG_ENABLED && false;
        public static final boolean DEBUG_SHOW_DECISION_INFO = DEBUG_ENABLED && false;
        public static final boolean DEBUG_SHOW_BOARD = DEBUG_ENABLED && false;
        public static final boolean DEBUG_SHOW_STATS = DEBUG_ENABLED && false;
        public static final boolean DEBUG_SHOW_STREAKS = DEBUG_ENABLED && false;
        public static final boolean DEBUG_SHOW_USEFUL = DEBUG_ENABLED && false;
        public static final boolean DEBUG_SHOW_WEIGHTS = DEBUG_ENABLED && false;
        public static final boolean DEBUG_SHOW_CANDIDATES = DEBUG_ENABLED && false;
        public static final boolean DEBUG_SHOW_MOVES_RESULT_ON_ROOT = DEBUG_ENABLED && false;
    }
}