package com.dfdx047.dragonfury

import android.app.Application
import com.dfdx047.dragonfury.data.AppPaths

class DragonFuryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPaths.init(this)
        com.dfdx047.dragonfury.data.AppLog.init(this)
        com.dfdx047.dragonfury.data.AppLog.i(
            R.string.log_app_start,
            BuildConfig.VERSION_NAME,
            getProcessName() ?: "?",
        )
    }

    companion object {
        /** Repositorio do port (pagina "Sobre" e verificacao de atualizacoes). */
        const val GITHUB_OWNER = "dfdx047"
        const val GITHUB_REPO = "DBZ3-Android"
        const val GITHUB_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO"
        const val DEVELOPER = "dfdx047"
        const val ORIGINAL_PROJECT_URL = "https://github.com/novapowers0/DBZ-Budokai-3-HD-Collection"
        const val ORIGINAL_VERSION = "1.4.5.1"

        /** Redes do desenvolvedor (aba Inicio). */
        const val YOUTUBE_URL = "https://www.youtube.com/@DFDX47"
        const val DISCORD_URL = "https://discord.gg/mxjxmkVPuW"
        const val DEVELOPER_GITHUB_URL = "https://github.com/dfdx047"
        const val KOFI_URL = "https://ko-fi.com/dfdx047"
    }
}
