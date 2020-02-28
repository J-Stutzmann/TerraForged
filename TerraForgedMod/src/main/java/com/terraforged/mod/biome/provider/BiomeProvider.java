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

package com.terraforged.mod.biome.provider;

import com.google.common.collect.Sets;
import com.terraforged.core.cell.Cell;
import com.terraforged.core.region.chunk.ChunkReader;
import com.terraforged.core.util.concurrent.ObjectPool;
import com.terraforged.core.world.terrain.Terrain;
import com.terraforged.mod.biome.map.BiomeMap;
import com.terraforged.mod.biome.modifier.BiomeModifierManager;
import com.terraforged.mod.chunk.TerraContainer;
import com.terraforged.mod.chunk.TerraContext;
import com.terraforged.mod.util.setup.SetupHooks;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.structure.Structure;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class BiomeProvider extends AbstractBiomeProvider {

    private final BiomeMap biomeMap;
    private final TerraContext context;
    private final BiomeModifierManager modifierManager;

    public BiomeProvider(TerraContext context) {
        this.context = context;
        this.biomeMap = BiomeHelper.getDefaultBiomeMap();
        this.modifierManager = SetupHooks.setup(new BiomeModifierManager(context, biomeMap), context.copy());
    }

    public BiomeModifierManager getModifierManager() {
        return modifierManager;
    }

    public TerraContainer createBiomeContainer(ChunkReader chunkReader) {
        TerraContainer.Builder builder = TerraContainer.builder();
        chunkReader.iterate((cell, dx, dz) -> {
            Biome biome = getBiome(cell, chunkReader.getBlockX() + dx, chunkReader.getBlockZ() + dz);
            builder.fill(dx, dz, biome);
        });
        return builder.build(chunkReader);
    }

    @Override
    public Biome getBiome(int x, int y, int z) {
        try (ObjectPool.Item<Cell<Terrain>> item = Cell.pooled()) {
            context.heightmap.apply(item.getValue(), x, z);
            return getBiome(item.getValue(), x, z);
        }
    }

    @Override
    public Set<Biome> getBiomesInSquare(int centerX, int centerY, int centerZ, int sideLength) {
        int minX = centerX - (sideLength >> 2);
        int minZ = centerZ - (sideLength >> 2);
        int maxX = centerX + (sideLength >> 2);
        int maxZ = centerZ + (sideLength >> 2);
        Set<Biome> biomes = Sets.newHashSet();
        context.heightmap.visit(minX, minZ, maxX, maxZ, (cell, x, z) -> {
            Biome biome = getBiome(cell, minX + x, minZ + z);
            biomes.add(biome);
        });
        return biomes;
    }

    @Override
    public BlockPos findBiomePosition(int centerX, int centerY, int centerZ, int range, List<Biome> biomes, Random random) {
        int minX = centerX - (range >> 2);
        int minZ = centerZ - (range >> 2);
        int maxX = centerX + (range >> 2);
        int maxZ = centerZ + (range >> 2);
        Set<Biome> matchBiomes = new HashSet<>(biomes);
        SearchContext search = new SearchContext();
        context.heightmap.visit(minX, minZ, maxX, maxZ, (cell, x, z) -> {
            Biome biome = getBiome(cell, minX + x, minZ + z);
            if (matchBiomes.contains(biome)) {
                if (search.first || random.nextInt(search.count + 1) == 0) {
                    search.first = false;
                    search.pos.setPos(minX + x, 0, minZ + z);
                }
                ++search.count;
            }
        });
        return search.pos;
    }

    @Override
    public boolean hasStructure(Structure<?> structureIn) {
        return this.hasStructureCache.computeIfAbsent(structureIn, (p_205006_1_) -> {
            for (Biome biome : defaultBiomes) {
                if (biome.hasStructure(p_205006_1_)) {
                    return true;
                }
            }
            return false;
        });
    }

    @Override
    public Set<BlockState> getSurfaceBlocks() {
        if (this.topBlocksCache.isEmpty()) {
            for (Biome biome : defaultBiomes) {
                this.topBlocksCache.add(biome.getSurfaceBuilderConfig().getTop());
            }
        }
        return this.topBlocksCache;
    }

    public Biome getBiome(Cell<Terrain> cell, int x, int z) {
        if (cell.value <= context.levels.water) {
            if (cell.tag == context.terrain.river || cell.tag == context.terrain.riverBanks) {
                return modifyBiome(biomeMap.getRiver(cell.temperature, cell.moisture, cell.biome), cell, x, z);
            } else if (cell.tag == context.terrain.ocean) {
                return biomeMap.getOcean(cell.temperature, cell.moisture, cell.biome);
            } else if (cell.tag == context.terrain.deepOcean) {
                return biomeMap.getDeepOcean(cell.temperature, cell.moisture, cell.biome);
            }
        }
        return modifyBiome(getBiome(cell), cell, x, z);
    }

    public Biome getBiome(Cell<Terrain> cell) {
        return biomeMap.getBiome(cell.biomeType, cell.temperature, cell.moisture, cell.biome);
    }

    public Biome modifyBiome(Biome biome, Cell<Terrain> cell, int x, int z) {
        return modifierManager.modify(biome, cell, x, z);
    }

    private static class SearchContext {

        private int count = 0;
        private boolean first = true;
        private final BlockPos.Mutable pos = new BlockPos.Mutable();
    }
}