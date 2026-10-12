package com.dfdx047.dragonfury.game

/**
 * Estado do combate lido da memoria do jogo (game_state_jni.cpp, em libmain.so).
 * Usado pelos botoes de ajuda que dependem do lado (ex.: carregar ki = defesa +
 * "para tras" duas vezes).
 */
object NativeGameState {
    const val SIDE_UNKNOWN = 0
    /** P1 a esquerda do adversario: "para tras" = esquerda. */
    const val SIDE_LEFT = 1
    /** P1 a direita do adversario: "para tras" = direita. */
    const val SIDE_RIGHT = 2

    @Volatile
    private var available = true

    /** Lado do P1 neste momento (SIDE_*). */
    fun p1Side(): Int {
        if (!available) return SIDE_UNKNOWN
        return try {
            nativeP1Side()
        } catch (e: UnsatisfiedLinkError) {
            available = false
            SIDE_UNKNOWN
        }
    }

    /**
     * Pausa (GPU + audio) enquanto [overlayOpen] ou, com [pauseQuickMenu], enquanto o
     * menu rapido do port estiver aberto. Devolve se o jogo esta pausado.
     */
    fun syncPause(overlayOpen: Boolean, pauseQuickMenu: Boolean): Boolean {
        if (!available) return false
        return try {
            nativeSyncPause(overlayOpen, pauseQuickMenu)
        } catch (e: UnsatisfiedLinkError) {
            available = false
            false
        }
    }

    /** 1 = luta a decorrer, 0 = fora da luta, -1 = desconhecido (jogo ainda a arrancar). */
    fun fightFlag(): Int {
        if (!available) return -1
        return try {
            nativeFightFlag()
        } catch (e: UnsatisfiedLinkError) {
            available = false
            -1
        }
    }

    /** Muda opcoes do jogo ao vivo (como o menu rapido do PC) e grava o dbz3_user.toml. */
    fun setCvars(values: Map<String, String>): Boolean {
        if (!available || values.isEmpty()) return false
        return try {
            nativeSetCvars(values.keys.toTypedArray(), values.values.toTypedArray())
        } catch (e: UnsatisfiedLinkError) {
            available = false
            false
        }
    }

    @JvmStatic
    private external fun nativeFightFlag(): Int

    @JvmStatic
    private external fun nativeSetCvars(names: Array<String>, values: Array<String>): Boolean

    @JvmStatic
    private external fun nativeP1Side(): Int

    @JvmStatic
    private external fun nativeSyncPause(overlayOpen: Boolean, pauseQuickMenu: Boolean): Boolean
}
