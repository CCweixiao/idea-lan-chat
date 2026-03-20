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
     * 获取第一个可用的 MAC 地址
     * 优先使用非回环、非点对点的网卡，但也接受虚拟网卡
     * 这样可以支持 Docker 容器等虚拟化环境
     */
    private fun getFirstMacAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
                .asSequence()
                .filter { it.isUp && !it.isLoopback }  // 只要是 UP 且非回环即可
                .sortedWith(compareBy<NetworkInterface> { it.isVirtual }.thenBy { it.isPointToPoint })
                .firstNotNullOfOrNull { ni ->
                    val mac = ni.hardwareAddress
                    if (mac != null && mac.isNotEmpty()) {
                        mac.joinToString(":") { "%02X".format(it) }
                    } else null
                }
            return interfaces
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
