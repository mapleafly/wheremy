package com.wheremy.app.data.backup

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份包的加密原语（`FR-BAK-09` / `ADR-27` / `RULES.md` §12.5）。
 *
 * 只做三件事：**派生密钥**、**按条目加解密**、**验口令**。不认识 zip、不认识数据库、不认识界面——
 * 把它独立出来是为了"换算法只用改这一个文件"（`ADR-25`：官方/成熟优先，能自证就自证）。
 *
 * 三条设计取舍：
 * 1. **AES-256-GCM**（`javax.crypto` 自带）：认证加密，改一个字节整体拒收，不会产生半截数据；
 * 2. **条目内容 = 12 字节随机 IV + 密文与标签**：IV 自己带着，读的人不需要额外的映射表；
 * 3. **AAD = 条目名**：把某个条目的密文换个名字粘过去会验不过（防止"张冠李戴"式篡改）。
 */
internal object BackupCrypto {

    const val ALGORITHM = "AES-256-GCM"
    const val KDF = "PBKDF2-HMAC-SHA256"

    /** 📐 迭代数写进包里，所以以后可以上调而旧包仍可读（`RULES.md` §12.5）。 */
    const val ITERATIONS = 210_000

    /** 📐 口令长度下限（不强制大小写数字混合，理由见 `RULES.md` §12.5）。 */
    const val MIN_PASSWORD_LENGTH = 8

    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val VERIFIER_AAD = "wheremy-verifier"
    private val VERIFIER_PLAIN = "wheremy-backup-v2".toByteArray()

    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { random.nextBytes(it) }

    /** PBKDF2-HMAC-SHA256 → 256 位 AES 密钥。**口令本身不进包**。 */
    fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    /** 加密一个条目：[12 字节 IV] + [密文与认证标签]。 */
    fun seal(key: SecretKeySpec, plain: ByteArray, aad: String): ByteArray {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad.toByteArray())
        return iv + cipher.doFinal(plain)
    }

    /**
     * 解密一个条目。**标签不对就拒**（口令不对或内容被改过都会走到这里）；
     * 界面要区分"口令不对"得先用 [verifierOk] 验一次，别靠这里的异常猜。
     */
    fun open(key: SecretKeySpec, payload: ByteArray, aad: String): ByteArray {
        if (payload.size <= IV_BYTES) throw BackupFormatException("加密的内容读不出来，文件可能坏了")
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES))
            cipher.updateAAD(aad.toByteArray())
            cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES)
        } catch (e: AEADBadTagException) {
            throw BackupFormatException("加密的内容读不出来：口令不对，或者文件被改过")
        }
    }

    /** 口令校验块：用它把"口令不对"与"文件坏了"分开（`RULES.md` §12.5 判据 3）。 */
    fun makeVerifier(key: SecretKeySpec): ByteArray = seal(key, VERIFIER_PLAIN, VERIFIER_AAD)

    fun verifierOk(key: SecretKeySpec, verifier: ByteArray): Boolean = runCatching {
        open(key, verifier, VERIFIER_AAD).contentEquals(VERIFIER_PLAIN)
    }.getOrDefault(false)

    fun toBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun fromBase64(text: String): ByteArray = Base64.getDecoder().decode(text)
}
