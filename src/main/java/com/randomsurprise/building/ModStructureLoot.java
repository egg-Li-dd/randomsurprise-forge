package com.randomsurprise.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.randomsurprise.affix.ModItems;

import java.util.Random;
import java.util.List;
import java.util.ArrayList;

/**
 * 模组建筑战利品配置
 * 配置箱子内容，涉及已安装模组中的物品
 *
 * 战利品分为5个稀有度等级：
 *   COMMON    - 基础资源（铁/金/钻石等）+ 廉价模组食物/材料
 *   UNCOMMON  - 模组消耗品 + 中级材料 + 原版药水/装备
 *   RARE      - 模组武器/防具 + 稀有材料 + 原版钻石装备
 *   EPIC      - 稀有模组物品（图腾/附魔金苹果/唱片/彩票）
 *   LEGENDARY - 传说物品（下界星/龙蛋/鞘翅/大量彩票）
 *
 * 已覆盖模组：
 *   Alex's Mobs, Ender Zoology, Friends & Foes, Mowzie's Mobs,
 *   Cataclysm, Twilight Forest, Blue Skies, Modular Golems,
 *   Mutant Monsters, Naturalist, Happy Ghast, Aquaculture Delight
 *
 * 箱子生成策略：
 *   - 每个建筑生成1-3个箱子
 *   - 每个箱子含3-6个物品槽位
 *   - 物品从各稀有度池中按权重抽取
 *   - 模组物品不存在时自动回退到原版替代品
 */
public class ModStructureLoot {
	private static final Random RANDOM = new Random();

	/** 战利品条目 */
	private static class LootEntry {
		final String itemId;     // 物品ID（如 "alexsmobs:blood_sprayer"）
		final int minCount;      // 最小数量
		final int maxCount;      // 最大数量
		final double weight;     // 权重

		LootEntry(String itemId, int minCount, int maxCount, double weight) {
			this.itemId = itemId;
			this.minCount = minCount;
			this.maxCount = maxCount;
			this.weight = weight;
		}
	}

	// ========== 战利品池 ==========

	/**
	 * COMMON 池: 基础原版资源 + 廉价模组食物/材料（重量高）
	 * 共 53 个条目
	 */
	private static final LootEntry[] COMMON_POOL = {
		// === 原版基础资源 ===
		new LootEntry("minecraft:iron_ingot", 4, 12, 10),
		new LootEntry("minecraft:gold_ingot", 2, 8, 8),
		new LootEntry("minecraft:diamond", 1, 3, 5),
		new LootEntry("minecraft:emerald", 2, 6, 6),
		new LootEntry("minecraft:coal", 8, 16, 5),
		new LootEntry("minecraft:redstone", 4, 12, 4),
		new LootEntry("minecraft:lapis_lazuli", 4, 12, 4),
		new LootEntry("minecraft:quartz", 8, 16, 4),
		new LootEntry("minecraft:copper_ingot", 4, 12, 5),
		new LootEntry("minecraft:amethyst_shard", 2, 6, 3),
		new LootEntry("minecraft:iron_nugget", 8, 16, 3),
		new LootEntry("minecraft:gold_nugget", 8, 16, 3),
		// === 原版基础物资 ===
		new LootEntry("minecraft:arrow", 16, 32, 4),
		new LootEntry("minecraft:torch", 16, 32, 4),
		new LootEntry("minecraft:oak_log", 8, 16, 3),
		new LootEntry("minecraft:obsidian", 4, 8, 2),
		new LootEntry("minecraft:wheat", 8, 16, 3),
		new LootEntry("minecraft:bread", 2, 6, 4),
		new LootEntry("minecraft:apple", 3, 8, 4),
		new LootEntry("minecraft:string", 4, 12, 3),
		new LootEntry("minecraft:leather", 2, 6, 3),
		new LootEntry("minecraft:paper", 2, 8, 2),
		new LootEntry("minecraft:bone", 4, 12, 3),
		new LootEntry("minecraft:gunpowder", 2, 6, 3),
		new LootEntry("minecraft:flint", 4, 12, 3),
		new LootEntry("minecraft:brick", 4, 12, 2),
		// === Alex's Mobs 廉价食物 ===
		new LootEntry("alexsmobs:banana", 2, 6, 5),
		new LootEntry("alexsmobs:shrimp_fried_rice", 1, 3, 4),
		new LootEntry("alexsmobs:raw_catfish", 1, 3, 3),
		new LootEntry("alexsmobs:lobster_tail", 1, 3, 3),
		new LootEntry("alexsmobs:kangaroo_meat", 1, 3, 3),
		new LootEntry("alexsmobs:moose_ribs", 1, 3, 3),
		new LootEntry("alexsmobs:fish_oil", 1, 3, 3),
		new LootEntry("alexsmobs:boiled_emu_egg", 1, 2, 2),
		// === Alex's Mobs 廉价材料 ===
		new LootEntry("alexsmobs:shark_tooth", 2, 6, 4),
		new LootEntry("alexsmobs:crocodile_scute", 1, 3, 3),
		new LootEntry("alexsmobs:moose_antler", 1, 2, 2),
		new LootEntry("alexsmobs:raccoon_tail", 1, 2, 2),
		new LootEntry("alexsmobs:bear_fur", 1, 3, 3),
		new LootEntry("alexsmobs:kangaroo_hide", 1, 2, 2),
		new LootEntry("alexsmobs:maggot", 2, 6, 3),
		new LootEntry("alexsmobs:fish_bones", 2, 6, 3),
		new LootEntry("alexsmobs:glowing_jelly", 1, 3, 2),
		new LootEntry("alexsmobs:mungal_spores", 1, 3, 2),
		// === Ender Zoology 廉价材料 ===
		new LootEntry("enderzoology:confusing_powder", 1, 3, 3),
		new LootEntry("enderzoology:ender_fragment", 1, 3, 3),
		// === Friends & Foes ===
		new LootEntry("friendsandfoes:buttercup", 2, 6, 3),
		new LootEntry("friendsandfoes:copper_button", 1, 3, 2),
		// === Naturalist 廉价食物/材料 ===
		new LootEntry("naturalist:bass", 1, 3, 3),
		new LootEntry("naturalist:bird_egg", 1, 3, 3),
		new LootEntry("naturalist:antler", 1, 2, 2),
		// === Aquaculture Delight 廉价食物 ===
		new LootEntry("aquaculturedelight:fish_and_chips", 1, 2, 3),
		new LootEntry("aquaculturedelight:crispy_fried_perch", 1, 2, 2),
	};

	/**
	 * UNCOMMON 池: 模组消耗品 + 中级材料 + 原版药水/装备（重量中）
	 * 共 60 个条目
	 */
	private static final LootEntry[] UNCOMMON_POOL = {
		// === 原版药水/装备/中级物资 ===
		new LootEntry("minecraft:golden_apple", 1, 3, 5),
		new LootEntry("minecraft:potion", 1, 2, 4),
		new LootEntry("minecraft:splash_potion", 1, 2, 3),
		new LootEntry("minecraft:experience_bottle", 2, 6, 4),
		new LootEntry("minecraft:book", 1, 3, 3),
		new LootEntry("minecraft:iron_sword", 1, 1, 3),
		new LootEntry("minecraft:iron_pickaxe", 1, 1, 3),
		new LootEntry("minecraft:iron_axe", 1, 1, 3),
		new LootEntry("minecraft:iron_helmet", 1, 1, 3),
		new LootEntry("minecraft:iron_chestplate", 1, 1, 3),
		new LootEntry("minecraft:iron_leggings", 1, 1, 3),
		new LootEntry("minecraft:iron_boots", 1, 1, 3),
		new LootEntry("minecraft:ender_pearl", 1, 3, 3),
		new LootEntry("minecraft:blaze_rod", 1, 3, 3),
		// === Alex's Mobs 中级食物/材料 ===
		new LootEntry("alexsmobs:cooked_catfish", 1, 3, 4),
		new LootEntry("alexsmobs:cooked_lobster_tail", 1, 3, 4),
		new LootEntry("alexsmobs:cooked_kangaroo_meat", 1, 3, 4),
		new LootEntry("alexsmobs:cooked_moose_ribs", 1, 3, 4),
		new LootEntry("alexsmobs:kangaroo_burger", 1, 2, 3),
		new LootEntry("alexsmobs:serrated_shark_tooth", 1, 3, 3),
		new LootEntry("alexsmobs:froststalker_horn", 1, 2, 2),
		new LootEntry("alexsmobs:gazelle_horn", 1, 2, 2),
		new LootEntry("alexsmobs:rattlesnake_rattle", 1, 2, 2),
		new LootEntry("alexsmobs:bear_dust", 1, 3, 2),
		new LootEntry("alexsmobs:komodo_spit_bottle", 1, 2, 2),
		new LootEntry("alexsmobs:poison_bottle", 1, 2, 2),
		new LootEntry("alexsmobs:blood_sac", 1, 2, 2),
		new LootEntry("alexsmobs:ender_residue", 1, 2, 2),
		new LootEntry("alexsmobs:soul_heart", 1, 2, 2),
		new LootEntry("alexsmobs:lava_bottle", 1, 2, 2),
		new LootEntry("alexsmobs:mimicream", 1, 2, 2),
		// === Ender Zoology ===
		new LootEntry("enderzoology:ender_charge", 1, 2, 3),
		new LootEntry("enderzoology:confusing_charge", 1, 2, 3),
		new LootEntry("enderzoology:enderios", 1, 3, 2),
		new LootEntry("enderzoology:owl_egg", 1, 2, 2),
		// === Friends & Foes ===
		new LootEntry("friendsandfoes:crab_claw", 1, 2, 3),
		new LootEntry("friendsandfoes:wildfire_crown_fragment", 1, 2, 2),
		// === Mowzie's Mobs 中级材料 ===
		new LootEntry("mowziesmobs:naga_fang", 1, 2, 2),
		new LootEntry("mowziesmobs:ice_crystal", 1, 2, 2),
		new LootEntry("mowziesmobs:foliaath_seed", 1, 2, 2),
		// === Cataclysm 中级材料 ===
		new LootEntry("cataclysm:ancient_metal_ingot", 1, 3, 3),
		new LootEntry("cataclysm:black_steel_ingot", 1, 3, 3),
		new LootEntry("cataclysm:blazing_bone", 1, 3, 2),
		new LootEntry("cataclysm:dying_ember", 1, 3, 2),
		new LootEntry("cataclysm:chitin_claw", 1, 2, 2),
		// === Twilight Forest 中级材料 ===
		new LootEntry("twilightforest:fiery_ingot", 1, 3, 3),
		new LootEntry("twilightforest:knightmetal_ingot", 1, 3, 3),
		new LootEntry("twilightforest:steeleaf_ingot", 2, 4, 3),
		new LootEntry("twilightforest:ironwood_ingot", 2, 4, 3),
		new LootEntry("twilightforest:arctic_fur", 1, 3, 2),
		// === Blue Skies 中级材料 ===
		new LootEntry("blue_skies:aquite", 2, 6, 3),
		new LootEntry("blue_skies:charoite", 1, 3, 2),
		new LootEntry("blue_skies:horizonite", 1, 3, 2),
		new LootEntry("blue_skies:moonstone", 1, 3, 2),
		// === Mutant Monsters ===
		new LootEntry("mutantmonsters:chemical_x", 1, 2, 2),
		// === Naturalist ===
		new LootEntry("naturalist:binoculars", 1, 1, 2),
		new LootEntry("naturalist:azure_froglass", 1, 2, 2),
		// === Modular Golems ===
		new LootEntry("modulargolems:azure_cube", 1, 3, 2),
		// === Aquaculture Delight 食物 ===
		new LootEntry("aquaculturedelight:fish_roll_medley", 1, 2, 2),
		new LootEntry("aquaculturedelight:halaszle", 1, 2, 2),
	};

	/**
	 * RARE 池: 模组武器/防具 + 稀有材料 + 原版钻石装备（重量中低）
	 * 共 60 个条目
	 */
	private static final LootEntry[] RARE_POOL = {
		// === 原版钻石装备/工具 ===
		new LootEntry("minecraft:diamond_sword", 1, 1, 4),
		new LootEntry("minecraft:diamond_pickaxe", 1, 1, 4),
		new LootEntry("minecraft:diamond_axe", 1, 1, 3),
		new LootEntry("minecraft:diamond_helmet", 1, 1, 3),
		new LootEntry("minecraft:diamond_chestplate", 1, 1, 3),
		new LootEntry("minecraft:diamond_leggings", 1, 1, 3),
		new LootEntry("minecraft:diamond_boots", 1, 1, 3),
		new LootEntry("minecraft:bow", 1, 1, 3),
		new LootEntry("minecraft:crossbow", 1, 1, 3),
		new LootEntry("minecraft:shield", 1, 1, 3),
		// === Alex's Mobs 装备 ===
		new LootEntry("alexsmobs:skelewag_sword", 1, 1, 3),
		new LootEntry("alexsmobs:blood_sprayer", 1, 1, 2),
		new LootEntry("alexsmobs:falconry_glove", 1, 1, 2),
		new LootEntry("alexsmobs:ancient_dart", 2, 4, 3),
		new LootEntry("alexsmobs:shark_tooth_arrow", 8, 16, 3),
		new LootEntry("alexsmobs:crocodile_chestplate", 1, 1, 2),
		new LootEntry("alexsmobs:rocky_chestplate", 1, 1, 2),
		new LootEntry("alexsmobs:froststalker_helmet", 1, 1, 2),
		new LootEntry("alexsmobs:centipede_leggings", 1, 1, 2),
		new LootEntry("alexsmobs:emu_leggings", 1, 1, 2),
		// === Ender Zoology 装备 ===
		new LootEntry("enderzoology:hunting_bow", 1, 1, 2),
		new LootEntry("enderzoology:death_pouch", 1, 1, 2),
		// === Friends & Foes ===
		new LootEntry("friendsandfoes:totem_of_freezing", 1, 1, 2),
		new LootEntry("friendsandfoes:totem_of_illusion", 1, 1, 2),
		// === Mowzie's Mobs 装备 ===
		new LootEntry("mowziesmobs:blowgun", 1, 1, 2),
		new LootEntry("mowziesmobs:dart", 4, 12, 3),
		new LootEntry("mowziesmobs:earthrend_gauntlet", 1, 1, 1),
		new LootEntry("mowziesmobs:naga_fang_dagger", 1, 1, 2),
		new LootEntry("mowziesmobs:wrought_axe", 1, 1, 1),
		new LootEntry("mowziesmobs:spear", 1, 1, 2),
		// === Cataclysm 装备 ===
		new LootEntry("cataclysm:ancient_spear", 1, 1, 2),
		new LootEntry("cataclysm:athame", 1, 1, 2),
		new LootEntry("cataclysm:black_steel_sword", 1, 1, 2),
		new LootEntry("cataclysm:cursed_bow", 1, 1, 2),
		new LootEntry("cataclysm:final_fractal", 1, 1, 1),
		new LootEntry("cataclysm:infernal_forge", 1, 1, 1),
		new LootEntry("cataclysm:gauntlet_of_bulwark", 1, 1, 1),
		new LootEntry("cataclysm:cursium_chestplate", 1, 1, 1),
		new LootEntry("cataclysm:ignitium_chestplate", 1, 1, 1),
		new LootEntry("cataclysm:necklace_of_the_desert", 1, 1, 1),
		// === Twilight Forest 装备 ===
		new LootEntry("twilightforest:fiery_sword", 1, 1, 2),
		new LootEntry("twilightforest:ice_sword", 1, 1, 2),
		new LootEntry("twilightforest:knightmetal_sword", 1, 1, 2),
		new LootEntry("twilightforest:glass_sword", 1, 1, 1),
		new LootEntry("twilightforest:end_bow", 1, 1, 1),
		new LootEntry("twilightforest:knightmetal_chestplate", 1, 1, 2),
		new LootEntry("twilightforest:arctic_chestplate", 1, 1, 2),
		new LootEntry("twilightforest:knightmetal_shield", 1, 1, 2),
		// === Blue Skies 装备 ===
		new LootEntry("blue_skies:aquite_sword", 1, 1, 2),
		new LootEntry("blue_skies:aquite_chestplate", 1, 1, 2),
		new LootEntry("blue_skies:horizonite_sword", 1, 1, 2),
		new LootEntry("blue_skies:charoite_sword", 1, 1, 2),
		new LootEntry("blue_skies:diopside_sword", 1, 1, 2),
		new LootEntry("blue_skies:ventium_sword", 1, 1, 2),
		// === Modular Golems 装备 ===
		new LootEntry("modulargolems:apocalyptium_chestplate", 1, 1, 1),
		new LootEntry("modulargolems:apocalyptium_helmet", 1, 1, 1),
		new LootEntry("modulargolems:barbaric_vanguard_chestplate", 1, 1, 1),
		new LootEntry("modulargolems:battle_axe", 1, 1, 1),
		// === Mutant Monsters 装备 ===
		new LootEntry("mutantmonsters:hulk_hammer", 1, 1, 1),
		new LootEntry("mutantmonsters:mutant_skeleton_chestplate", 1, 1, 2),
	};

	/**
	 * EPIC 池: 模组稀有装备 + 图腾/附魔金苹果/唱片 + 彩票（重量低）
	 * 共 51 个条目
	 */
	private static final LootEntry[] EPIC_POOL = {
		// === 原版稀有物品 ===
		new LootEntry("minecraft:enchanted_golden_apple", 1, 2, 4),
		new LootEntry("minecraft:totem_of_undying", 1, 1, 4),
		new LootEntry("minecraft:enchanted_book", 1, 2, 5),
		new LootEntry("minecraft:ender_pearl", 2, 6, 4),
		new LootEntry("minecraft:blaze_rod", 2, 4, 3),
		new LootEntry("minecraft:ghast_tear", 1, 2, 3),
		new LootEntry("minecraft:netherite_ingot", 1, 1, 2),
		new LootEntry("minecraft:netherite_scrap", 1, 2, 2),
		new LootEntry("minecraft:beacon", 1, 1, 1),
		new LootEntry("minecraft:conduit", 1, 1, 1),
		new LootEntry("minecraft:trident", 1, 1, 1),
		new LootEntry("minecraft:wither_skeleton_skull", 1, 1, 1),
		// === Alex's Mobs 稀有装备 ===
		new LootEntry("alexsmobs:shield_of_the_deep", 1, 1, 2),
		new LootEntry("alexsmobs:dimensional_carver", 1, 1, 1),
		new LootEntry("alexsmobs:straddle_helmet", 1, 1, 2),
		new LootEntry("alexsmobs:moose_headgear", 1, 1, 2),
		new LootEntry("alexsmobs:fedora", 1, 1, 2),
		new LootEntry("alexsmobs:sombrero", 1, 1, 2),
		new LootEntry("alexsmobs:novelty_hat", 1, 1, 2),
		new LootEntry("alexsmobs:halo", 1, 1, 1),
		new LootEntry("alexsmobs:endolocator", 1, 1, 2),
		new LootEntry("alexsmobs:pupfish_locator", 1, 1, 2),
		new LootEntry("alexsmobs:mysterious_worm", 1, 1, 1),
		new LootEntry("alexsmobs:tarantula_hawk_helmet", 1, 1, 1),
		// === Friends & Foes 稀有 ===
		new LootEntry("friendsandfoes:wildfire_crown", 1, 1, 1),
		// === Mowzie's Mobs 稀有 ===
		new LootEntry("mowziesmobs:sculptor_staff", 1, 1, 1),
		new LootEntry("mowziesmobs:sunblock_staff", 1, 1, 1),
		new LootEntry("mowziesmobs:sol_visage", 1, 1, 1),
		new LootEntry("mowziesmobs:umvuthana_mask", 1, 1, 1),
		new LootEntry("mowziesmobs:gong", 1, 1, 1),
		new LootEntry("mowziesmobs:captured_grottol", 1, 1, 1),
		// === Cataclysm 稀有 ===
		new LootEntry("cataclysm:ignitium_elytra_chestplate", 1, 1, 1),
		new LootEntry("cataclysm:belt_of_monstrosity", 1, 1, 1),
		new LootEntry("cataclysm:berserker_soul_amulet", 1, 1, 1),
		new LootEntry("cataclysm:netherite_effigy", 1, 1, 1),
		new LootEntry("cataclysm:emp", 1, 1, 1),
		new LootEntry("cataclysm:boss_respawner", 1, 1, 1),
		new LootEntry("cataclysm:essence_of_the_storm", 1, 1, 1),
		// === Twilight Forest 稀有 ===
		new LootEntry("twilightforest:alpha_yeti_banner_pattern", 1, 1, 1),
		// === Blue Skies 稀有 ===
		new LootEntry("blue_skies:alchemy_scroll", 1, 1, 1),
		new LootEntry("blue_skies:alchemy_table", 1, 1, 1),
		// === Aquaculture Delight 稀有工具 ===
		new LootEntry("aquaculturedelight:neptunium_knife", 1, 1, 1),
		// === 音乐唱片 ===
		new LootEntry("alexsmobs:music_disc_daze", 1, 1, 2),
		new LootEntry("alexsmobs:music_disc_thime", 1, 1, 2),
		new LootEntry("mowziesmobs:music_disc_petiole", 1, 1, 2),
		new LootEntry("cataclysm:music_disc_ancient_remnant", 1, 1, 1),
		new LootEntry("cataclysm:music_disc_ignis", 1, 1, 1),
		new LootEntry("cataclysm:music_disc_maledictus", 1, 1, 1),
		new LootEntry("cataclysm:music_disc_scylla", 1, 1, 1),
		new LootEntry("happyghastmod:music_disc_tears", 1, 1, 1),
		// === 彩票 ===
		new LootEntry("randomsurprise:lottery_ticket", 1, 3, 4),
	};

	/**
	 * LEGENDARY 池: 传说物品 + 下界星/龙蛋/鞘翅 + 大量彩票（重量最低）
	 * 共 41 个条目
	 */
	private static final LootEntry[] LEGENDARY_POOL = {
		// === 原版传说物品 ===
		new LootEntry("minecraft:nether_star", 1, 1, 3),
		new LootEntry("minecraft:dragon_egg", 1, 1, 1),
		new LootEntry("minecraft:elytra", 1, 1, 2),
		new LootEntry("minecraft:totem_of_undying", 1, 1, 3),
		new LootEntry("minecraft:netherite_ingot", 1, 2, 3),
		new LootEntry("minecraft:netherite_scrap", 2, 4, 3),
		new LootEntry("minecraft:enchanted_golden_apple", 1, 2, 4),
		new LootEntry("minecraft:dragon_breath", 1, 3, 2),
		new LootEntry("minecraft:end_crystal", 1, 2, 2),
		new LootEntry("minecraft:wither_skeleton_skull", 1, 1, 1),
		new LootEntry("minecraft:dragon_head", 1, 1, 1),
		new LootEntry("minecraft:beacon", 1, 1, 2),
		new LootEntry("minecraft:conduit", 1, 1, 2),
		new LootEntry("minecraft:trident", 1, 1, 2),
		new LootEntry("minecraft:netherite_block", 1, 1, 1),
		new LootEntry("minecraft:diamond_block", 1, 1, 2),
		new LootEntry("minecraft:emerald_block", 1, 1, 2),
		// === Alex's Mobs 传说 ===
		new LootEntry("alexsmobs:dimensional_carver", 1, 1, 1),
		new LootEntry("alexsmobs:ghostly_pickaxe", 1, 1, 1),
		new LootEntry("alexsmobs:skreecher_soul", 1, 1, 1),
		new LootEntry("alexsmobs:mysterious_worm", 1, 1, 1),
		// === Cataclysm 传说 ===
		new LootEntry("cataclysm:ignitium_elytra_chestplate", 1, 1, 1),
		new LootEntry("cataclysm:boss_respawner", 1, 1, 1),
		new LootEntry("cataclysm:essence_of_the_storm", 1, 1, 1),
		new LootEntry("cataclysm:netherite_effigy", 1, 1, 1),
		new LootEntry("cataclysm:emp", 1, 1, 1),
		// === Mowzie's Mobs 传说 ===
		new LootEntry("mowziesmobs:frostmaw_plushie", 1, 1, 1),
		new LootEntry("mowziesmobs:mob_remover", 1, 1, 1),
		new LootEntry("mowziesmobs:grant_suns_blessing", 1, 1, 1),
		// === Modular Golems 传说 ===
		new LootEntry("modulargolems:apocalyptium_chestplate", 1, 1, 1),
		new LootEntry("modulargolems:apocalyptium_helmet", 1, 1, 1),
		// === Happy Ghast 传说 ===
		new LootEntry("happyghastmod:ghastling_incubator", 1, 1, 1),
		new LootEntry("happyghastmod:harness", 1, 1, 1),
		new LootEntry("happyghastmod:harness_black", 1, 1, 1),
		new LootEntry("happyghastmod:harness_blue", 1, 1, 1),
		new LootEntry("happyghastmod:harness_red", 1, 1, 1),
		new LootEntry("happyghastmod:harness_purple", 1, 1, 1),
		new LootEntry("happyghastmod:harness_white", 1, 1, 1),
		// === 随机惊喜传说 ===
		new LootEntry("randomsurprise:lottery_ticket", 3, 6, 4),
		new LootEntry("randomsurprise:money_bag", 1, 1, 3),
		new LootEntry("randomsurprise:purify_potion", 1, 2, 3),
	};

	// ========== 战利品生成 ==========

	/**
	 * 在指定位置放置一个战利品箱子
	 * @param level 世界
	 * @param pos 箱子位置
	 * @param chestType 箱子类型（影响战利品等级分布）
	 */
	public static void placeLootChest(ServerLevel level, BlockPos pos, ChestTier chestType) {
		// 确保该位置是箱子
		if (!(level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.ChestBlock)) {
			level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
		}

		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof ChestBlockEntity chest)) return;

		List<ItemStack> loot = generateLoot(chestType);
		for (int i = 0; i < loot.size() && i < 27; i++) {
			chest.setItem(i, loot.get(i));
		}
	}

	/**
	 * 在建筑中寻找合适位置放置箱子（找已有箱子或放置新箱子）
	 * @param level 世界
	 * @param center 建筑中心
	 * @param radius 搜索半径
	 * @param chestCount 要放置的箱子数量
	 * @param chestType 箱子等级
	 */
	public static void placeLootChestsInArea(ServerLevel level, BlockPos center, int radius, int chestCount, ChestTier chestType) {
		List<BlockPos> chestPositions = new ArrayList<>();
		// 搜索已有箱子
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -3; dy <= 5; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					BlockPos pos = center.offset(dx, dy, dz);
					if (level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
						chestPositions.add(pos);
						if (chestPositions.size() >= chestCount) break;
					}
				}
			}
		}
		// 如果已有箱子不够，在中心附近放置新箱子
		while (chestPositions.size() < chestCount) {
			int dx = -2 + RANDOM.nextInt(5);
			int dz = -2 + RANDOM.nextInt(5);
			int dy = 1; // 地面上1格
			BlockPos pos = center.offset(dx, dy, dz);
			if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir()) {
				chestPositions.add(pos);
			} else {
				break; // 防止无限循环
			}
		}
		// 填充箱子
		for (BlockPos pos : chestPositions) {
			placeLootChest(level, pos, chestType);
		}
	}

	/** 根据箱子等级生成战利品 */
	private static List<ItemStack> generateLoot(ChestTier tier) {
		List<ItemStack> result = new ArrayList<>();
		int slotCount = 3 + RANDOM.nextInt(4); // 3-6个物品

		for (int i = 0; i < slotCount; i++) {
			LootEntry entry = rollLoot(tier);
			if (entry == null) continue;
			ItemStack stack = createItemStack(entry);
			if (stack != null) {
				result.add(stack);
			}
		}
		return result;
	}

	/** 根据箱子等级抽取战利品条目 */
	private static LootEntry rollLoot(ChestTier tier) {
		// 各等级箱子的稀有度概率分布
		double roll = RANDOM.nextDouble();
		LootEntry[] pool;
		switch (tier) {
			case COMMON:
				if (roll < 0.60) pool = COMMON_POOL;
				else if (roll < 0.90) pool = UNCOMMON_POOL;
				else pool = RARE_POOL;
				break;
			case UNCOMMON:
				if (roll < 0.35) pool = COMMON_POOL;
				else if (roll < 0.70) pool = UNCOMMON_POOL;
				else if (roll < 0.95) pool = RARE_POOL;
				else pool = EPIC_POOL;
				break;
			case RARE:
				if (roll < 0.15) pool = COMMON_POOL;
				else if (roll < 0.40) pool = UNCOMMON_POOL;
				else if (roll < 0.80) pool = RARE_POOL;
				else if (roll < 0.98) pool = EPIC_POOL;
				else pool = LEGENDARY_POOL;
				break;
			case LEGENDARY:
				if (roll < 0.10) pool = UNCOMMON_POOL;
				else if (roll < 0.35) pool = RARE_POOL;
				else if (roll < 0.75) pool = EPIC_POOL;
				else pool = LEGENDARY_POOL;
				break;
			default:
				pool = COMMON_POOL;
		}
		return weightedPick(pool);
	}

	/** 从战利品池中按权重抽取 */
	private static LootEntry weightedPick(LootEntry[] pool) {
		double totalWeight = 0;
		for (LootEntry e : pool) totalWeight += e.weight;
		double roll = RANDOM.nextDouble() * totalWeight;
		for (LootEntry e : pool) {
			roll -= e.weight;
			if (roll <= 0) return e;
		}
		return pool[pool.length - 1];
	}

	/** 创建物品堆叠 */
	private static ItemStack createItemStack(LootEntry entry) {
		// 先尝试从注册表获取物品（支持模组物品）
		var itemOpt = BuiltInRegistries.ITEM.getOptional(new ResourceLocation(entry.itemId));
		if (itemOpt.isEmpty()) {
			// 物品不存在（模组未安装），返回原版替代品
			return getFallbackItem(entry);
		}
		int count = entry.minCount + (entry.maxCount > entry.minCount ? RANDOM.nextInt(entry.maxCount - entry.minCount + 1) : 0);
		return new ItemStack(itemOpt.get(), count);
	}

	/** 模组物品不存在时的替代品 */
	private static ItemStack getFallbackItem(LootEntry entry) {
		// 根据原始物品ID的命名空间决定替代品
		if (entry.itemId.startsWith("alexsmobs") || entry.itemId.startsWith("enderzoology") ||
			entry.itemId.startsWith("friendsandfoes") || entry.itemId.startsWith("mowziesmobs") ||
			entry.itemId.startsWith("cataclysm") || entry.itemId.startsWith("twilightforest") ||
			entry.itemId.startsWith("blue_skies") || entry.itemId.startsWith("modulargolems") ||
			entry.itemId.startsWith("mutantmonsters") || entry.itemId.startsWith("naturalist") ||
			entry.itemId.startsWith("happyghastmod") || entry.itemId.startsWith("aquaculturedelight")) {
			// 模组物品不存在时，用钻石/绿宝石/金锭替代
			int r = RANDOM.nextInt(3);
			return switch (r) {
				case 0 -> new ItemStack(Items.DIAMOND, 1 + RANDOM.nextInt(3));
				case 1 -> new ItemStack(Items.EMERALD, 2 + RANDOM.nextInt(4));
				default -> new ItemStack(Items.GOLD_INGOT, 4 + RANDOM.nextInt(8));
			};
		}
		if (entry.itemId.startsWith("randomsurprise")) {
			// 随机惊喜物品不存在时，用经验瓶替代
			return new ItemStack(Items.EXPERIENCE_BOTTLE, 2 + RANDOM.nextInt(4));
		}
		return new ItemStack(Items.IRON_INGOT, 4 + RANDOM.nextInt(8));
	}

	/** 箱子等级 */
	public enum ChestTier {
		COMMON,      // 普通箱子：60%基础资源 + 30%模组消耗品 + 10%模组装备
		UNCOMMON,    // 优秀箱子：35%基础 + 35%消耗品 + 25%装备 + 5%稀有
		RARE,        // 稀有箱子：15%基础 + 25%消耗品 + 40%装备 + 18%稀有 + 2%传说
		LEGENDARY    // 传说箱子：10%消耗品 + 25%装备 + 40%稀有 + 25%传说
	}
}
