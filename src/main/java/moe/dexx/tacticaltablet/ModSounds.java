package moe.dexx.tacticaltablet;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** The mod's sound events. What each one plays is defined in assets/tactical_tablet/sounds.json. */
public final class ModSounds {
    public static final SoundEvent TABLET_OPEN = create("tablet_open");
    public static final SoundEvent BUTTON_CLICK = create("button_click");
    public static final SoundEvent COUNTDOWN_BEEP = create("countdown_beep");
    public static final SoundEvent CANNON_CHARGE = create("cannon_charge");
    public static final SoundEvent CANNON_FIRE = create("cannon_fire");
    public static final SoundEvent LASER_FLYBY = create("laser_flyby");
    public static final SoundEvent EXPLOSION = create("explosion");
    public static final SoundEvent DESTRUCTION = create("destruction");
    public static final SoundEvent STRIKE_COMPLETE = create("strike_complete");

    private static final SoundEvent[] ALL = {TABLET_OPEN, BUTTON_CLICK, COUNTDOWN_BEEP, CANNON_CHARGE, CANNON_FIRE,
            LASER_FLYBY, EXPLOSION, DESTRUCTION, STRIKE_COMPLETE};

    private ModSounds() {
    }

    private static SoundEvent create(String name) {
        return SoundEvent.createVariableRangeEvent(TacticalTablet.id(name));
    }

    public static void register() {
        for (SoundEvent event : ALL) {
            ResourceLocation id = event.getLocation();
            Registry.register(BuiltInRegistries.SOUND_EVENT, id, event);
        }
    }
}
