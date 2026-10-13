#!/usr/bin/env python3
"""生成 MIFARE Classic 1K 转储样本（`+Sector: N` 分节文本格式）。

**重要说明**：本样本不是从真实卡片抓取的数据，而是按 MIFARE Classic 1K 的公开布局
（每块 16 字节、每扇区 4 块、扇区尾块 = KeyA(6) + 访问位(4) + KeyB(6)）构造的，
并刻意做到内部自洽：块 0 的 BCC 与 UID 匹配，扇区 1 内放入了由 ndeflib 产出的
真实 NDEF 消息（外包 NDEF TLV）。

这样构造的目的是给解析器一个**结构真实、可逐字节核对**的输入；
它不能替代真实卡片抓包，这一点在 README「测试与验证」中已明确标注。
"""
from __future__ import annotations

import os
import struct

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(REPO_ROOT, "app", "src", "test", "resources", "mifare")
NDEF_FIXTURE = os.path.join(REPO_ROOT, "app", "src", "test", "resources", "ndef", "text_en.ndef")

SECTOR_COUNT = 16
BLOCKS_PER_SECTOR = 4
BLOCK_SIZE = 16

# 扇区尾块 = KeyA(6 字节) + 访问位(4 字节) + KeyB(6 字节)，合计 16 字节。
# 出厂默认尾块的访问位是 FF 07 80 69（4 字节），对应十六进制 "FF078069"。
DEFAULT_TRAILER = bytes.fromhex("FFFFFFFFFFFF" + "FF078069" + "FFFFFFFFFFFF")
MAD_TRAILER = bytes.fromhex("A0A1A2A3A4A5" + "FF078069" + "FFFFFFFFFFFF")


def bcc(uid: bytes) -> int:
    value = 0
    for byte in uid:
        value ^= byte
    return value


def main() -> int:
    with open(NDEF_FIXTURE, "rb") as handle:
        ndef_message = handle.read()

    uid = bytes.fromhex("4A1B2C3D")
    sak = 0x08
    atqa = bytes.fromhex("0400")

    # 块 0：厂商块
    block0 = uid + bytes([bcc(uid), sak]) + atqa + bytes(8)

    # 扇区 1 存放 NDEF：TLV = 0x03 | 长度 | 消息 | 0xFE(终止符) | 0x00 填充
    if len(ndef_message) > 0xFE:
        raise SystemExit("NDEF 消息过长，本样本只演示短格式 TLV")
    tlv = bytes([0x03, len(ndef_message)]) + ndef_message + bytes([0xFE])
    sector1_payload = tlv.ljust(BLOCKS_PER_SECTOR * BLOCK_SIZE - BLOCK_SIZE, b"\x00")

    blocks: list[bytes] = []
    for sector in range(SECTOR_COUNT):
        first = sector * BLOCKS_PER_SECTOR
        for offset in range(BLOCKS_PER_SECTOR):
            index = first + offset
            if index == 0:
                blocks.append(block0)
            elif sector == 1 and offset < BLOCKS_PER_SECTOR - 1:
                start = offset * BLOCK_SIZE
                blocks.append(sector1_payload[start:start + BLOCK_SIZE])
            elif offset == BLOCKS_PER_SECTOR - 1:
                # 第 2 扇区用 MAD 密钥，用于验证非默认密钥的解析
                blocks.append(MAD_TRAILER if sector == 2 else DEFAULT_TRAILER)
            else:
                blocks.append(bytes([sector, offset] + [0] * (BLOCK_SIZE - 2)))

    assert len(blocks) == SECTOR_COUNT * BLOCKS_PER_SECTOR
    for index, block in enumerate(blocks):
        if len(block) != BLOCK_SIZE:
            raise SystemExit(f"第 {index} 块长度为 {len(block)}，应为 {BLOCK_SIZE}")

    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, "classic_1k_sector_text.txt")
    with open(path, "w", encoding="utf-8") as handle:
        handle.write("# MIFARE Classic 1K 样本：按公开布局构造，非真实卡片抓包\n")
        handle.write(f"# UID {uid.hex().upper()}，扇区 1 内含真实 NDEF 消息（{len(ndef_message)} 字节）\n")
        for sector in range(SECTOR_COUNT):
            handle.write(f"+Sector: {sector}\n")
            first = sector * BLOCKS_PER_SECTOR
            for offset in range(BLOCKS_PER_SECTOR):
                handle.write(blocks[first + offset].hex().upper() + "\n")

    print(f"扇区数 {SECTOR_COUNT}，块数 {len(blocks)}")
    print(f"块 0        {blocks[0].hex().upper()}")
    print(f"块 3 尾块   {blocks[3].hex().upper()}")
    print(f"块 4        {blocks[4].hex().upper()}")
    print(f"块 5        {blocks[5].hex().upper()}")
    print(f"块 11 尾块  {blocks[11].hex().upper()}")
    print(f"NDEF 消息   {ndef_message.hex().upper()}")
    print(f"TLV         {tlv.hex().upper()}")
    print(f"-> {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
