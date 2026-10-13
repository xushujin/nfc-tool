package com.nfctool.app.core.ndef

/** NDEF 二进制数据不符合 NFC Forum NDEF 1.0 规范时抛出。 */
class NdefFormatException(message: String) : IllegalArgumentException(message)
