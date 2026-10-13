package com.nfctool.app.core.wsc

/**
 * Wi-Fi Simple Configuration（WSC，即早期所称 WPS）的属性 ID。
 *
 * 取值来源：hostap / wpa_supplicant 源码 `src/wps/wps_defs.h` 中的 `wps_attribute` 枚举
 * （下载地址 https://w1.fi/releases/wpa_supplicant-2.11.tar.gz），已逐条比对。
 * 详见 README「外部依据」章节。
 */
object WscAttributeId {
    const val AUTH_TYPE = 0x1003
    const val CREDENTIAL = 0x100E
    const val ENCRYPTION_TYPE = 0x100F
    const val MAC_ADDRESS = 0x1020
    const val NETWORK_INDEX = 0x1026
    const val NETWORK_KEY = 0x1027
    const val SSID = 0x1045
    const val VERSION = 0x104A
}

/**
 * 认证类型取值。
 * 0x0001 / 0x0002 / 0x0004 / 0x0008 / 0x0010 / 0x0020 与 hostap `wps_defs.h` 的
 * `WPS_AUTH_*` 宏逐条一致。
 */
enum class WscAuthType(val code: Int, val label: String) {
    OPEN(0x0001, "开放（无密码）"),
    WPA_PERSONAL(0x0002, "WPA-Personal"),
    SHARED(0x0004, "Shared（已废弃）"),
    WPA_ENTERPRISE(0x0008, "WPA-Enterprise"),
    WPA2_ENTERPRISE(0x0010, "WPA2-Enterprise"),
    WPA2_PERSONAL(0x0020, "WPA2-Personal"),
    ;

    companion object {
        fun fromCode(code: Int): WscAuthType? = entries.firstOrNull { it.code == code }
    }
}

/**
 * 加密类型取值，与 hostap `wps_defs.h` 的 `WPS_ENCR_*` 宏一致。
 * 注：WPA/WPA2 混合模式在 WSC 中的认证类型取值为 0x0022、加密类型为 0x000C，
 * 这两个组合值 hostap 未以宏形式给出，本工程**未采纳**，仅支持上表中的单一取值。
 */
enum class WscEncryptionType(val code: Int, val label: String) {
    NONE(0x0001, "无加密"),
    WEP(0x0002, "WEP（已废弃）"),
    TKIP(0x0004, "TKIP"),
    AES(0x0008, "AES"),
    ;

    companion object {
        fun fromCode(code: Int): WscEncryptionType? = entries.firstOrNull { it.code == code }
    }
}

/** WSC 版本：0x10 = WSC 1.0，0x20 = WSC 2.0。Android 写入 WiFi 标签时使用 1.0。 */
const val WSC_VERSION_1_0: Byte = 0x10

/** 一条 WiFi 凭据（对应 WSC Credential 属性）。 */
data class WifiCredential(
    val ssid: String,
    val networkKey: String,
    val authType: WscAuthType,
    val encryptionType: WscEncryptionType,
    val networkIndex: Int = 1,
    val macAddress: ByteArray? = null,
) {
    val isOpen: Boolean get() = authType == WscAuthType.OPEN

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WifiCredential) return false
        val macEquals = when {
            macAddress == null -> other.macAddress == null
            other.macAddress == null -> false
            else -> macAddress.contentEquals(other.macAddress)
        }
        return ssid == other.ssid &&
            networkKey == other.networkKey &&
            authType == other.authType &&
            encryptionType == other.encryptionType &&
            networkIndex == other.networkIndex &&
            macEquals
    }

    override fun hashCode(): Int {
        var result = ssid.hashCode()
        result = 31 * result + networkKey.hashCode()
        result = 31 * result + authType.hashCode()
        result = 31 * result + encryptionType.hashCode()
        result = 31 * result + networkIndex
        result = 31 * result + (macAddress?.contentHashCode() ?: 0)
        return result
    }

    /** 用于日志与界面展示，刻意不包含明文密码。 */
    override fun toString(): String =
        "WifiCredential(ssid=$ssid, auth=${authType.name}, encryption=${encryptionType.name}, " +
            "hasKey=${networkKey.isNotEmpty()})"
}
