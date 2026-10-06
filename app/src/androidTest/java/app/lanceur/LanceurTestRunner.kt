package app.lanceur

import android.app.Application
import androidx.test.runner.AndroidJUnitRunner
import app.lanceur.i18n.L10n
import app.lanceur.i18n.Lang

/** Les tests d'interface vérifient les textes français, quelle que soit la langue du téléphone. */
class LanceurTestRunner : AndroidJUnitRunner() {
    override fun callApplicationOnCreate(app: Application) {
        L10n.forced = Lang.FR
        super.callApplicationOnCreate(app)
    }
}
