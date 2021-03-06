/*
 * This file is part of Vanilla.
 *
 * Copyright (c) 2011-2012, VanillaDev <http://www.spout.org/>
 * Vanilla is licensed under the SpoutDev License Version 1.
 *
 * Vanilla is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * In addition, 180 days after any changes are published, you can use the
 * software, incorporating those changes, under the terms of the MIT license,
 * as described in the SpoutDev License Version 1.
 *
 * Vanilla is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License,
 * the MIT license and the SpoutDev License Version 1 along with this program.
 * If not, see <http://www.gnu.org/licenses/> for the GNU Lesser General Public
 * License and see <http://www.spout.org/SpoutDevLicenseV1.txt> for the full license,
 * including the MIT license.
 */
package org.spout.vanilla.world.generator.nether;

import java.util.Arrays;
import java.util.Random;
import net.royawesome.jlibnoise.MathHelper;

import net.royawesome.jlibnoise.NoiseQuality;
import net.royawesome.jlibnoise.module.source.Perlin;

import org.spout.api.generator.WorldGeneratorUtils;
import org.spout.api.generator.biome.BiomeManager;
import org.spout.api.generator.biome.Simple2DBiomeManager;
import org.spout.api.generator.biome.selector.PerBlockBiomeSelector;
import org.spout.api.geo.World;
import org.spout.api.geo.cuboid.Chunk;
import org.spout.api.geo.discrete.Point;
import org.spout.api.util.cuboid.CuboidShortBuffer;

import org.spout.vanilla.material.VanillaMaterials;
import org.spout.vanilla.material.block.Liquid;
import org.spout.vanilla.world.generator.VanillaBiomeGenerator;
import org.spout.vanilla.world.generator.VanillaBiomes;
import org.spout.vanilla.world.generator.VanillaGenerator;

public class NetherGenerator extends VanillaBiomeGenerator implements VanillaGenerator {
	public final static int SEA_LEVEL = 31;
	private static final Perlin PERLIN = new Perlin();
	private static final Perlin BLOCK_REPLACER = new Perlin();

	static {
		PERLIN.setFrequency(0.05);
		PERLIN.setLacunarity(1);
		PERLIN.setNoiseQuality(NoiseQuality.STANDARD);
		PERLIN.setPersistence(0.5);
		PERLIN.setOctaveCount(1);

		BLOCK_REPLACER.setFrequency(0.35);
		BLOCK_REPLACER.setLacunarity(1);
		BLOCK_REPLACER.setNoiseQuality(NoiseQuality.FAST);
		BLOCK_REPLACER.setPersistence(0.7);
		BLOCK_REPLACER.setOctaveCount(1);
	}

	@Override
	public void registerBiomes() {
		setSelector(new PerBlockBiomeSelector(VanillaBiomes.NETHERRACK));
		register(VanillaBiomes.NETHERRACK);
	}

	@Override
	public String getName() {
		return "VanillaNether";
	}

	@Override
	public BiomeManager generate(CuboidShortBuffer blockData, int chunkX, int chunkY, int chunkZ) {
		if (chunkY < 0) {
			return super.generate(blockData, chunkX, chunkY, chunkZ);
		}
		final int x = chunkX << Chunk.BLOCKS.BITS;
		final int y = chunkY << Chunk.BLOCKS.BITS;
		final int z = chunkZ << Chunk.BLOCKS.BITS;
		final int seed = (int) blockData.getWorld().getSeed();
		PERLIN.setSeed(seed * 23);
		BLOCK_REPLACER.setSeed(seed * 83);
		generateTerrain(blockData, x, y, z);
		replaceBlocks(blockData, x, y, z);
		final BiomeManager biomeManger = new Simple2DBiomeManager(chunkX, chunkY, chunkZ);
		final byte[] biomeData = new byte[Chunk.BLOCKS.AREA];
		Arrays.fill(biomeData, (byte) VanillaBiomes.NETHERRACK.getId());
		biomeManger.deserialize(biomeData);
		return biomeManger;
	}

	private void generateTerrain(CuboidShortBuffer blockData, int x, int y, int z) {
		final int size = Chunk.BLOCKS.SIZE;
		final int height = blockData.getWorld().getHeight();
		final double[][][] noise;
		// build the density maps
		if (y == height - size || y == 0) {
			// just solid netherrack
			noise = new double[size][size][size];
			for (int xx = 0; xx < size; xx++) {
				for (int yy = 0; yy < size; yy++) {
					for (int zz = 0; zz < size; zz++) {
						noise[xx][yy][zz] = 1;
					}
				}
			}
		} else if (y == height - size * 2) {
			// a height map for smooth progression from density to solid netherrack
			// this one is upside down
			final double[][] flatNoise = WorldGeneratorUtils.fastNoise(PERLIN, size, size, 4, x, y, z);
			noise = new double[size][size][size];
			for (int xx = 0; xx < size; xx++) {
				for (int zz = 0; zz < size; zz++) {
					final int heightMapValue = (int) (flatNoise[xx][zz] * size + size + 1);
					final int endY = MathHelper.clamp(size - heightMapValue, 0, size - 1);
					for (int yy = size - 1; yy >= endY; yy--) {
						noise[xx][yy][zz] = 1;
					}
				}
			}
		} else if (y == size) {
			// a height map for smooth progression from density to solid netherrack
			final double[][] flatNoise = WorldGeneratorUtils.fastNoise(PERLIN, size, size, 4, x, y + size - 1, z);
			noise = new double[size][size][size];
			for (int xx = 0; xx < size; xx++) {
				for (int zz = 0; zz < size; zz++) {
					final int heightMapValue = (int) (flatNoise[xx][zz] * size + size);
					final int endY = MathHelper.clamp(heightMapValue, 0, size);
					for (int yy = 0; yy < endY; yy++) {
						noise[xx][yy][zz] = 1;
					}
				}
			}
		} else {
			// just density noise
			noise = WorldGeneratorUtils.fastNoise(PERLIN, size, size, size, 4, x, y, z);
		}
		for (int xx = 0; xx < size; xx++) {
			for (int yy = 0; yy < size; yy++) {
				for (int zz = 0; zz < size; zz++) {
					final double value = noise[xx][yy][zz];
					if (value > 0) {
						blockData.set(x + xx, y + yy, z + zz, VanillaMaterials.NETHERRACK.getId());
					} else {
						if (y < 32) {
							blockData.set(x + xx, y + yy, z + zz, VanillaMaterials.STATIONARY_LAVA.getId());
						} else {
							blockData.set(x + xx, y + yy, z + zz, VanillaMaterials.AIR.getId());
						}
					}
				}
			}
		}
	}

	private void replaceBlocks(CuboidShortBuffer blockData, int x, int y, int z) {
		final int size = Chunk.BLOCKS.SIZE;
		final int height = blockData.getWorld().getHeight();
		if (y == 0) {
			for (int xx = 0; xx < size; xx++) {
				for (int zz = 0; zz < size; zz++) {
					final byte bedrockDepth = (byte) MathHelper.clamp(BLOCK_REPLACER.GetValue(x + xx, -5, z + zz) * 2 + 4, 1, 4);
					for (y = 0; y < bedrockDepth; y++) {
						blockData.set(x + xx, y, z + zz, VanillaMaterials.BEDROCK.getId());
					}
				}
			}
		} else if (y == height - size) {
			for (int xx = 0; xx < size; xx++) {
				for (int zz = 0; zz < size; zz++) {
					final byte bedrockDepth = (byte) MathHelper.clamp(BLOCK_REPLACER.GetValue(x + xx, -73, z + zz) * 2 + 4, 1, 4);
					for (y = height - 1; y >= height - 1 - bedrockDepth; y--) {
						blockData.set(x + xx, y, z + zz, VanillaMaterials.BEDROCK.getId());
					}
				}
			}
		}
	}

	@Override
	public Point getSafeSpawn(World world) {
		final Random random = new Random();
		for (byte attempts = 0; attempts < 10; attempts++) {
			final int x = random.nextInt(31) - 15;
			final int z = random.nextInt(31) - 15;
			final int y = getHighestSolidBlock(world, x, z);
			if (y != -1) {
				return new Point(world, x, y, z);
			}
		}
		return new Point(world, 0, 80, 0);
	}

	private int getHighestSolidBlock(World world, int x, int z) {
		int y = world.getHeight() - 1;
		while (world.getBlockMaterial(x, y, z).equals(VanillaMaterials.AIR)) {
			y--;
			if (y == 0 || world.getBlockMaterial(x, y, z) instanceof Liquid) {
				return -1;
			}
		}
		return y + 2;
	}

	@Override
	public int[][] getSurfaceHeight(World world, int chunkX, int chunkZ) {
		int height = world.getHeight() - 1;
		int[][] heights = new int[Chunk.BLOCKS.SIZE][Chunk.BLOCKS.SIZE];
		for (int x = 0; x < Chunk.BLOCKS.SIZE; x++) {
			for (int z = 0; z < Chunk.BLOCKS.SIZE; z++) {
				heights[x][z] = height;
			}
		}
		return heights;
	}
}
