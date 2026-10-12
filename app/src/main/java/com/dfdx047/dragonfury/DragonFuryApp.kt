package com.dfdx047.dragonfury

import android.app.Application
import com.dfdx047.dragonfury.data.AppPaths

class DragonFuryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPaths.init(this)
    }

    companion object {
        /** Repositorio do port (pagina "Sobre" e verificacao de atualizacoes). */
        const val GITHUB_OWNER = "dfdx047"
        const val GITHUB_REPO = "DBZ3-Android"
        const val GITHUB_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO"
        const val DEVELOPER = "dfdx047"
        const val ORIGINAL_PROJECT_URL = "https://github.com/novapowers0/DBZ-Budokai-3-HD-Collection"
    }
}
