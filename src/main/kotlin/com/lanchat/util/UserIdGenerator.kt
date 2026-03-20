package com.lanchat.util

import java.net.NetworkInterface
import java.security.MessageDigest

/**
 * 用户唯一 ID 生成器
 * 基于本机 MAC 地址生成稳定的唯一标识
 */
object UserIdGenerator {

    /**
     * 生成基于 MAC 地址的唯一用户 ID
     * 优先使用第一个非回环网卡的 MAC 地址
     * 如果无法获取 MAC 地址，使用随机 UUID
     *
     * @return 32 位十六进制字符串（小写）
     */
    fun generateUserId(): String {
        val macAddress = getFirstMacAddress()
        return if (macAddress != null) {
            md5Hash(macAddress)
        } else {
            // 无法获取 MAC 地址时使用随机 UUID
            // 但这不是推荐的方案，因为每次启动都会变
            java.util.UUID.randomUUID().toString().replace("-", "")
        }
    }

    /**
     * 检查当前用户 ID 是否有效
     * 有效 ID 应该是 32 位十六进制字符串（小写，无连字符）
     */
    fun isValidUserId(userId: String): Boolean {
        return userId.matches(Regex("^[a-f0-9]{32}$"))
    }

    /**
     * 获取第一个非回环网卡的 MAC 地址
     */
    private fun getFirstMacAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            for (ni in interfaces) {
                // 跳过回环接口和虚拟接口
                if (ni.isLoopback || ni.isVirtual) continue
                // 跳过没有 MAC 地址的接口
                val mac = ni.hardwareAddress ?: continue
                if (mac.isNotEmpty()) {
                    return mac.joinToString(":") { "%02X".format(it) }
                }
            }
        } catch (e: Exception) {
            // 忽略异常，返回 null
        }
        return null
    }

    /**
     * 计算 MD5 哈希值
     */
    private fun md5Hash(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
