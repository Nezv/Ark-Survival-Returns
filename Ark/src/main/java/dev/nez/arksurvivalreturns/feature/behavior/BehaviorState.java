package dev.nez.arksurvivalreturns.feature.behavior;

public enum BehaviorState {
    ROAM, FORAGE, DRINK, SEEK_WATER, REST, ALERT, INVESTIGATE, THREATEN, HUNT, DEFEND, FLEE, RETURN_HOME, FEED, SLEEP, SEARCH, REGROUP;
    public boolean sleeping() { return this == SLEEP || this == REST; }
    public boolean combat() { return this == HUNT || this == DEFEND; }
    public boolean alarm() { return this == ALERT || this == THREATEN || combat() || this == FLEE; }
    public String key() { return "behavior.arksurvivalreturns." + name().toLowerCase(java.util.Locale.ROOT); }
}
