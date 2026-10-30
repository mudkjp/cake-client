package dev.mudkip.cake.ui;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import net.minecraft.network.chat.Component;

public abstract class MenuOwoScreen extends BaseOwoScreen<FlowLayout> {
    protected MenuOwoScreen(Component title) {
        super(title);
    }

    @Override
    protected OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
