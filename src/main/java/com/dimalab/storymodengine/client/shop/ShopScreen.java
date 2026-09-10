package com.dimalab.storymodengine.client.shop;

import com.dimalab.storymodengine.common.network.Network;
import com.dimalab.storymodengine.common.shop.network.ShopBuyPacket;
import com.dimalab.storymodengine.common.shop.network.ShopEntrySnapshot;
import com.dimalab.storymodengine.common.shop.network.ShopOpenPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * A basic vanilla-styled shop: a scrollable-if-needed list of items on the left (icon, name, a quick
 * "Buy 1" button each), a detail panel on the right for the currently selected item (bigger icon,
 * name, a {@code -}/{@code +} quantity stepper, unit price, computed total, a big Buy button), and
 * the player's current balance at the bottom. Standard vanilla widgets only ({@link Button}, {@link
 * GuiGraphics#renderItem}, plain translucent {@link GuiGraphics#fill} panels) — no custom textures,
 * no story-configurable styling ({@code dialogue.DialogueStyle}) — this is deliberately a different,
 * plainer visual register from the story-facing dialogue screens.
 *
 * <p>Stateless server-side (see {@code ShopSystem}'s own doc) — this screen just reflects whatever
 * the latest {@link ShopOpenPacket} said; a buy sends a request and waits for the next packet to
 * actually update anything, rather than predicting the result locally.
 */
public final class ShopScreen extends Screen {

    private static final int PANEL_WIDTH = 280;
    private static final int LEFT_WIDTH = 140;
    private static final int RIGHT_WIDTH = 140;
    private static final int LIST_HEIGHT = 140;
    private static final int ROW_HEIGHT = 22;
    private static final int BOTTOM_HEIGHT = 24;
    private static final int MAX_QUANTITY = 64;
    private static final int COLOR_PANEL = 0x90000000;
    private static final int COLOR_BORDER = 0xFF3F3F3F;
    private static final int COLOR_SELECTED = 0x8055AA55;
    private static final int COLOR_ERROR = 0xFFFF5555;

    private String shopId;
    private String currencyItemId;
    private List<ShopEntrySnapshot> entries;
    private int balance;
    private int selectedIndex;
    private int quantity = 1;

    public ShopScreen(ShopOpenPacket packet) {
        super(Component.literal("Shop"));
        this.shopId = packet.shopId();
        this.currencyItemId = packet.currencyItemId();
        this.entries = packet.entries();
        this.balance = packet.balance();
    }

    public boolean matches(String otherShopId) {
        return shopId.equals(otherShopId);
    }

    /** Called by {@link ClientShopPlayer} when a new {@link ShopOpenPacket} arrives for this same shop — updates in place rather than reopening. */
    public void update(ShopOpenPacket packet) {
        this.currencyItemId = packet.currencyItemId();
        this.entries = packet.entries();
        this.balance = packet.balance();
        if (selectedIndex >= entries.size()) {
            selectedIndex = Math.max(0, entries.size() - 1);
        }
        rebuildShopWidgets();
    }

    @Override
    protected void init() {
        rebuildShopWidgets();
    }

    private void rebuildShopWidgets() {
        clearWidgets();
        if (entries.isEmpty()) {
            return;
        }

        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - (LIST_HEIGHT + BOTTOM_HEIGHT)) / 2;

        // Left panel: one quick-buy button per row.
        for (int i = 0; i < entries.size() && i * ROW_HEIGHT < LIST_HEIGHT; i++) {
            int rowY = panelY + i * ROW_HEIGHT;
            int index = i;
            addRenderableWidget(Button.builder(Component.literal("Купить"), b -> buy(index, 1))
                    .bounds(panelX + LEFT_WIDTH - 54, rowY + 2, 50, ROW_HEIGHT - 4)
                    .build());
        }

        // Right panel: quantity stepper + big buy button for the current selection.
        int rightX = panelX + LEFT_WIDTH + 4;
        int stepperY = panelY + 60;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> adjustQuantity(-1))
                .bounds(rightX, stepperY, 20, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> adjustQuantity(1))
                .bounds(rightX + RIGHT_WIDTH - 20, stepperY, 20, 20)
                .build());

        ShopEntrySnapshot selected = entries.get(selectedIndex);
        int totalCost = selected.price() * quantity;
        Button buyButton = Button.builder(Component.literal("Купить"), b -> buy(selectedIndex, quantity))
                .bounds(rightX, panelY + LIST_HEIGHT - 24, RIGHT_WIDTH, 20)
                .build();
        buyButton.active = balance >= totalCost;
        addRenderableWidget(buyButton);
    }

    private void adjustQuantity(int delta) {
        quantity = Math.max(1, Math.min(MAX_QUANTITY, quantity + delta));
        rebuildShopWidgets();
    }

    private void buy(int entryIndex, int amount) {
        Network.sendToServer(new ShopBuyPacket(shopId, entryIndex, amount));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        if (entries.isEmpty()) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - (LIST_HEIGHT + BOTTOM_HEIGHT)) / 2;

        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + LIST_HEIGHT + BOTTOM_HEIGHT, COLOR_PANEL);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, COLOR_BORDER);
        graphics.fill(panelX + LEFT_WIDTH, panelY, panelX + LEFT_WIDTH + 1, panelY + LIST_HEIGHT, COLOR_BORDER);

        renderItemList(graphics, panelX, panelY);
        renderDetailPanel(graphics, panelX + LEFT_WIDTH + 4, panelY);
        renderBalance(graphics, panelX, panelY + LIST_HEIGHT);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderItemList(GuiGraphics graphics, int panelX, int panelY) {
        for (int i = 0; i < entries.size() && i * ROW_HEIGHT < LIST_HEIGHT; i++) {
            int rowY = panelY + i * ROW_HEIGHT;
            if (i == selectedIndex) {
                graphics.fill(panelX, rowY, panelX + LEFT_WIDTH, rowY + ROW_HEIGHT, COLOR_SELECTED);
            }
            ItemStack stack = stackFor(entries.get(i).itemId(), 1);
            graphics.renderItem(stack, panelX + 4, rowY + 3);
            graphics.renderItemDecorations(font, stack, panelX + 4, rowY + 3);
            graphics.drawString(font, stack.getHoverName(), panelX + 24, rowY + 7, 0xFFFFFF, false);
        }
    }

    private void renderDetailPanel(GuiGraphics graphics, int x, int panelY) {
        ShopEntrySnapshot selected = entries.get(selectedIndex);
        ItemStack stack = stackFor(selected.itemId(), selected.count());

        graphics.renderItem(stack, x, panelY + 4);
        graphics.renderItemDecorations(font, stack, x, panelY + 4);
        graphics.drawString(font, stack.getHoverName(), x + 20, panelY + 8, 0xFFFFFF, false);

        graphics.drawString(font, "Цена за шт.: " + selected.price(), x, panelY + 28, 0xAAAAAA, false);

        int totalCost = selected.price() * quantity;
        graphics.drawCenteredString(font, String.valueOf(quantity), x + RIGHT_WIDTH / 2, panelY + 66, 0xFFFFFF);
        graphics.drawString(font, "Итого: " + totalCost, x, panelY + 86, 0xFFFFFF, false);

        if (balance < totalCost) {
            graphics.drawString(font, "Недостаточно средств", x, panelY + LIST_HEIGHT - 36, COLOR_ERROR, false);
        }
    }

    private void renderBalance(GuiGraphics graphics, int panelX, int barY) {
        ItemStack currencyStack = stackFor(currencyItemId, 1);
        graphics.renderItem(currencyStack, panelX + 4, barY + (BOTTOM_HEIGHT - 16) / 2);
        graphics.drawString(font, "Баланс: " + balance, panelX + 24, barY + (BOTTOM_HEIGHT - font.lineHeight) / 2, 0xFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (entries.isEmpty()) {
            return false;
        }
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - (LIST_HEIGHT + BOTTOM_HEIGHT)) / 2;
        if (mouseX >= panelX && mouseX < panelX + LEFT_WIDTH - 56 && mouseY >= panelY && mouseY < panelY + LIST_HEIGHT) {
            int row = (int) ((mouseY - panelY) / ROW_HEIGHT);
            if (row >= 0 && row < entries.size()) {
                selectedIndex = row;
                quantity = 1;
                rebuildShopWidgets();
                return true;
            }
        }
        return false;
    }

    private static ItemStack stackFor(String itemId, int count) {
        Item item = ForgeRegistries.ITEMS.getValue(id(itemId));
        return item == null ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, count));
    }

    private static ResourceLocation id(String raw) {
        return raw.contains(":") ? new ResourceLocation(raw) : new ResourceLocation("minecraft", raw);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
