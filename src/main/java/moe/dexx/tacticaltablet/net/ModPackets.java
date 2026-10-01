package moe.dexx.tacticaltablet.net;

import moe.dexx.tacticaltablet.TacticalTablet;
import net.minecraft.resources.ResourceLocation;

public final class ModPackets {
    // client -> server
    public static final ResourceLocation UPDATE_SETTINGS = TacticalTablet.id("update_settings");
    public static final ResourceLocation LAUNCH_REQUEST = TacticalTablet.id("launch_request");
    public static final ResourceLocation CANCEL_REQUEST = TacticalTablet.id("cancel_request");
    public static final ResourceLocation EMERGENCY_STOP = TacticalTablet.id("emergency_stop");
    // server -> client
    public static final ResourceLocation LAUNCH_REJECTED = TacticalTablet.id("launch_rejected");
    public static final ResourceLocation STRIKE_STATE = TacticalTablet.id("strike_state");
    public static final ResourceLocation STRIKE_ABORT = TacticalTablet.id("strike_abort");
    public static final ResourceLocation STRIKE_PROGRESS = TacticalTablet.id("strike_progress");

    private ModPackets() {
    }
}
