/*
 *
 * MIT License
 *
 * Copyright (c) 2020 TerraForged
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.terraforged.app.biome;

import com.terraforged.core.cell.Cell;
import com.terraforged.core.util.grid.FixedGrid;
import com.terraforged.core.world.biome.BiomeData;
import com.terraforged.core.world.biome.BiomeType;
import com.terraforged.core.world.terrain.Terrain;
import processing.data.JSONArray;
import processing.data.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class BiomeProvider {

    private final List<FixedGrid<BiomeData>> biomes;

    public BiomeProvider() {
        biomes = getBiomes(5);
    }

    public BiomeData getBiome(Cell<Terrain> cell) {
        FixedGrid<BiomeData> grid = biomes.get(cell.biomeType.ordinal());
        if (grid == null) {
            return null;
        }
        return grid.get(cell.moisture, cell.temperature, cell.biome);
    }

    private static List<FixedGrid<BiomeData>> getBiomes(int gridSize) {
        List<FixedGrid<BiomeData>> data = new ArrayList<>();
        for (BiomeType type : BiomeType.values()) {
            data.add(type.ordinal(), null);
        }

        Map<String, BiomeData> biomes = loadBiomes();
        Map<BiomeType, List<String>> types = loadBiomeTypes();
        for (Map.Entry<BiomeType, List<String>> e : types.entrySet()) {
            List<BiomeData> list = new LinkedList<>();
            for (String id : e.getValue()) {
                BiomeData biome = biomes.get(id);
                if (biome != null) {
                    list.add(biome);
                }
            }
            FixedGrid<BiomeData> grid = FixedGrid.generate(gridSize, list, b -> b.rainfall, b -> b.temperature);
            data.set(e.getKey().ordinal(), grid);
        }

        return data;
    }

    private static Map<String, BiomeData> loadBiomes() {
        try (InputStream inputStream = BiomeProvider.class.getResourceAsStream("/biome_data.json")) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            reader.lines().forEach(sb::append);
            JSONArray array = JSONArray.parse(sb.toString());
            Map<String,BiomeData> biomes = new HashMap<>();
            for (int i = 0; i < array.size(); i++) {
                JSONObject object = array.getJSONObject(i);
                String name = object.getString("id");
                float moisture = object.getFloat("moisture");
                float temperature = object.getFloat("temperature");
                int color = BiomeColor.getRGB(temperature, moisture);
                BiomeData biome = new BiomeData(name, null, color, moisture, temperature);
                biomes.put(name, biome);
            }
            return biomes;
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    private static Map<BiomeType, List<String>> loadBiomeTypes() {
        try (InputStream inputStream = BiomeProvider.class.getResourceAsStream("/biome_groups.json")) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            reader.lines().forEach(sb::append);
            JSONObject object = JSONObject.parse(sb.toString());
            Iterator iterator = object.keyIterator();
            Map<BiomeType, List<String>> biomes = new HashMap<>();
            while (iterator.hasNext()) {
                String key = "" + iterator.next();
                if (key.contains("rivers")) {
                    continue;
                }
                if (key.contains("oceans")) {
                    continue;
                }
                BiomeType type = BiomeType.valueOf(key);
                List<String> group = new LinkedList<>();
                JSONArray array = object.getJSONArray(key);
                for (int i = 0; i < array.size(); i++) {
                    group.add(array.getString(i));
                }
                biomes.put(type, group);
            }
            return biomes;
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }
}
