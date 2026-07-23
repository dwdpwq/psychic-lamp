package com.visualcrafting.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DimensionBiomesData {
    public List<String> dimIds = new ArrayList<>();
    public Map<String, List<String>> biomesByDim = new LinkedHashMap<>();
    public List<String> allBiomes = new ArrayList<>();

    public DimensionBiomesData() {
    }

    public DimensionBiomesData(List<String> dimIds, Map<String, List<String>> biomesByDim, List<String> allBiomes) {
        this.dimIds = dimIds;
        this.biomesByDim = biomesByDim;
        this.allBiomes = allBiomes;
    }

    public String toJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject root = new JsonObject();

        JsonArray dimArray = new JsonArray();
        for (String id : dimIds) {
            dimArray.add(id);
        }
        root.add("dimIds", dimArray);

        JsonObject biomesObj = new JsonObject();
        for (Map.Entry<String, List<String>> entry : biomesByDim.entrySet()) {
            JsonArray biomeArray = new JsonArray();
            for (String biome : entry.getValue()) {
                biomeArray.add(biome);
            }
            biomesObj.add(entry.getKey(), biomeArray);
        }
        root.add("biomesByDim", biomesObj);

        JsonArray allBiomesArray = new JsonArray();
        for (String biome : allBiomes) {
            allBiomesArray.add(biome);
        }
        root.add("allBiomes", allBiomesArray);

        return gson.toJson(root);
    }

    public static DimensionBiomesData fromJson(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        DimensionBiomesData data = new DimensionBiomesData();

        for (JsonElement elem : root.getAsJsonArray("dimIds")) {
            data.dimIds.add(elem.getAsString());
        }

        JsonObject biomesObj = root.getAsJsonObject("biomesByDim");
        for (Map.Entry<String, JsonElement> entry : biomesObj.entrySet()) {
            List<String> biomes = new ArrayList<>();
            for (JsonElement elem : entry.getValue().getAsJsonArray()) {
                biomes.add(elem.getAsString());
            }
            data.biomesByDim.put(entry.getKey(), biomes);
        }

        for (JsonElement elem : root.getAsJsonArray("allBiomes")) {
            data.allBiomes.add(elem.getAsString());
        }

        return data;
    }
}
