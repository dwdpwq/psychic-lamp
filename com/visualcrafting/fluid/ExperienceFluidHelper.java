package com.visualcrafting.fluid;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.MEStorage;

public class ExperienceFluidHelper {
    public static boolean insertExpFluidToNetwork(IGrid grid, IActionSource source, int amount) {
        if (grid == null || amount <= 0) {
            return false;
        }
        try {
            IStorageService storageService = grid.getStorageService();
            MEStorage storage = storageService.getInventory();
            if (storage == null) {
                return false;
            }
            AEFluidKey fluidKey = AEFluidKey.of(ExperienceFluidFinder.getTargetFluid());
            if (fluidKey == null) {
                return false;
            }
            long inserted = storage.insert(fluidKey, amount, Actionable.MODULATE, source);
            return inserted > 0L;
        } catch (Exception ignored) {
            return false;
        }
    }
}
