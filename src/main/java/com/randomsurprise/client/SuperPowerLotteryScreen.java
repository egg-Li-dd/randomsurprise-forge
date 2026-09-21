package com.randomsurprise.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.randomsurprise.superpower.SuperPower;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 超能力抽奖转盘界面（横向滚动老虎机式）—— 厚重金属沉浸风格（增强版）
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
 * 与 LotteryScreen 的关键差异：
 * - 数据源：SuperPower.values() / SuperPower.getById(String)
 * - CELL_HEIGHT = 80（比 LotteryScreen 的 72 高 8px）
 * - 不显示稀有度，改显示超能力类型（◆ 主动 / ◆ 被动）使用 sp.hasActiveAction()
 * - 格子色调来自 sp.getColor()
 * - 揭晓阶段显示超能力名称 + 描述（getNameKey() / getDescKey()）
 * - 翻译键前缀："superpower.randomsurprise."
 */
public class SuperPowerLotteryScreen extends Screen {
	private final String superPowerId;
	private SuperPower superPower;

	private static final int CELL_WIDTH = 120;       // 每个超能力格子宽度
	private static final int CELL_HEIGHT = 80;       // 格子高度（比词条格子高 8px，名称更长）
	private static final int VISIBLE_CELLS = 9;      // 屏幕可见格子数
	private static final int ANIMATION_DURATION = 120; // 6 秒动画（120 ticks，更慢更有重量感）

	// ========== 金属配色方案（MC方块配色参考） ==========
	// COL_IRON_DARK: IRON_BLOCK 暗面调暗
	// COL_STEEL: DAMAGED_ANVIL 铁质部分
	// COL_DARK_GOLD: GOLD_BLOCK 暗面
	// COL_RUST_RED: REDSTONE_BLOCK 暗化
	// COL_MOSS: MOSSY_COBBLESTONE 苔藓色
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
	// 废土感配色（Wasteland Aesthetic）
	private static final int COL_MOSS = 0xFF3D5C3D;        // 苔藓/氧化色（MOSSY_COBBLESTONE）
	private static final int COL_RUST_1 = 0xFF8B4513;       // 锈蚀橙棕
	private static final int COL_RUST_2 = 0xFFA0522D;       // 锈蚀赭石
	private static final int COL_RUST_3 = 0xFFCD853F;       // 锈蚀 Peru
	private static final int COL_CRACK = 0xFF0A0A0A;         // 裂纹黑
	private static final int COL_REDSTONE = 0xFFFF0000;     // 红石粉
	private static final int COL_ENCHANT = 0xFF8B5CF6;     // 附魔光紫色
	private static final int COL_SCORCH = 0xFF8B2500;       // 灼烧暗红
	private static final int COL_DUST = 0xFF6B6B6B;          // 灰尘灰

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

	// 粒子系统（总粒子数控制在 60 以内）
	private final List<AmbientParticle> ambientParticles = new ArrayList<>(); // 环境余烬（15-20个）
	private final List<SparkParticle> sparkParticles = new ArrayList<>();     // 指针火花
	private final List<BurstParticle> burstParticles = new ArrayList<>();     // 揭晓粒子爆发

	private final List<SuperPower> wheelPowers = new ArrayList<>();
	private int targetIndex = 0;
	private int centerX, centerY;

	public SuperPowerLotteryScreen(String superPowerId) {
		super(Component.translatable("superpower.randomsurprise.lottery_title"));
		this.superPowerId = superPowerId;
	}

	@Override
	protected void init() {
		super.init();
		this.centerX = this.width / 2;
		this.centerY = this.height / 2;
		this.superPower = SuperPower.getById(superPowerId);

		// 构建轮盘条带：所有超能力重复 5 次以实现长距离滚动
		wheelPowers.clear();
		List<SuperPower> all = Arrays.asList(SuperPower.values());
		for (int rep = 0; rep < 5; rep++) {
			wheelPowers.addAll(all);
		}

		// 计算目标超能力在条带中的索引（第 3 圈）
		int basePowerIndex = 0;
		if (superPower != null) {
			List<SuperPower> base = Arrays.asList(SuperPower.values());
			for (int i = 0; i < base.size(); i++) {
				if (base.get(i).getId().equals(superPowerId)) {
					basePowerIndex = i;
					break;
				}
			}
		}
		int repsBefore = 2;
		targetIndex = repsBefore * all.size() + basePowerIndex;
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
			}
		} else {
			revealTicks++;
			// 揭晓阶段音效与粒子触发
			updateRevealEffects();
		}
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

		// 动画期间：滚动标题（MC风格阴影增强）
		if (!animationDone) {
			Component titleComp = Component.translatable("superpower.randomsurprise.lottery_spinning");
			int titleW = this.font.width(titleComp);
			// 阴影层（1px 偏移，无 dropShadow）
			graphics.drawString(this.font, titleComp,
					centerX - titleW / 2, stripTop - 79, 0xFF050505, false);
			// 主文字（自带 MC dropShadow）
			graphics.drawCenteredString(this.font, titleComp,
					centerX, stripTop - 80, COL_DARK_GOLD);
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
		if (animationDone && superPower != null) {
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

		// 齿间隙绿色氧化痕迹（废土感）
		for (int i = 0; i < outerTeeth; i++) {
			float ga = rad + (i + 0.5F) * (float) (Math.PI * 2 / outerTeeth);
			int mx = gx + (int) (Math.cos(ga) * 19);
			int my = gy + (int) (Math.sin(ga) * 19);
			graphics.fill(mx - 1, my - 1, mx + 1, my + 1, withAlpha(COL_MOSS, 0x44));
		}

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

		// 内圈暗铁盘
		fillCircle(graphics, gx, gy, 10, COL_IRON_DARK);
		// 内齿间隙绿色氧化
		for (int i = 0; i < innerTeeth; i++) {
			float ga = radInner + (i + 0.5F) * (float) (Math.PI * 2 / innerTeeth);
			int mx = gx + (int) (Math.cos(ga) * 11);
			int my = gy + (int) (Math.sin(ga) * 11);
			graphics.fill(mx, my, mx + 1, my + 1, withAlpha(COL_MOSS, 0x33));
		}
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
		// 中心宝石周围放射状灼烧纹（4条暗色短线向外）
		renderRadialScorch(graphics, gx, gy, 9);
	}

	// ===================== 轮盘条带 =====================

	/** 渲染横向滚动条带（金属铭牌） */
	private void renderWheelStrip(GuiGraphics graphics, int stripTop) {
		int firstVisibleIndex = Math.max(0, (int) (currentOffset / CELL_WIDTH) - 1);
		int lastVisibleIndex = Math.min(wheelPowers.size() - 1,
				firstVisibleIndex + VISIBLE_CELLS + 2);

		for (int i = firstVisibleIndex; i <= lastVisibleIndex; i++) {
			SuperPower sp = wheelPowers.get(i);
			int cellX = i * CELL_WIDTH - (int) currentOffset;
			if (cellX + CELL_WIDTH < 0 || cellX > this.width) continue;
			renderCell(graphics, cellX, stripTop, sp, i);
		}
	}

	/** 渲染增强金属铭牌格子（深斜面 + 划痕 + 磨损斑点 + 分隔线 + hue微调） */
	private void renderCell(GuiGraphics graphics, int x, int y, SuperPower sp, int index) {
		// 金属底色占 70%，超能力颜色占 30%，hue 根据 index 微调 ±5
		int baseMetal = mix(COL_STEEL_LIGHT, sp.getColor(), 0.30F);
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
		int seed = index * 0x9E3779B1;
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

		// 锈蚀斑点（废土感）：基于 cell index 的确定性伪随机
		renderRustPatches(graphics, x + 2, y + 2, CELL_WIDTH - 4, CELL_HEIGHT - 4, index * 7919 + 13);

		// 格子间分隔线：1px 深灰铁竖线 + 1px 银色高光（右侧）
		graphics.fill(x + CELL_WIDTH, y, x + CELL_WIDTH + 1, y + CELL_HEIGHT, COL_IRON_DARK);
		graphics.fill(x + CELL_WIDTH + 1, y, x + CELL_WIDTH + 2, y + CELL_HEIGHT, withAlpha(COL_SILVER, 0x44));

		// 超能力类型标识（◆ 主动 / ◆ 被动）：主动用暗金、被动用银色（§l 粗体增强雕刻感）
		boolean active = sp.hasActiveAction();
		String typeText = "\u00A7l" + (active ? "\u25C6 \u4E3B\u52A8" : "\u25C6 \u88AB\u52A8");
		int typeColor = active ? COL_DARK_GOLD : COL_SILVER;
		int typeWidth = this.font.width(typeText);
		graphics.drawString(this.font, typeText,
				x + (CELL_WIDTH - typeWidth) / 2, y + 12, typeColor);

		// 超能力名称（过长则按像素宽度截断，保留至少 4 字符）
		Component nameComp = Component.translatable(sp.getNameKey());
		String nameText = nameComp.getString();
		while (this.font.width(nameText) > CELL_WIDTH - 8 && nameText.length() > 4) {
			nameText = nameText.substring(0, nameText.length() - 1);
		}
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

		// 废土感：边框锈蚀斑点 + 裂纹（上下边框各调用）
		renderRustPatches(graphics, 0, stripTop - 9, this.width, 6, 0x1234);
		renderRustPatches(graphics, 0, stripBot + 3, this.width, 6, 0x5678);
		renderCracks(graphics, 0, stripTop - 9, this.width, 6, 0x9ABC);
		renderCracks(graphics, 0, stripBot + 3, this.width, 6, 0xDEF0);
		// 红石粉装饰（MC 风格：机械装置有红石电路）
		renderRedstoneTraces(graphics, 0, stripTop - 7, this.width, 0x1111);
		renderRedstoneTraces(graphics, 0, stripBot + 5, this.width, 0x2222);
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
		} else if (!left && top) {
			// 右上
			graphics.fill(cornerX - armLen, cornerY, cornerX, cornerY + thick, COL_STEEL);
			graphics.fill(cornerX - thick, cornerY, cornerX, cornerY + armLen, COL_STEEL);
			graphics.fill(cornerX - armLen, cornerY, cornerX, cornerY + 1, COL_SILVER_BRIGHT);
			graphics.fill(cornerX - 1, cornerY, cornerX, cornerY + armLen, COL_SILVER_BRIGHT);
			for (int c = 0; c < chamfSize; c++) {
				graphics.fill(cornerX - thick - c - 1, cornerY + thick, cornerX - thick - c, cornerY + thick + chamfSize - c, COL_STEEL);
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
		} else {
			// 右下
			graphics.fill(cornerX - armLen, cornerY - thick, cornerX, cornerY, COL_STEEL);
			graphics.fill(cornerX - thick, cornerY - armLen, cornerX, cornerY, COL_STEEL);
			graphics.fill(cornerX - armLen, cornerY - 1, cornerX, cornerY, COL_SILVER_BRIGHT);
			graphics.fill(cornerX - 1, cornerY - armLen, cornerX, cornerY, COL_SILVER_BRIGHT);
			for (int c = 0; c < chamfSize; c++) {
				graphics.fill(cornerX - thick - c - 1, cornerY - thick - chamfSize + c, cornerX - thick - c, cornerY - thick, COL_STEEL);
			}
		}

		// 苔藓氧化（内侧边缘 2px 渐变绿）
		int mossX = left ? cornerX : cornerX - armLen;
		int mossY = top ? cornerY + thick : cornerY - thick - 2;
		graphics.fill(mossX, mossY, mossX + armLen, mossY + 1, withAlpha(COL_MOSS, 0x55));
		graphics.fill(mossX, mossY + 1, mossX + armLen, mossY + 2, withAlpha(COL_MOSS, 0x22));

		// 部分角落添加裂纹（左上 + 右下）
		if ((left && top) || (!left && !top)) {
			int crackX = left ? cornerX + 6 : cornerX - 18;
			int crackY = top ? cornerY + 6 : cornerY - 12;
			renderCracks(graphics, crackX, crackY, 12, 6, cornerX + cornerY);
		}
	}

	/**
	 * 像素艺术铆钉：主体 2x2 方块 + 高光 1x1 左上角 + 暗部 1x1 右下角
	 * 约 15% 铆钉渲染为破损（基于位置 x 伪随机）：只画暗色底 + 旁有裂纹 + 脱落碎片
	 * 铆钉周围微弱绿色晕（3x3 alpha 20）
	 */
	private void renderRivet(GuiGraphics graphics, int x, int y) {
		// 基于位置 x 伪随机决定破损（约 15%）
		int hash = (x * 265443576) & 0x7FFFFFFF;
		boolean broken = (hash % 100) < 15;

		if (broken) {
			// 破损铆钉：只画暗色底不画高光
			graphics.fill(x - 1, y - 1, x + 1, y + 1, withAlpha(COL_IRON_DARK, 0xAA));
			// 旁边裂纹像素
			graphics.fill(x + 1, y - 1, x + 2, y, withAlpha(COL_CRACK, 0xC8));
			// 脱落碎片（2x1 暗色像素偏移 2px）
			graphics.fill(x + 2, y + 1, x + 4, y + 2, withAlpha(COL_IRON_DARK, 0x88));
		} else {
			// 像素艺术铆钉：主体 2x2 方块
			graphics.fill(x - 1, y - 1, x + 1, y + 1, COL_SILVER);
			// 高光 1x1 左上角
			graphics.fill(x - 1, y - 1, x, y, COL_SILVER_BRIGHT);
			// 暗部 1x1 右下角
			graphics.fill(x, y, x + 1, y + 1, withAlpha(COL_IRON_DARK, 0xAA));
		}

		// 铆钉周围微弱绿色晕（3x3 alpha 20）
		graphics.fill(x - 1, y - 1, x + 2, y + 2, withAlpha(COL_MOSS, 0x14));
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
		// 指针尖端灼烧痕迹（3x3 半透明黑色渐变）
		renderScorchMark(graphics, px, pyTop + h + 1);
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

	// ===================== 废土感渲染方法（Wasteland Aesthetic） =====================

	/**
	 * 锈蚀斑点：用确定性伪随机生成 3-5 个橙棕色斑点（2x2 到 4x3 像素，alpha 60-100）
	 */
	private void renderRustPatches(GuiGraphics graphics, int x, int y, int w, int h, int seed) {
		int[] rustColors = {COL_RUST_1, COL_RUST_2, COL_RUST_3};
		int s = seed;
		int count = 3 + (Math.abs(s) % 3); // 3-5 个
		for (int i = 0; i < count; i++) {
			s = s * 1103515245 + 12345;
			int px = x + (Math.abs(s) % Math.max(1, w - 4));
			s = s * 1103515245 + 12345;
			int py = y + (Math.abs(s) % Math.max(1, h - 3));
			s = s * 1103515245 + 12345;
			int pw = 2 + (Math.abs(s) % 3); // 2-4 宽
			s = s * 1103515245 + 12345;
			int ph = 2 + (Math.abs(s) % 2); // 2-3 高
			s = s * 1103515245 + 12345;
			int alpha = 60 + (Math.abs(s) % 41); // 60-100
			int color = rustColors[Math.abs(s) % 3];
			graphics.fill(px, py, px + pw, py + ph, withAlpha(color, alpha));
		}
	}

	/**
	 * 锯齿状裂纹：1-2 条，每条 3-5 段，方向随机偏转±30°，长度5-15px
	 * 颜色 #0A0A0A alpha 200
	 */
	private void renderCracks(GuiGraphics graphics, int x, int y, int w, int h, int seed) {
		int s = seed;
		int crackCount = 1 + (Math.abs(s) % 2); // 1-2 条
		for (int c = 0; c < crackCount; c++) {
			s = s * 1103515245 + 12345;
			int cx = x + (Math.abs(s) % Math.max(1, w - 2));
			s = s * 1103515245 + 12345;
			int cy = y + (Math.abs(s) % Math.max(1, h - 2));
			s = s * 1103515245 + 12345;
			int segments = 3 + (Math.abs(s) % 3); // 3-5 段
			s = s * 1103515245 + 12345;
			float angle = (Math.abs(s) % 628) / 100.0F; // 初始方向 0-2π
			for (int seg = 0; seg < segments; seg++) {
				s = s * 1103515245 + 12345;
				// 偏转 ±30°
				float delta = ((Math.abs(s) % 61) - 30) / 180.0F * (float) Math.PI;
				angle += delta;
				s = s * 1103515245 + 12345;
				int len = 5 + (Math.abs(s) % 11); // 5-15px
				int nx = cx + (int) (Math.cos(angle) * len);
				int ny = cy + (int) (Math.sin(angle) * len);
				drawCrackLine(graphics, cx, cy, nx, ny, withAlpha(COL_CRACK, 0xC8));
				cx = nx;
				cy = ny;
			}
		}
	}

	/** 简单线段绘制（Bresenham 逐点填充） */
	private void drawCrackLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
		int dx = Math.abs(x2 - x1);
		int dy = Math.abs(y2 - y1);
		int sx = x1 < x2 ? 1 : -1;
		int sy = y1 < y2 ? 1 : -1;
		int err = dx - dy;
		int cx = x1, cy = y1;
		int steps = Math.max(dx, dy);
		int maxSteps = Math.min(steps + 1, 30);
		for (int i = 0; i < maxSteps; i++) {
			graphics.fill(cx, cy, cx + 1, cy + 1, color);
			if (cx == x2 && cy == y2) break;
			int e2 = 2 * err;
			if (e2 > -dy) { err -= dy; cx += sx; }
			if (e2 < dx) { err += dx; cy += sy; }
		}
	}

	/**
	 * 红石粉装饰：边框上 2-3 个红石粉点（1x1 红色 alpha 80），粉点间极淡红色线连接
	 */
	private void renderRedstoneTraces(GuiGraphics graphics, int x, int y, int w, int seed) {
		int s = seed;
		int pointCount = 2 + (Math.abs(s) % 2); // 2-3 点
		int[] pointsX = new int[pointCount];
		for (int i = 0; i < pointCount; i++) {
			s = s * 1103515245 + 12345;
			pointsX[i] = x + 10 + (Math.abs(s) % Math.max(1, w - 20));
			graphics.fill(pointsX[i], y, pointsX[i] + 1, y + 1, withAlpha(COL_REDSTONE, 0x50));
		}
		// 粉点间连接线（极淡 alpha 20）
		for (int i = 0; i < pointCount - 1; i++) {
			int x1 = Math.min(pointsX[i], pointsX[i + 1]);
			int x2 = Math.max(pointsX[i], pointsX[i + 1]);
			for (int xx = x1; xx < x2; xx++) {
				graphics.fill(xx, y, xx + 1, y + 1, withAlpha(COL_REDSTONE, 0x14));
			}
		}
	}

	/** 灼烧痕迹：3x3 半透明黑色渐变（中心最深） */
	private void renderScorchMark(GuiGraphics graphics, int cx, int cy) {
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				int dist = Math.abs(dx) + Math.abs(dy);
				int alpha = 0xC8 - dist * 0x33;
				if (alpha > 0) {
					graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, (alpha << 24));
				}
			}
		}
	}

	/** 放射状灼烧纹：4条暗色短线向外 */
	private void renderRadialScorch(GuiGraphics graphics, int cx, int cy, int radius) {
		for (int i = 0; i < 4; i++) {
			float a = i * (float) Math.PI / 2.0F;
			for (int d = 2; d <= radius; d++) {
				int px = cx + (int) (Math.cos(a) * d);
				int py = cy + (int) (Math.sin(a) * d);
				int alpha = 0x88 - d * 0x10;
				if (alpha > 0) {
					graphics.fill(px, py, px + 1, py + 1, (alpha << 24));
				}
			}
		}
	}

	/** 揭晓金光周围灼烧边缘（暗红色渐变） */
	private void renderScorchBorder(GuiGraphics graphics, int x, int y, int w, int h, int alpha) {
		// 外层 2px 暗红渐变
		graphics.fill(x - 2, y - 2, x + w + 2, y - 1, withAlpha(COL_SCORCH, alpha / 3));
		graphics.fill(x - 2, y + h + 1, x + w + 2, y + h + 2, withAlpha(COL_SCORCH, alpha / 3));
		graphics.fill(x - 2, y - 2, x - 1, y + h + 2, withAlpha(COL_SCORCH, alpha / 3));
		graphics.fill(x + w + 1, y - 2, x + w + 2, y + h + 2, withAlpha(COL_SCORCH, alpha / 3));
		// 内层 1px 更深
		graphics.fill(x - 1, y - 1, x + w + 1, y, withAlpha(COL_SCORCH, alpha / 2));
		graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, withAlpha(COL_SCORCH, alpha / 2));
		graphics.fill(x - 1, y - 1, x, y + h + 1, withAlpha(COL_SCORCH, alpha / 2));
		graphics.fill(x + w, y - 1, x + w + 1, y + h + 1, withAlpha(COL_SCORCH, alpha / 2));
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
			// 灼烧边缘（暗红色渐变）
			renderScorchBorder(graphics, x, stripTop, CELL_WIDTH, CELL_HEIGHT, glowA);
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
			// 灼烧边缘（暗红色渐变）
			renderScorchBorder(graphics, x, stripTop, CELL_WIDTH, CELL_HEIGHT, a);
		}

		// 附魔光效（揭晓稳定阶段 revealTicks >= 34）：MC 风格周期性闪烁紫色像素行
		if (revealTicks >= 34) {
			float glintFactor = (float) (Math.sin(revealTicks * 0.15) * 0.5 + 0.5); // 0-1
			int glintAlpha = (int) (60 * glintFactor); // 0-60
			if (glintAlpha > 0) {
				// 每隔 2px 水平线叠加紫色
				for (int yy = stripTop; yy < stripBot; yy += 2) {
					graphics.fill(x, yy, x + CELL_WIDTH, yy + 1, withAlpha(COL_ENCHANT, glintAlpha));
				}
			}
		}

		// 文字浮现（revealTicks > 16）
		if (revealTicks > 16) {
			float fade = Math.min(1.0F, (revealTicks - 16) / 14.0F);
			renderResult(graphics, fade, stripBot);
		}
	}

	/** 渲染中奖结果金属铭牌（带淡入 + 微缩放进入）：显示超能力名称与描述 */
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

		int powerColor = superPower.getColor();
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

		// 类型标签（主动 / 被动）：色调由暗金与超能力色混合
		boolean active = superPower.hasActiveAction();
		String typeLabel = active ? "\u25C6 \u4E3B\u52A8" : "\u25C6 \u88AB\u52A8";
		int typeBase = active ? COL_DARK_GOLD : COL_SILVER;
		int labelColor = mix(COL_DARK_GOLD, typeBase, 0.4F);
		labelColor = mix(labelColor, powerColor, 0.35F);
		graphics.drawCenteredString(this.font,
				Component.literal("\u00A7l" + typeLabel),
				centerX, boxY + 6, withAlpha(labelColor, alpha));

		// 超能力名称
		graphics.drawCenteredString(this.font,
				Component.translatable(superPower.getNameKey()),
				centerX, boxY + 20, withAlpha(COL_SILVER_BRIGHT, alpha));

		// 超能力描述
		graphics.drawCenteredString(this.font,
				Component.translatable(superPower.getDescKey()),
				centerX, boxY + 34, withAlpha(COL_TEXT, (int) (0xCC * fade)));

		// 关闭提示
		graphics.drawCenteredString(this.font,
				Component.translatable("lottery.randomsurprise.close_hint"),
				centerX, boxY + 50, withAlpha(COL_SILVER, (int) (0x88 * fade)));

		// MC风格耐久度条装饰：底部 boxY+boxH-4，宽 boxW-8，高2px
		int dBarW = boxW - 8;
		int dBarX = boxX + 4;
		int dBarY = boxY + boxH - 4;
		// 背景黑色
		graphics.fill(dBarX, dBarY, dBarX + dBarW, dBarY + 2, withAlpha(0xFF000000, alpha));
		// 前景暗金色，约 70% 填充
		int fillW = (int) (dBarW * 0.7F);
		graphics.fill(dBarX, dBarY, dBarX + fillW, dBarY + 2, withAlpha(COL_DARK_GOLD, alpha));
		// 末端红色 1px 表示磨损
		graphics.fill(dBarX + fillW, dBarY, dBarX + fillW + 1, dBarY + 2, withAlpha(0xFFFF4444, alpha));

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

	/** 在中奖格底部生成微弱烟雾粒子（强制为余烬类型，非灰尘） */
	private void spawnSmokeParticle() {
		int total = ambientParticles.size() + sparkParticles.size() + burstParticles.size();
		if (total >= 55) return;
		int x = centerX - CELL_WIDTH / 2 + (int) (Math.random() * CELL_WIDTH);
		int y = centerY + CELL_HEIGHT / 2;
		ambientParticles.add(new AmbientParticle(x, y, this.width, this.height, false));
	}

	// ===================== 粒子内部类 =====================

	/** 环境粒子：金色余烬（向上漂浮）与灰色灰尘（水平漂移）两种类型，比例约 1:2 */
	private static class AmbientParticle {
		float x, y;
		float vx, vy;
		int color;
		float size;
		int life;
		int maxLife;
		float phase;
		int screenWidth, screenHeight;
		boolean dust;
		int baseAlpha;

		AmbientParticle(float x, float y, int sw, int sh) {
			this(x, y, sw, sh, Math.random() < 0.67); // 灰尘与余烬比例约 2:1
		}

		AmbientParticle(float x, float y, int sw, int sh, boolean dust) {
			this.x = x;
			this.y = y;
			this.screenWidth = sw;
			this.screenHeight = sh;
			this.dust = dust;
			this.phase = (float) (Math.random() * Math.PI * 2);
			if (dust) {
				// 灰尘粒子：灰色，水平漂移，无重力，alpha 30-50
				this.color = 0xFF6B6B6B;
				this.size = 1;
				this.maxLife = 120 + (int) (Math.random() * 80);
				this.life = this.maxLife;
				this.vy = 0;
				this.vx = 0.2F + (float) (Math.random() * 0.3F);
				this.baseAlpha = 30 + (int) (Math.random() * 21);
			} else {
				// 余烬粒子：金色/灰色，向上漂浮
				this.color = Math.random() < 0.5 ? 0xFFD4A437 : 0xFF4A4A52;
				this.size = 1 + (float) (Math.random() * 1.0);
				this.maxLife = 100 + (int) (Math.random() * 100);
				this.life = this.maxLife;
				this.vy = -0.3F - (float) (Math.random() * 0.3F);
				this.vx = 0;
				this.baseAlpha = 255;
			}
		}

		void update() {
			if (dust) {
				// 灰尘：水平漂移，微弱垂直摆动
				x += vx;
				y += (float) Math.sin(phase) * 0.1F;
				phase += 0.03F;
				if (x > screenWidth + 5) {
					x = -5;
					y = (float) (Math.random() * screenHeight);
				}
			} else {
				// 余烬：向上漂浮，水平摆动
				x += (float) Math.sin(phase) * 0.2F;
				y += vy;
				phase += 0.05F;
				if (y < -5) {
					x = (float) (Math.random() * screenWidth);
					y = screenHeight + 5;
				}
			}
			life--;
			if (life <= 0) {
				reset();
			}
		}

		void reset() {
			phase = (float) (Math.random() * Math.PI * 2);
			if (dust) {
				x = -5;
				y = (float) (Math.random() * screenHeight);
				maxLife = 120 + (int) (Math.random() * 80);
				life = maxLife;
				vx = 0.2F + (float) (Math.random() * 0.3F);
				baseAlpha = 30 + (int) (Math.random() * 21);
			} else {
				x = (float) (Math.random() * screenWidth);
				y = screenHeight + 5;
				color = Math.random() < 0.5 ? 0xFFD4A437 : 0xFF4A4A52;
				size = 1 + (float) (Math.random() * 1.0);
				maxLife = 100 + (int) (Math.random() * 100);
				life = maxLife;
				vy = -0.3F - (float) (Math.random() * 0.3F);
			}
		}

		void render(GuiGraphics graphics) {
			float lifeRatio = (float) life / maxLife;
			int alpha = (int) (baseAlpha * lifeRatio);
			if (alpha > 255) alpha = 255;
			if (alpha <= 0) return;
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

	/** 颜色线性混合：w2 为 c2 权重（0~1） */
	private static int mix(int c1, int c2, float w2) {
		float w1 = 1.0F - w2;
		int r = (int) (((c1 >> 16) & 0xFF) * w1 + ((c2 >> 16) & 0xFF) * w2);
		int g = (int) (((c1 >> 8) & 0xFF) * w1 + ((c2 >> 8) & 0xFF) * w2);
		int b = (int) ((c1 & 0xFF) * w1 + (c2 & 0xFF) * w2);
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
		super.onClose();
	}
}
