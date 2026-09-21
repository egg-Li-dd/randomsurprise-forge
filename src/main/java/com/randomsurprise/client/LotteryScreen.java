package com.randomsurprise.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.randomsurprise.affix.Affix;
import com.randomsurprise.affix.AffixRarity;
import com.randomsurprise.affix.AffixRegistry;
import com.randomsurprise.network.LotteryConfirmPayload;
import com.randomsurprise.network.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 抽奖转盘界面（横向滚动老虎机式）—— 厚重金属沉浸风格（增强版）
 *
 * 在金属厚重美学（深灰铁 / 暗钢 / 暗金 / 血锈红 / 银色高光）基础上叠加：
 * - 环境余烬粒子（始终 15-20 个浮动金色/暗铁粒子）
 * - 暗角晕影（四边渐变暗化聚焦中心）
 * - 屏幕震动（轮盘停止时触发，递减衰减）
 * - 三层金属边框 + L形角落加固板 + 双排铆钉
 * - 增强格子（深斜面 4px 三层渐变 + 划痕 + 磨损斑点 + 分隔线 + hue 微调）
 * - 增强指针（更大 + 圆形枢轴 + 投影 + 火花粒子）
 * - 双层中心齿轮（外8齿CW + 内6齿CCW）+ 宝石脉冲 + 圆形阴影
 * - 速度线（progress < 0.4 时叠加）
 * - 五阶段揭晓动画（闪光 → 暗下 → 粒子爆发 → 火焰金光 → 稳定金边+文字）
 *
 * 数据源：AffixRegistry，词条含稀有度（rarity.getColor() 融入金属底色占 30%）。
 */
public class LotteryScreen extends Screen {
	private final String affixId;
	private Affix affix;

	private static final int CELL_WIDTH = 120;       // 每个词条格子宽度
	private static final int CELL_HEIGHT = 72;       // 格子高度
	private static final int VISIBLE_CELLS = 9;      // 屏幕可见格子数
	private static final int ANIMATION_DURATION = 200; // 10 秒动画（200 ticks，更慢更有紧张感，可空格加速/左击跳过）

	// ========== 金属配色方案（绝对不用彩虹色或花哨颜色） ==========
	// MC 方块配色对应关系说明（仅注释，不改颜色值）：
	//   COL_IRON_DARK    → 对应 IRON_BLOCK 暗面 #787878 调暗
	//   COL_STEEL        → 对应 DAMAGED_ANVIL 的铁质部分
	//   COL_DARK_GOLD    → 对应 GOLD_BLOCK 暗面 #B8860B
	//   COL_RUST_RED     → 对应 REDSTONE_BLOCK 暗化
	//   COL_MOSS         → 对应 MOSSY_COBBLESTONE 的苔藓色
	//   COL_RUST_* 系列  → 对应暴露在外的氧化铜/锈铁渐变
	private static final int COL_BG = 0xFF0A0A0C;          // 深黑底色
	private static final int COL_IRON_DARK = 0xFF2A2A2E;   // 深灰铁
	private static final int COL_STEEL = 0xFF3D3D42;        // 暗钢
	private static final int COL_STEEL_LIGHT = 0xFF4A4A52;  // 亮暗钢（铭牌底）
	private static final int COL_DARK_GOLD = 0xFFB8860B;    // 暗金
	private static final int COL_GOLD_BRIGHT = 0xFFD4A437; // 亮暗金
	private static final int COL_RUST_RED = 0xFF8B2500;     // 血锈红
	private static final int COL_RUST_GEM = 0xFFB23020;    // 宝石亮红
	private static final int COL_SILVER = 0xFF999999;       // 银色高光
	private static final int COL_SILVER_BRIGHT = 0xFFBCBCBC;// 亮银
	private static final int COL_TEXT = 0xFFCFCFCF;         // 铭文主色
	private static final int COL_GOOD = 0xFFC8A04A;        // 增益：黄铜色
	private static final int COL_BAD = 0xFFB05540;         // 敌对：锈红

	// ========== 废土感（Wasteland）配色 ==========
	private static final int COL_MOSS = 0xFF3D5C3D;          // 暗绿苔藓色（MOSSY_COBBLESTONE）
	private static final int COL_RUST_1 = 0xFF8B4513;        // 锈蚀棕（深）
	private static final int COL_RUST_2 = 0xFFA0522D;         // 锈蚀棕（中）
	private static final int COL_RUST_3 = 0xFFCD853F;        // 锈蚀棕（浅）
	private static final int COL_CRACK = 0xFF0A0A0A;         // 裂纹深黑
	private static final int COL_DUST = 0xFF6B6B6B;          // 灰尘灰
	private static final int COL_REDSTONE = 0xFFFF0000;      // 红石粉红
	private static final int COL_ENCHANT = 0xFF8B5CF6;       // 附魔紫
	private static final int COL_SCORCH = 0xFF8B2500;        // 灼烧暗红

	// 动画状态
	private int animationTicks = 0;
	private float currentOffset = 0;   // 当前滚动偏移
	private float targetOffset = 0;    // 目标偏移
	private boolean animationDone = false;
	private boolean stopSoundPlayed = false;
	private boolean chainSoundPlayed = false;

	// 指针逐格抖动 + 咔哒音
	private int lastCenterCell = -1;
	private int pointerShake = 0;
	private int lastClickTick = -10;

	// 揭晓动画计时（动画结束后递增）
	private int revealTicks = 0;

	// 中心齿轮旋转角度
	private float gearAngle = 0;

	// ========== 增强状态 ==========
	private int screenShake = 0;           // 屏幕震动剩余 ticks
	private float shakeIntensity = 0.0F;   // 震动强度
	private boolean flashTriggered = false; // 揭晓闪光是否已触发
	private boolean chimeStage1 = false;    // AMETHYST_BLOCK_CHIME 第一阶段
	private boolean chimeStage2 = false;    // AMETHYST_BLOCK_CHIME 第二阶段
	private boolean chimeStage3 = false;    // AMETHYST_BLOCK_CHIME 第三阶段
	private boolean burstTriggered = false; // 粒子爆发是否已触发

	// ========== 词条延迟生效：确认包发送状态 ==========
	private boolean confirmSent = false;   // 是否已向服务端发送 LotteryConfirmPayload（词条生效确认）

	// 粒子系统（总粒子数控制在 60 以内）
	private final List<AmbientParticle> ambientParticles = new ArrayList<>(); // 环境余烬（15-20个）
	private final List<SparkParticle> sparkParticles = new ArrayList<>();     // 指针火花
	private final List<BurstParticle> burstParticles = new ArrayList<>();     // 揭晓粒子爆发

	private final List<Affix> wheelAffixes = new ArrayList<>();
	private int targetIndex = 0;
	private int centerX, centerY;

	public LotteryScreen(String affixId) {
		super(Component.translatable("lottery.randomsurprise.title"));
		this.affixId = affixId;
	}

	@Override
	protected void init() {
		super.init();
		this.centerX = this.width / 2;
		this.centerY = this.height / 2;
		this.affix = AffixRegistry.getById(affixId);

		// 构建轮盘条带：所有词条重复 5 次以实现长距离滚动
		wheelAffixes.clear();
		List<Affix> all = AffixRegistry.getAll();
		for (int rep = 0; rep < 5; rep++) {
			wheelAffixes.addAll(all);
		}

		// 计算目标词条在条带中的索引（第 3 圈）
		int baseAffixIndex = 0;
		if (affix != null) {
			List<Affix> base = AffixRegistry.getAll();
			for (int i = 0; i < base.size(); i++) {
				if (base.get(i).getId().equals(affixId)) {
					baseAffixIndex = i;
					break;
				}
			}
		}
		int repsBefore = 2;
		targetIndex = repsBefore * all.size() + baseAffixIndex;
		this.targetOffset = targetIndex * CELL_WIDTH - (centerX - CELL_WIDTH / 2);

		this.animationTicks = 0;
		this.animationDone = false;
		this.currentOffset = 0;
		this.stopSoundPlayed = false;
		this.chainSoundPlayed = false;
		this.revealTicks = 0;
		this.lastCenterCell = -1;
		this.pointerShake = 0;
		this.lastClickTick = -10;
		this.gearAngle = 0;

		// 增强状态重置
		this.screenShake = 0;
		this.shakeIntensity = 0.0F;
		this.flashTriggered = false;
		this.chimeStage1 = false;
		this.chimeStage2 = false;
		this.chimeStage3 = false;
		this.burstTriggered = false;
		this.confirmSent = false;

		// 清空粒子列表
		ambientParticles.clear();
		sparkParticles.clear();
		burstParticles.clear();

		// 初始化环境余烬粒子（18 个）
		for (int i = 0; i < 18; i++) {
			ambientParticles.add(new AmbientParticle(
					(float) (Math.random() * this.width),
					(float) (Math.random() * this.height),
					this.width, this.height));
		}
	}

	@Override
	public void tick() {
		super.tick();
		// 齿轮缓慢旋转（始终旋转，作为氛围装饰）
		gearAngle += 1.2F;

		// 更新环境粒子（始终更新）
		for (AmbientParticle p : ambientParticles) {
			p.update();
		}
		// 更新火花粒子（移除已死亡的）
		for (int i = sparkParticles.size() - 1; i >= 0; i--) {
			SparkParticle p = sparkParticles.get(i);
			p.update();
			if (p.life <= 0) sparkParticles.remove(i);
		}
		// 更新爆发粒子
		for (int i = burstParticles.size() - 1; i >= 0; i--) {
			BurstParticle p = burstParticles.get(i);
			p.update();
			if (p.life <= 0) burstParticles.remove(i);
		}

		// 屏幕震动衰减
		if (screenShake > 0) {
			screenShake--;
			if (screenShake <= 0) {
				shakeIntensity = 0.0F;
			}
		}

		if (!animationDone) {
			animationTicks++;
			// 长按空格加速轮盘转动（每 tick 额外 +2 进度，共 3x 速，明显但不突兀）
			long window = Minecraft.getInstance().getWindow().getWindow();
			if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS) {
				animationTicks += 2;
			}
			float progress = Math.min(1.0F, (float) animationTicks / ANIMATION_DURATION);
			// 三次方缓出：沉重的铁轮逐渐减速
			float baseEased = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
			// 末端微微前冲后沉稳停住
			float rush = 0;
			if (progress > 0.88F) {
				float t = (progress - 0.88F) / 0.12F;
				rush = (float) (Math.sin(t * Math.PI) * CELL_WIDTH * 0.15F);
			}
			currentOffset = targetOffset * baseEased + rush;

			// 指针逐格抖动 + 铁砧咔哒音
			int centerCell = Math.round((currentOffset + centerX - CELL_WIDTH / 2) / CELL_WIDTH);
			if (centerCell != lastCenterCell) {
				lastCenterCell = centerCell;
				pointerShake = 5;
				if (animationTicks - lastClickTick >= 1) {
					lastClickTick = animationTicks;
					playClick(0.8F - progress * 0.2F);
					// 抖动时生成火花粒子（后期速度更快时火花更多）
					if (progress > 0.3F) {
						spawnSparks(progress > 0.7F ? 3 : 2);
					}
				}
			}
			if (pointerShake > 0) pointerShake--;

			if (progress >= 1.0F) {
				currentOffset = targetOffset;
				animationDone = true;
				if (!stopSoundPlayed) {
					stopSoundPlayed = true;
					playStopSound();
					// 触发屏幕震动
					screenShake = 8;
					shakeIntensity = 3.0F;
				}
				// 轮盘动画结束，发送确认包到服务端 —— 词条此时才真正生效
				sendConfirm();
			}
		} else {
			revealTicks++;
			// 揭晓阶段音效与粒子触发
			updateRevealEffects();
		}
	}

	// ===================== 词条延迟生效：确认与跳过 =====================

	/**
	 * 发送抽奖确认包到服务端（幂等：仅发送一次）。
	 * 服务端收到后才真正应用词条（addAffix → applyGoodEffects → 广播 → 同步）。
	 */
	private void sendConfirm() {
		if (confirmSent) return;
		confirmSent = true;
		ModNetworking.sendToServer(new LotteryConfirmPayload(false, Collections.singletonList(affixId)));
	}

	/**
	 * 左击跳过：立即完成轮盘动画并显示结果。
	 * 跳过旋转阶段与揭晓渐变，直接进入稳定结果展示，同时发送确认包。
	 */
	private void skipAnimation() {
		if (animationDone) return;
		animationTicks = ANIMATION_DURATION;
		currentOffset = targetOffset;
		animationDone = true;
		if (!stopSoundPlayed) {
			stopSoundPlayed = true;
			playStopSound();
			screenShake = 8;
			shakeIntensity = 3.0F;
		}
		// 跳过揭晓阶段渐变，直接进入稳定金边+文字状态（revealTicks >= 30 即稳定）
		revealTicks = 40;
		// 跳过后同样发送确认包，词条生效
		sendConfirm();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			if (!animationDone) {
				// 动画中左击：跳过动画
				skipAnimation();
			} else {
				// 动画结束后左击：关闭界面（词条已通过 sendConfirm 生效）
				this.onClose();
			}
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	// ===================== 音效方法 =====================

	/** 咔哒音：铁砧落地，音量 0.15，音调 0.6~0.8 随进度递减 */
	private void playClick(float pitch) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.playSound(SoundEvents.ANVIL_LAND, 0.15F, pitch);
		}
	}

	/** 停止音：铁砧落地(0.5/0.4) + 凋灵死亡(0.2/0.6)，金属落定 + 沉重共鸣 */
	private void playStopSound() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.playSound(SoundEvents.ANVIL_LAND, 0.5F, 0.4F);
			mc.player.playSound(SoundEvents.WITHER_DEATH, 0.2F, 0.6F);
		}
	}

	/** 揭晓金属锁链声：铁砧放置的清脆金属叮 */
	private void playChainSound() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.playSound(SoundEvents.ANVIL_PLACE, 0.3F, 0.8F);
		}
	}

	/** 揭晓闪光音：不死图腾使用 */
	private void playFlashSound() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.playSound(SoundEvents.TOTEM_USE, 0.4F, 0.8F);
		}
	}

	/** 金光升起音：紫水晶块叮当声（递增音调） */
	private void playChime(float pitch) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.3F, pitch);
		}
	}

	/** 金属共鸣音：下界合金块脚步声 */
	private void playResonance() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.playSound(SoundEvents.NETHERITE_BLOCK_STEP, 0.1F, 0.5F);
		}
	}

	// ===================== 揭晓效果触发 =====================

	/** 揭晓阶段的音效与粒子触发逻辑 */
	private void updateRevealEffects() {
		// Stage 1: 屏幕闪光 + TOTEM_USE 音效 (revealTicks 0)
		if (!flashTriggered && revealTicks == 0) {
			flashTriggered = true;
			playFlashSound();
		}

		// 金属锁链声（停定后短暂延迟，原功能保留）
		if (!chainSoundPlayed && revealTicks == 5) {
			chainSoundPlayed = true;
			playChainSound();
		}

		// Stage 3: 粒子爆发 (revealTicks 5)
		if (!burstTriggered && revealTicks == 5) {
			burstTriggered = true;
			spawnBurstParticles(25);
		}

		// 金光升起：3 个递增 AMETHYST_BLOCK_CHIME
		if (!chimeStage1 && revealTicks == 10) {
			chimeStage1 = true;
			playChime(0.8F);
		}
		if (!chimeStage2 && revealTicks == 20) {
			chimeStage2 = true;
			playChime(1.0F);
		}
		if (!chimeStage3 && revealTicks == 30) {
			chimeStage3 = true;
			playChime(1.2F);
		}

		// 金属共鸣：每 20 tick
		if (revealTicks > 0 && revealTicks % 20 == 0) {
			playResonance();
		}

		// Stage 5: 微弱烟雾粒子 (revealTicks > 30，偶尔生成)
		if (revealTicks > 30 && revealTicks % 8 == 0) {
			spawnSmokeParticle();
		}
	}

	// ===================== 渲染主入口 =====================

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		RenderSystem.enableBlend();

		// 深黑底色（不参与震动，铺满全屏，避免震动产生边缘缝隙）
		graphics.fill(0, 0, this.width, this.height, COL_BG);

		// 屏幕震动偏移计算
		int shakeX = 0, shakeY = 0;
		if (screenShake > 0) {
			shakeX = (int) ((Math.random() - 0.5) * shakeIntensity * 2);
			shakeY = (int) ((Math.random() - 0.5) * shakeIntensity * 2);
		}

		graphics.pose().pushPose();
		graphics.pose().translate(shakeX, shakeY, 0);

		// 径向金属纹理（中心微亮辉光）
		renderRadialGlow(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);

		int stripTop = centerY - CELL_HEIGHT / 2;
		int stripBot = centerY + CELL_HEIGHT / 2;

		// 环境余烬粒子（背景层）
		renderAmbientParticles(graphics);

		// 中心齿轮装饰（始终缓慢旋转）
		renderCenterGear(graphics, stripTop);

		// 动画期间：滚动标题（MC 风格阴影文字）+ 操作提示
		if (!animationDone) {
			drawShadowedCenteredString(graphics,
					Component.translatable("lottery.randomsurprise.spinning"),
					centerX, stripTop - 80, COL_DARK_GOLD);
			drawShadowedCenteredString(graphics,
					Component.translatable("lottery.randomsurprise.spinning_hint"),
					centerX, stripTop - 66, COL_SILVER);
		}

		// 轮盘条带
		renderWheelStrip(graphics, stripTop);

		// 速度线（动画初期 progress < 0.4）
		if (!animationDone) {
			float progress = Math.min(1.0F, (float) animationTicks / ANIMATION_DURATION);
			if (progress < 0.4F) {
				renderSpeedLines(graphics, stripTop, stripBot);
			}
		}

		// 左右渐变遮罩（聚焦）
		renderEdgeFade(graphics, stripTop, stripBot);

		// 三层金属边框 + 角落板 + 双排铆钉
		renderMetalFrame(graphics, stripTop, stripBot);

		// 中央金属聚焦孔（暗金内框）
		renderCenterFocus(graphics, stripTop, stripBot);

		// 增强铁质楔形指针
		renderPointer(graphics, stripTop);

		// 火花粒子
		renderSparkParticles(graphics);

		// 揭晓动画（动画结束后）
		if (animationDone && affix != null) {
			renderReveal(graphics, stripTop, stripBot);
		}

		// 爆发粒子（揭晓期间）
		renderBurstParticles(graphics);

		graphics.pose().popPose();

		// 暗角晕影（不震动，铺满全屏）
		renderVignette(graphics);

		// 屏幕闪光（不震动，全屏覆盖）
		renderScreenFlash(graphics);

		RenderSystem.disableBlend();
	}

	// ===================== 背景与径向纹理 =====================

	/** 中心微亮的暗色径向金属纹理（中心微亮 → 边缘极暗） */
	private void renderRadialGlow(GuiGraphics graphics) {
		int maxDim = Math.max(this.width, this.height);
		int[] radii = {maxDim, (int) (maxDim * 0.72), (int) (maxDim * 0.48), (int) (maxDim * 0.26)};
		int[] alphas = {6, 10, 16, 24};
		int tint = 0x0026262E;
		for (int i = 0; i < radii.length; i++) {
			fillCircle(graphics, centerX, centerY, radii[i], (alphas[i] << 24) | tint);
		}
	}

	// ===================== 暗角晕影 =====================

	/** 暗角晕影：上下各 80px 渐变黑(alpha 0→120)，左右各 60px 渐变黑，聚焦中心隧道效果 */
	private void renderVignette(GuiGraphics graphics) {
		// 上下渐变
		int vGrad = 80;
		for (int i = 0; i < vGrad; i++) {
			int alpha = (int) (120 * (1.0 - (double) i / vGrad));
			graphics.fill(0, i, this.width, i + 1, (alpha << 24));
			graphics.fill(0, this.height - 1 - i, this.width, this.height - i, (alpha << 24));
		}
		// 左右渐变
		int hGrad = 60;
		for (int i = 0; i < hGrad; i++) {
			int alpha = (int) (120 * (1.0 - (double) i / hGrad));
			graphics.fill(i, 0, i + 1, this.height, (alpha << 24));
			graphics.fill(this.width - 1 - i, 0, this.width - i, this.height, (alpha << 24));
		}
	}

	// ===================== 屏幕闪光 =====================

	/** 揭晓开始的全屏白色闪光（revealTicks 0-3，alpha 200→0） */
	private void renderScreenFlash(GuiGraphics graphics) {
		if (animationDone && revealTicks >= 0 && revealTicks < 4) {
			float t = revealTicks / 4.0F;
			int alpha = (int) (200 * (1.0F - t));
			if (alpha > 0) {
				graphics.fill(0, 0, this.width, this.height, (alpha << 24) | 0x00FFFFFF);
			}
		}
	}

	// ===================== 中心齿轮装饰（增强版） =====================

	/** 双层旋转金属齿轮（外8齿CW + 内6齿CCW）+ 宝石脉冲发光 + 圆形阴影 */
	private void renderCenterGear(GuiGraphics graphics, int stripTop) {
		int gx = centerX;
		int gy = stripTop - 54;
		float rad = (float) Math.toRadians(gearAngle);

		// 齿轮下方圆形阴影
		fillCircle(graphics, gx, gy + 3, 20, 0x55000000);

		// ===== 外层 8 齿（顺时针旋转） =====
		int outerTeeth = 8;
		int outerToothR = 21;
		for (int i = 0; i < outerTeeth; i++) {
			float a = rad + i * (float) (Math.PI * 2 / outerTeeth);
			int tx = gx + (int) (Math.cos(a) * outerToothR);
			int ty = gy + (int) (Math.sin(a) * outerToothR);
			graphics.fill(tx - 3, ty - 3, tx + 4, ty + 4, COL_STEEL_LIGHT);
			graphics.fill(tx - 3, ty - 3, tx + 4, ty - 2, COL_SILVER); // 齿顶高光
		}

		// 外齿轮主体：暗钢圆盘
		fillCircle(graphics, gx, gy, 18, COL_STEEL);
		// 外圈暗铁环（凹陷感）
		fillCircleRing(graphics, gx, gy, 18, 16, COL_IRON_DARK);

		// ===== 内层 6 齿（逆时针旋转，更快） =====
		float radInner = -rad * 1.5F;
		int innerTeeth = 6;
		int innerToothR = 13;
		for (int i = 0; i < innerTeeth; i++) {
			float a = radInner + i * (float) (Math.PI * 2 / innerTeeth);
			int tx = gx + (int) (Math.cos(a) * innerToothR);
			int ty = gy + (int) (Math.sin(a) * innerToothR);
			graphics.fill(tx - 2, ty - 2, tx + 3, ty + 3, COL_IRON_DARK);
			graphics.fill(tx - 2, ty - 2, tx + 3, ty - 1, COL_STEEL); // 高光
		}

		// ===== 废土感：齿间隙绿色氧化痕迹 =====
		// 外齿轮齿间隙（8 个，位于齿与齿之间）
		for (int i = 0; i < outerTeeth; i++) {
			float a = rad + (i + 0.5F) * (float) (Math.PI * 2 / outerTeeth);
			int tx = gx + (int) (Math.cos(a) * (outerToothR - 3));
			int ty = gy + (int) (Math.sin(a) * (outerToothR - 3));
			graphics.fill(tx, ty, tx + 1, ty + 1, withAlpha(COL_MOSS, 0x55));
		}
		// 内齿轮齿间隙（6 个）
		for (int i = 0; i < innerTeeth; i++) {
			float a = radInner + (i + 0.5F) * (float) (Math.PI * 2 / innerTeeth);
			int tx = gx + (int) (Math.cos(a) * (innerToothR - 2));
			int ty = gy + (int) (Math.sin(a) * (innerToothR - 2));
			graphics.fill(tx, ty, tx + 1, ty + 1, withAlpha(COL_MOSS, 0x66));
		}

		// 内圈暗铁盘
		fillCircle(graphics, gx, gy, 10, COL_IRON_DARK);
		// 中心轴：亮钢
		fillCircle(graphics, gx, gy, 6, COL_STEEL_LIGHT);
		fillCircle(graphics, gx, gy, 6, withAlpha(COL_SILVER, 0x44));

		// 宝石脉冲发光
		float pulse = (float) (Math.sin(gearAngle * 0.08) * 0.5 + 0.5);
		// 外发光（脉冲半径）
		int glowR = 6 + (int) (pulse * 2);
		fillCircle(graphics, gx, gy, glowR, withAlpha(COL_RUST_GEM, (int) (0x33 + 0x55 * pulse)));
		// 宝石核心
		fillCircle(graphics, gx, gy, 4, COL_RUST_RED);
		// 宝石高光点
		graphics.fill(gx - 1, gy - 1, gx, gy, COL_RUST_GEM);

		// ===== 废土感：宝石周围放射状灼烧纹（4 条暗色短线从宝石向外延伸） =====
		int[][] dirs = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
		for (int[] d : dirs) {
			int sx = gx + d[0] * 7;
			int sy = gy + d[1] * 7;
			int ex = sx + d[0] * 4;
			int ey = sy + d[1] * 4;
			// 1px 暗色短线（alpha 180）
			if (d[0] != 0) {
				int x1 = Math.min(sx, ex);
				int x2 = Math.max(sx, ex);
				graphics.fill(x1, sy, x2 + 1, sy + 1, withAlpha(COL_CRACK, 0xB4));
			} else {
				int y1 = Math.min(sy, ey);
				int y2 = Math.max(sy, ey);
				graphics.fill(sx, y1, sx + 1, y2 + 1, withAlpha(COL_CRACK, 0xB4));
			}
		}
	}

	// ===================== 轮盘条带 =====================

	/** 渲染横向滚动条带（金属铭牌） */
	private void renderWheelStrip(GuiGraphics graphics, int stripTop) {
		int firstVisibleIndex = Math.max(0, (int) (currentOffset / CELL_WIDTH) - 1);
		int lastVisibleIndex = Math.min(wheelAffixes.size() - 1,
				firstVisibleIndex + VISIBLE_CELLS + 2);

		for (int i = firstVisibleIndex; i <= lastVisibleIndex; i++) {
			Affix a = wheelAffixes.get(i);
			int cellX = i * CELL_WIDTH - (int) currentOffset;
			if (cellX + CELL_WIDTH < 0 || cellX > this.width) continue;
			renderCell(graphics, cellX, stripTop, a, i);
		}
	}

	/** 渲染增强金属铭牌格子（深斜面 + 划痕 + 磨损斑点 + 分隔线 + hue微调） */
	private void renderCell(GuiGraphics graphics, int x, int y, Affix affix, int index) {
		AffixRarity rarity = affix.getRarity();
		// 金属底色占 70%，稀有度颜色占 30%，hue 根据 index 微调 ±5
		int baseMetal = mix(COL_STEEL_LIGHT, rarity.getColor(), 0.30F);
		int hueShift = ((index % 3) - 1) * 5;
		if (hueShift != 0) {
			baseMetal = shiftHue(baseMetal, hueShift);
		}

		// 主体金属底
		graphics.fill(x, y, x + CELL_WIDTH, y + CELL_HEIGHT, baseMetal);

		// 深斜面顶部 4px 高光（3层渐变）
		graphics.fill(x, y, x + CELL_WIDTH, y + 1, COL_SILVER_BRIGHT);
		graphics.fill(x, y + 1, x + CELL_WIDTH, y + 2, withAlpha(COL_SILVER, 0xBB));
		graphics.fill(x, y + 2, x + CELL_WIDTH, y + 3, withAlpha(COL_SILVER, 0x77));
		graphics.fill(x, y + 3, x + CELL_WIDTH, y + 4, withAlpha(COL_SILVER, 0x33));

		// 深斜面底部 4px 阴影（3层渐变）
		graphics.fill(x, y + CELL_HEIGHT - 4, x + CELL_WIDTH, y + CELL_HEIGHT - 3, withAlpha(COL_IRON_DARK, 0x44));
		graphics.fill(x, y + CELL_HEIGHT - 3, x + CELL_WIDTH, y + CELL_HEIGHT - 2, withAlpha(COL_IRON_DARK, 0x88));
		graphics.fill(x, y + CELL_HEIGHT - 2, x + CELL_WIDTH, y + CELL_HEIGHT - 1, withAlpha(COL_IRON_DARK, 0xCC));
		graphics.fill(x, y + CELL_HEIGHT - 1, x + CELL_WIDTH, y + CELL_HEIGHT, COL_IRON_DARK);

		// 左侧高光 / 右侧阴影（立体感）
		graphics.fill(x, y, x + 1, y + CELL_HEIGHT, withAlpha(COL_SILVER, 0x66));
		graphics.fill(x + CELL_WIDTH - 1, y, x + CELL_WIDTH, y + CELL_HEIGHT, withAlpha(COL_IRON_DARK, 0xCC));

		// 随机划痕：2-3 条 1px 暗色斜线（基于 cell index 种子的确定性伪随机）
		int seed = index * 1103515245 + 12345;
		int scratchCount = 2 + (Math.abs(seed) % 2);
		for (int s = 0; s < scratchCount; s++) {
			seed = seed * 1103515245 + 12345;
			int sx = x + 4 + (Math.abs(seed) % (CELL_WIDTH - 12));
			seed = seed * 1103515245 + 12345;
			int sy = y + 8 + (Math.abs(seed) % (CELL_HEIGHT - 16));
			seed = seed * 1103515245 + 12345;
			int len = 3 + (Math.abs(seed) % 4);
			for (int d = 0; d < len; d++) {
				graphics.fill(sx + d, sy + d, sx + d + 1, sy + d + 1, withAlpha(COL_IRON_DARK, 0x88));
			}
		}

		// 磨损斑点：2-3 个 2x2 暗色斑点
		seed = index * 40503 + 12345;
		int spotCount = 2 + (Math.abs(seed) % 2);
		for (int s = 0; s < spotCount; s++) {
			seed = seed * 1103515245 + 12345;
			int sx = x + 3 + (Math.abs(seed) % (CELL_WIDTH - 8));
			seed = seed * 1103515245 + 12345;
			int sy = y + 3 + (Math.abs(seed) % (CELL_HEIGHT - 8));
			graphics.fill(sx, sy, sx + 2, sy + 2, withAlpha(COL_IRON_DARK, 0x66));
		}

		// 废土感：锈蚀斑点（用 cell index 作为 seed，确定性伪随机）
		renderRustPatches(graphics, x, y, CELL_WIDTH, CELL_HEIGHT, index * 7 + 31);

		// 格子间分隔线：1px 深灰铁竖线 + 1px 银色高光（右侧）
		graphics.fill(x + CELL_WIDTH, y, x + CELL_WIDTH + 1, y + CELL_HEIGHT, COL_IRON_DARK);
		graphics.fill(x + CELL_WIDTH + 1, y, x + CELL_WIDTH + 2, y + CELL_HEIGHT, withAlpha(COL_SILVER, 0x44));

		// 稀有度名称（铭文，§l 粗体增强雕刻感）
		String rarityText = "\u00A7l" + rarity.getDisplayName();
		int textWidth = this.font.width(rarityText);
		graphics.drawString(this.font, rarityText,
				x + (CELL_WIDTH - textWidth) / 2, y + 8, COL_SILVER_BRIGHT);

		// 类型标识（◆ 增益 / ◆ 敌对）
		boolean good = affix.isGood();
		String typeText = good ? "\u25C6 \u589E\u76CA" : "\u25C6 \u654C\u5BF9";
		int typeColor = good ? COL_GOOD : COL_BAD;
		int typeWidth = this.font.width(typeText);
		graphics.drawString(this.font, typeText,
				x + (CELL_WIDTH - typeWidth) / 2, y + 24, typeColor);

		// 词条简称
		Component nameComp = Component.translatable(affix.getNameKey());
		String nameText = nameComp.getString();
		if (nameText.length() > 10) nameText = nameText.substring(0, 10);
		int nameWidth = this.font.width(nameText);
		graphics.drawString(this.font, nameText,
				x + (CELL_WIDTH - nameWidth) / 2, y + 44, COL_TEXT);
	}

	// ===================== 速度线 =====================

	/** 速度线：半透明银色水平线，从右向左移动（动画初期叠加） */
	private void renderSpeedLines(GuiGraphics graphics, int stripTop, int stripBot) {
		int numLines = 6;
		for (int i = 0; i < numLines; i++) {
			int y = stripTop + (CELL_HEIGHT / (numLines + 1)) * (i + 1);
			int speed = 30 + i * 5;
			int offset = (animationTicks * speed + i * 137) % (this.width + 120);
			int x = this.width - offset;
			int len = 30 + (i * 13) % 40;
			int alpha = 0x44 + (i * 17) % 0x33;
			if (x + len > 0 && x < this.width) {
				graphics.fill(x, y, x + len, y + 1, (alpha << 24) | (COL_SILVER & 0x00FFFFFF));
			}
		}
	}

	// ===================== 金属边框 + 角落板 + 铆钉 =====================

	/** 三层金属边框（外深灰铁6px + 中暗金2px + 内亮钢1px）+ L形角落板 + 双排铆钉 */
	private void renderMetalFrame(GuiGraphics graphics, int stripTop, int stripBot) {
		// 外层深灰铁（6px 厚）
		graphics.fill(0, stripTop - 9, this.width, stripTop - 3, COL_IRON_DARK);
		graphics.fill(0, stripBot + 3, this.width, stripBot + 9, COL_IRON_DARK);
		// 外层顶部银色高光线
		graphics.fill(0, stripTop - 9, this.width, stripTop - 8, withAlpha(COL_SILVER, 0x77));
		// 中层暗金（2px）
		graphics.fill(0, stripTop - 3, this.width, stripTop - 1, COL_DARK_GOLD);
		graphics.fill(0, stripBot + 1, this.width, stripBot + 3, COL_DARK_GOLD);
		// 内层亮钢（1px）
		graphics.fill(0, stripTop - 1, this.width, stripTop, COL_SILVER_BRIGHT);
		graphics.fill(0, stripBot, this.width, stripBot + 1, COL_SILVER_BRIGHT);

		// ===== 废土感叠加：锈蚀斑点 + 裂纹 + 红石粉痕迹 =====
		// 上边框：锈蚀（seed 101）+ 裂纹（seed 202）+ 红石粉（seed 303）
		renderRustPatches(graphics, 0, stripTop - 9, this.width, 6, 101);
		renderCracks(graphics, 0, stripTop - 9, this.width, 6, 202);
		renderRedstoneTraces(graphics, 0, stripTop - 6, this.width, 303);
		// 下边框：锈蚀（seed 404）+ 裂纹（seed 505）+ 红石粉（seed 606）
		renderRustPatches(graphics, 0, stripBot + 3, this.width, 6, 404);
		renderCracks(graphics, 0, stripBot + 3, this.width, 6, 505);
		renderRedstoneTraces(graphics, 0, stripBot + 6, this.width, 606);

		// 四角 L 形加固板（每边 24px，3px 厚）
		renderCornerPlate(graphics, 0, stripTop - 9, true, true);     // 左上
		renderCornerPlate(graphics, this.width, stripTop - 9, false, true);  // 右上
		renderCornerPlate(graphics, 0, stripBot + 9, true, false);   // 左下
		renderCornerPlate(graphics, this.width, stripBot + 9, false, false); // 右下

		// 双排铆钉：上下各两排，第二排偏移 spacing/2
		int spacing = 64;
		int topRivY1 = stripTop - 6;
		int topRivY2 = stripTop - 3;
		int botRivY1 = stripBot + 3;
		int botRivY2 = stripBot + 6;
		for (int rx = spacing / 2; rx < this.width; rx += spacing) {
			renderRivet(graphics, rx, topRivY1);
			renderRivet(graphics, rx + spacing / 2, topRivY2);
			renderRivet(graphics, rx, botRivY1);
			renderRivet(graphics, rx + spacing / 2, botRivY2);
		}
	}

	/** L 形角落加固板（水平臂 + 垂直臂 + 斜切角 + 高光） */
	private void renderCornerPlate(GuiGraphics graphics, int cornerX, int cornerY, boolean left, boolean top) {
		int armLen = 24;
		int thick = 3;
		int chamfSize = 4; // 斜切角大小

		if (left && top) {
			// 左上：水平臂 + 垂直臂
			graphics.fill(cornerX, cornerY, cornerX + armLen, cornerY + thick, COL_STEEL);
			graphics.fill(cornerX, cornerY, cornerX + thick, cornerY + armLen, COL_STEEL);
			// 高光（外边缘）
			graphics.fill(cornerX, cornerY, cornerX + armLen, cornerY + 1, COL_SILVER_BRIGHT);
			graphics.fill(cornerX, cornerY, cornerX + 1, cornerY + armLen, COL_SILVER_BRIGHT);
			// 斜切角（内角尖端）
			for (int c = 0; c < chamfSize; c++) {
				graphics.fill(cornerX + thick + c, cornerY + thick, cornerX + thick + c + 1, cornerY + thick + chamfSize - c, COL_STEEL);
			}
			// 苔藓氧化：内侧边缘 2px 渐变绿
			for (int c = 0; c < chamfSize; c++) {
				int a = 0x55 - c * 0x10;
				if (a < 0) a = 0;
				graphics.fill(cornerX + thick + c, cornerY + thick + chamfSize - c - 1,
						cornerX + thick + c + 1, cornerY + thick + chamfSize - c, withAlpha(COL_MOSS, a));
			}
			// 裂纹（角落板加裂纹）
			renderCracks(graphics, cornerX, cornerY, armLen, armLen, 11);
		} else if (!left && top) {
			// 右上
			graphics.fill(cornerX - armLen, cornerY, cornerX, cornerY + thick, COL_STEEL);
			graphics.fill(cornerX - thick, cornerY, cornerX, cornerY + armLen, COL_STEEL);
			graphics.fill(cornerX - armLen, cornerY, cornerX, cornerY + 1, COL_SILVER_BRIGHT);
			graphics.fill(cornerX - 1, cornerY, cornerX, cornerY + armLen, COL_SILVER_BRIGHT);
			for (int c = 0; c < chamfSize; c++) {
				graphics.fill(cornerX - thick - c - 1, cornerY + thick, cornerX - thick - c, cornerY + thick + chamfSize - c, COL_STEEL);
			}
			// 苔藓氧化：内侧边缘
			for (int c = 0; c < chamfSize; c++) {
				int a = 0x55 - c * 0x10;
				if (a < 0) a = 0;
				graphics.fill(cornerX - thick - c - 1, cornerY + thick + chamfSize - c - 1,
						cornerX - thick - c, cornerY + thick + chamfSize - c, withAlpha(COL_MOSS, a));
			}
		} else if (left && !top) {
			// 左下
			graphics.fill(cornerX, cornerY - thick, cornerX + armLen, cornerY, COL_STEEL);
			graphics.fill(cornerX, cornerY - armLen, cornerX + thick, cornerY, COL_STEEL);
			graphics.fill(cornerX, cornerY - 1, cornerX + armLen, cornerY, COL_SILVER_BRIGHT);
			graphics.fill(cornerX, cornerY - armLen, cornerX + 1, cornerY, COL_SILVER_BRIGHT);
			for (int c = 0; c < chamfSize; c++) {
				graphics.fill(cornerX + thick + c, cornerY - thick - chamfSize + c, cornerX + thick + c + 1, cornerY - thick, COL_STEEL);
			}
			// 苔藓氧化：内侧边缘
			for (int c = 0; c < chamfSize; c++) {
				int a = 0x55 - c * 0x10;
				if (a < 0) a = 0;
				graphics.fill(cornerX + thick + c, cornerY - thick - chamfSize + c,
						cornerX + thick + c + 1, cornerY - thick - chamfSize + c + 1, withAlpha(COL_MOSS, a));
			}
		} else {
			// 右下
			graphics.fill(cornerX - armLen, cornerY - thick, cornerX, cornerY, COL_STEEL);
			graphics.fill(cornerX - thick, cornerY - armLen, cornerX, cornerY, COL_STEEL);
			graphics.fill(cornerX - armLen, cornerY - 1, cornerX, cornerY, COL_SILVER_BRIGHT);
			graphics.fill(cornerX - 1, cornerY - armLen, cornerX, cornerY, COL_SILVER_BRIGHT);
			for (int c = 0; c < chamfSize; c++) {
				graphics.fill(cornerX - thick - c - 1, cornerY - thick - chamfSize + c, cornerX - thick - c, cornerY - thick, COL_STEEL);
			}
			// 苔藓氧化：内侧边缘
			for (int c = 0; c < chamfSize; c++) {
				int a = 0x55 - c * 0x10;
				if (a < 0) a = 0;
				graphics.fill(cornerX - thick - c - 1, cornerY - thick - chamfSize + c,
						cornerX - thick - c, cornerY - thick - chamfSize + c + 1, withAlpha(COL_MOSS, a));
			}
			// 裂纹（角落板加裂纹）
			renderCracks(graphics, cornerX - armLen, cornerY - armLen, armLen, armLen, 44);
		}
	}

	/**
	 * 单颗铆钉：像素艺术风格 2x2 方块（MC 像素美学）+ 左上高光 + 右下暗部。
	 * 根据位置 x 伪随机判定约 15% 概率为"破损"铆钉：
	 *   - 破损时只画暗色底，不画高光，并附带 1-2 个小裂纹像素 + 脱落碎片
	 */
	private void renderRivet(GuiGraphics graphics, int x, int y) {
		// 像素艺术铆钉：2x2 主体方块（保持 MC 像素方块美学）
		// 破损判定（基于位置 x 伪随机，约 15%）
		int hash = (x * 1103515245 + 12345) & 0x7FFFFFFF;
		boolean broken = (hash % 100) < 15;

		if (broken) {
			// 破损铆钉：只画暗色底（无高光）
			graphics.fill(x - 1, y - 1, x + 1, y + 1, COL_IRON_DARK);
			// 旁边 1-2 个小裂纹像素
			int crackHash = hash / 100;
			int cn = 1 + (crackHash % 2);
			for (int i = 0; i < cn; i++) {
				crackHash = crackHash * 1103515245 + 12345;
				int cx = x + 2 + (crackHash % 3);
				crackHash = crackHash * 1103515245 + 12345;
				int cy = y - 1 + (crackHash % 3);
				graphics.fill(cx, cy, cx + 1, cy + 1, withAlpha(COL_CRACK, 0xC8));
			}
			// 脱落铆钉碎片（2x1 暗色像素，偏移 2px）
			graphics.fill(x + 2, y + 1, x + 4, y + 2, withAlpha(COL_IRON_DARK, 0x88));
		} else {
			// 像素艺术铆钉：2x2 主体（银色）
			graphics.fill(x - 1, y - 1, x + 1, y + 1, COL_SILVER);
			// 高光：1x1 像素在左上角
			graphics.fill(x - 1, y - 1, x, y, COL_SILVER_BRIGHT);
			// 暗部：1x1 像素在右下角
			graphics.fill(x, y, x + 1, y + 1, withAlpha(COL_IRON_DARK, 0xAA));
			// 苔藓氧化：铆钉周围微弱绿色晕（3x3 alpha 20）
			graphics.fill(x - 2, y - 2, x + 3, y + 3, withAlpha(COL_MOSS, 0x14));
		}
	}

	// ===================== 废土感装饰：锈蚀 / 裂纹 / 红石 / 苔藓 =====================

	/**
	 * 锈蚀斑点：基于 seed 的确定性伪随机生成 3-5 个锈蚀斑点。
	 * 颜色取自 #8B4513 / #A0522D / #CD853F（半透明 alpha 60-100）。
	 * 斑点为 2x2 ~ 4x3 像素的不规则矩形。
	 */
	private void renderRustPatches(GuiGraphics graphics, int x, int y, int w, int h, int seed) {
		int s = seed * 1103515245 + 12345;
		int count = 3 + (Math.abs(s) % 3); // 3-5 个
		int[] rustColors = {COL_RUST_1, COL_RUST_2, COL_RUST_3};
		for (int i = 0; i < count; i++) {
			s = s * 1103515245 + 12345;
			int px = x + 2 + Math.abs(s) % Math.max(1, w - 4);
			s = s * 1103515245 + 12345;
			int py = y + 2 + Math.abs(s) % Math.max(1, h - 4);
			s = s * 1103515245 + 12345;
			int pw = 2 + (Math.abs(s) % 3); // 2-4 像素宽
			s = s * 1103515245 + 12345;
			int ph = 2 + (Math.abs(s) % 2); // 2-3 像素高
			s = s * 1103515245 + 12345;
			int color = rustColors[Math.abs(s) % 3];
			s = s * 1103515245 + 12345;
			int alpha = 60 + (Math.abs(s) % 41); // alpha 60-100
			graphics.fill(px, py, px + pw, py + ph, withAlpha(color, alpha));
		}
	}

	/**
	 * 裂纹：基于 seed 生成 1-2 条锯齿状裂纹。
	 * 从某个起点开始，分 3-5 段，每段方向随机偏转 ±30°，长度 5-15px。
	 * 颜色为深黑 #0A0A0A（alpha 200）。
	 */
	private void renderCracks(GuiGraphics graphics, int x, int y, int w, int h, int seed) {
		int s = seed * 1103515245 + 12345;
		int crackCount = 1 + (Math.abs(s) % 2); // 1-2 条
		for (int c = 0; c < crackCount; c++) {
			s = s * 1103515245 + 12345;
			int cx = x + 3 + Math.abs(s) % Math.max(1, w - 6);
			s = s * 1103515245 + 12345;
			int cy = y + 2 + Math.abs(s) % Math.max(1, h - 4);
			s = s * 1103515245 + 12345;
			int segments = 3 + (Math.abs(s) % 3); // 3-5 段
			s = s * 1103515245 + 12345;
			float angle = (Math.abs(s) % 360) * (float) Math.PI / 180.0F;
			for (int seg = 0; seg < segments; seg++) {
				s = s * 1103515245 + 12345;
				// ±30° 偏转
				float delta = ((Math.abs(s) % 61) - 30) * (float) Math.PI / 180.0F;
				angle += delta;
				s = s * 1103515245 + 12345;
				int len = 5 + (Math.abs(s) % 11); // 5-15px
				int nx = cx + (int) (Math.cos(angle) * len);
				int ny = cy + (int) (Math.sin(angle) * len);
				// 用 1px 步进画线段（锯齿感）
				int steps = Math.max(Math.abs(nx - cx), Math.abs(ny - cy));
				if (steps == 0) steps = 1;
				for (int st = 0; st <= steps; st++) {
					int px = cx + (nx - cx) * st / steps;
					int py = cy + (ny - cy) * st / steps;
					graphics.fill(px, py, px + 1, py + 1, withAlpha(COL_CRACK, 0xC8));
				}
				cx = nx;
				cy = ny;
			}
		}
	}

	/**
	 * 红石粉痕迹（MC 红石电路感）：
	 * 在区域内画 2-3 个红石粉点（1x1 #FF0000 alpha 80），
	 * 粉点之间用极淡红色线连接（alpha 20）。
	 */
	private void renderRedstoneTraces(GuiGraphics graphics, int x, int y, int w, int seed) {
		int s = seed * 1103515245 + 12345;
		int nodeCount = 2 + (Math.abs(s) % 2); // 2-3 个
		int[] nodesX = new int[nodeCount];
		int[] nodesY = new int[nodeCount];
		for (int i = 0; i < nodeCount; i++) {
			s = s * 1103515245 + 12345;
			nodesX[i] = x + 2 + Math.abs(s) % Math.max(1, w - 4);
			s = s * 1103515245 + 12345;
			nodesY[i] = y + 1 + Math.abs(s) % 3; // 仅在边框厚度范围内
		}
		// 粉点
		for (int i = 0; i < nodeCount; i++) {
			graphics.fill(nodesX[i], nodesY[i], nodesX[i] + 1, nodesY[i] + 1,
					withAlpha(COL_REDSTONE, 0x50));
		}
		// 极淡连线
		for (int i = 0; i < nodeCount - 1; i++) {
			int x1 = nodesX[i], y1 = nodesY[i];
			int x2 = nodesX[i + 1], y2 = nodesY[i + 1];
			int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
			if (steps == 0) steps = 1;
			for (int st = 0; st <= steps; st++) {
				int px = x1 + (x2 - x1) * st / steps;
				int py = y1 + (y2 - y1) * st / steps;
				graphics.fill(px, py, px + 1, py + 1, withAlpha(COL_REDSTONE, 0x14));
			}
		}
	}

	// ===================== 中央聚焦孔 =====================

	/** 中央金属聚焦孔：暗金内框（沉稳，不脉冲），引导视线到落点 */
	private void renderCenterFocus(GuiGraphics graphics, int stripTop, int stripBot) {
		int x = centerX - CELL_WIDTH / 2;
		graphics.fill(x - 2, stripTop - 2, x + CELL_WIDTH + 2, stripTop, COL_DARK_GOLD);
		graphics.fill(x - 2, stripBot, x + CELL_WIDTH + 2, stripBot + 2, COL_DARK_GOLD);
		graphics.fill(x - 2, stripTop - 2, x, stripBot + 2, COL_DARK_GOLD);
		graphics.fill(x + CELL_WIDTH, stripTop - 2, x + CELL_WIDTH + 2, stripBot + 2, COL_DARK_GOLD);
		graphics.fill(x - 3, stripTop - 3, x + CELL_WIDTH + 3, stripTop - 2, withAlpha(COL_SILVER, 0x44));
	}

	// ===================== 增强铁质楔形指针 =====================

	/** 增强铁质楔形指针（更大 h=20 + 圆形枢轴 + 下方投影 + 抖动火花） */
	private void renderPointer(GuiGraphics graphics, int stripTop) {
		int px = centerX;
		int shakeY = pointerShake > 0 ? (int) (Math.sin(pointerShake * 1.8) * 2) : 0;
		int pyTop = stripTop - 24 + shakeY;
		int h = 20; // 更大

		// 下方投影
		graphics.fill(px - 10, pyTop + h + 2, px + 11, pyTop + h + 3, 0x55000000);

		// 圆形枢轴（半径 5px）
		fillCircle(graphics, px, pyTop - 3, 5, COL_IRON_DARK);
		fillCircle(graphics, px, pyTop - 3, 4, COL_STEEL);
		fillCircle(graphics, px, pyTop - 4, 3, COL_STEEL_LIGHT);
		fillCircle(graphics, px - 1, pyTop - 4, 2, withAlpha(COL_SILVER, 0x88));

		// 楔形指针主体：逐行绘制，宽度递减，颜色由亮银渐变到暗铁
		for (int i = 0; i < h; i++) {
			float t = i / (float) h;
			int halfW = (int) (12 * (1.0F - t * t)); // 更宽的楔形
			if (halfW < 0) halfW = 0;
			int shade = mix(COL_SILVER_BRIGHT, COL_IRON_DARK, t);
			graphics.fill(px - halfW, pyTop + i, px + halfW + 1, pyTop + i + 1, shade);
		}
		// 指针尖端
		graphics.fill(px, pyTop + h, px + 1, pyTop + h + 3, COL_IRON_DARK);
		// 中心高光线
		graphics.fill(px, pyTop, px + 1, pyTop + h, withAlpha(COL_SILVER_BRIGHT, 0x66));

		// ===== 废土感：指针尖端灼烧痕迹（3x3 半透明黑色渐变） =====
		int tipY = pyTop + h + 3;
		graphics.fill(px - 1, tipY - 1, px + 2, tipY + 2, 0x88000000);
		graphics.fill(px, tipY, px + 1, tipY + 1, 0xCC000000);
	}

	// ===================== 边缘渐变遮罩 =====================

	/** 左右暗色渐变遮罩（向深黑底色过渡） */
	private void renderEdgeFade(GuiGraphics graphics, int stripTop, int stripBot) {
		int fadeWidth = 90;
		for (int i = 0; i < fadeWidth; i++) {
			int alpha = (int) (0xFF * (1.0 - (double) i / fadeWidth));
			graphics.fill(i, stripTop, i + 1, stripBot, (alpha << 24) | (COL_BG & 0x00FFFFFF));
			graphics.fill(this.width - 1 - i, stripTop, this.width - i, stripBot, (alpha << 24) | (COL_BG & 0x00FFFFFF));
		}
	}

	// ===================== 增强揭晓动画（五阶段） =====================

	/**
	 * 增强揭晓动画（五阶段）：
	 * 1. 屏幕闪光(0-3)：全屏白色 alpha 200→0 + TOTEM_USE 音效
	 * 2. 中奖格暗下(0-10)
	 * 3. 粒子爆发(5-30)：25 个金色火花从中心向外辐射 + 重力
	 * 4. 火焰金光(8-40)：波浪形边缘(sin±3px) + 亮度脉动
	 * 5. 金光稳定 + 文字(>30)：脉冲金边 + 2px 发光晕 + 微弱烟雾粒子
	 */
	private void renderReveal(GuiGraphics graphics, int stripTop, int stripBot) {
		int x = centerX - CELL_WIDTH / 2;

		// Stage 2: 中奖格暗下（revealTicks 0~10）
		if (revealTicks < 10) {
			float dt = revealTicks / 10.0F;
			int da = (int) (dt * 175);
			graphics.fill(x, stripTop, x + CELL_WIDTH, stripBot, (da << 24) | 0x00000000);
		}

		// Stage 4: 火焰金光（revealTicks 8~40）：波浪形边缘 + 亮度脉动
		if (revealTicks >= 8 && revealTicks < 40) {
			float ft = (revealTicks - 8) / 32.0F;
			int glowA = (int) (100 + 80 * Math.sin(ft * Math.PI));
			// 主体金光
			graphics.fill(x, stripTop, x + CELL_WIDTH, stripBot,
					(glowA << 24) | (COL_DARK_GOLD & 0x00FFFFFF));
			// 波浪形上下边缘高光（sin±3px）
			for (int xx = 0; xx < CELL_WIDTH; xx += 4) {
				int waveTop = (int) (Math.sin((xx + revealTicks * 3) * 0.25) * 3);
				int waveBot = (int) (Math.sin((xx + revealTicks * 3 + 3) * 0.25) * 3);
				int brightA = Math.min(255, glowA + 50);
				graphics.fill(x + xx, stripTop + waveTop, x + xx + 4, stripTop + waveTop + 2,
						(brightA << 24) | (COL_GOLD_BRIGHT & 0x00FFFFFF));
				graphics.fill(x + xx, stripBot + waveBot - 2, x + xx + 4, stripBot + waveBot,
						(brightA << 24) | (COL_GOLD_BRIGHT & 0x00FFFFFF));
			}
			// ===== 废土感：金光周围灼烧边缘效果（暗红色 #8B2500 渐变） =====
			int scorchA = (int) (60 + 40 * Math.sin(ft * Math.PI));
			// 上下灼烧边缘
			graphics.fill(x, stripTop - 2, x + CELL_WIDTH, stripTop, withAlpha(COL_SCORCH, scorchA));
			graphics.fill(x, stripBot, x + CELL_WIDTH, stripBot + 2, withAlpha(COL_SCORCH, scorchA));
			graphics.fill(x - 2, stripTop, x, stripBot, withAlpha(COL_SCORCH, scorchA));
			graphics.fill(x + CELL_WIDTH, stripTop, x + CELL_WIDTH + 2, stripBot, withAlpha(COL_SCORCH, scorchA));
		}

		// Stage 5: 金光稳定 + 脉冲金边 + 2px 发光晕（revealTicks >= 30）
		if (revealTicks >= 30) {
			float pulse = (float) (Math.sin(revealTicks * 0.2) * 0.5 + 0.5);
			// 内部辉光
			int a = (int) (50 + 35 * pulse);
			graphics.fill(x, stripTop, x + CELL_WIDTH, stripBot,
					(a << 24) | (COL_DARK_GOLD & 0x00FFFFFF));
			// 2px 发光晕边框
			int glowA = (int) (0x88 + 0x77 * pulse);
			int glowC = (glowA << 24) | (COL_GOLD_BRIGHT & 0x00FFFFFF);
			for (int b = 0; b < 2; b++) {
				graphics.fill(x - 1 - b, stripTop - 1 - b, x + CELL_WIDTH + 1 + b, stripTop - b, glowC);
				graphics.fill(x - 1 - b, stripBot + b, x + CELL_WIDTH + 1 + b, stripBot + 1 + b, glowC);
				graphics.fill(x - 1 - b, stripTop - 1 - b, x - b, stripBot + 1 + b, glowC);
				graphics.fill(x + CELL_WIDTH + b, stripTop - 1 - b, x + CELL_WIDTH + 1 + b, stripBot + 1 + b, glowC);
			}

			// ===== MC 附魔光效（Enchantment Glint）：revealTicks >= 34 稳定阶段 =====
			// 周期性闪烁的紫色像素行，每隔 2px 画一条水平线
			// 闪烁频率：sin(tick * 0.15) 控制 alpha 0~60
			if (revealTicks >= 34) {
				float glintPulse = (float) (Math.sin(revealTicks * 0.15) * 0.5 + 0.5);
				int glintA = (int) (60 * glintPulse);
				if (glintA > 0) {
					int glintColor = (glintA << 24) | (COL_ENCHANT & 0x00FFFFFF);
					for (int yy = stripTop + 2; yy < stripBot - 2; yy += 2) {
						graphics.fill(x + 2, yy, x + CELL_WIDTH - 2, yy + 1, glintColor);
					}
				}
				// 灼烧边缘持续（暗红渐变，较淡）
				int scorchA2 = (int) (40 + 20 * pulse);
				graphics.fill(x - 1, stripTop - 1, x + CELL_WIDTH + 1, stripTop, withAlpha(COL_SCORCH, scorchA2));
				graphics.fill(x - 1, stripBot, x + CELL_WIDTH + 1, stripBot + 1, withAlpha(COL_SCORCH, scorchA2));
			}
		}

		// 文字浮现（revealTicks > 16）
		if (revealTicks > 16) {
			float fade = Math.min(1.0F, (revealTicks - 16) / 14.0F);
			renderResult(graphics, fade, stripBot);
		}
	}

	/** 渲染中奖结果金属铭牌（带淡入 + 微缩放进入）：显示稀有度标签 / 词条名称 / 描述 */
	private void renderResult(GuiGraphics graphics, float fade, int stripBot) {
		int boxW = 260;
		int boxH = 66;
		int boxX = centerX - boxW / 2;
		int boxY = stripBot + 16;

		// 微缩放进入（使用 pose 栈）
		float scale = 0.94F + 0.06F * fade;
		graphics.pose().pushPose();
		graphics.pose().translate(centerX, boxY + boxH / 2.0F, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.pose().translate(-centerX, -(boxY + boxH / 2.0F), 0);

		String rarityName = affix.getRarity().getDisplayName();
		int rarityColor = affix.getRarity().getColor();
		int alpha = (int) (0xFF * fade);

		// 金属铭牌底
		graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, withAlpha(COL_STEEL, (int) (0xF2 * fade)));
		// 顶部高光 / 底部阴影（雕刻感）
		graphics.fill(boxX, boxY, boxX + boxW, boxY + 2, withAlpha(COL_SILVER, (int) (0x99 * fade)));
		graphics.fill(boxX, boxY + boxH - 2, boxX + boxW, boxY + boxH, withAlpha(COL_IRON_DARK, alpha));
		// 暗金边框
		int borderC = withAlpha(COL_DARK_GOLD, alpha);
		graphics.fill(boxX, boxY, boxX + boxW, boxY + 1, borderC);
		graphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, borderC);
		graphics.fill(boxX, boxY, boxX + 1, boxY + boxH, borderC);
		graphics.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, borderC);

		// 稀有度标签（稀有度色调融入暗金）
		int labelColor = mix(COL_DARK_GOLD, rarityColor, 0.4F);
		graphics.drawCenteredString(this.font,
				Component.literal("\u00A7l[" + rarityName + "]"),
				centerX, boxY + 6, withAlpha(labelColor, alpha));

		// 词条名称
		graphics.drawCenteredString(this.font,
				Component.translatable(affix.getNameKey()),
				centerX, boxY + 20, withAlpha(COL_SILVER_BRIGHT, alpha));

		// 词条描述
		graphics.drawCenteredString(this.font,
				Component.translatable(affix.getDescKey()),
				centerX, boxY + 34, withAlpha(COL_TEXT, (int) (0xCC * fade)));

		// 关闭提示
		graphics.drawCenteredString(this.font,
				Component.translatable("lottery.randomsurprise.close_hint"),
				centerX, boxY + 50, withAlpha(COL_SILVER, (int) (0x88 * fade)));

		// ===== MC 风格耐久度条装饰（Damage Bar） =====
		// 条宽 = boxW - 8，高 2px，位于 boxY + boxH - 4
		// 背景黑色，前景暗金色（约 70% 填充表示"未满耐久"），末端红色 1px 表示磨损
		int barX = boxX + 4;
		int barY = boxY + boxH - 4;
		int barW = boxW - 8;
		// 背景（黑色）
		graphics.fill(barX, barY, barX + barW, barY + 2, withAlpha(0xFF000000, alpha));
		// 前景（暗金色，约 70% 填充）
		int fillW = (int) (barW * 0.70F);
		graphics.fill(barX, barY, barX + fillW, barY + 2, withAlpha(COL_DARK_GOLD, alpha));
		// 末端红色 1px 表示磨损
		graphics.fill(barX + fillW, barY, barX + fillW + 1, barY + 2, withAlpha(0xFFFF4444, alpha));

		graphics.pose().popPose();
	}

	// ===================== 粒子渲染 =====================

	/** 渲染环境余烬粒子 */
	private void renderAmbientParticles(GuiGraphics graphics) {
		for (AmbientParticle p : ambientParticles) {
			p.render(graphics);
		}
	}

	/** 渲染火花粒子 */
	private void renderSparkParticles(GuiGraphics graphics) {
		for (SparkParticle p : sparkParticles) {
			p.render(graphics);
		}
	}

	/** 渲染爆发粒子 */
	private void renderBurstParticles(GuiGraphics graphics) {
		for (BurstParticle p : burstParticles) {
			p.render(graphics);
		}
	}

	// ===================== 粒子生成 =====================

	/** 在指针位置生成火花粒子 */
	private void spawnSparks(int count) {
		if (sparkParticles.size() > 15) return;
		int px = centerX;
		int py = centerY - CELL_HEIGHT / 2 - 20;
		for (int i = 0; i < count; i++) {
			sparkParticles.add(new SparkParticle(px, py));
		}
	}

	/** 在中奖格中心生成爆发粒子 */
	private void spawnBurstParticles(int count) {
		int cx = centerX;
		int cy = centerY;
		for (int i = 0; i < count; i++) {
			burstParticles.add(new BurstParticle(cx, cy));
		}
	}

	/** 在中奖格底部生成微弱烟雾粒子（强制为余烬类型向上漂浮） */
	private void spawnSmokeParticle() {
		int total = ambientParticles.size() + sparkParticles.size() + burstParticles.size();
		if (total >= 55) return;
		int x = centerX - CELL_WIDTH / 2 + (int) (Math.random() * CELL_WIDTH);
		int y = centerY + CELL_HEIGHT / 2;
		ambientParticles.add(new AmbientParticle(x, y, this.width, this.height, true));
	}

	// ===================== 粒子内部类 =====================

	/** 环境余烬/灰尘粒子：余烬从底部生成向上漂浮；灰尘水平漂移无重力 */
	private static class AmbientParticle {
		float x, y;
		float vy;
		float vx;          // 灰尘水平漂移速度（仅 dust 使用）
		int color;
		float size;
		int life;
		int maxLife;
		float phase;
		int screenWidth, screenHeight;
		boolean dust;      // 废土感灰尘粒子（水平漂移，无重力）

		AmbientParticle(float x, float y, int sw, int sh) {
			this(x, y, sw, sh, false);
		}

		/** forceEmber=true 时强制为余烬（向上漂浮），用于烟雾粒子 */
		AmbientParticle(float x, float y, int sw, int sh, boolean forceEmber) {
			this.x = x;
			this.y = y;
			this.screenWidth = sw;
			this.screenHeight = sh;
			// 灰尘与余烬比例约 2:1（灰尘更多）；forceEmber 强制为余烬
			this.dust = forceEmber ? false : (Math.random() < 0.66F);
			if (dust) {
				this.color = 0xFF6B6B6B; // 灰尘灰
				this.size = 1 + (float) (Math.random() * 1.0);
				this.maxLife = 120 + (int) (Math.random() * 100);
				this.life = this.maxLife;
				this.vy = 0; // 无重力影响
				this.vx = 0.2F + (float) (Math.random() * 0.3F); // 水平漂移 0.2~0.5
				if (Math.random() < 0.5F) this.vx = -this.vx; // 随机左右方向
				this.phase = (float) (Math.random() * Math.PI * 2);
			} else {
				this.color = Math.random() < 0.5 ? 0xFFD4A437 : 0xFF4A4A52;
				this.size = 1 + (float) (Math.random() * 1.0);
				this.maxLife = 100 + (int) (Math.random() * 100);
				this.life = this.maxLife;
				this.vy = -0.3F - (float) (Math.random() * 0.3F);
				this.vx = 0;
				this.phase = (float) (Math.random() * Math.PI * 2);
			}
		}

		void update() {
			if (dust) {
				// 灰尘：水平漂移 + 轻微垂直摆动（无重力）
				x += vx;
				y += (float) Math.sin(phase) * 0.1F;
				phase += 0.03F;
				life--;
				// 灰尘出屏或寿命到期则从对侧重生
				if (x < -5 || x > screenWidth + 5 || life <= 0) {
					boolean goRight = vx > 0;
					x = goRight ? -5 : screenWidth + 5;
					y = (float) (Math.random() * screenHeight);
					maxLife = 120 + (int) (Math.random() * 100);
					life = maxLife;
					color = 0xFF6B6B6B;
					size = 1 + (float) (Math.random() * 1.0);
					vx = 0.2F + (float) (Math.random() * 0.3F);
					if (!goRight) vx = -vx;
					phase = (float) (Math.random() * Math.PI * 2);
				}
			} else {
				// 余烬：向上漂浮 + 水平摆动
				x += (float) Math.sin(phase) * 0.2F;
				y += vy;
				phase += 0.05F;
				life--;
				if (y < -5 || life <= 0) {
					x = (float) (Math.random() * screenWidth);
					y = screenHeight + 5;
					maxLife = 100 + (int) (Math.random() * 100);
					life = maxLife;
					color = Math.random() < 0.5 ? 0xFFD4A437 : 0xFF4A4A52;
					size = 1 + (float) (Math.random() * 1.0);
					vy = -0.3F - (float) (Math.random() * 0.3F);
					phase = (float) (Math.random() * Math.PI * 2);
				}
			}
		}

		void render(GuiGraphics graphics) {
			int alpha = (int) (255.0F * life / maxLife);
			if (alpha > 255) alpha = 255;
			if (alpha <= 0) return;
			// 灰尘 alpha 更低（30-50 上限）
			if (dust) {
				alpha = Math.min(alpha, 30 + (int) (20 * Math.abs(Math.sin(phase))));
				if (alpha <= 0) return;
			}
			int c = (alpha << 24) | (color & 0x00FFFFFF);
			int s = (int) Math.ceil(size);
			graphics.fill((int) x, (int) y, (int) x + s, (int) y + s, c);
		}
	}

	/** 火花粒子：指针抖动时生成，短寿命，带微弱重力 */
	private static class SparkParticle {
		float x, y;
		float vx, vy;
		int life;
		int maxLife;

		SparkParticle(float x, float y) {
			this.x = x;
			this.y = y;
			float angle = (float) (Math.random() * Math.PI * 2);
			float speed = 0.5F + (float) (Math.random() * 1.5F);
			this.vx = (float) Math.cos(angle) * speed;
			this.vy = (float) Math.sin(angle) * speed - 0.3F;
			this.maxLife = 10 + (int) (Math.random() * 8);
			this.life = this.maxLife;
		}

		void update() {
			x += vx;
			y += vy;
			vy += 0.05F;
			vx *= 0.95F;
			life--;
		}

		void render(GuiGraphics graphics) {
			int alpha = (int) (255.0F * life / maxLife);
			if (alpha <= 0) return;
			int c = (alpha << 24) | 0x00D4A437;
			graphics.fill((int) x, (int) y, (int) x + 1, (int) y + 1, c);
		}
	}

	/** 爆发粒子：揭晓时从中奖格中心向外辐射 + 重力 */
	private static class BurstParticle {
		float x, y;
		float vx, vy;
		int life;
		int maxLife;

		BurstParticle(float x, float y) {
			this.x = x;
			this.y = y;
			float angle = (float) (Math.random() * Math.PI * 2);
			float speed = 1.0F + (float) (Math.random() * 2.5F);
			this.vx = (float) Math.cos(angle) * speed;
			this.vy = (float) Math.sin(angle) * speed;
			this.maxLife = 25 + (int) (Math.random() * 15);
			this.life = this.maxLife;
		}

		void update() {
			x += vx;
			y += vy;
			vy += 0.08F;
			vx *= 0.97F;
			life--;
		}

		void render(GuiGraphics graphics) {
			int alpha = (int) (255.0F * life / maxLife);
			if (alpha <= 0) return;
			int c = (alpha << 24) | 0x00D4A437;
			graphics.fill((int) x, (int) y, (int) x + 2, (int) y + 2, c);
		}
	}

	// ===================== 工具方法 =====================

	/**
	 * MC 风格阴影文字：先画暗色偏移 1px 再画亮色（模拟 MC 字体投影）。
	 * 居中版本。
	 */
	private void drawShadowedCenteredString(GuiGraphics graphics, Component text, int x, int y, int color) {
		int shadowColor = 0xFF000000;
		graphics.drawCenteredString(this.font, text, x + 1, y + 1, shadowColor);
		graphics.drawCenteredString(this.font, text, x, y, color);
	}

	/**
	 * MC 风格阴影文字：左对齐版本（先画暗色偏移 1px 再画亮色）。
	 */
	private void drawShadowedString(GuiGraphics graphics, String text, int x, int y, int color) {
		int shadowColor = 0xFF000000;
		graphics.drawString(this.font, text, x + 1, y + 1, shadowColor);
		graphics.drawString(this.font, text, x, y, color);
	}

	/** 颜色线性混合：w2 为 c2 权重（0~1） */
	private static int mix(int c1, int c2, float w2) {
		float w1 = 1.0F - w2;
		int r = (int) (((c1 >> 16) & 0xFF) * w1 + ((c2 >> 16) & 0xFF) * w2);
		int g = (int) (((c1 >> 8) & 0xFF) * w1 + ((c2 >> 8) & 0xFF) * w2);
		int b = (int) (((c1 & 0xFF) * w1) + ((c2 & 0xFF) * w2));
		return (0xFF << 24) | (r << 16) | (g << 8) | b;
	}

	/** 替换颜色 alpha（0~255 或 0~1.0F） */
	private static int withAlpha(int color, int alpha) {
		return (color & 0x00FFFFFF) | (alpha << 24);
	}

	private static int withAlpha(int color, float alpha) {
		return (color & 0x00FFFFFF) | (((int) (alpha * 255.0F)) << 24);
	}

	/** 色相偏移（RGB→HSV→偏移H→RGB），degrees 为偏移角度 */
	private static int shiftHue(int argb, float degrees) {
		float r = ((argb >> 16) & 0xFF) / 255.0F;
		float g = ((argb >> 8) & 0xFF) / 255.0F;
		float b = (argb & 0xFF) / 255.0F;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		float h;
		if (delta == 0) {
			h = 0;
		} else if (max == r) {
			h = 60.0F * (((g - b) / delta) % 6);
		} else if (max == g) {
			h = 60.0F * (((b - r) / delta) + 2);
		} else {
			h = 60.0F * (((r - g) / delta) + 4);
		}
		float s = (max == 0) ? 0 : delta / max;
		float v = max;
		h += degrees;
		if (h < 0) h += 360;
		if (h >= 360) h -= 360;
		float c = v * s;
		float x = c * (1 - Math.abs((h / 60.0F) % 2 - 1));
		float m = v - c;
		float r1, g1, b1;
		if (h < 60) { r1 = c; g1 = x; b1 = 0; }
		else if (h < 120) { r1 = x; g1 = c; b1 = 0; }
		else if (h < 180) { r1 = 0; g1 = c; b1 = x; }
		else if (h < 240) { r1 = 0; g1 = x; b1 = c; }
		else if (h < 300) { r1 = x; g1 = 0; b1 = c; }
		else { r1 = c; g1 = 0; b1 = x; }
		int ri = (int) ((r1 + m) * 255);
		int gi = (int) ((g1 + m) * 255);
		int bi = (int) ((b1 + m) * 255);
		return (0xFF << 24) | (ri << 16) | (gi << 8) | bi;
	}

	/** 实心圆（用水平扫描线填充，步长 2 减少绘制量） */
	private void fillCircle(GuiGraphics graphics, int cx, int cy, int r, int color) {
		if (r <= 0) return;
		for (int y = -r; y <= r; y += 2) {
			int dx = (int) Math.sqrt((double) r * r - (double) y * y);
			graphics.fill(cx - dx, cy + y, cx + dx + 1, cy + y + 2, color);
		}
	}

	/** 圆环（半径 rOuter 与 rInner 之间） */
	private void fillCircleRing(GuiGraphics graphics, int cx, int cy, int rOuter, int rInner, int color) {
		if (rOuter <= 0) return;
		for (int y = -rOuter; y <= rOuter; y += 2) {
			int dxOut = (int) Math.sqrt((double) rOuter * rOuter - (double) y * y);
			int yy2 = y + 1;
			int dy = Math.max(Math.abs(y), Math.abs(yy2));
			int dxIn = (dy <= rInner) ? (int) Math.sqrt((double) rInner * rInner - (double) dy * dy) : 0;
			graphics.fill(cx - dxOut, cy + y, cx - dxIn, cy + y + 2, color);
			graphics.fill(cx + dxIn, cy + y, cx + dxOut + 1, cy + y + 2, color);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		// 安全兜底：若玩家在动画结束前关闭界面（ESC），仍发送确认包应用词条（券已消耗，避免白白损失）
		sendConfirm();
		super.onClose();
	}
}
