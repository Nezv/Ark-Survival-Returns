package dev.nez.arksurvivalreturns.feature.creature;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.land.LandFamily;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

public enum Species {
    PTERANODON("pteranodon", "Pteranodon", 24, 3, 0.28, 0.9f, 1.2f, 3, 4, 18, false, "Ptero-Ground-Idle", "Ptero-Ground-Move-Fwd", "Ptero-Ground-Attack"),
    VELOCIRAPTOR("velociraptor", "Velociraptor", 32, 5, 0.32, 0.85f, 1.5f, 4, 6, 14, true, "Raptor-Idle", "Raptor-Move-Fwd", "Raptor-Attack"),
    ARGENTAVIS("argentavis", "Argentavis", 46, 6, 0.25, 1.2f, 1.6f, 3, 4, 4, false, "Argentavis-Ground-Idle", "Argentavis-Ground-Move-Fwd", "Argentavis-Ground-Attack"),
    TRICERATOPS("triceratops", "Triceratops", 85, 8, 0.22, 2.5f, 2.5f, 2, 4, 12, false, "Trike-Idle", "Trike-Move-Fwd", "Trike-Attack-Horn"),
    THERIZINOSAURUS("therizinosaurus", "Therizinosaurus", 100, 10, 0.25, 1.8f, 3.0f, 2, 4, 12, false, "Therizinosaurus-Idle", "Therizinosaurus-Move-Fwd", "Therizinosaurus-Attack-Claw"),
    BRONTOSAURUS("brontosaurus", "Brontosaurus", 145, 12, 0.18, 4.0f, 5.5f, 2, 4, 10, false, "Sauropod-Idle", "Sauropod-Move-Fwd", "Sauropod-Attack-FootStomp"),
    TYRANNOSAURUS("tyrannosaurus", "Tyrannosaurus", 130, 14, 0.25, 2.8f, 4.5f, 1, 1, 12, true, "Rex-Idle", "Rex-Move-Fwd", "Rex-Bite2"),
    GIGANOTOSAURUS("giganotosaurus", "Giganotosaurus", 170, 17, 0.26, 3.5f, 5.0f, 1, 1, 8, true, "Giganotosaurus-Idle", "Giganotosaurus-Move-Fwd", "Giganotosaurus-Attack-Bite"),
    TITANOSAUR("titanosaur", "Titanosaur", 190, 20, 0.15, 5.0f, 7.0f, 1, 1, 6, false, "Titanosaur-Idle", "Titanosaur-Move-Fwd", "Titanosaur-Attack-Footstomp"),
    SPINOSAURUS("spinosaurus", "Spinosaurus", 125, 13, .25, 2.8f, 4.5f, 1, 1, 8, true, "Spino-Idle", "Spino-Move-Fwd", "Spino-Attack-Bite",
            new LandProfile(LandFamily.BIG_CARNIVORE, false, 4, 1.85, 1.0, .73333334, "Spino-Charge-Fwd", "Spino-Eat", "Spino-Roar")),
    PARASAUR("parasaur", "Parasaur", 48, 3, .25, 1.3f, 2.0f, 4, 6, 14, false, "Para-Idle", "Para-Move-Fwd", "Para-Attack-Bite",
            new LandProfile(LandFamily.SMALL_HERBIVORE, true, 1, 1.55, 1.33333333, .95238097, "Para-Charge-Fwd", "Para-Graze", "Para-Roar-Alert")),
    CERATOSAURUS("ceratosaurus", "Ceratosaurus", 88, 10, .25, 1.8f, 2.8f, 1, 1, 8, true, "Ceratosaurus_Idle", "Ceratosaurus_MoveFWD", "Cerato_Attack_Bite1",
            new LandProfile(LandFamily.BIG_CARNIVORE, false, 3, 1.65, 1.7999999, .93333334, "Ceratosaurus_ChargeFWD_NOBOOST", "Ceratosaurus_Eat", "Ceratosaurus_Roar1")),
    DILOPHOSAUR("dilophosaur", "Dilophosaur", 22, 3, .25, .65f, .95f, 4, 6, 12, true, "Dilo-Idle", "Dilo-Move-Fwd", "Dilo-Attack-Bite",
            new LandProfile(LandFamily.SMALL_CARNIVORE, false, 1, 1.25, .88888889, .55555556, "Dilo-Charge-Fwd", "Dilo-Eat", "Dilo-Startled")),
    ACROCANTHOSAURUS("acrocanthosaurus", "Acrocanthosaurus", 155, 16, .25, 3.2f, 4.8f, 1, 1, 6, true, "Acro_Idle", "Acro_Move_Walk_FWD", "Acro_Attack_Bite",
            new LandProfile(LandFamily.BIG_CARNIVORE, false, 5, 1.7, 2.08333321, 1.38888878, "Acro_Move_Charge_FWD", "Acro_Eat", "Acro_Attack_Roar")),
    ALLOSAURUS("allosaurus", "Allosaurus", 78, 9, .25, 1.8f, 3.0f, 4, 6, 9, true, "Allosaurus-Idle", "Allosaurus-Move-Fwd", "Allosaurus-Attack-Bite",
            new LandProfile(LandFamily.SMALL_CARNIVORE, false, 3, 1.9, 1.24999994, .74074069, "Allosaurus-Charge-Fwd", "Allosaurus-Eat-Additive", "Allosaurus-Roar_Anim")),
    ANKYLOSAURUS("ankylosaurus", "Ankylosaurus", 95, 9, .2, 2.1f, 2.0f, 2, 4, 10, false, "Ankylo-Idle", "Ankylo-Move-Fwd", "Ankylo-Attack-Tail-Sweep",
            new LandProfile(LandFamily.BIG_HERBIVORE, false, 2, .95, 1.11111107, .60606063, "Ankylo-Charge-Fwd", "Ankylo-Graze", "Ankylo-Startled")),
    CARNOTAURUS("carnotaurus", "Carnotaurus", 82, 10, .25, 1.8f, 3.0f, 1, 1, 10, true, "Carno-Idle", "Carno-Move-Fwd", "Carno-Attack-Bite",
            new LandProfile(LandFamily.BIG_CARNIVORE, false, 3, 1.8, 1.17647057, .71428575, "Carno-Charge-Fwd", "Carno-Eat", "Carno-Startled")),
    PEGOMASTAX("pegomastax", "Pegomastax", 18, 2, .25, .5f, .65f, 4, 6, 10, false, "Pegomastax-Idle", "Pegomastax-Move-Fwd", "Pegomastax-Attack-Bite",
            new LandProfile(LandFamily.SMALL_HERBIVORE, true, 1, 1.55, 1.0, .46153849, "Pegomastax-Biped-Charge-Fwd", "Pegomastax-Eat", "Pegomastax-Roar")),
    LYSTROSAURUS("lystrosaurus", "Lystrosaurus", 20, 2, .2, .6f, .5f, 4, 6, 14, false, "Lystrosaurus-Idle", "Lystrosaurus-Move-Fwd", "Lystrosaurus-Attack-Bite",
            new LandProfile(LandFamily.SMALL_HERBIVORE, true, 1, .85, 1.0, .55555556, "Lystrosaurus-Charge-Fwd", "Lystrosaurus-Eat", "Lystrosaurus-Startled"));

    private record LandProfile(LandFamily family, boolean timid, int danger, double sprintRatio,
            double walkCycle, double runCycle, String run, String food, String warning) {}
    private final LandProfile profile;

    public final String id, displayName, idle, walk, attack;
    public final double health, damage, speed;
    public final float width, height, sizeMultiplier;
    public final int minGroup, maxGroup, weight;
    public final boolean predator;
    public final TagKey<Biome> biomes;
    Species(String id, String name, double hp, double damage, double speed, float width, float height,
            int min, int max, int weight, boolean predator, String idle, String walk, String attack) {
        this(id,name,hp,damage,speed,width,height,min,max,weight,predator,idle,walk,attack,null);
    }
    Species(String id, String name, double hp, double damage, double speed, float width, float height,
            int min, int max, int weight, boolean predator, String idle, String walk, String attack, LandProfile profile) {
        this.profile=profile;
        this.id = id; displayName = name; health = hp; this.damage = damage; this.speed = MovementTuning.attribute(profile==null ? defaultSprintRatio(id) : profile.sprintRatio, 5.612);
        float size = switch (id) {
            case "titanosaur" -> 6;
            case "giganotosaurus", "tyrannosaurus", "spinosaurus", "acrocanthosaurus" -> 3;
            default -> 2;
        };
        sizeMultiplier = size;
        this.width = width * size; this.height = height * size; minGroup = min; maxGroup = max;
        this.weight = weight; this.predator = predator; this.idle = idle; this.walk = walk; this.attack = attack;
        biomes = TagKey.create(Registries.BIOME, ArkSurvivalReturns.id("spawns/" + id));
    }
    public static double defaultSprintRatio(String id) {
        return switch (id) {
            case "velociraptor" -> 2.2;
            case "tyrannosaurus" -> 1.8;
            case "giganotosaurus" -> 2.0;
            case "titanosaur" -> 1.25;
            case "brontosaurus" -> 1.05;
            case "triceratops" -> 1.1;
            case "therizinosaurus" -> 1.5;
            case "spinosaurus" -> 1.85;
            case "parasaur", "pegomastax" -> 1.55;
            case "ceratosaurus" -> 1.65;
            case "dilophosaur" -> 1.25;
            case "acrocanthosaurus" -> 1.7;
            case "allosaurus" -> 1.9;
            case "ankylosaurus" -> .95;
            case "carnotaurus" -> 1.8;
            default -> 0.85;
        };
    }
    public double strideCycleSeconds(boolean running) {
        if (profile!=null) return running ? profile.runCycle : profile.walkCycle;
        return switch (this) {
            case PTERANODON -> 1.0;
            case ARGENTAVIS -> 1.3333333;
            case VELOCIRAPTOR -> running ? 0.6666667 : 1.0666667;
            case TRICERATOPS -> running ? 0.8 : 2.3333333;
            case THERIZINOSAURUS -> running ? 0.8196721 : 1.4285715;
            case BRONTOSAURUS -> running ? 1.3888888 : 3.3333332;
            case TYRANNOSAURUS -> running ? 0.8888889 : 2.0408162;
            case GIGANOTOSAURUS -> running ? 0.7142858 : 1.5686275;
            case TITANOSAUR -> running ? 1.8518518 : 3.1746031;
            default -> throw new IllegalStateException("Missing locomotion profile: " + id);
        };
    }
    public dev.nez.arksurvivalreturns.feature.land.LandFamily family() {
        if (profile!=null) return profile.family;
        return switch (this) {
            case TYRANNOSAURUS, GIGANOTOSAURUS -> dev.nez.arksurvivalreturns.feature.land.LandFamily.BIG_CARNIVORE;
            case VELOCIRAPTOR -> dev.nez.arksurvivalreturns.feature.land.LandFamily.SMALL_CARNIVORE;
            case TITANOSAUR -> dev.nez.arksurvivalreturns.feature.land.LandFamily.TITANOSAUR;
            case TRICERATOPS, BRONTOSAURUS, THERIZINOSAURUS -> dev.nez.arksurvivalreturns.feature.land.LandFamily.BIG_HERBIVORE;
            default -> throw new IllegalStateException("Flying species have no land family");
        };
    }
    public boolean solitary() { return maxGroup == 1; }
    public boolean flyer() { return this == ARGENTAVIS || this == PTERANODON; }
    public boolean herd() { return !flyer() && !predator && maxGroup>1; }
    public boolean timid() { return this == PTERANODON || profile!=null && profile.timid; }
    public boolean defensiveHerd() { return herd() && !timid(); }
    public int groupRadius() { return this == ARGENTAVIS ? 26 : herd() ? 22 : 7; }
    public int groupHeightRange() { return flyer() ? 24 : herd() ? 8 : 3; }
    public double cohesionDistance() { return this == ARGENTAVIS ? 30 : herd() ? 20 : 6 + width; }
    public int minimumDanger() {
        if (profile!=null) return profile.danger;
        return switch (this) {
            case PTERANODON, TRICERATOPS -> 1;
            case VELOCIRAPTOR, ARGENTAVIS -> 2;
            case THERIZINOSAURUS -> 3;
            case TYRANNOSAURUS, BRONTOSAURUS -> 4;
            case GIGANOTOSAURUS, TITANOSAUR -> 5;
            default -> throw new IllegalStateException("Missing danger profile: " + id);
        };
    }
    public boolean apex() { return this == TYRANNOSAURUS || this == GIGANOTOSAURUS || this == TITANOSAUR || this == SPINOSAURUS || this == ACROCANTHOSAURUS; }
    public String runClip() {
        if (profile!=null) return profile.run;
        return switch (this) {
            case PTERANODON, ARGENTAVIS -> walk;
            default -> idle.substring(0, idle.length() - "Idle".length()) + "Charge-Fwd";
        };
    }
    public String foodClip() {
        if (profile!=null) return profile.food;
        return switch (this) {
            case TRICERATOPS -> "Trike-Graze";
            case BRONTOSAURUS -> "Sauropod-Graze";
            case TYRANNOSAURUS -> "Rex-Eat-Additive";
            case VELOCIRAPTOR -> "Raptor-Eat-Additive";
            default -> idle.substring(0, idle.length() - "Idle".length()) + "Eat";
        };
    }
    public String warningClip() {
        if (profile!=null) return profile.warning;
        return switch (this) {
            case TYRANNOSAURUS -> "Rex-Roar";
            case GIGANOTOSAURUS -> "Giganotosaurus-Roar";
            case VELOCIRAPTOR -> "Raptor-Call";
            case BRONTOSAURUS -> "Sauropod-Startled-Lft";
            case TITANOSAUR -> "Titanosaur-Startled-Lft";
            default -> idle.substring(0, idle.length() - "Idle".length()) + "Startled";
        };
    }
    public String restClip() {
        return this == TYRANNOSAURUS ? "Rex-Sleeping" : this == TRICERATOPS ? "Trike-Sleeping" : idle;
    }
    public boolean additiveFood() { return foodClip().contains("Additive"); }
    public String sleepClip() {
        return this == TYRANNOSAURUS || this == TRICERATOPS || flyer() ? restClip() : "Ark-Sleep";
    }
}
