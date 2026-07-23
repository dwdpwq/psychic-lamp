package com.visualcrafting.recipe;

import com.visualcrafting.block.VisualCraftingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RecipeRegistrar {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeRegistrar.class);

    private static final Map<BlockPos, List<VisualCraftingBlockEntity.SavedRecipe>> ALL_TABLE_RECIPES =
            new ConcurrentHashMap<>();
    private static final Map<BlockPos, Integer> TABLE_FORMATS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, List<VisualCraftingBlockEntity.InfusingRecipe>> ALL_INFUSING_TABLE_RECIPES =
            new ConcurrentHashMap<>();
    private static final Map<BlockPos, Integer> INFUSING_TABLE_FORMATS = new ConcurrentHashMap<>();

    private static final Path KUBEJS_OUTPUTS = Path.of("kubejs/server_scripts/visualcrafting_outputs.txt");
    private static final Path KUBEJS_BANNED = Path.of("kubejs/server_scripts/visualcrafting_banned.txt");
    private static final Path CRT_OUTPUTS = Path.of("scripts/visualcrafting_outputs.txt");
    private static final Path CRT_BANNED = Path.of("scripts/visualcrafting_banned.txt");
    private static final Path KUBEJS_INFUSING_OUTPUTS =
            Path.of("kubejs/server_scripts/visualcrafting_infusing_outputs.txt");
    private static final Path KUBEJS_INFUSING_BANNED =
            Path.of("kubejs/server_scripts/visualcrafting_infusing_banned.txt");
    private static final Path CRT_INFUSING_OUTPUTS = Path.of("scripts/visualcrafting_infusing_outputs.txt");
    private static final Path CRT_INFUSING_BANNED = Path.of("scripts/visualcrafting_infusing_banned.txt");

    // ---- Public update methods ----

    public static void updateTableRecipes(BlockPos pos, List<VisualCraftingBlockEntity.SavedRecipe> recipes,
                                          int format) {
        ALL_TABLE_RECIPES.put(pos, new ArrayList<>(recipes));
        TABLE_FORMATS.put(pos, format);
    }

    public static void updateInfusingTableRecipes(BlockPos pos,
                                                  List<VisualCraftingBlockEntity.InfusingRecipe> recipes, int format) {
        ALL_INFUSING_TABLE_RECIPES.put(pos, new ArrayList<>(recipes));
        INFUSING_TABLE_FORMATS.put(pos, format);
    }

    // ---- Collect all recipes for given format ----

    private static List<VisualCraftingBlockEntity.SavedRecipe> collectAllRecipes(int format) {
        List<VisualCraftingBlockEntity.SavedRecipe> all = new ArrayList<>();
        for (Map.Entry<BlockPos, List<VisualCraftingBlockEntity.SavedRecipe>> entry : ALL_TABLE_RECIPES.entrySet()) {
            Integer fmt = TABLE_FORMATS.get(entry.getKey());
            if (fmt != null && fmt == format) {
                all.addAll(entry.getValue());
            }
        }
        return all;
    }

    private static List<VisualCraftingBlockEntity.InfusingRecipe> collectAllInfusingRecipes(int format) {
        List<VisualCraftingBlockEntity.InfusingRecipe> all = new ArrayList<>();
        for (Map.Entry<BlockPos, List<VisualCraftingBlockEntity.InfusingRecipe>> entry :
                ALL_INFUSING_TABLE_RECIPES.entrySet()) {
            Integer fmt = INFUSING_TABLE_FORMATS.get(entry.getKey());
            if (fmt != null && fmt == format) {
                all.addAll(entry.getValue());
            }
        }
        return all;
    }

    // ---- Ban / unban ----

    public static void banOutput(String outputId, List<VisualCraftingBlockEntity.SavedRecipe> recipes,
                                 int tier, int format) {
        Path path = format == 1 ? CRT_BANNED : KUBEJS_BANNED;
        Set<String> banned = loadSet(path);
        if (banned.add(outputId)) {
            saveSet(path, banned);
        }
        regenerateScript(recipes, tier, format);
    }

    public static void banInfusingOutput(String outputId,
                                         List<VisualCraftingBlockEntity.InfusingRecipe> recipes, int format) {
        Path path = format == 1 ? CRT_INFUSING_BANNED : KUBEJS_INFUSING_BANNED;
        Set<String> banned = loadSet(path);
        if (banned.add(outputId)) {
            saveSet(path, banned);
        }
        regenerateInfusingScript(recipes, format);
    }

    public static void unbanOutput(String outputId, int format) {
        Path path = format == 1 ? CRT_BANNED : KUBEJS_BANNED;
        Set<String> banned = loadSet(path);
        if (banned.remove(outputId)) {
            saveSet(path, banned);
        }
    }

    public static void unbanInfusingOutput(String outputId, int format) {
        Path path = format == 1 ? CRT_INFUSING_BANNED : KUBEJS_INFUSING_BANNED;
        Set<String> banned = loadSet(path);
        if (banned.remove(outputId)) {
            saveSet(path, banned);
        }
    }

    // ---- Script regeneration: crafting table ----

    public static void regenerateScript(List<VisualCraftingBlockEntity.SavedRecipe> callerRecipes,
                                        int tier, int format) {
        LOGGER.info("[VisualCrafting] regenerateScript: format={} ({}), tier={}, callerRecipes={}",
                format, format == 0 ? "KubeJS" : "CRT", tier, callerRecipes.size());

        List<VisualCraftingBlockEntity.SavedRecipe> allRecipes = collectAllRecipes(format);
        if (allRecipes.isEmpty()) {
            allRecipes = callerRecipes;
            LOGGER.info("[VisualCrafting] ALL_TABLE_RECIPES empty — falling back to caller list ({} recipes)",
                    callerRecipes.size());
        }
        LOGGER.info("[VisualCrafting] Total allRecipes (after fallback): {}", allRecipes.size());

        Path bannedPath = format == 1 ? CRT_BANNED : KUBEJS_BANNED;
        Path outputsPath = format == 1 ? CRT_OUTPUTS : KUBEJS_OUTPUTS;
        Set<String> banned = loadSet(bannedPath);

        // Group recipes by namespace
        LinkedHashMap<String, List<VisualCraftingBlockEntity.SavedRecipe>> byNamespace = new LinkedHashMap<>();
        LinkedHashSet<String> allOutputIds = new LinkedHashSet<>();
        for (VisualCraftingBlockEntity.SavedRecipe recipe : allRecipes) {
            String id = BuiltInRegistries.ITEM.getKey(recipe.result.getItem()).toString();
            allOutputIds.add(id);
            String ns = id.split(":")[0];
            byNamespace.computeIfAbsent(ns, k -> new ArrayList<>()).add(recipe);
        }
        saveSet(outputsPath, allOutputIds);

        // Group banned by namespace
        LinkedHashMap<String, List<String>> bannedByNs = new LinkedHashMap<>();
        for (String bannedId : banned) {
            String ns = bannedId.split(":")[0];
            bannedByNs.computeIfAbsent(ns, k -> new ArrayList<>()).add(bannedId);
        }

        LOGGER.info("[VisualCrafting] byNamespace: {} namespaces ({}), bannedByNamespace: {} namespaces ({}), banned={}",
                byNamespace.size(), byNamespace.keySet(), bannedByNs.size(), bannedByNs.keySet(), banned);

        LinkedHashSet<String> allNamespaces = new LinkedHashSet<>(byNamespace.keySet());
        allNamespaces.addAll(bannedByNs.keySet());

        String ext = format == 1 ? ".zs" : ".js";
        String dirPrefix = format == 1 ? "scripts/" : "kubejs/server_scripts/";
        boolean isCRT = format == 1;

        for (String namespace : allNamespaces) {
            List<VisualCraftingBlockEntity.SavedRecipe> nsRecipes =
                    byNamespace.getOrDefault(namespace, Collections.emptyList());
            List<String> nsBanned = bannedByNs.getOrDefault(namespace, Collections.emptyList());

            boolean has3x3 = false;
            for (VisualCraftingBlockEntity.SavedRecipe r : nsRecipes) {
                int side = (int) Math.sqrt(r.ingredients.size());
                if (side * side == r.ingredients.size() && side == 3) {
                    has3x3 = true;
                    break;
                }
            }

            if (!has3x3 && nsBanned.isEmpty()) continue;

            String fileName = "visualcrafting_" + namespace + ext;
            Path outputPath = Path.of(dirPrefix + fileName);
            StringBuilder sb = new StringBuilder();

            if (isCRT) {
                sb.append("// VisualCrafting auto-generated - ").append(namespace).append("\n");
                sb.append("// /reload to apply\n\n");
            } else {
                sb.append("ServerEvents.recipes(event => {\n");
            }

            if (isCRT && !nsBanned.isEmpty()) {
                sb.append("// Banned recipes\n");
            }
            for (String bannedId : nsBanned) {
                String name = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(bannedId)))
                        .getHoverName().getString();
                if (isCRT) {
                    sb.append("craftingTable.remove(<item:").append(bannedId).append(">);//删除\"")
                            .append(name).append("\"配方\n");
                } else {
                    sb.append("  event.remove({ output: '").append(bannedId).append("' });//删除\"")
                            .append(name).append("\"配方\n");
                }
            }

            // 3x3 recipes
            List<VisualCraftingBlockEntity.SavedRecipe> grid3x3 = new ArrayList<>();
            for (VisualCraftingBlockEntity.SavedRecipe r : nsRecipes) {
                int side = (int) Math.sqrt(r.ingredients.size());
                if (side * side == r.ingredients.size() && side == 3) {
                    grid3x3.add(r);
                }
            }

            if (isCRT && !grid3x3.isEmpty()) {
                sb.append("\n// Recipes\n");
            }
            Map<String, Integer> nameCounter = new HashMap<>();
            for (VisualCraftingBlockEntity.SavedRecipe r : grid3x3) {
                String outputId = BuiltInRegistries.ITEM.getKey(r.result.getItem()).toString();
                int count = r.result.getCount();

                if (isCRT) {
                    String pathName = ResourceLocation.parse(outputId).getPath();
                    int idx = nameCounter.getOrDefault(pathName, 0);
                    nameCounter.put(pathName, idx + 1);
                    String recipeName = idx == 0 ? pathName : pathName + "_" + idx;

                    if (r.shaped) {
                        generateShapedCRT(sb, r, outputId, count, recipeName);
                    } else {
                        generateShapelessCRT(sb, r, outputId, count, recipeName);
                    }
                } else {
                    if (r.shaped) {
                        generateShaped(sb, r, outputId, count);
                    } else {
                        generateShapeless(sb, r, outputId, count);
                    }
                }
                sb.append("\n");
            }

            if (!isCRT) {
                sb.append("});\n");
            }

            try {
                Files.createDirectories(outputPath.getParent());
                Files.writeString(outputPath, sb.toString());
                LOGGER.info("[VisualCrafting] Wrote {} ({} chars) → {}", fileName, sb.length(),
                        outputPath.toAbsolutePath());
            } catch (IOException e) {
                LOGGER.error("[VisualCrafting] Write failed: {} — {}", outputPath.toAbsolutePath(), e.getMessage());
            }
        }

        // Extended crafting (4x4+)
        String extCraftFile = "visualcrafting~Extended_Crafting" + ext;
        Path extCraftPath = Path.of(dirPrefix + extCraftFile);
        StringBuilder extSb = new StringBuilder();

        if (isCRT) {
            extSb.append("// VisualCrafting auto-generated - Extended Crafting\n");
            extSb.append("// /reload to apply\n\n");
            extSb.append("// Recipes\n");
        } else {
            extSb.append("ServerEvents.recipes(event => {\n");
        }

        List<VisualCraftingBlockEntity.SavedRecipe> extended = new ArrayList<>();
        for (String ns : allNamespaces) {
            List<VisualCraftingBlockEntity.SavedRecipe> nsRecipes =
                    byNamespace.getOrDefault(ns, Collections.emptyList());
            for (VisualCraftingBlockEntity.SavedRecipe r : nsRecipes) {
                int side = (int) Math.sqrt(r.ingredients.size());
                if (side * side == r.ingredients.size() && side > 3) {
                    extended.add(r);
                }
            }
        }

        Map<String, Integer> extNameCounter = new HashMap<>();
        for (VisualCraftingBlockEntity.SavedRecipe r : extended) {
            String outputId = BuiltInRegistries.ITEM.getKey(r.result.getItem()).toString();
            int count = r.result.getCount();

            if (isCRT) {
                String pathName = ResourceLocation.parse(outputId).getPath();
                int idx = extNameCounter.getOrDefault(pathName, 0);
                extNameCounter.put(pathName, idx + 1);
                String recipeName = idx == 0 ? pathName : pathName + "_" + idx;

                if (r.shaped) {
                    generateShapedCRT(extSb, r, outputId, count, recipeName);
                } else {
                    generateShapelessCRT(extSb, r, outputId, count, recipeName);
                }
            } else {
                if (r.shaped) {
                    generateShaped(extSb, r, outputId, count);
                } else {
                    generateShapeless(extSb, r, outputId, count);
                }
            }
            extSb.append("\n");
        }

        if (!isCRT) {
            extSb.append("});\n");
        }

        try {
            Files.createDirectories(extCraftPath.getParent());
            Files.writeString(extCraftPath, extSb.toString());
            LOGGER.info("[VisualCrafting] Wrote {} ({} chars) → {}", extCraftFile, extSb.length(),
                    extCraftPath.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("[VisualCrafting] Write failed: {} — {}", extCraftPath.toAbsolutePath(), e.getMessage());
        }
    }

    // ---- Script regeneration: metallurgic infusing ----

    public static void regenerateInfusingScript(List<VisualCraftingBlockEntity.InfusingRecipe> callerRecipes,
                                                int format) {
        LOGGER.info("[VisualCrafting] regenerateInfusingScript: format={} ({}), callerRecipes={}",
                format, format == 0 ? "KubeJS" : "CRT", callerRecipes.size());

        List<VisualCraftingBlockEntity.InfusingRecipe> allRecipes = collectAllInfusingRecipes(format);
        if (allRecipes.isEmpty()) {
            allRecipes = callerRecipes;
            LOGGER.info("[VisualCrafting] ALL_INFUSING_TABLE_RECIPES empty — falling back to caller list ({} recipes)",
                    callerRecipes.size());
        }
        LOGGER.info("[VisualCrafting] Total infusing allRecipes: {}", allRecipes.size());

        Path bannedPath = format == 1 ? CRT_INFUSING_BANNED : KUBEJS_INFUSING_BANNED;
        Path outputsPath = format == 1 ? CRT_INFUSING_OUTPUTS : KUBEJS_INFUSING_OUTPUTS;
        Set<String> banned = loadSet(bannedPath);

        String ext = format == 1 ? ".zs" : ".js";
        String dirPrefix = format == 1 ? "scripts/" : "kubejs/server_scripts/";
        String fileName = "visualcrafting_metallurgic_infusing" + ext;
        Path outputPath = Path.of(dirPrefix + fileName);
        boolean isCRT = format == 1;

        StringBuilder sb = new StringBuilder();
        if (isCRT) {
            sb.append("// VisualCrafting auto-generated - Metallurgic Infusing\n");
            sb.append("// /reload to apply\n\n");
        } else {
            sb.append("ServerEvents.recipes(event => {\n");
        }

        if (!banned.isEmpty()) {
            if (isCRT) {
                sb.append("// Banned infusing recipes\n");
            }
            for (String bannedId : banned) {
                String name = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(bannedId)))
                        .getHoverName().getString();
                if (isCRT) {
                    sb.append("// Banned: ").append(bannedId).append(" // \"").append(name).append("\"\n");
                } else {
                    sb.append("  event.remove({ output: '").append(bannedId).append("' });//删除灌注配方\"")
                            .append(name).append("\"\n");
                }
            }
        }

        List<VisualCraftingBlockEntity.InfusingRecipe> active = new ArrayList<>();
        LinkedHashSet<String> outputIds = new LinkedHashSet<>();
        for (VisualCraftingBlockEntity.InfusingRecipe r : allRecipes) {
            if (r.banned) continue;
            String id = BuiltInRegistries.ITEM.getKey(r.output.getItem()).toString();
            outputIds.add(id);
            active.add(r);
        }
        saveSet(outputsPath, outputIds);

        if (!active.isEmpty()) {
            if (isCRT) {
                sb.append("\n// Recipes\n\n");
            }
            for (VisualCraftingBlockEntity.InfusingRecipe r : active) {
                String outputId = BuiltInRegistries.ITEM.getKey(r.output.getItem()).toString();

                // Resolve inputA: may be chemical (has CUSTOM_DATA with chemicalId) or item
                String inputAStr;
                CustomData customData = r.inputA.get(DataComponents.CUSTOM_DATA);
                if (customData != null) {
                    CompoundTag tag = customData.copyTag();
                    if (tag != null && !tag.isEmpty()) {
                        inputAStr = tag.getString("chemicalId");
                    } else {
                        inputAStr = "";
                    }
                } else {
                    inputAStr = "";
                }
                if (inputAStr.isEmpty()) {
                    inputAStr = r.inputA.isEmpty() ? "minecraft:air"
                            : BuiltInRegistries.ITEM.getKey(r.inputA.getItem()).toString();
                }

                String inputBStr = r.inputB.isEmpty() ? "minecraft:air"
                        : BuiltInRegistries.ITEM.getKey(r.inputB.getItem()).toString();

                if (isCRT) {
                    sb.append("mods.mekanism.metallurgic_infusing.recipeBuilder()\n");
                    sb.append("    .itemInput(<item:").append(inputBStr).append(">)\n");
                    sb.append("    .infusionInput(<item:").append(inputAStr).append(">, ")
                            .append(r.infusionAmount).append(")\n");
                    sb.append("    .output(<item:").append(outputId).append(">)\n");
                    sb.append("    .build();//").append(r.output.getHoverName().getString()).append("\n\n");
                } else {
                    sb.append("  event.remove({ output: \"").append(outputId).append("\" });\n");
                    sb.append("  event.custom({\n");
                    sb.append("    type: \"mekanism:metallurgic_infusing\",\n");
                    sb.append("    chemical_input: { amount: ").append(r.infusionAmount);
                    sb.append(", chemical: \"").append(inputAStr).append("\" },\n");
                    sb.append("    item_input: { ingredient: { item: \"").append(inputBStr).append("\" } },\n");
                    sb.append("    output: { id: \"").append(outputId).append("\" }\n");
                    sb.append("  });//").append(r.output.getHoverName().getString()).append("\n");
                }
            }
        }

        if (!isCRT) {
            sb.append("});\n");
        }

        try {
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, sb.toString());
            LOGGER.info("[VisualCrafting] Wrote {} ({} chars) → {}", fileName, sb.length(),
                    outputPath.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("[VisualCrafting] Write failed: {} — {}", outputPath.toAbsolutePath(), e.getMessage());
        }
    }

    // ---- KubeJS shaped/shapeless generators ----

    private static void generateShaped(StringBuilder sb, VisualCraftingBlockEntity.SavedRecipe recipe,
                                       String outputId, int count) {
        int side = (int) Math.sqrt(recipe.ingredients.size());
        if (side * side != recipe.ingredients.size() || side < 3 || side > 9) return;

        int minRow = side, maxRow = -1, minCol = side, maxCol = -1;
        for (int r = 0; r < side; r++) {
            for (int c = 0; c < side; c++) {
                int idx = r * side + c;
                if (idx >= recipe.ingredients.size() || recipe.ingredients.get(idx).isEmpty()) continue;
                minRow = Math.min(minRow, r);
                maxRow = Math.max(maxRow, r);
                minCol = Math.min(minCol, c);
                maxCol = Math.max(maxCol, c);
            }
        }
        if (minRow > maxRow) return;

        LinkedHashMap<String, String> keyMap = new LinkedHashMap<>();
        char nextChar = 'A';

        sb.append("  event.shaped(\n");
        sb.append("    Item.of('").append(outputId).append("'");
        if (count > 1) sb.append(", ").append(count);
        sb.append("),\n");
        sb.append("    [\n");

        for (int r = minRow; r <= maxRow; r++) {
            StringBuilder rowStr = new StringBuilder();
            for (int c = minCol; c <= maxCol; c++) {
                int idx = r * side + c;
                if (idx < recipe.ingredients.size() && !recipe.ingredients.get(idx).isEmpty()) {
                    String itemId = BuiltInRegistries.ITEM.getKey(recipe.ingredients.get(idx).getItem()).toString();
                    String key = null;
                    for (Map.Entry<String, String> e : keyMap.entrySet()) {
                        if (e.getValue().equals(itemId)) {
                            key = e.getKey();
                            break;
                        }
                    }
                    if (key == null) {
                        key = String.valueOf(nextChar++);
                        keyMap.put(key, itemId);
                    }
                    rowStr.append(key);
                } else {
                    rowStr.append(' ');
                }
            }
            sb.append("      '").append(rowStr).append("'");
            if (r < maxRow) sb.append(",");
            sb.append("\n");
        }

        sb.append("    ],\n");
        sb.append("    {\n");
        int ki = 0;
        for (Map.Entry<String, String> e : keyMap.entrySet()) {
            sb.append("      ").append(e.getKey()).append(": '").append(e.getValue()).append("'");
            if (++ki < keyMap.size()) sb.append(",");
            sb.append("\n");
        }
        sb.append("    }\n");
        sb.append("  );//添加有序合成\"").append(recipe.result.getHoverName().getString()).append("\"配方");
    }

    private static void generateShapeless(StringBuilder sb, VisualCraftingBlockEntity.SavedRecipe recipe,
                                          String outputId, int count) {
        sb.append("  event.shapeless(\n");
        sb.append("    Item.of('").append(outputId).append("'");
        if (count > 1) sb.append(", ").append(count);
        sb.append("),\n");
        sb.append("    [\n");

        List<ItemStack> nonEmpty = new ArrayList<>();
        for (ItemStack s : recipe.ingredients) {
            if (!s.isEmpty()) nonEmpty.add(s);
        }
        for (int i = 0; i < nonEmpty.size(); i++) {
            String itemId = BuiltInRegistries.ITEM.getKey(nonEmpty.get(i).getItem()).toString();
            sb.append("      '").append(itemId).append("'");
            if (i < nonEmpty.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("    ]\n");
        sb.append("  );//添加无序合成\"").append(recipe.result.getHoverName().getString()).append("\"配方");
    }

    // ---- CRT shaped/shapeless generators ----

    private static void generateShapedCRT(StringBuilder sb, VisualCraftingBlockEntity.SavedRecipe recipe,
                                          String outputId, int count, String recipeName) {
        int side = (int) Math.sqrt(recipe.ingredients.size());
        if (side * side != recipe.ingredients.size() || side < 3 || side > 9) return;

        boolean hasAny = false;
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                int idx = i * side + j;
                if (idx < recipe.ingredients.size() && !recipe.ingredients.get(idx).isEmpty()) {
                    hasAny = true;
                    break;
                }
            }
            if (hasAny) break;
        }
        if (!hasAny) return;

        boolean extended = side > 3;
        if (extended) {
            int tier = side == 5 ? 2 : (side == 7 ? 3 : 4);
            sb.append("<recipetype:extendedcrafting:table>.addShaped(\"");
            sb.append(recipeName).append("\", ");
            sb.append(tier);
            sb.append(", <item:").append(outputId).append(">");
            if (count > 1) sb.append(" * ").append(count);
            sb.append(", [\n");
        } else {
            sb.append("craftingTable.addShaped(\"");
            sb.append(recipeName).append("\", ");
            sb.append("<item:").append(outputId).append(">");
            if (count > 1) sb.append(" * ").append(count);
            sb.append(", [\n");
        }

        for (int r = 0; r < side; r++) {
            sb.append("  [");
            for (int c = 0; c < side; c++) {
                int idx = r * side + c;
                if (idx < recipe.ingredients.size() && !recipe.ingredients.get(idx).isEmpty()) {
                    String itemId = BuiltInRegistries.ITEM.getKey(recipe.ingredients.get(idx).getItem()).toString();
                    sb.append("<item:").append(itemId).append(">");
                } else {
                    sb.append("<item:minecraft:air>");
                }
                if (c < side - 1) sb.append(", ");
            }
            sb.append("]");
            if (r < side - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]);//添加有序合成\"").append(recipe.result.getHoverName().getString()).append("\"配方");
    }

    private static void generateShapelessCRT(StringBuilder sb, VisualCraftingBlockEntity.SavedRecipe recipe,
                                             String outputId, int count, String recipeName) {
        List<ItemStack> nonEmpty = new ArrayList<>();
        for (ItemStack s : recipe.ingredients) {
            if (!s.isEmpty()) nonEmpty.add(s);
        }

        int side = (int) Math.sqrt(recipe.ingredients.size());
        boolean extended = side > 3;

        if (extended) {
            int tier = side == 5 ? 2 : (side == 7 ? 3 : 4);
            sb.append("<recipetype:extendedcrafting:table>.addShapeless(\"");
            sb.append(recipeName).append("\", ");
            sb.append(tier);
            sb.append(", <item:").append(outputId).append(">");
            if (count > 1) sb.append(" * ").append(count);
            sb.append(", [");
        } else {
            sb.append("craftingTable.addShapeless(\"");
            sb.append(recipeName).append("\", ");
            sb.append("<item:").append(outputId).append(">");
            if (count > 1) sb.append(" * ").append(count);
            sb.append(", [");
        }

        for (int i = 0; i < nonEmpty.size(); i++) {
            String itemId = BuiltInRegistries.ITEM.getKey(nonEmpty.get(i).getItem()).toString();
            sb.append("<item:").append(itemId).append(">");
            if (i < nonEmpty.size() - 1) sb.append(", ");
        }
        sb.append("]);//添加无序合成\"").append(recipe.result.getHoverName().getString()).append("\"配方");
    }

    // ---- File helpers ----

    private static Set<String> loadSet(Path path) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        try {
            if (Files.exists(path)) {
                set.addAll(Files.readAllLines(path));
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to load set file", e);
        }
        return set;
    }

    private static void saveSet(Path path, Set<String> set) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, new ArrayList<>(set));
        } catch (IOException e) {
            System.err.println("[VisualCrafting] 写入文件失败: " + path + " - " + e.getMessage());
        }
    }
}
