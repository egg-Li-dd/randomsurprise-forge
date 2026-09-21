package com.randomsurprise.affix;

/**
 * 词条稀有度等级
 * 白 → 绿 → 蓝 → 紫 → 红 → 金
 */
public enum AffixRarity {
	COMMON(0xFFFFFF, "白", 0.40, 0),
	UNCOMMON(0x55FF55, "绿", 0.25, 1),
	RARE(0x5555FF, "蓝", 0.15, 2),
	EPIC(0xAA00FF, "紫", 0.10, 3),
	LEGENDARY(0xFF5555, "红", 0.06, 4),
	MYTHIC(0xFFAA00, "金", 0.04, 5);

	private final int color;
	private final String displayName;
	private final double weight; // 抽中权重
	private final int tier;

	AffixRarity(int color, String displayName, double weight, int tier) {
		this.color = color;
		this.displayName = displayName;
		this.weight = weight;
		this.tier = tier;
	}

	public int getColor() { return color; }
	public String getDisplayName() { return displayName; }
	public double getWeight() { return weight; }
	public int getTier() { return tier; }

	/**
	 * 根据随机值（0~1）返回对应稀有度
	 */
	public static AffixRarity roll(double randomValue) {
		double cumulative = 0;
		for (AffixRarity rarity : values()) {
			cumulative += rarity.weight;
			if (randomValue < cumulative) return rarity;
		}
		return COMMON;
	}
}
