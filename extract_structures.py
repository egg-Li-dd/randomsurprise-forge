#!/usr/bin/env python3
"""
结构模组 .nbt 文件提取脚本
从已安装的 Forge 1.20.1 模组 jar 文件中提取结构 .nbt 文件
放置到 schematics/ 目录供 BuildingGenerator 使用

用法:
    python extract_structures.py [mods_dir] [output_dir]

默认:
    mods_dir = C:\creategame\MCzhenghebao\MODS1.20.1fro\安装的mods
    output_dir = C:\creategame\MCzhenghebao\MODS1.20.1fro\schematics
"""

import os
import sys
import zipfile
import shutil
from pathlib import Path

def extract_nbt_from_jar(jar_path, output_dir, mod_name):
    """从 jar 文件中提取 .nbt 结构文件"""
    extracted = 0
    try:
        with zipfile.ZipFile(jar_path, 'r') as jar:
            # 查找所有 .nbt 文件
            nbt_files = [f for f in jar.namelist()
                        if f.endswith('.nbt') and ('structure' in f.lower() or 'structures' in f.lower())]

            if not nbt_files:
                # 也查找 data/ 目录下的 .nbt 文件
                nbt_files = [f for f in jar.namelist()
                            if f.endswith('.nbt') and f.startswith('data/')]

            for nbt_file in nbt_files:
                # 生成唯一文件名：mod名_原文件名
                original_name = os.path.basename(nbt_file)
                output_name = f"{mod_name}_{original_name}"

                # 避免重复
                output_path = os.path.join(output_dir, output_name)
                if os.path.exists(output_path):
                    continue

                # 提取文件
                with jar.open(nbt_file) as src, open(output_path, 'wb') as dst:
                    shutil.copyfileobj(src, dst)
                extracted += 1
                print(f"  提取: {nbt_file} -> {output_name}")

    except Exception as e:
        print(f"  [错误] 处理 {jar_path} 时: {e}")

    return extracted

def main():
    # 参数解析
    mods_dir = sys.argv[1] if len(sys.argv) > 1 else r"C:\creategame\MCzhenghebao\MODS1.20.1fro\安装的mods"
    output_dir = sys.argv[2] if len(sys.argv) > 2 else r"C:\creategame\MCzhenghebao\MODS1.20.1fro\schematics"

    print(f"=== 结构模组 .nbt 文件提取工具 ===")
    print(f"模组目录: {mods_dir}")
    print(f"输出目录: {output_dir}")
    print()

    # 创建输出目录
    os.makedirs(output_dir, exist_ok=True)

    # 扫描所有 jar 文件
    jar_files = [f for f in os.listdir(mods_dir) if f.endswith('.jar')]
    if not jar_files:
        print("未找到 jar 文件!")
        return

    total_extracted = 0
    for jar_file in jar_files:
        jar_path = os.path.join(mods_dir, jar_file)
        mod_name = jar_file.replace('.jar', '').split('-')[0]  # 取模组名
        print(f"扫描: {jar_file}")

        count = extract_nbt_from_jar(jar_path, output_dir, mod_name)
        total_extracted += count

    print()
    print(f"=== 完成 ===")
    print(f"共提取 {total_extracted} 个 .nbt 结构文件")
    print(f"文件保存在: {output_dir}")
    print()
    print("注意: 将 schematics 目录放在 Minecraft 运行目录下，")
    print("BuildingGenerator 会自动读取其中的 .nbt 文件作为随机事件建筑。")

if __name__ == "__main__":
    main()
