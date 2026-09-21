package com.randomsurprise.client;

import com.randomsurprise.affix.Affix;
import com.randomsurprise.affix.AffixRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 客户端词条数据缓存
 * 接收服务端同步的：
 * - 玩家自己的词条列表（好+坏）
 * - 全局所有玩家的敌对词条列表（所有玩家可见）
 */
public class ClientAffixData {
	/** 当前玩家自己的词条ID列表 */
	private static List<String> ownAffixes = Collections.emptyList();
	/** 全局所有玩家的敌对词条ID列表（按玩家分组） */
	private static List<PlayerBadAffixes> allPlayerBadAffixes = Collections.emptyList();
	/** 缓存：本地玩家所有词条（update 时重建，避免每帧创建列表与注册表查找） */
	private static List<Affix> cachedOwnAffixes = Collections.emptyList();
	/** 缓存：本地玩家好词条 */
	private static List<Affix> cachedGoodAffixes = Collections.emptyList();
	/** 缓存：本地玩家坏词条 */
	private static List<Affix> cachedBadAffixes = Collections.emptyList();

	public record PlayerBadAffixes(String playerName, List<String> affixIds) {}

	public static void update(List<String> own, List<PlayerBadAffixes> allBad) {
		ownAffixes = new ArrayList<>(own);
		allPlayerBadAffixes = new ArrayList<>(allBad);
		// 重建三个缓存列表，getOwnAffixes 等直接返回缓存，避免每帧创建列表与注册表查找
		List<Affix> all = new ArrayList<>();
		List<Affix> good = new ArrayList<>();
		List<Affix> bad = new ArrayList<>();
		for (String id : ownAffixes) {
			Affix a = AffixRegistry.getById(id);
			if (a != null) {
				all.add(a);
				if (a.isGood()) good.add(a);
				else bad.add(a);
			}
		}
		cachedOwnAffixes = Collections.unmodifiableList(all);
		cachedGoodAffixes = Collections.unmodifiableList(good);
		cachedBadAffixes = Collections.unmodifiableList(bad);
	}

	public static List<Affix> getOwnAffixes() {
		return cachedOwnAffixes;
	}

	public static List<Affix> getOwnGoodAffixes() {
		return cachedGoodAffixes;
	}

	public static List<Affix> getOwnBadAffixes() {
		return cachedBadAffixes;
	}

	public static List<PlayerBadAffixes> getAllPlayerBadAffixes() {
		return allPlayerBadAffixes;
	}

	/** 获取全局敌对词条总数 */
	public static int getGlobalBadCount() {
		int count = 0;
		for (PlayerBadAffixes pba : allPlayerBadAffixes) {
			count += pba.affixIds().size();
		}
		return count;
	}
}
