package com.dfdx047.dragonfury

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.dfdx047.dragonfury.ui.LauncherApp
import com.dfdx047.dragonfury.ui.LauncherViewModel
import com.dfdx047.dragonfury.ui.theme.DragonFuryTheme

/**
 * Launcher Dragon Fury (Jetpack Compose). AppCompatActivity por causa do idioma
 * por app (pt/en) tambem em Android 11 e 12.
 */
class LauncherActivity : AppCompatActivity() {
    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DragonFuryTheme(themeMode = vm.themeMode, darkMode = vm.darkMode) {
                LauncherApp(vm)
            }
        }
        vm.checkUpdatesOnLaunch()
    }

    override fun onResume() {
        super.onResume()
        // Ao voltar do jogo: o menu rapido pode ter mudado opcoes e ha log novo.
        vm.reloadAfterGame()
    }
}
