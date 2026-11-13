package dev.nez.arksurvivalreturns.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.CreatureVariants;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.testground.SpecimenPayload;
import dev.nez.arksurvivalreturns.feature.testground.SpecimenPostBlockEntity;
import dev.nez.arksurvivalreturns.feature.testground.SpecimenPostBlockEntity.Action;
import dev.nez.arksurvivalreturns.feature.testground.SpecimenPostBlockEntity.Mode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * The Specimen Post's screen. Left, every creature, searchable; right, its colour, level, how it may move, its hunger,
 * whether it comes back after it dies, the actions, and what the post has timed. Each button sends the settings as shown
 * with its action; Apply sends them alone (a creature already out takes a new mode or hunger at once, and is replaced
 * for a new species, colour or level).
 */
public final class SpecimenPostScreen extends Screen {
    private static final int W = 380, H = 232, LIST_W = 124, ROW = 11;
    private static final int PANEL = 0xF0201D18, WELL = 0xFF14120E, EDGE = 0xFF6B5A3A, TITLE = 0xFFE8DCC0, MUTED = 0xFFB8AC90,
            PICKED = 0xFF5B4A2A, HOVER = 0xFF3A3224, REPORT = 0xFFE0C890;

    private final BlockPos pos;
    private final List<Species> all = Arrays.stream(Species.values()).sorted(Comparator.comparing(s -> s.displayName)).toList();
    private List<Species> shown = all;
    private Species species;
    private int variant, level, hunger;
    private Mode mode;
    private boolean respawn;
    private int scroll;
    private int left, top;
    private EditBox search, levelBox;
    private Button colourButton, modeButton, respawnButton;

    @EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
    public static final class Opener {
        @SubscribeEvent public static void payloads(RegisterClientPayloadHandlersEvent event) {
            event.register(SpecimenPayload.Open.TYPE, (payload, context) -> {
                var mc = Minecraft.getInstance();
                if (mc.level != null && mc.level.getBlockEntity(payload.pos()) instanceof SpecimenPostBlockEntity)
                    mc.setScreen(new SpecimenPostScreen(payload.pos()));
            });
        }
        private Opener() {}
    }

    public SpecimenPostScreen(BlockPos pos) {
        super(Component.literal("Specimen Post"));
        this.pos = pos;
        var post = post();
        species = post != null ? post.species() : Species.TYRANNOSAURUS;
        variant = post != null ? post.variant() : 0;
        level = post != null ? post.creatureLevel() : 1;
        hunger = post != null ? post.hunger() : 50;
        mode = post != null ? post.mode() : Mode.FROZEN;
        respawn = post != null && post.respawn();
    }

    private SpecimenPostBlockEntity post() {
        var mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof SpecimenPostBlockEntity post ? post : null;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        left = (width - W) / 2; top = (height - H) / 2;
        search = addRenderableWidget(new EditBox(font, left + 8, top + 20, LIST_W - 4, 14, Component.literal("Search")));
        search.setHint(Component.literal("Search creatures"));
        search.setResponder(text -> filter());
        int rx = left + LIST_W + 14, rw = W - LIST_W - 22;
        colourButton = addRenderableWidget(Button.builder(Component.empty(), b -> { variant = (variant + 1) % Math.max(1, colours().size()); refresh(); })
                .bounds(rx, top + 34, rw, 18).build());
        levelBox = addRenderableWidget(new EditBox(font, rx + 40, top + 56, 40, 16, Component.literal("Level")));
        levelBox.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit) && text.length() <= 3);
        levelBox.setValue(Integer.toString(level));
        levelBox.setResponder(text -> level = text.isEmpty() ? 1 : Math.clamp(Integer.parseInt(text), 1, 100));
        modeButton = addRenderableWidget(Button.builder(Component.empty(), b -> { mode = mode.next(); refresh(); })
                .bounds(rx + 88, top + 55, rw - 88, 18).build());
        addRenderableWidget(new AbstractSliderButton(rx, top + 77, rw, 18, Component.empty(), hunger / 100.0) {
            { updateMessage(); }
            @Override protected void updateMessage() {
                setMessage(Component.literal("Hunger: " + Math.round(value * 100) + "%" + (value >= .7 ? " (hunts, eats)" : value <= .2 ? " (fed)" : "")));
            }
            @Override protected void applyValue() { hunger = (int) Math.round(value * 100); }
        });
        respawnButton = addRenderableWidget(Button.builder(Component.empty(), b -> { respawn = !respawn; refresh(); })
                .bounds(rx, top + 99, rw, 18).build());
        Action[] actions = {Action.SPAWN, Action.APPLY, Action.CLEAR, Action.KILL, Action.KNOCK_OUT, Action.WAKE, Action.TAME, Action.FIGHT};
        String[] labels = {"Spawn", "Apply", "Clear", "Kill", "Knock out", "Wake", "Tame", "Fight"};
        int bw = (rw - 6) / 4;
        for (int i = 0; i < actions.length; i++) {
            Action action = actions[i];
            addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> send(action))
                    .bounds(rx + (i % 4) * (bw + 2), top + 122 + (i / 4) * 20, bw, 18).build());
        }
        refresh();
    }

    private List<String> colours() { return CreatureVariants.of(species); }

    private void refresh() {
        var names = colours();
        variant = Math.floorMod(variant, Math.max(1, names.size()));
        colourButton.setMessage(Component.literal("Colour: " + (names.isEmpty() ? "-" : names.get(variant)) + " (" + (variant + 1) + "/" + Math.max(1, names.size()) + ")"));
        modeButton.setMessage(Component.literal("Mode: " + mode.label));
        respawnButton.setMessage(Component.literal("Respawn after death: " + (respawn ? "on" : "off")));
    }

    private void filter() {
        String text = search.getValue().trim().toLowerCase(Locale.ROOT);
        shown = text.isEmpty() ? all : all.stream().filter(s -> s.displayName.toLowerCase(Locale.ROOT).contains(text) || s.id.contains(text)).toList();
        scroll = 0;
    }

    private void send(Action action) {
        ClientPacketDistributor.sendToServer(new SpecimenPayload.Command(pos, species.id, variant, level, mode.ordinal(), hunger, respawn, action.ordinal()));
    }

    private int listTop() { return top + 38; }
    private int rows() { return (H - 46) / ROW; }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, EDGE);
        g.fill(left, top, left + W, top + H, PANEL);
        g.fill(left + 6, listTop() - 2, left + LIST_W + 6, listTop() + rows() * ROW + 2, WELL);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.text(font, title, left + 8, top + 7, TITLE, true);
        int rx = left + LIST_W + 14, rw = W - LIST_W - 22;
        g.text(font, Component.literal(species.displayName + " | " + species.realm().name().toLowerCase(Locale.ROOT)
                + (species.predator ? " | predator" : "")), rx, top + 20, TITLE, false);
        g.text(font, Component.literal("Level"), rx, top + 60, MUTED, false);
        int rows = rows();
        scroll = Math.clamp(scroll, 0, Math.max(0, shown.size() - rows));
        for (int i = 0; i < rows && scroll + i < shown.size(); i++) {
            var s = shown.get(scroll + i);
            int y = listTop() + i * ROW;
            boolean hover = mouseX >= left + 6 && mouseX < left + LIST_W + 6 && mouseY >= y && mouseY < y + ROW;
            if (s == species) g.fill(left + 6, y, left + LIST_W + 6, y + ROW, PICKED);
            else if (hover) g.fill(left + 6, y, left + LIST_W + 6, y + ROW, HOVER);
            g.text(font, Component.literal(s.displayName), left + 10, y + 2, s == species ? TITLE : MUTED, false);
        }
        if (shown.size() > rows)
            g.text(font, Component.literal((scroll + 1) + "-" + Math.min(shown.size(), scroll + rows) + " of " + shown.size()), left + 8, top + H - 9, MUTED, false);
        var post = post();
        int y = top + 176;
        g.text(font, font.plainSubstrByWidth(post == null ? "The post is gone" : post.status(), rw), rx, y - 10, TITLE, false);
        if (post != null && post.hasSpecimen() && post.spawnedAt() >= 0 && Minecraft.getInstance().level != null) {
            long now = Minecraft.getInstance().level.getGameTime();
            String timers = "Out " + seconds(now - post.spawnedAt())
                    + (post.downedAt() >= 0 ? " | down " + seconds(now - post.downedAt()) : "")
                    + (post.diedAt() >= 0 ? " | dying " + seconds(now - post.diedAt()) : "");
            g.text(font, Component.literal(timers), rx, y, MUTED, false);
        }
        if (post != null) {
            var lines = new ArrayList<String>();
            for (String report : post.reports()) lines.addAll(font.getSplitter().splitLines(report, rw, net.minecraft.network.chat.Style.EMPTY)
                    .stream().map(line -> line.getString()).toList());
            for (int i = 0; i < Math.min(4, lines.size()); i++) g.text(font, Component.literal(lines.get(i)), rx, y + 12 + i * 10, REPORT, false);
        }
    }

    private static String seconds(long ticks) {
        return String.format(Locale.ROOT, "%.1fs", Math.max(0, ticks) / 20.0);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x(), y = event.y();
        if (x >= left + 6 && x < left + LIST_W + 6 && y >= listTop() && y < listTop() + rows() * ROW) {
            int index = scroll + (int) ((y - listTop()) / ROW);
            if (index >= 0 && index < shown.size() && shown.get(index) != species) {
                species = shown.get(index);
                variant = 0;
                refresh();
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (x >= left + 6 && x < left + LIST_W + 6) {
            scroll = Math.clamp(scroll - (int) Math.signum(scrollY) * 3, 0, Math.max(0, shown.size() - rows()));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }
}
