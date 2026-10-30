package com.dfdx047.dragonfury.data

/**
 * Os dois jogos do disco da HD Collection. Cada um tem a sua pasta (com o executavel e a us/ do disco), o seu toml de
 * opcoes, os seus saves (user_data/dbz3 e user_data/dbz1: os dois tem o title ID 4E4D0856) e o seu libgame
 * encriptado (assets/libgame_<id>.zdat, chave tirada do executavel desse jogo). O launcher escolhe o jogo no cartao
 * da aba Inicio; o resto das abas (opcoes, desempenho, controles) segue o jogo escolhido via [AppPaths.game].
 */
enum class Game(
    val id: String,
    /** Pasta do jogo dentro da pasta da app e no disco. */
    val dirName: String,
    val xexName: String,
    val tomlName: String,
    /** Pasta dos saves e da cache do runtime (user_data/<x>). */
    val userDataName: String,
) {
    B3("b3", "DBZ3", "yae3_xenon.xex", "dbz3_user.toml", "dbz3"),
    B1("b1", "DBZ1", "yae1_xenon.xex", "dbz1_user.toml", "dbz1");

    val libName: String get() = "libgame_$id"

    /** Nome curto ("Budokai 3") e titulo completo, nas tres linguas. */
    val nameRes: Int get() = if (this == B1) com.dfdx047.dragonfury.R.string.game_b1_name else com.dfdx047.dragonfury.R.string.game_b3_name
    val titleRes: Int get() = if (this == B1) com.dfdx047.dragonfury.R.string.game_b1_title else com.dfdx047.dragonfury.R.string.game_b3_title

    companion object {
        const val EXTRA = "com.dfdx047.dragonfury.GAME"
        fun byId(id: String?): Game = entries.firstOrNull { it.id == id } ?: B3
    }
}
