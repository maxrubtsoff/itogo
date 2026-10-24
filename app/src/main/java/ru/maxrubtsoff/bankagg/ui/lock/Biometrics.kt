package ru.maxrubtsoff.bankagg.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** The system fingerprint prompt; the PIN keypad stays underneath as the way back. */
object Biometrics {

    private val KINDS = BIOMETRIC_STRONG or BIOMETRIC_WEAK

    /** A sensor is there and a fingerprint is enrolled in the system. */
    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(KINDS) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(activity: FragmentActivity, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Itogo")
                .setSubtitle("Подтвердите вход отпечатком")
                .setNegativeButtonText("Ввести код")
                .setAllowedAuthenticators(KINDS)
                .build()
        )
    }
}
