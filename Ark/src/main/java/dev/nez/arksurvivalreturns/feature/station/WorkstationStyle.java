package dev.nez.arksurvivalreturns.feature.station;

import java.util.Map;

/** Names match graph_style.json; no client or Minecraft dependency. */
public record WorkstationStyle(Panel panel, Map<String, Double> node, Links link, Force force, Motion motion,
        Map<String, String> palette) {
    public record Panel(double width, double height, double widthFraction, double maxHeightFraction,
            double title, double bar, double inset) {}
    public record Links(double chain, double item, double groupItem, double ingredient, double parentWeight) {}
    public record Force(double chargeCategory, double chargeGroup, double chargeItem, double chargeIngredient,
            double centre, double focus, double pathFocus, double collide, double velocityDecay,
            double alphaDecay, double alphaMin, double reheat, double dragTarget, double maxSpeed) {}
    public record Motion(int grow, int shrink, int fly, int shake, int pulse) {}
}
