package mnkgame;

public final class Debug {
    public static final boolean DEBUG_ENABLED = true;
    public static final boolean DEBUG_USE_COLORS = true;

    public static void println(String msg) {
        if( DEBUG_ENABLED )
            System.out.println(msg);
    }

    public static void println(Object o) {
        if( DEBUG_ENABLED )
            System.out.println(o);
    }
}