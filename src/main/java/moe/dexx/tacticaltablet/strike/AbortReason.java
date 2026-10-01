package moe.dexx.tacticaltablet.strike;

/** Translation keys shown to the owner when a launch is cancelled before the shot. */
public final class AbortReason {
    public static final String CANCELLED = "tactical_tablet.abort.cancelled";
    public static final String PLAYER_LEFT = "tactical_tablet.abort.player_left";
    public static final String PLAYER_UNAVAILABLE = "tactical_tablet.abort.player_unavailable";
    public static final String TARGET_UNAVAILABLE = "tactical_tablet.abort.target_unavailable";
    public static final String GAMEMODE = "tactical_tablet.abort.gamemode";
    public static final String SERVER_STOPPING = "tactical_tablet.abort.server_stopping";
    public static final String RESOURCES = "tactical_tablet.abort.resources";
    public static final String STOPPED_BY_OPERATOR = "tactical_tablet.abort.stopped_by_operator";

    private AbortReason() {
    }
}
