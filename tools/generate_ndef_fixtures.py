#!/usr/bin/env python3
"""生成 NDEF 二进制测试样本（真实文件），用于交叉验证自实现的 NDEF 解码器。

样本由 **独立第三方实现** ndeflib 0.3.3 编码产出，不是本工程自己生成的，
因此可以用来发现"编码/解码自洽但都不符合规范"这类问题。

产出：
  app/src/test/resources/ndef/<name>.ndef          二进制样本
  app/src/test/resources/ndef/expectations.tsv     期望值表（制表符分隔）

期望值表格式：`样本文件名<TAB>维度<TAB>值`
维度说明见 README「测试与验证」章节。
"""
from __future__ import annotations

import os
import struct
import sys

import ndef
import ndef.wifi as wsc

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(REPO_ROOT, "app", "src", "test", "resources", "ndef")

WIFI_MIME = "application/vnd.wfa.wsc"
ANDROID_PKG_TYPE = "android.com:pkg"


def wifi_record(ssid: bytes, key: bytes | None, auth: int, encr: int) -> ndef.Record:
    """构造一条 WSC 记录；key 为 None 表示开放网络（不写 Network Key 属性）。"""
    attrs: list[tuple[str, bytes]] = [
        ("network-index", b"\x01"),
        ("ssid", ssid),
        ("authentication-type", struct.pack(">H", auth)),
        ("encryption-type", struct.pack(">H", encr)),
    ]
    if key is not None:
        attrs.append(("network-key", key))
    credential = wsc.Credential(*attrs)
    return wsc.WifiSimpleConfigRecord(
        ("version-1", b"\x10"),
        ("credential", credential.encode()),
    )


def build_fixtures() -> list[tuple[str, bytes, list[tuple[str, str]]]]:
    """返回 (文件名, 字节内容, [(维度, 期望值), ...]) 列表。"""
    fixtures: list[tuple[str, bytes, list[tuple[str, str]]]] = []

    def add(name: str, records: list[ndef.Record], expectations: list[tuple[str, str]]) -> None:
        data = b"".join(ndef.message_encoder(records))
        fixtures.append((name, data, expectations))

    # 1) 英文文本记录
    add(
        "text_en",
        [ndef.TextRecord("Hello NFC", language="en")],
        [
            ("record_count", "1"),
            ("record.0.tnf", "1"),
            ("record.0.type", "T"),
            ("record.0.language", "en"),
            ("record.0.text", "Hello NFC"),
        ],
    )

    # 2) 中文文本记录（UTF-8 多字节）
    add(
        "text_zh",
        [ndef.TextRecord("你好，NFC 标签", language="zh")],
        [
            ("record_count", "1"),
            ("record.0.tnf", "1"),
            ("record.0.type", "T"),
            ("record.0.language", "zh"),
            ("record.0.text", "你好，NFC 标签"),
        ],
    )

    # 3) https 链接（触发 URI 前缀缩写 0x02）
    add(
        "uri_https_www",
        [ndef.UriRecord("https://www.example.com/path?q=1")],
        [
            ("record_count", "1"),
            ("record.0.tnf", "1"),
            ("record.0.type", "U"),
            ("record.0.uri", "https://www.example.com/path?q=1"),
        ],
    )

    # 4) 电话链接（触发 tel: 缩写 0x05）
    add(
        "uri_tel",
        [ndef.UriRecord("tel:+8613800138000")],
        [
            ("record_count", "1"),
            ("record.0.tnf", "1"),
            ("record.0.type", "U"),
            ("record.0.uri", "tel:+8613800138000"),
        ],
    )

    # 5) 智能海报：内嵌 NDEF 消息（URI 记录 + 标题文本记录）
    smart_poster = ndef.SmartposterRecord("https://example.com/")
    smart_poster.set_title("示例标题", "zh")
    add(
        "smartposter",
        [smart_poster],
        [
            ("record_count", "1"),
            ("record.0.tnf", "1"),
            ("record.0.type", "Sp"),
            ("record.0.sp_uri", "https://example.com/"),
            ("record.0.sp_title", "示例标题"),
        ],
    )

    # 6) WiFi 凭据（WPA2-Personal / AES）
    add(
        "wifi_wpa2",
        [wifi_record(b"Lab-Network", b"correct horse battery", auth=0x0020, encr=0x0008)],
        [
            ("record_count", "1"),
            ("record.0.tnf", "2"),
            ("record.0.type", WIFI_MIME),
            ("record.0.wifi_ssid", "Lab-Network"),
            ("record.0.wifi_key", "correct horse battery"),
            ("record.0.wifi_auth", "0x0020"),
            ("record.0.wifi_encr", "0x0008"),
        ],
    )

    # 7) WiFi 凭据（开放网络，无 Network Key 属性）
    add(
        "wifi_open",
        [wifi_record(b"Guest-WiFi", None, auth=0x0001, encr=0x0001)],
        [
            ("record_count", "1"),
            ("record.0.tnf", "2"),
            ("record.0.type", WIFI_MIME),
            ("record.0.wifi_ssid", "Guest-WiFi"),
            ("record.0.wifi_key", ""),
            ("record.0.wifi_auth", "0x0001"),
            ("record.0.wifi_encr", "0x0001"),
        ],
    )

    # 8) Android 应用记录（外部类型 android.com:pkg）
    # ndeflib 用 "urn:nfc:ext:" 前缀表达外部类型，编码后类型字段即为 android.com:pkg，TNF=4
    add(
        "external_android_pkg",
        [ndef.Record(type="urn:nfc:ext:android.com:pkg", data=b"com.nfctool.app")],
        [
            ("record_count", "1"),
            ("record.0.tnf", "4"),
            ("record.0.type", ANDROID_PKG_TYPE),
            ("record.0.payload_utf8", "com.nfctool.app"),
        ],
    )

    # 9) MIME 记录（PNG 文件签名）
    png_signature = bytes([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])
    add(
        "mime_png",
        [ndef.Record(type="image/png", data=png_signature)],
        [
            ("record_count", "1"),
            ("record.0.tnf", "2"),
            ("record.0.type", "image/png"),
            ("record.0.payload_hex", png_signature.hex().upper()),
        ],
    )

    # 10) 多记录消息：文本 + 链接
    add(
        "multi_text_uri",
        [
            ndef.TextRecord("标签说明", language="zh"),
            ndef.UriRecord("https://nfctool.example/"),
        ],
        [
            ("record_count", "2"),
            ("record.0.tnf", "1"),
            ("record.0.type", "T"),
            ("record.0.text", "标签说明"),
            ("record.1.tnf", "1"),
            ("record.1.type", "U"),
            ("record.1.uri", "https://nfctool.example/"),
        ],
    )

    # 11) 长负载记录：强制使用 4 字节长度字段（SR 不置位）
    long_text = "A" * 300
    add(
        "long_payload_text",
        [ndef.TextRecord(long_text, language="en")],
        [
            ("record_count", "1"),
            ("record.0.tnf", "1"),
            ("record.0.type", "T"),
            ("record.0.text", long_text),
            ("record.0.sr_flag", "false"),
        ],
    )

    # 12) 分块记录（手工按 NDEF 1.0 规范 3.2.7 节构造，ndeflib 不支持生成分块记录）
    #     首块：MB|CF|SR|TNF=1 = 0xB1，携带类型；末块：ME|SR|TNF=6(Unchanged) = 0x56，无类型。
    text_payload = bytes([0x02]) + b"en" + "hello world".encode()
    first_chunk_payload = text_payload[:6]
    second_chunk_payload = text_payload[6:]
    chunked = bytes([0xB1, 0x01, len(first_chunk_payload), ord("T")]) + first_chunk_payload
    chunked += bytes([0x56, 0x00, len(second_chunk_payload)]) + second_chunk_payload
    fixtures.append(
        (
            "chunked_text",
            chunked,
            [
                ("record_count", "1"),
                ("record.0.tnf", "1"),
                ("record.0.type", "T"),
                ("record.0.language", "en"),
                ("record.0.text", "hello world"),
                # 分块记录重新编码后会变成单条非分块记录，字节自然不同
                ("reencode_identical", "false"),
            ],
        ),
    )

    # 13) 带 ID 的记录（IL 置位，手工构造）
    #     header = MB|ME|SR|IL|TNF=1 = 0xD9；ID 字段为 ASCII "id"。
    id_payload = bytes([0x02]) + b"en" + "with id".encode()
    with_id = bytes([0xD9, 0x01, len(id_payload), 0x02, ord("T"), ord("i"), ord("d")]) + id_payload
    fixtures.append(
        (
            "record_with_id",
            with_id,
            [
                ("record_count", "1"),
                ("record.0.tnf", "1"),
                ("record.0.type", "T"),
                ("record.0.id_hex", "6964"),
                ("record.0.text", "with id"),
            ],
        ),
    )

    # 14) 截断数据：text_en 的前 8 字节，负载被截断，解码必须报错而不是返回半截数据
    truncated = b"".join(ndef.message_encoder([ndef.TextRecord("Hello NFC", language="en")]))[:8]
    fixtures.append(("truncated_text", truncated, [("decode_error", "true")]))

    return fixtures


def main() -> int:
    os.makedirs(OUT_DIR, exist_ok=True)
    fixtures = build_fixtures()
    rows: list[str] = []

    for name, data, expectations in fixtures:
        path = os.path.join(OUT_DIR, f"{name}.ndef")
        with open(path, "wb") as handle:
            handle.write(data)
        rows.append(f"{name}.ndef\tencoded_size\t{len(data)}")
        rows.append(f"{name}.ndef\theader_first_byte\t0x{data[0]:02X}")
        for aspect, value in expectations:
            if "\t" in value or "\n" in value:
                print(f"期望值含制表符或换行，无法写入 TSV：{name} {aspect}", file=sys.stderr)
                return 1
            rows.append(f"{name}.ndef\t{aspect}\t{value}")
        print(f"{name:24s} {len(data):5d} 字节  {data[:16].hex().upper()}...")

    expectations_path = os.path.join(OUT_DIR, "expectations.tsv")
    with open(expectations_path, "w", encoding="utf-8") as handle:
        handle.write("# 由 tools/generate_ndef_fixtures.py 生成；样本由 ndeflib 编码产出\n")
        handle.write("# 格式：样本文件名<TAB>维度<TAB>期望值\n")
        handle.write("\n".join(rows))
        handle.write("\n")

    print(f"\n共 {len(fixtures)} 个样本 -> {OUT_DIR}")
    print(f"期望值表 -> {expectations_path}（{len(rows)} 行）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
