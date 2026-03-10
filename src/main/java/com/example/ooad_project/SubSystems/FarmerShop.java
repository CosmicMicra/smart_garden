package com.example.ooad_project.SubSystems;

import com.example.ooad_project.GardenLog;
import com.example.ooad_project.Plant.Children.Flower;
import com.example.ooad_project.Plant.Children.Tree;
import com.example.ooad_project.Plant.Plant;
import com.example.ooad_project.ThreadUtils.EventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Farmer's shop: harvested plants are stored here.
 * When 10 items accumulate, they auto-sell and money is added.
 */
public class FarmerShop {
    private static FarmerShop instance;
    private static final Logger logger = LogManager.getLogger("FarmerShopLogger");

    private static final int AUTO_SELL_THRESHOLD = 10;

    private static final Map<String, Integer> PLANT_VALUES = Map.ofEntries(
            Map.entry("Oak", 12), Map.entry("Maple", 11), Map.entry("Pine", 10),
            Map.entry("Rose", 8), Map.entry("Lily", 7), Map.entry("Tulip", 6),
            Map.entry("Spinach", 3), Map.entry("Zucchini", 3), Map.entry("Carrot", 2)
    );

    private final List<HarvestedItem> inventory = new ArrayList<>();
    private final AtomicInteger totalMoney = new AtomicInteger(0);

    private FarmerShop() {}

    public static synchronized FarmerShop getInstance() {
        if (instance == null) instance = new FarmerShop();
        return instance;
    }

    public static int getValueForPlant(Plant plant) {
        Integer v = PLANT_VALUES.get(plant.getName());
        if (v != null) return v;
        if (plant instanceof Tree) return 10;
        if (plant instanceof Flower) return 6;
        return 2;
    }

    /** Harvest a fully grown plant. Returns the item value. */
    public synchronized int harvest(Plant plant) {
        int value = getValueForPlant(plant);
        inventory.add(new HarvestedItem(plant.getName(), value));
        logger.info("Harvested {} (${}) — shop now has {} items", plant.getName(), value, inventory.size());
        GardenLog.log(GardenLog.Category.PLANT, "Harvested %s ($%d) — shop: %d items", plant.getName(), value, inventory.size());

        EventBus.publish("HarvestEvent", plant);

        if (inventory.size() >= AUTO_SELL_THRESHOLD) {
            sellAll();
        }
        return value;
    }

    private void sellAll() {
        int saleTotal = 0;
        for (HarvestedItem item : inventory) saleTotal += item.value;
        totalMoney.addAndGet(saleTotal);
        logger.info("Auto-sold {} items for ${} — total money: ${}", inventory.size(), saleTotal, totalMoney.get());
        GardenLog.log(GardenLog.Category.RANDOM_EVENT, "Farmer sold %d items for $%d — total: $%d", inventory.size(), saleTotal, totalMoney.get());
        inventory.clear();
        EventBus.publish("ShopSaleEvent", saleTotal);
    }

    public void deductMoney(int amount) { totalMoney.addAndGet(-amount); }
    public int getTotalMoney() { return totalMoney.get(); }
    public synchronized int getInventoryCount() { return inventory.size(); }
    public synchronized List<HarvestedItem> getInventory() { return new ArrayList<>(inventory); }

    public static class HarvestedItem {
        public final String name;
        public final int value;
        public HarvestedItem(String name, int value) { this.name = name; this.value = value; }
    }
}
