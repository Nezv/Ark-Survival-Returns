package dev.nez.arksurvivalreturns.feature.creature;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.land.LandFamily;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

public enum Species {
    PTERANODON("pteranodon", "Pteranodon", 24, 3, 0.28, 0.9f, 1.2f, 3, 4, 18, false, "Ptero-Ground-Idle", "Ptero-Ground-Move-Fwd", "Ptero-Ground-Attack",
            new FlyerProfile(true, -1, 0, -1, 16, true, 1, "Ptero-Fly-Fwd", "Ptero-Fly-Hover", "Ptero-Land", "Ptero-Take-Off",
                    "Ptero-Fly-Attack-Swoop-Loop", "Ptero-Fly-Attack-Swoop-Out", "Ptero-Fly-Attack-Bite", null)),
    VELOCIRAPTOR("velociraptor", "Velociraptor", 32, 5, 0.32, 0.85f, 1.5f, 4, 6, 14, true, "Raptor-Idle", "Raptor-Move-Fwd", "Raptor-Attack"),
    ARGENTAVIS("argentavis", "Argentavis", 46, 6, 0.25, 1.2f, 1.6f, 3, 4, 4, false, "Argentavis-Ground-Idle", "Argentavis-Ground-Move-Fwd", "Argentavis-Ground-Attack",
            new FlyerProfile(false, -1, -1, 0, 24, false, 2, "Argentavis-Fly-Fwd", "Argentavis-Fly-Hover",
                    "Argentavis-Land", "Argentavis-Take-Off", null, "Argentavis-Fly-Attack-Swoop-Out", "Argentavis-Fly-Attack-Claw", null)),
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
            new LandProfile(LandFamily.SMALL_HERBIVORE, true, 1, .85, 1.0, .55555556, "Lystrosaurus-Charge-Fwd", "Lystrosaurus-Eat", "Lystrosaurus-Startled")),

    // --------------------------------------------------------------- aquatic
    CNIDARIA("cnidaria", "Cnidaria", 12, 2, .6, .35f, .6f, 1, 1, 8, false, "Cnidaria-Idle", "Cnidaria-Idle", "Cnidaria-Shock",
            new LandProfile(LandFamily.AQUATIC, true, 1, .6, 2.0, 2.0, "Cnidaria-Idle", null, "Cnidaria-Wide-Idle")),
    PLESIOSAUR("plesiosaur", "Plesiosaur", 60, 6, .9, 1.7f, 3.5f, 1, 1, 8, true, "Plesiosaur-Idle", "Plesiosaur-Move-Fwd", "Plesiosaur-Attack-Bite",
            new LandProfile(LandFamily.AQUATIC, false, 2, .9, 2.2, 1.4, "Plesiosaur-Move-Fwd", "Plesiosaur-Eat", null)),
    MEGALODON("megalodon", "Megalodon", 90, 12, 1.3, 1.6f, 3.0f, 1, 1, 7, true, "Megalodon-Idle", "Megalodon-Swim-Fwd", "Megalodon-Attack-Bite",
            new LandProfile(LandFamily.AQUATIC, false, 3, 1.3, 1.8, 1.1, "Megalodon-Swim-Fwd", "Megalodon-Eat", null)),
    LIOPLEURODON("liopleurodon", "Liopleurodon", 80, 11, 1.2, 1.3f, 2.5f, 1, 1, 5, true, "Liopleurodon-Idle", "Liopleurodon-Swim-Fwd", "Liopleurodon-Attack-Chomp",
            new LandProfile(LandFamily.AQUATIC, false, 3, 1.2, 1.7, 1.1, "Liopleurodon-Swim-Charge-Fwd", "Liopleurodon-Torpid-Eat", "Liopleurodon-Spin")),
    MOSASAURUS("mosasaurus", "Mosasaurus", 160, 18, 1.1, 2.6f, 5.0f, 1, 1, 3, true, "Mosasaurus-Idle", "Mosasaurus-Swim-Fwd", "Mosasaurus-Attack-Bite",
            new LandProfile(LandFamily.AQUATIC, false, 4, 1.1, 2.4, 1.5, "Mosasaurus-Charge-Fwd", "Mosasaurus-Eat", null)),
    TUSOTEUTHIS("tusoteuthis", "Tusoteuthis", 150, 16, 1.0, 2.0f, 3.5f, 1, 1, 3, true, "Tusoteuthis-Idle", "Tusoteuthis-Swim-Fwd", "Tusoteuthis-Attack-Bite",
            new LandProfile(LandFamily.AQUATIC, false, 4, 1.0, 2.0, 1.3, "Tusoteuthis-Swim-Fwd", "Tusoteuthis-Eat", "Tusoteuthis-Attack-Crush-Loop")),

    // ----------------------------------------------------------- semi-aquatic
    KAPROSUCHUS("kaprosuchus", "Kaprosuchus", 55, 8, 1.35, .95f, 2.0f, 1, 1, 7, true, "Kaprosuchus-Idle", "Kaprosuchus-Move-Fwd", "Kaprosuchus-Attack-Bite",
            new LandProfile(LandFamily.AMPHIBIOUS, false, 2, 1.35, 1.4, .9, "Kaprosuchus-Charge-Fwd", "Kaprosuchus-Eat", "Kaprosuchus-Startle",
                    new SwimProfile("Kaprosuchus-Swim-Idle", "Kaprosuchus-Swim-Fwd", "Kaprosuchus-Swim-Fwd"))),
    SARCO("sarco", "Sarco", 70, 10, 1.3, 1.2f, 2.5f, 2, 3, 8, true, "Sarco-Ground-Idle", "Sarco-Ground-Move-Fwd", "Sarco-Ground-Attack-Bite",
            new LandProfile(LandFamily.SWAMP_PACK, false, 2, 1.3, 1.7, 1.1, "Sarco-Ground-Charge-Fwd", "Sarco-Ground-Eat-Additive", "Sarco-Ground-Attack-Lunge",
                    new SwimProfile("Sarco-Swim-Idle", "Sarco-Swim-Fwd", "Sarco-Swim-Charge-Fwd"))),
    DEINOSUCHUS("deinosuchus", "Deinosuchus", 120, 14, 1.2, 1.8f, 3.5f, 1, 1, 4, true, "Deinosuchus_Idle", "Deinosuchus_Move_FWD", "Deinosuchus_Attack_Bite",
            new LandProfile(LandFamily.AMPHIBIOUS, false, 3, 1.2, 2.0, 1.3, "Deinosuchus_Charge_FWD", "Deinosuchus_Eat", "Deinosuchus_Attack_Hiss",
                    new SwimProfile("Deinosuchus_Swim_Idle", "Deinosuchus_Swim_Move_FWD", "Deinosuchus_Swim_Charge_FWD"))),
    TITANOBOA("titanoboa", "Titanoboa", 45, 9, 1.4, .8f, 1.2f, 1, 1, 6, true, "BoaFrill-Idle", "BoaFrill-Move-Fwd", "BoaFrill-Attack-Lunge",
            new LandProfile(LandFamily.AMPHIBIOUS, false, 3, 1.4, 1.6, 1.0, "BoaFrill-Charge-Fwd", "BoaFrill-Eat-Additive", "BoaFrill-Startled")),

    // ------------------------------------------------------------------- cold
    MEGALOCERUS("megalocerus", "Megalocerus", 60, 6, 1.5, 1.0f, 2.2f, 4, 6, 10, false, "Stag-Idle", "Stag-Move-Fwd", "Stag-Attack-Gore",
            new LandProfile(LandFamily.COLD_GRAZER, true, 1, 1.5, 1.1, .75, "Stag-Charge-Fwd", "Stag-Graze", "Stag-Startled")),
    UNICORN("unicorn", "Unicorn", 65, 7, 1.7, .95f, 2.0f, 1, 1, 3, false, "Equus-Idle2", "Equus-Move-Fwd", "Equus-Attack-Buck",
            new LandProfile(LandFamily.RARE_GRAZER, true, 1, 1.7, 1.05, .7, "Equus-Charge-Fwd", "Equus-Eat", "Equus-Roar")),
    MAMMOTH("mammoth", "Mammoth", 140, 12, 1.0, 1.9f, 4.0f, 2, 4, 7, false, "Mammoth-Idle", "Mammoth-Move-Fwd", "Mammoth-Attack-Tusk-Jab",
            new LandProfile(LandFamily.COLD_BROWSER, false, 2, 1.0, 2.4, 1.5, "Mammoth-Charge-Fwd", "Mammoth-Graze", "Mammoth_new_Call")),
    DIREWOLF("direwolf", "Direwolf", 50, 8, 2.0, .8f, 1.6f, 4, 6, 9, true, "Direwolf-Idle", "Direwolf-Move-Fwd", "Direwolf-Attack-Bite",
            new LandProfile(LandFamily.COLD_PREDATOR, false, 2, 2.0, .9, .6, "Direwolf-Charge-Fwd", "Direwolf-Eat", "Direwolf-Howl")),
    SABERTOOTH("sabertooth", "Sabertooth", 60, 11, 1.9, .8f, 1.6f, 1, 2, 6, true, "Saber-Idle", "Saber-Move-Fwd", "Saber-Attack-Bite",
            new LandProfile(LandFamily.COLD_STALKER, false, 3, 1.9, .95, .65, "Saber-Charge-Fwd", "Saber-Eat", "Saber-Startled")),
    MEGAPITHECUS("megapithecus", "Megapithecus", 180, 18, 1.4, 1.7f, 3.5f, 1, 1, 1, true, "Gorilla-Idle", "Gorilla-Move-Fwd", "Gorilla-Attack-Pound",
            new LandProfile(LandFamily.GUARDIAN, false, 5, 1.4, 1.6, 1.05, "Gorilla-Charge-Fwd", null, "Gorilla_Chest_Pounding")),

    // ------------------------------------------- warm land from the collection
    PARACERATHERIUM("paraceratherium", "Paraceratherium", 155, 13, 1.0, 2.0f, 4.5f, 2, 4, 6, false, "Paraceratherium-Idle", "Paraceratherium-Move-Fwd", "Paraceratherium-Attack-Footstomp",
            new LandProfile(LandFamily.BIG_HERBIVORE, false, 3, 1.0, 2.6, 1.6, "Paraceratherium-Charge-Fwd", "Paraceratherium-Eat", "Paraceratherium-Startled")),
    TERRORBIRD("terrorbird", "Terrorbird", 45, 9, 2.1, .9f, 2.0f, 4, 6, 8, true, "TerrorBird-Idle", "TerrorBird-Move-Fwd", "TerrorBird-Attack-Bite",
            new LandProfile(LandFamily.SMALL_CARNIVORE, false, 2, 2.1, .85, .58, "TerrorBird-Charge-Fwd", "TerrorBird-Eat", "TerrorBird-Startled")),
    RAVAGER("ravager", "Ravager", 65, 11, 1.9, .9f, 1.8f, 4, 6, 5, true, "CaveWolf-Idle", "CaveWolf-Walk-Fwd", "CaveWolf-Attack-Bite",
            new LandProfile(LandFamily.SMALL_CARNIVORE, false, 3, 1.9, .9, .62, "CaveWolf-Charge-Fwd", "CaveWolf-Eat", "CaveWolf-Howl")),

    // ----------------------------------------------------------------- flying
    ARCHAEOPTERYX("archaeopteryx", "Archaeopteryx", 10, 2, 1.2, .4f, .5f, 3, 4, 8, false, "Archaeopteryx-Idle", "Archaeopteryx-Move-Fwd", "Archaeopteryx-Attack-Bite",
            new FlyerProfile(false, 24, 62, 0, 12, true, 1, "Archaeopteryx-Fly", "Archaeopteryx-Glide", "Archaeopteryx-StartPerch",
                    "Archaeopteryx-Fly-Startle", null, null, "Archaeopteryx-Attack-Bite", "Archaeopteryx-Perched")),
    QUETZAL("quetzal", "Quetzal", 130, 10, .9, 2.4f, 5.0f, 1, 1, 2, false, "Quetzalcoatlus-Ground-Idle", "Quetzalcoatlus-Ground-Platform-Move-Fwd", "Quetzalcoatlus-Fly-Attack-Bite",
            new FlyerProfile(false, 72, 110, 0, 20, false, 4, "Quetzalcoatlus-Fly-Fwd", "Quetzalcoatlus-Fly-Flap-Fwd",
                    "Quetzalcoatlus-Land-Platform", "Quetzalcoatlus-Take-Off-Platform", null, null,
                    "Quetzalcoatlus-Fly-Attack-Bite", "Quetzalcoatlus-Ground-Idle")),
    DRAGON("dragon", "Dragon", 190, 22, 1.3, 2.2f, 4.5f, 1, 1, 1, true, "Dragon-Ground-Idle", "Dragon-Ground-Move-Fwd", "Dragon-Ground-Attack-Bite",
            new FlyerProfile(false, 80, 100, 0, 24, false, 5, "Dragon-Fly-Fwd", "Dragon-Fly-Idle", "Dragon-Land", "Dragon-Take-Off",
                    null, "Dragon-Fly-Attack-Swoop-Out", "Dragon-Fly-Attack-Fire", "Dragon-Ground-Idle"));

    /** Movement domain and habitat system used by a species. */
    public enum Realm { LAND, AMPHIBIOUS, WATER, AIR }

    /** Water clip set for semi-aquatic and water-bound species. */
    private record SwimProfile(String idle, String walk, String run) {}

    /**
     * Nest-colony policy and flight clips for realm AIR species, in declaration order:
     * shore sand requirement, roaming radius (-1 = config), minimum nest floor Y (-1 = config),
     * shoreline water radius (-1 = config), nest spread radius, timid, minimum danger,
     * then the fly-move, hover, land, take-off, swoop-loop, swoop-out, attack and perch clips.
     */
    public record FlyerProfile(boolean shoreSand, int roamRadius, int nestFloorY, int shoreWaterRadius, int nestRadius,
            boolean timid, int danger, String flyMove, String hover, String land, String takeOff,
            String swoopLoop, String swoopOut, String attack, String perchIdle) {}

    private record LandProfile(LandFamily family, boolean timid, int danger, double sprintRatio,
            double walkCycle, double runCycle, String run, String food, String warning, SwimProfile swim) {
        LandProfile(LandFamily family, boolean timid, int danger, double sprintRatio,
                double walkCycle, double runCycle, String run, String food, String warning) {
            this(family, timid, danger, sprintRatio, walkCycle, runCycle, run, food, warning, null);
        }
    }
    private final LandProfile profile;
    private final FlyerProfile flyerProfile;
    private final Realm realm;

    public final String id, displayName, idle, walk, attack;
    public final double health, damage, speed;
    public final float width, height, sizeMultiplier;
    public final int minGroup, maxGroup, weight;
    public final boolean predator;
    public final TagKey<Biome> biomes;
    Species(String id, String name, double hp, double damage, double speed, float width, float height,
            int min, int max, int weight, boolean predator, String idle, String walk, String attack) {
        this(id,name,hp,damage,speed,width,height,min,max,weight,predator,idle,walk,attack,null,(FlyerProfile)null);
    }
    Species(String id, String name, double hp, double damage, double speed, float width, float height,
            int min, int max, int weight, boolean predator, String idle, String walk, String attack, FlyerProfile flyerProfile) {
        this(id,name,hp,damage,speed,width,height,min,max,weight,predator,idle,walk,attack,null,flyerProfile);
    }
    Species(String id, String name, double hp, double damage, double speed, float width, float height,
            int min, int max, int weight, boolean predator, String idle, String walk, String attack, LandProfile profile) {
        this(id,name,hp,damage,speed,width,height,min,max,weight,predator,idle,walk,attack,profile,null);
    }
    Species(String id, String name, double hp, double damage, double speed, float width, float height,
            int min, int max, int weight, boolean predator, String idle, String walk, String attack,
            LandProfile profile, FlyerProfile flyerProfile) {
        this.profile=profile; this.flyerProfile=flyerProfile;
        this.realm = flyerProfile != null ? Realm.AIR
                : profile == null ? Realm.LAND
                : profile.family() == LandFamily.AQUATIC ? Realm.WATER
                : profile.swim() != null ? Realm.AMPHIBIOUS : Realm.LAND;
        this.id = id; displayName = name; health = hp; this.damage = damage; this.speed = MovementTuning.attribute(profile==null ? defaultSprintRatio(id) : profile.sprintRatio, 5.612);
        float size = switch (id) {
            case "titanosaur" -> 6;
            case "giganotosaurus", "tyrannosaurus", "spinosaurus", "acrocanthosaurus" -> 3;
            case "cnidaria", "plesiosaur", "megalodon", "liopleurodon", "mosasaurus", "tusoteuthis",
                 "kaprosuchus", "sarco", "deinosuchus", "titanoboa", "megalocerus", "unicorn", "mammoth",
                 "direwolf", "sabertooth", "megapithecus", "paraceratherium", "terrorbird", "ravager",
                 "archaeopteryx", "quetzal", "dragon" -> 1;
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
    /** Configured sprint default: the species profile wins so the config and the entity agree. */
    public double sprintRatioDefault() { return profile != null ? profile.sprintRatio : defaultSprintRatio(id); }
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
            case ARCHAEOPTERYX -> running ? 0.7 : 0.8;
            case QUETZAL -> running ? 1.4 : 1.6;
            case DRAGON -> running ? 1.5 : 2.2;
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
    public boolean flyer() { return realm == Realm.AIR; }
    public boolean aquatic() { return realm == Realm.WATER; }
    public boolean amphibious() { return realm == Realm.AMPHIBIOUS; }
    public boolean swimmer() { return realm == Realm.WATER || realm == Realm.AMPHIBIOUS; }
    /** Realm LAND and AMPHIBIOUS species keep a saved land habitat and shared group satiation. */
    public boolean landHabitat() { return realm == Realm.LAND || realm == Realm.AMPHIBIOUS; }
    public boolean sleeps() { return landHabitat(); }
    public Realm realm() { return realm; }
    public boolean herd() { return !flyer() && !predator && maxGroup>1; }
    public boolean timid() {
        if (flyerProfile != null) return flyerProfile.timid();
        return this == PTERANODON || profile!=null && profile.timid;
    }
    public boolean defensiveHerd() { return herd() && !timid(); }
    public int groupRadius() { return this == ARGENTAVIS ? 26 : herd() ? 22 : 7; }
    public int groupHeightRange() { return flyer() ? 24 : herd() ? 8 : 3; }
    public double cohesionDistance() { return this == ARGENTAVIS ? 30 : herd() ? 20 : 6 + width; }
    public int minimumDanger() {
        if (profile!=null) return profile.danger;
        if (flyerProfile != null) return flyerProfile.danger();
        return switch (this) {
            case PTERANODON, TRICERATOPS -> 1;
            case VELOCIRAPTOR, ARGENTAVIS -> 2;
            case THERIZINOSAURUS -> 3;
            case TYRANNOSAURUS, BRONTOSAURUS -> 4;
            case GIGANOTOSAURUS, TITANOSAUR -> 5;
            default -> throw new IllegalStateException("Missing danger profile: " + id);
        };
    }
    public boolean apex() { return this == TYRANNOSAURUS || this == GIGANOTOSAURUS || this == TITANOSAUR || this == SPINOSAURUS || this == ACROCANTHOSAURUS
            || this == MOSASAURUS || this == TUSOTEUTHIS || this == DRAGON || this == MEGAPITHECUS; }
    /** Cold-adapted species satisfy hydration from snow or ice over water and browse through snow cover. */
    public boolean coldAdapted() { return this == MEGALOCERUS || this == UNICORN || this == MAMMOTH
            || this == DIREWOLF || this == SABERTOOTH || this == MEGAPITHECUS; }
    public String runClip() {
        if (profile!=null) return profile.run;
        return switch (this) {
            case PTERANODON, ARGENTAVIS -> walk;
            default -> idle.substring(0, idle.length() - "Idle".length()) + "Charge-Fwd";
        };
    }
    public String foodClip() {
        if (profile!=null) return profile.food == null ? idle : profile.food;
        return switch (this) {
            case TRICERATOPS -> "Trike-Graze";
            case BRONTOSAURUS -> "Sauropod-Graze";
            case TYRANNOSAURUS -> "Rex-Eat-Additive";
            case VELOCIRAPTOR -> "Raptor-Eat-Additive";
            default -> idle.substring(0, idle.length() - "Idle".length()) + "Eat";
        };
    }
    public String warningClip() {
        if (profile!=null) return profile.warning == null ? idle : profile.warning;
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
        if (this == TYRANNOSAURUS || this == TRICERATOPS || flyer()) return restClip();
        return sleeps() ? "Ark-Sleep" : restClip();
    }
    /** Water clip set; non-swimmers fall back to the ground clips. */
    public String swimIdle() { return profile!=null && profile.swim != null ? profile.swim.idle() : idle; }
    public String swimWalk() { return profile!=null && profile.swim != null ? profile.swim.walk() : walk; }
    public String swimRun() { return profile!=null && profile.swim != null ? profile.swim.run() : runClip(); }
    /** Realm AIR policy; null for every other realm. */
    public FlyerProfile flyerProfile() { return flyerProfile; }
    /** Nest / habitat policy for realm AIR species, with -1 entries resolved against the server config. */
    public int flyerRoamRadius() {
        if (flyerProfile == null || flyerProfile.roamRadius() < 0)
            return this == ARGENTAVIS ? dev.nez.arksurvivalreturns.Config.ARGENT_ROAM_RADIUS.get()
                    : dev.nez.arksurvivalreturns.Config.PTERO_ROAM_RADIUS.get();
        return flyerProfile.roamRadius();
    }
    public int flyerNestFloorY() {
        if (flyerProfile == null) return 0;
        return flyerProfile.nestFloorY() < 0 ? dev.nez.arksurvivalreturns.Config.ARGENT_NEST_Y.get() : flyerProfile.nestFloorY();
    }
    public int flyerShoreWaterRadius() {
        if (flyerProfile == null || flyerProfile.shoreWaterRadius() < 0)
            return dev.nez.arksurvivalreturns.Config.NEST_WATER_RADIUS.get();
        return flyerProfile.shoreWaterRadius();
    }
    /**
     * Eye bones that receive the emissive night glow.
     *
     * Rigs whose eye bones carry no cube geometry are excluded: the layer renders the named bone's own
     * geometry, so naming a joint-only bone would light nothing. That covers Deinosuchus, Dragon and
     * Mosasaurus (whose two eye bones are not both geometry-bearing) plus the rigs without eyes.
     */
    public String[] eyeBones() {
        return switch (id) {
            case "velociraptor", "tyrannosaurus" -> new String[]{"Lft_Eye_JNT_SKL", "Rht_Eye_JNT_SKL"};
            case "ceratosaurus", "acrocanthosaurus" -> new String[]{"Eye_L", "Eye_R"};
            case "argentavis", "ravager" -> new String[]{"l_Eye_01", "r_Eye_01"};
            case "archaeopteryx" -> new String[]{"l_Eye", "r_Eye"};
            case "lystrosaurus", "cnidaria", "tusoteuthis", "kaprosuchus", "sarco", "terrorbird",
                 "deinosuchus", "dragon", "mosasaurus" -> new String[0];
            default -> new String[]{"l_eye", "r_eye"};
        };
    }
    /** Predators with dedicated eye geometry receive the red night glow. */
    public boolean glowingEyes() { return predator && eyeBones().length > 0; }
}
