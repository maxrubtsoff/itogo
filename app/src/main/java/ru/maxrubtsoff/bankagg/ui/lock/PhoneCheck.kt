package ru.maxrubtsoff.bankagg.ui.lock

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * «Забыли код?» (B-27): the system asks for the phone's own lock — fingerprint,
 * PIN, pattern or password. The app only learns yes or no. Whoever knows the
 * phone's lock owns the phone, so the app's code can be changed without wiping data.
 */
object PhoneCheck {

    // Android 9–10 can't combine a strong biometric with the screen lock.
    private val KINDS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** The phone has a screen lock to check against. */
    fun available(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun prompt(activity: FragmentActivity, onSuccess: () -> Unit) {
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            }
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Подтвердите, что это вы")
                .setSubtitle("Itogo · сменить код приложения")
                .setAllowedAuthenticators(KINDS)
                .build()
        )
    }
}
