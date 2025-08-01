package com.ai.assistance.operit.core.invitation

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Base64
import com.ai.assistance.operit.data.preferences.InvitationRepository
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.first

/**
 * Represents the result of processing an invitation code from the clipboard.
 */
sealed class ProcessInvitationResult {
    /**
     * Indicates that the invitation was successfully processed.
     * @param message A success message for the user.
     * @param confirmationCode The generated code to be sent back to the inviter.
     */
    data class Success(val message: String, val confirmationCode: String) : ProcessInvitationResult()

    /**
     * Indicates that the processing failed.
     * @param reason A message explaining why it failed.
     */
    data class Failure(val reason: String) : ProcessInvitationResult()

    /**
     * Indicates that the device has already been invited.
     */
    object AlreadyInvited : ProcessInvitationResult()

    /**
     * Indicates that the user is being invited again by the same person, likely because they forgot to send the code.
     * @param confirmationCode The original confirmation code to be shown again.
     */
    data class Reminder(val confirmationCode: String) : ProcessInvitationResult()
}


/**
 * Manages the entire invitation flow, from code generation to verification.
 */
class InvitationManager(private val context: Context) {

    private val invitationRepository = InvitationRepository(context)

    /**
     * Retrieves the unique device ID.
     * Note: ANDROID_ID can change on factory reset, but is sufficient for this purpose.
     */
    @SuppressLint("HardwareIds")
    private fun getDeviceID(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    /**
     * Generates a user-friendly and unique invitation code from the device's ID.
     * This code is what user A shares with user B.
     */
    fun generateInvitationCode(): String {
        val deviceId = getDeviceID()
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(deviceId.toByteArray())
        // Use the first 4 bytes of the hash for a shorter, 8-character code.
        return hash.take(4).joinToString("") { "%02x".format(it) }
    }

    private val invitationMessages = listOf(
        // Re-adding the device ID placeholder to the format
        "OperitAI支持在手机上一键制作软件啦！[OperitAI邀请码:%s:%s] 复制并打开软件即可解锁新功能！",
        "OperitAI能够在手机和你语音，一边说话就能一边帮你干活，处理文件、音视频编辑都没问题！[OperitAI邀请码:%s:%s] 复制并打开软件即可解锁新功能！",
        "OperitAI让你在手机上，一键配置环境，编写代码！[OperitAI邀请码:%s:%s] 复制消息打开软件直接解锁工作区功能哦！",
        "什么？可以让AI自动控制你的手机，你只要说话就行了？OperitAI能做到！[OperitAI邀请码:%s:%s] 复制消息打开软件，体验未来交互！",
    )

    /**
     * Generates a promotional message containing the user's invitation code and a hash of their device ID.
     */
    fun generateInvitationMessage(): String {
        val code = generateInvitationCode()
        val deviceIdHash = MessageDigest.getInstance("SHA-256")
            .digest(getDeviceID().toByteArray())
            .take(8).joinToString("") { "%02x".format(it) } // Use a hash of the device ID
        val randomMessageTemplate = invitationMessages.random()
        return String.format(randomMessageTemplate, code, deviceIdHash)
    }

    /**
     * Processes an invitation from a string (e.g., from the clipboard).
     */
    suspend fun processInvitationFromText(text: String): ProcessInvitationResult {
        // 1. Extract the potential invitation code and inviter's device ID hash from the text.
        val regex = """\[OperitAI邀请码:([^:]+):([^\]]+)\]""".toRegex()
        val matchResult = regex.find(text)
        val invitationCode = matchResult?.groupValues?.getOrNull(1)
        val inviterDeviceIdHash = matchResult?.groupValues?.getOrNull(2)

        if (invitationCode.isNullOrBlank() || inviterDeviceIdHash.isNullOrBlank()) {
            return ProcessInvitationResult.Failure("未能在文本中找到有效格式的邀请码。")
        }

        // Self-invitation check
        if (invitationCode == generateInvitationCode()) {
            return ProcessInvitationResult.Failure("不能邀请自己哦！")
        }

        // 2. Anti-reciprocal check: See if the inviter is someone this device has already invited.
        val mySentInvitations = invitationRepository.sentInvitationToDeviceIdsFlow.first()
        if (mySentInvitations.contains(inviterDeviceIdHash)) {
            return ProcessInvitationResult.Failure("不能让对方返回来邀请一个邀请他的人哦")
        }

        // 3. Check if this device has already been invited by someone else.
        val isAlreadyInvited = invitationRepository.isInvitedFlow.first()
        if (isAlreadyInvited) {
            val lastUsedCode = invitationRepository.lastUsedInvitationCodeFlow.first()
            if (invitationCode == lastUsedCode) {
                val confirmationCode = generateConfirmationCode(invitationCode)
                    ?: return ProcessInvitationResult.Failure("无法重新生成返回码。")
                return ProcessInvitationResult.Reminder(confirmationCode)
            } else {
                return ProcessInvitationResult.AlreadyInvited
            }
        }

        // 4. This is a new invitation. Generate the confirmation code.
        val confirmationCode = generateConfirmationCode(invitationCode)
            ?: return ProcessInvitationResult.Failure("生成返回码失败，请稍后重试。")

        // 5. Mark this device as invited, save the code, and increment its own count.
        invitationRepository.setDeviceAsInvited()
        invitationRepository.setLastUsedInvitationCode(invitationCode)
        invitationRepository.incrementInvitationCount()

        return ProcessInvitationResult.Success("邀请已接受！", confirmationCode)
    }

    /**
     * Generates a confirmation code on the invitee's device (Device B).
     * This code is generated using the invitation code from Device A and the device ID of Device B.
     *
     * @param invitationCodeFromA The invitation code provided by the inviter.
     * @return A confirmation code string to be sent back to the inviter, or null on failure.
     *         The format is "deviceIdB:hmacSignature".
     */
    fun generateConfirmationCode(invitationCodeFromA: String): String? {
        val deviceIdB = getDeviceID()
        // The invitation code from A acts as the secret key for the HMAC.
        val hmac = generateHmac(secret = invitationCodeFromA, message = deviceIdB)
        // The confirmation code packs B's ID and the HMAC signature.
        return hmac?.let { "$deviceIdB:$it" }
    }

    /**
     * Verifies the confirmation code on the inviter's device (Device A).
     */
    suspend fun verifyConfirmationCode(confirmationCodeFromB: String): Boolean {
        val parts = confirmationCodeFromB.split(':', limit = 2)
        if (parts.size != 2) return false

        val deviceIdB = parts[0]
        val hmacFromB = parts[1]

        val myInvitationCode = generateInvitationCode()
        val expectedHmac = generateHmac(secret = myInvitationCode, message = deviceIdB)

        if (expectedHmac != null && hmacFromB == expectedHmac) {
            val deviceIdBHash = MessageDigest.getInstance("SHA-256")
                .digest(deviceIdB.toByteArray())
                .take(8).joinToString("") { "%02x".format(it) }
            if (invitationRepository.sentInvitationToDeviceIdsFlow.first().contains(deviceIdBHash)) {
                return false // Already verified.
            }
            invitationRepository.addSentInvitation(deviceIdBHash)
            invitationRepository.incrementInvitationCount()
            return true
        }
        return false
    }

    /**
     * A flow that emits the current total number of successful invitations.
     */
    fun getInvitationCountFlow() = invitationRepository.invitationCountFlow

    /**
     * Generates an HMAC-SHA256 signature for a given message and secret key.
     */
    private fun generateHmac(secret: String, message: String): String? {
        return try {
            val secretKey = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(secretKey)
            val hmacBytes = mac.doFinal(message.toByteArray())
            Base64.encodeToString(hmacBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Log error in a real app
            e.printStackTrace()
            null
        }
    }
} 