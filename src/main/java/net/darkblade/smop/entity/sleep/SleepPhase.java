package net.darkblade.smop.entity.sleep;

import org.jetbrains.annotations.Nullable;

public enum SleepPhase {

    NONE(null),

    SITTING_DOWN("sitting"),

    SITTING("sit"),

    PREPARING_SLEEP("preparing_sleep"),

    SLEEPING("sleep"),

    AWAKENING("awakening"),

    STANDING_UP("standing_up");

    private static final SleepPhase[] BY_ID = values();

    @Nullable
    private final String clipName;

    SleepPhase(@Nullable String clipName) {
        this.clipName = clipName;
    }

    @Nullable
    public String clipName() {
        return this.clipName;
    }

    public boolean isSkippable() {
        return this != SLEEPING;
    }

    public static SleepPhase byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : NONE;
    }
}
