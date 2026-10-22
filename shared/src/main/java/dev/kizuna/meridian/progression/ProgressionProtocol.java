package dev.kizuna.meridian.progression;

/** Inventory UI 统一路由中的必需模块身份。 */
public final class ProgressionProtocol {
    public static final String MODULE = "meridian_progression:reading";
    public static final String INTENT = "meridian_progression:intent";
    public static final String EVENT = "meridian_progression:event";
    public static final int VERSION = 1;
    public static final int PROGRESS_SCHEMA = 1;
    public static final String PROGRESS_KEY = "meridian_progression:progress";

    private ProgressionProtocol() {}
}
