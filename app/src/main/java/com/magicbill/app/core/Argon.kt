package com.magicbill.app.core

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import java.util.Base64

/**
 * The one hash the phone computes: a staff PIN, exactly as the counter hashes it, so a PIN set
 * on the phone opens the counter. Parameters are the contract (PHONE_API.md §3):
 * Argon2id, 19456 KiB, 2 iterations, 1 lane, 16-byte salt, 32-byte hash, PHC string.
 * The PIN itself never leaves the phone.
 */
object Argon {
    const val MEMORY_KIB = 19456
    const val ITERATIONS = 2
    const val LANES = 1
    const val SALT_BYTES = 16
    const val HASH_BYTES = 32

    private val b64 = Base64.getEncoder().withoutPadding()
    private val b64d = Base64.getDecoder()

    fun hashPin(pin: String, salt: ByteArray = randomSalt()): String {
        require(salt.size == SALT_BYTES) { "salt must be $SALT_BYTES bytes" }
        val out = derive(pin, salt)
        return "\$argon2id\$v=19\$m=$MEMORY_KIB,t=$ITERATIONS,p=$LANES\$" + b64.encodeToString(salt) + "\$" + b64.encodeToString(out)
    }

    /** True when the PIN matches a PHC string made with these parameters. */
    fun verify(pin: String, phc: String): Boolean {
        val parts = phc.split('$')
        // "", "argon2id", "v=19", "m=…,t=…,p=…", salt, hash
        if (parts.size != 6 || parts[1] != "argon2id") return false
        val params = parts[3].split(',').associate { kv -> kv.substringBefore('=') to kv.substringAfter('=') }
        val m = params["m"]?.toIntOrNull() ?: return false
        val t = params["t"]?.toIntOrNull() ?: return false
        val p = params["p"]?.toIntOrNull() ?: return false
        val salt = b64d.decode(parts[4])
        val expected = b64d.decode(parts[5])
        val got = derive(pin, salt, m, t, p, expected.size)
        return got.contentEquals(expected)
    }

    private fun derive(pin: String, salt: ByteArray, m: Int = MEMORY_KIB, t: Int = ITERATIONS, p: Int = LANES, size: Int = HASH_BYTES): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(m)
            .withIterations(t)
            .withParallelism(p)
            .withSalt(salt)
            .build()
        val gen = Argon2BytesGenerator()
        gen.init(params)
        val out = ByteArray(size)
        gen.generateBytes(pin.toByteArray(Charsets.UTF_8), out)
        return out
    }

    fun randomSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
}
