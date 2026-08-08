package com.flatts.recompile.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One declaration of where everything on a screen is, read by both the menu and the screen.
 *
 * <p><b>The bug this exists to make impossible is two copies of one truth.</b> A container's slot
 * coordinates were baked into {@code Slot} objects in the menu, and the drawing that has to line up with
 * them lived in a client-only screen class with nothing connecting the two. The Burner Generator shipped
 * with its readout drawn straight through its own fuel row, and the Tree Nursery still declares
 * {@code FERT_X = 44} in a screen while its menu independently passes {@code 44} to a {@code Slot}. Every
 * machine paid for that agreement by hand, forever, and a mistake is a slot drawn where no slot is.
 *
 * <p><b>This class is common code and must stay that way.</b> A menu is constructed on a dedicated
 * server where no client class exists, so nothing here may import {@code net.minecraft.client} - not
 * {@code GuiGraphics}, not {@code RenderPipelines}, not {@code Font}. Drawing is a separate client-side
 * pass ({@code GuiPainter}) that walks the same declaration. That constraint is the whole architecture,
 * and it is a deliberate choice rather than the only option: owo-lib solves the same problem the other
 * way round, letting a client layout move slots after the menu has placed them. That is sound - slot
 * coordinates are purely visual, since a click sends a slot index - but it puts the truth on the client,
 * where {@code MenuLayoutTests} cannot reach it. Keeping the truth server-side is what makes the geometry
 * testable at all, which is the reason we build rather than adopt. See issue #164.
 *
 * <p>A second constraint follows from vanilla: {@code imageWidth} and {@code imageHeight} are final and
 * are passed to {@code super(...)}, so <b>the layout must be computable before the screen object
 * exists</b>. Hence a pure declaration built into a static constant, never something accumulated while
 * rendering.
 *
 * <p><b>There is deliberately no auto-layout.</b> Vanilla containers are hand-placed by nature and there
 * are four of them here; a flexbox would be a second thing to learn for no gain. What the framework
 * supplies is the single declaration, the query API the menu places slots from, and the drawing visitor
 * the screen paints through.
 */
public final class ScreenLayout {

    /** What a group of rectangles is, which decides what (if anything) the chrome pass draws for it. */
    public enum Kind {
        /** The container panel, nine-sliced from vanilla's. Declared, not implied - see the builder. */
        PANEL,
        /** A real container slot: the menu emits a {@code Slot} here and the chrome draws the sprite. */
        SLOT,
        /** Drawn exactly like a slot, but backed by no container slot - the Tree Nursery's species picker. */
        CELL,
        /** A recessed well, the frame a gauge is drawn inside. */
        WELL,
        /** Vanilla's progress arrow. Its fill is dynamic, so the screen paints it rather than the chrome. */
        ARROW,
        /** A named rectangle that nothing draws: a hit region, or an origin for content the screen owns. */
        REGION,
        /**
         * A surface other elements sit on top of.
         *
         * <p>The distinction from {@link #REGION} is entirely about the overlap sweep. A region that
         * overlaps another element is the Burner Generator's readout drawn through its own fuel row - a
         * bug, and the reason the sweep exists. A backdrop overlapping things is what a backdrop is for.
         * Marking the two apart is what lets the sweep stay strict about everything else.
         */
        BACKDROP
    }

    /**
     * One named run of identically-sized rectangles.
     *
     * <p>Stored as an origin plus a pitch rather than as a materialised list, which is what lets a
     * single-column run answer for an index past its own count. That is not a loophole - a list of rows
     * genuinely has a next row, and the connected-storage shelf needs exactly that to place its tail line
     * ("+6 more") directly under however many rows it actually drew. Anything else refuses.
     */
    public static final class Group {

        private final String name;
        private final Kind kind;
        private final int x;
        private final int y;
        private final int cellWidth;
        private final int cellHeight;
        private final int pitchX;
        private final int pitchY;
        private final int columns;
        private final int count;
        private boolean chrome = true;

        private Group(String name, Kind kind, int x, int y, int cellWidth, int cellHeight,
                int pitchX, int pitchY, int columns, int count) {
            this.name = name;
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;
            this.pitchX = pitchX;
            this.pitchY = pitchY;
            this.columns = columns;
            this.count = count;
        }

        public String name() {
            return name;
        }

        public Kind kind() {
            return kind;
        }

        /**
         * Whether this framework draws the group's chrome, or something else already has.
         *
         * <p>False for a screen built on a whole vanilla container background: {@code crafting_table.png}
         * carries its own slot wells, and drawing ours over them would be at best a redundant blit and at
         * worst a visible double edge. The slots still exist and the menu still places them from here -
         * only the painting is somebody else's.
         */
        public boolean hasChrome() {
            return chrome;
        }

        /** How many cells were declared. A single-column run may still be asked for more; see {@link #cell}. */
        public int count() {
            return count;
        }

        /** The one cell, for a group declared as a single element. */
        public Rect only() {
            if (count != 1) {
                throw new IllegalStateException(
                    "group '" + name + "' has " + count + " cells, ask for one by index");
            }
            return cell(0);
        }

        public Rect cell(int index) {
            if (index < 0) {
                throw new IndexOutOfBoundsException("negative cell " + index + " in group '" + name + "'");
            }
            // A vertical run extrapolates; anything else is a fixed set and an index past it is a bug.
            boolean extrapolates = columns == 1 && pitchY != 0;
            if (index >= count && !extrapolates) {
                throw new IndexOutOfBoundsException(
                    "cell " + index + " in group '" + name + "', which has " + count);
            }
            int row = index / columns;
            int column = index % columns;
            return new Rect(x + column * pitchX, y + row * pitchY, cellWidth, cellHeight);
        }

        /** Every declared cell, in declaration order. */
        public List<Rect> cells() {
            List<Rect> out = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                out.add(cell(i));
            }
            return out;
        }
    }

    /** What the menu is handed for each slot it must place: where it goes, and which one it is. */
    @FunctionalInterface
    public interface SlotSink {
        void accept(int index, int x, int y);
    }

    private static final String BACKPACK = "player_backpack";
    private static final String HOTBAR = "player_hotbar";

    private final int width;
    private final int height;
    private final int titleX;
    private final int titleY;
    private final int inventoryLabelX;
    private final int inventoryLabelY;
    private final Map<String, Group> groups;

    private ScreenLayout(Builder builder) {
        this.width = builder.width;
        this.height = builder.height;
        this.titleX = builder.titleX;
        this.titleY = builder.titleY;
        this.inventoryLabelX = builder.inventoryLabelX;
        this.inventoryLabelY = builder.inventoryLabelY;
        this.groups = Collections.unmodifiableMap(new LinkedHashMap<>(builder.groups));
    }

    public static Builder builder(int width, int height) {
        return new Builder(width, height);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int titleX() {
        return titleX;
    }

    public int titleY() {
        return titleY;
    }

    public int inventoryLabelX() {
        return inventoryLabelX;
    }

    public int inventoryLabelY() {
        return inventoryLabelY;
    }

    /** Every group in declaration order, which is also the order the chrome pass draws them. */
    public Iterable<Group> groups() {
        return groups.values();
    }

    public Group group(String name) {
        Group group = groups.get(name);
        if (group == null) {
            throw new IllegalArgumentException(
                "no group '" + name + "' in this layout; declared: " + groups.keySet());
        }
        return group;
    }

    public boolean has(String name) {
        return groups.containsKey(name);
    }

    /** The single rect of a one-element group. */
    public Rect rect(String name) {
        return group(name).only();
    }

    public Rect rect(String name, int index) {
        return group(name).cell(index);
    }

    /**
     * Hand every cell of a slot group to the menu, in order.
     *
     * <p>This is the whole point of the class from the menu's side: the menu never types a coordinate,
     * so its slots cannot be anywhere the screen does not draw one.
     */
    public void forEachSlot(String name, SlotSink sink) {
        Group group = group(name);
        for (int i = 0; i < group.count(); i++) {
            Rect rect = group.cell(i);
            sink.accept(i, rect.x(), rect.y());
        }
    }

    /**
     * The player's 36 slots, in vanilla's order, with the inventory index each one carries.
     *
     * <p>Identical in every container in the game, and it should be one line in a menu forever. The index
     * mapping is vanilla's and is the classic place to go wrong: the backpack is inventory slots 9-35 and
     * the hotbar is 0-8, so writing them out in visual order gets the hotbar pointing at the wrong items.
     */
    public void forEachPlayerSlot(SlotSink sink) {
        Group backpack = group(BACKPACK);
        for (int i = 0; i < backpack.count(); i++) {
            Rect rect = backpack.cell(i);
            sink.accept(i + 9, rect.x(), rect.y());
        }
        Group hotbar = group(HOTBAR);
        for (int i = 0; i < hotbar.count(); i++) {
            Rect rect = hotbar.cell(i);
            sink.accept(i, rect.x(), rect.y());
        }
    }

    /** Every rect the layout declares, tagged with its group. Used by the geometry sweep in GameTest. */
    public List<Map.Entry<Group, Rect>> everything() {
        List<Map.Entry<Group, Rect>> out = new ArrayList<>();
        for (Group group : groups.values()) {
            for (Rect rect : group.cells()) {
                out.add(Map.entry(group, rect));
            }
        }
        return out;
    }

    /** Builder. Every coordinate in the mod's screens is typed exactly once, here. */
    public static final class Builder {

        private final int width;
        private final int height;
        private final Map<String, Group> groups = new LinkedHashMap<>();
        private int titleX = GuiTheme.TITLE_X;
        private int titleY = GuiTheme.TITLE_Y;
        private int inventoryLabelX = GuiTheme.INVENTORY_X;
        private int inventoryLabelY = GuiTheme.PANEL_H - 94;

        private Builder(int width, int height) {
            this.width = width;
            this.height = height;
        }

        /**
         * The groups the most recent verb added, which {@link #noChrome()} applies to.
         *
         * <p>A list rather than a single group because {@link #playerInventory(int)} adds two - the
         * backpack and the hotbar - and a screen that suppresses chrome wants both. Suppressing only the
         * hotbar would draw twenty-seven slot sprites over a background that already had them and leave
         * nine that matched, which is the kind of half-applied fix that looks deliberate.
         */
        private final List<Group> lastAdded = new ArrayList<>();

        private Builder add(String name, Kind kind, int x, int y, int cellWidth, int cellHeight,
                int pitchX, int pitchY, int columns, int count) {
            if (groups.containsKey(name)) {
                throw new IllegalArgumentException("duplicate layout group '" + name + "'");
            }
            Group group = new Group(name, kind, x, y, cellWidth, cellHeight,
                pitchX, pitchY, columns, count);
            groups.put(name, group);
            lastAdded.add(group);
            return this;
        }

        /** Start a verb: whatever it adds becomes the target of a following {@link #noChrome()}. */
        private Builder verb() {
            lastAdded.clear();
            return this;
        }

        /**
         * The group just declared draws no chrome, because something else already drew it.
         *
         * <p>Applies to a screen built on a whole vanilla container background. The slots are still
         * declared here and the menu still places them from here - which is the point, since that is what
         * keeps the menu and the picture in agreement - but the wells under them come from the image.
         */
        public Builder noChrome() {
            if (lastAdded.isEmpty()) {
                throw new IllegalStateException("noChrome() before any group was declared");
            }
            for (Group group : lastAdded) {
                group.chrome = false;
            }
            return this;
        }

        /**
         * The vanilla panel, filling the whole screen, drawn before anything else.
         *
         * <p>Declared rather than assumed, because one of the four screens does not have one: the Scrap
         * Crafting Table reuses vanilla's entire {@code crafting_table.png} on its left half and paints a
         * flat slab on its right, so a nine-sliced panel underneath would be drawn and then completely
         * covered. Making the backdrop a group keeps that a choice a layout states rather than a special
         * case a screen has to opt out of.
         */
        public Builder panel() {
            return verb().add("panel", Kind.PANEL, 0, 0, width, height, 0, 0, 1, 1);
        }

        /** One container slot. */
        public Builder slot(String name, int x, int y) {
            return verb().add(name, Kind.SLOT, x, y,
                GuiTheme.SLOT_SIZE, GuiTheme.SLOT_SIZE, 0, 0, 1, 1);
        }

        /** A horizontal run of container slots at vanilla's pitch. */
        public Builder slotRow(String name, int count, int x, int y) {
            return verb().add(name, Kind.SLOT, x, y, GuiTheme.SLOT_SIZE, GuiTheme.SLOT_SIZE,
                GuiTheme.SLOT_PITCH, 0, count, count);
        }

        /** A grid of container slots at vanilla's pitch, filled left to right then top to bottom. */
        public Builder slotGrid(String name, int columns, int rows, int x, int y) {
            return verb().add(name, Kind.SLOT, x, y, GuiTheme.SLOT_SIZE, GuiTheme.SLOT_SIZE,
                GuiTheme.SLOT_PITCH, GuiTheme.SLOT_PITCH, columns, columns * rows);
        }

        /** A grid drawn as slots but backed by no container slot - a picker. */
        public Builder cellGrid(String name, int columns, int count, int x, int y) {
            return verb().add(name, Kind.CELL, x, y, GuiTheme.SLOT_SIZE, GuiTheme.SLOT_SIZE,
                GuiTheme.SLOT_PITCH, GuiTheme.SLOT_PITCH, columns, count);
        }

        /** A recessed well for a gauge to fill. */
        public Builder well(String name, int x, int y, int width, int height) {
            return verb().add(name, Kind.WELL, x, y, width, height, 0, 0, 1, 1);
        }

        /** Vanilla's progress arrow, whose size comes from the sprite. */
        public Builder arrow(String name, int x, int y) {
            return verb().add(name, Kind.ARROW, x, y,
                GuiTheme.ARROW_W, GuiTheme.ARROW_H, 0, 0, 1, 1);
        }

        /** A named rectangle nothing draws: a hit region, or an origin for content the screen owns. */
        public Builder region(String name, int x, int y, int width, int height) {
            return verb().add(name, Kind.REGION, x, y, width, height, 0, 0, 1, 1);
        }

        /** A surface that other elements sit on: a reused vanilla background, or a side panel. */
        public Builder backdrop(String name, int x, int y, int width, int height) {
            return verb().add(name, Kind.BACKDROP, x, y, width, height, 0, 0, 1, 1);
        }

        /**
         * A vertical run of rows. The only shape that answers for an index past its count, so a list can
         * place a tail line directly beneath however many rows it drew.
         *
         * <p>{@code rowHeight} is what a row occupies and {@code pitch} is how far apart they sit, which
         * are not the same number: the connected-storage shelf hit-tests and highlights a 16px band but
         * steps 20px, so the rows have air between them.
         */
        public Builder rows(String name, int count, int x, int y, int width, int rowHeight, int pitch) {
            return verb().add(name, Kind.REGION, x, y, width, rowHeight, 0, pitch, 1, count);
        }

        /**
         * The player's inventory, from the single number that actually varies between screens.
         *
         * <p>The hotbar gap and the label rise are vanilla's grammar, not a per-screen choice, so they are
         * derived rather than passed. This also sets {@code inventoryLabelY}, which every one of the four
         * screens had been setting by hand or inheriting from a vanilla default that happened to agree.
         */
        public Builder playerInventory(int y) {
            verb();
            add(BACKPACK, Kind.SLOT, GuiTheme.INVENTORY_X, y, GuiTheme.SLOT_SIZE, GuiTheme.SLOT_SIZE,
                GuiTheme.SLOT_PITCH, GuiTheme.SLOT_PITCH, 9, 27);
            add(HOTBAR, Kind.SLOT, GuiTheme.INVENTORY_X,
                y + 3 * GuiTheme.SLOT_PITCH + GuiTheme.HOTBAR_GAP,
                GuiTheme.SLOT_SIZE, GuiTheme.SLOT_SIZE, GuiTheme.SLOT_PITCH, 0, 9, 9);
            this.inventoryLabelY = y - GuiTheme.LABEL_RISE;
            return this;
        }

        /** Move the title, for the one screen whose title sits over a crafting grid rather than at the edge. */
        public Builder title(int x) {
            this.titleX = x;
            return this;
        }

        /**
         * Finish, declaring the two text labels as real regions on the way out.
         *
         * <p>Vanilla draws the title and the "Inventory" label itself, from fields rather than from
         * anything a layout can see, so it is easy to forget they occupy space - and the Burner Generator's
         * original defect was precisely a piece of text drawn through something else. Declaring them makes
         * the overlap sweep cover them like anything else, instead of the sweep needing a hand-written
         * clause per screen. They are added last so a screen may not accidentally collide with a name.
         *
         * <p>{@link GuiTheme#LABEL_W} is a bound rather than a measurement: real width depends on the font
         * and the translation, neither of which exists on a server. It is generous enough that a machine
         * element clearing it clears any plausible label.
         */
        public ScreenLayout build() {
            if (!groups.containsKey("title")) {
                verb().add("title", Kind.REGION, titleX, titleY, GuiTheme.LABEL_W, 9, 0, 0, 1, 1);
            }
            if (groups.containsKey(BACKPACK) && !groups.containsKey("inventory_label")) {
                verb().add("inventory_label", Kind.REGION, inventoryLabelX, inventoryLabelY,
                    GuiTheme.LABEL_W, 9, 0, 0, 1, 1);
            }
            return new ScreenLayout(this);
        }
    }
}
