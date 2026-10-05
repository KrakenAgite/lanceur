package app.lanceur.vault

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal

/** Empreinte ou visage (biométrie forte), avec le code du téléphone en secours. */
class Authenticator(private val activity: Activity) {
    private val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** `false` si le téléphone n'a aucun verrouillage d'écran. */
    fun canAuthenticate(): Boolean =
        activity.getSystemService(BiometricManager::class.java).canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(title: String, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setAllowedAuthenticators(allowed)
            .build()
        prompt.authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)

                // Erreur définitive ou annulation ; un essai raté (onAuthenticationFailed) laisse la fenêtre ouverte
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            },
        )
    }
}
