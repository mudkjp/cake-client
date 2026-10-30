package dev.mudkip.cake.ui;

import dev.mudkip.cake.module.ClientModule;
import dev.mudkip.cake.module.ModuleCategory;
import dev.mudkip.cake.module.ModuleManager;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Locale;

public final class ModMenuScreen extends MenuOwoScreen {
    private static final int PANEL_MAX_WIDTH = 520;
    private static final int PANEL_MAX_HEIGHT = 320;
    private static final int THREE_COLUMN_MIN_WIDTH = 340;
    private static final int TWO_COLUMN_MIN_WIDTH = 220;
    private static final double PANEL_WIDTH_FRACTION = 0.5D;
    private static final double PANEL_HEIGHT_FRACTION = 0.55D;
    private static final int CARD_HEIGHT = 100;
    private static final int CARD_MARGIN = 4;
    private static final int ICON_SIZE = 20;
    private static final int FILTER_WIDTH = 64;
    private static final int MAX_NAME_LINES = 2;
    private static final int MAX_DESCRIPTION_LINES = 4;

    private FlowLayout filterRow;
    private FlowLayout gridContainer;
    private String searchQuery;
    private @Nullable ModuleCategory categoryFilter;
    private int panelWidth;

    public ModMenuScreen() {
        super(Component.literal("Cake Client"));
        this.searchQuery = "";
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.BLANK);
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        var availableWidth = Math.max(1, this.width - 32);
        var availableHeight = Math.max(1, this.height - 32);
        var widthFraction = (int) Math.round(this.width * ModMenuScreen.PANEL_WIDTH_FRACTION);
        var heightFraction = (int) Math.round(this.height * ModMenuScreen.PANEL_HEIGHT_FRACTION);

        this.panelWidth = Math.max(1, Math.min(ModMenuScreen.PANEL_MAX_WIDTH, Math.min(availableWidth, widthFraction)));
        var panelHeight = Math.max(1, Math.min(ModMenuScreen.PANEL_MAX_HEIGHT, Math.min(availableHeight, heightFraction)));

        var panel = UIContainers.verticalFlow(Sizing.fixed(this.panelWidth), Sizing.fixed(panelHeight))
                .gap(8);
        panel.padding(Insets.of(12));
        panel.surface(Surface.flat(UiStyles.PANEL_BACKGROUND).and(Surface.outline(UiStyles.PANEL_OUTLINE)));

        var searchBox = UIComponents.textBox(Sizing.fill(100));
        searchBox.setBordered(false);
        searchBox.setHint(Component.translatable("cake.menu.search"));
        searchBox.setMaxLength(50);
        searchBox.text(this.searchQuery);
        searchBox.onChanged().subscribe(value -> {
            this.searchQuery = value;
            this.rebuildModules();
        });

        var searchPanel = UIContainers.horizontalFlow(Sizing.expand(100), Sizing.fixed(24));
        searchPanel.margins(Insets.left(ModMenuScreen.CARD_MARGIN));
        searchPanel.padding(Insets.both(2, 6));
        searchPanel.verticalAlignment(VerticalAlignment.CENTER);
        searchPanel.surface(Surface.flat(UiStyles.SEARCH_BACKGROUND).and(Surface.outline(UiStyles.SEARCH_OUTLINE)));
        searchPanel.child(searchBox);

        var close = UIComponents.button(Component.literal("X"), button -> this.onClose());
        close.renderer(UiStyles.CLOSE_BUTTON);
        close.textShadow(false);
        close.horizontalSizing(Sizing.fixed(20));
        close.verticalSizing(Sizing.fixed(20));

        var header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                .gap(8)
                .child(searchPanel)
                .child(close);
        header.verticalAlignment(VerticalAlignment.CENTER);

        this.filterRow = UIContainers.ltrTextFlow(Sizing.fill(100), Sizing.content()).gap(4);
        this.filterRow.padding(Insets.left(ModMenuScreen.CARD_MARGIN));

        this.gridContainer = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.gridContainer.horizontalAlignment(HorizontalAlignment.LEFT);
        this.gridContainer.padding(Insets.bottom(16));

        var scroll = UIContainers.verticalScroll(Sizing.fill(100), Sizing.expand(100), this.gridContainer)
                .scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(UiStyles.SCROLLBAR)))
                .scrollbarThiccness(4)
                .scrollStep(0);

        panel.child(header);
        panel.child(this.filterRow);
        panel.child(scroll);
        root.child(panel);

        this.rebuildFilters();
        this.rebuildModules();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ModuleManager.get().saveIfDirty();
        this.minecraft.gui.setScreen(null);
    }

    private void rebuildFilters() {
        if (this.filterRow == null) {
            return;
        }

        this.filterRow.clearChildren();
        this.filterRow.child(this.filterButton(null));

        for (var category : ModuleCategory.values()) {
            if (this.hasModules(category)) {
                this.filterRow.child(this.filterButton(category));
            }
        }
    }

    private ButtonComponent filterButton(@Nullable ModuleCategory category) {
        var button = UIComponents.button(
                category == null ? Component.translatable("cake.menu.category.all") : category.getDisplayName(),
                component -> {
                    this.categoryFilter = category;
                    this.filterRow.queue(() -> {
                        this.rebuildFilters();
                        this.rebuildModules();
                    });
                }
        );
        var activeColor = category == null ? UiStyles.ALL_ACCENT : category.getColor();
        button.renderer(this.categoryFilter == category
                ? ModMenuScreen.activeFilter(activeColor)
                : UiStyles.SECONDARY_BUTTON);
        button.textShadow(false);
        button.horizontalSizing(Sizing.fixed(ModMenuScreen.FILTER_WIDTH));
        return button;
    }

    private static ButtonComponent.Renderer activeFilter(int color) {
        return (context, button, delta) -> {
            context.fill(button.getX(), button.getY(), button.getX() + button.getWidth(), button.getY() + button.getHeight(), color);
            context.drawRectOutline(button.getX(), button.getY(), button.getWidth(), button.getHeight(), 0x40FFFFFF);
        };
    }

    private boolean hasModules(ModuleCategory category) {
        for (var module : ModuleManager.get().getModules()) {
            if (module.getCategory() == category && module.isAvailable()) {
                return true;
            }
        }

        return false;
    }

    private void rebuildModules() {
        if (this.gridContainer == null) {
            return;
        }

        this.withMenuFont(() -> {
            this.gridContainer.clearChildren();

            var query = this.searchQuery.trim().toLowerCase(Locale.ROOT);
            var visible = new ArrayList<ClientModule>();

            for (var category : ModuleCategory.values()) {
                if (this.categoryFilter != null && this.categoryFilter != category) {
                    continue;
                }

                for (var module : ModuleManager.get().getModules()) {
                    if (module.getCategory() == category && module.isAvailable() && this.matches(module, query)) {
                        visible.add(module);
                    }
                }
            }

            if (visible.isEmpty()) {
                this.gridContainer.child(UIComponents.label(Component.translatable("cake.menu.no_results"))
                        .color(Color.ofArgb(UiStyles.MUTED_TEXT)));
                return;
            }

            var maxColumns = this.panelWidth >= ModMenuScreen.THREE_COLUMN_MIN_WIDTH
                    ? 3
                    : this.panelWidth >= ModMenuScreen.TWO_COLUMN_MIN_WIDTH ? 2 : 1;
            var columns = Math.max(1, Math.min(maxColumns, visible.size()));
            var usableWidth = Math.max(1, this.panelWidth - 24);
            var cardWidth = Math.max(1, usableWidth / columns - ModMenuScreen.CARD_MARGIN * 2);
            var rows = (visible.size() + columns - 1) / columns;
            var grid = UIContainers.grid(Sizing.content(), Sizing.content(), rows, columns);

            for (var index = 0; index < visible.size(); index++) {
                grid.child(this.moduleCard(visible.get(index), cardWidth), index / columns, index % columns);
            }

            this.gridContainer.child(grid);
        });
    }

    private boolean matches(ClientModule module, String query) {
        if (query.isEmpty()) {
            return true;
        }

        var integration = module.getIntegration();
        return module.getId().contains(query)
                || module.getName().getString().toLowerCase(Locale.ROOT).contains(query)
                || module.getDescription().getString().toLowerCase(Locale.ROOT).contains(query)
                || (integration != null && integration.getName().toLowerCase(Locale.ROOT).contains(query));
    }

    private UIComponent moduleCard(ClientModule module, int cardWidth) {
        var card = UIContainers.verticalFlow(Sizing.fixed(cardWidth), Sizing.fixed(ModMenuScreen.CARD_HEIGHT))
                .gap(4);
        card.padding(Insets.of(8));
        card.margins(Insets.of(ModMenuScreen.CARD_MARGIN));
        card.surface(Surface.flat(UiStyles.CARD_BACKGROUND).and(Surface.outline(UiStyles.CARD_OUTLINE)));

        var icon = UIComponents.texture(module.getIcon(), 0, 0, 128, 128, 128, 128);
        icon.horizontalSizing(Sizing.fixed(ModMenuScreen.ICON_SIZE));
        icon.verticalSizing(Sizing.fixed(ModMenuScreen.ICON_SIZE));
        icon.blend(true);

        var info = UIContainers.verticalFlow(Sizing.expand(100), Sizing.content()).gap(1);
        var textWidth = Math.max(1, cardWidth - 16 - ModMenuScreen.ICON_SIZE - 6);

        var name = UIComponents.label(this.cardText(module.getName(), textWidth, ModMenuScreen.MAX_NAME_LINES))
                .shadow(true)
                .lineSpacing(0);
        name.horizontalSizing(Sizing.fill(100));

        var integration = module.getIntegration();
        var descriptionLines = integration == null
                ? ModMenuScreen.MAX_DESCRIPTION_LINES
                : ModMenuScreen.MAX_DESCRIPTION_LINES - 1;
        var description = UIComponents.label(this.cardText(module.getDescription(), textWidth, descriptionLines))
                .color(Color.ofArgb(UiStyles.MUTED_TEXT))
                .lineSpacing(0)
                .maxWidth(textWidth);
        description.horizontalSizing(Sizing.fill(100));

        info.child(name);
        info.child(description);

        if (integration != null) {
            var badge = UIComponents.label(this.cardText(Component.literal(integration.getName()), textWidth, 1))
                    .color(Color.ofArgb(UiStyles.SERVER_ACCENT))
                    .lineSpacing(0)
                    .maxWidth(textWidth);
            badge.horizontalSizing(Sizing.fill(100));
            info.child(badge);
        }

        var top = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                .gap(6)
                .child(icon)
                .child(info);
        top.verticalAlignment(VerticalAlignment.TOP);

        card.child(top);
        card.child(UIComponents.spacer());

        var actions = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content()).gap(4);
        var actionWidth = Math.max(1, (cardWidth - 16 - 4) / 2);

        var enabled = ModuleWidgets.enabledButton(module, () -> this.uiAdapter.rootComponent.queue(this::rebuildModules));
        enabled.horizontalSizing(Sizing.fixed(actionWidth));
        actions.child(enabled);

        var configure = UIComponents.button(Component.translatable("cake.menu.configure"), button -> this.minecraft.gui.setScreen(new ModuleDetailsScreen(module)));
        configure.renderer(UiStyles.SECONDARY_BUTTON);
        configure.textShadow(false);
        configure.horizontalSizing(Sizing.fixed(actionWidth));
        actions.child(configure);

        card.child(actions);
        return card;
    }

    private Component cardText(Component text, int maxWidth, int maxLines) {
        var font = this.menuFont;

        if (font.split(text, maxWidth).size() <= maxLines) {
            return text;
        }

        var plain = text.getString();

        for (var end = plain.length() - 1; end > 0; end--) {
            var truncated = Component.literal(plain.substring(0, end).stripTrailing() + "...");

            if (font.split(truncated, maxWidth).size() <= maxLines) {
                return truncated;
            }
        }

        return Component.literal("...");
    }
}
