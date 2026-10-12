package com.dfdx047.dragonfury.game;

import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;

import org.libsdl.app.SDLActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Atividade do jogo. O SDLActivity carrega libSDL3.so e libmain.so e chama
 * SDL_main (windowed_app_main_sdl.cpp) numa thread propria.
 *
 * Aberta pelo launcher (LauncherActivity) ou pelo atalho da tela inicial. As
 * opcoes do jogo ja estao no dbz3_user.toml (escrito pelo launcher); aqui so se
 * aplica a orientacao, o driver Vulkan e os controles por toque (GameOverlay).
 *
 * Antes disso definimos as variaveis de ambiente que o runtime le no Android:
 *   REX_APP_DIR            pasta de dados (jogo, configuracoes, logs, cache)
 *   REX_NATIVE_LIB_DIR     pasta das .so extraidas (plugin da GPU)
 *   REX_DBZ3_SKIP_LAUNCHER salta o launcher ImGui do PC e arranca o jogo
 */
public class GameActivity extends SDLActivity {
    private static final String TAG = "dbz3";

    private GameOverlay mOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyOrientation();

        File appDir = getExternalFilesDir(null);
        if (appDir == null) {
            appDir = getFilesDir();
        }
        //noinspection ResultOfMethodCallIgnored
        appDir.mkdirs();

        setEnv("REX_APP_DIR", appDir.getAbsolutePath());
        setEnv("REX_NATIVE_LIB_DIR", getApplicationInfo().nativeLibraryDir);
        setEnv("REX_DBZ3_SKIP_LAUNCHER", "true");
        // Compilacao de pipelines sincrona: no Android a async escondia 1 frame
        // de cada vez que um pipeline faltava (travadinhas), e a sincrona custa
        // menos de 1 ms por frame com a cache do driver. O cvars.txt pode mudar.
        setEnv("REX_DBZ3_ASYNC_SHADERS", "false");
        applyCvarOverrides(new File(appDir, "cvars.txt"));
        installCustomVulkanDriver(appDir);
        prepareLogFile(appDir);

        Log.i(TAG, "Pasta do jogo: " + appDir.getAbsolutePath() + "/DBZ3");
        super.onCreate(savedInstanceState);

        // O jogo corre a 60 FPS. Em ecras de 120 Hz o presenter (FIFO) estava a
        // apresentar 120 vezes por segundo o mesmo frame: o dobro do trabalho e
        // do calor para nada. Pedimos ao sistema o modo de 60 Hz.
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.preferredRefreshRate = 60.0f;
        getWindow().setAttributes(params);

        // Controles por toque por cima do jogo (se nao foram desligados no launcher).
        mOverlay = new GameOverlay(this);
        mOverlay.onCreate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mOverlay != null) {
            mOverlay.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (mOverlay != null) {
            mOverlay.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mOverlay != null) {
            mOverlay.onDestroy();
            mOverlay = null;
        }
        super.onDestroy();
    }

    /** Com o menu dos controles aberto, o Back do Android fecha o menu (nao vai para o jogo). */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mOverlay != null && mOverlay.isMenuOpen()) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    mOverlay.onBackPressed();
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /** Orientacao escolhida no launcher: paisagem, paisagem invertida ou automatica. */
    private void applyOrientation() {
        SharedPreferences prefs = getSharedPreferences("dragonfury", MODE_PRIVATE);
        String mode = prefs.getString("game_orientation", "landscape");
        int orientation;
        if ("reverse".equals(mode)) {
            orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
        } else if ("auto".equals(mode)) {
            orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
        } else {
            orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        }
        setRequestedOrientation(orientation);
    }

    /**
     * O log desta sessao vai para logs/dragonfury_last.log (a aba Texturas do
     * launcher le-o para mostrar o estado dos packs). O cvars.txt pode mudar.
     */
    private static void prepareLogFile(File appDir) {
        File logs = new File(appDir, "logs");
        //noinspection ResultOfMethodCallIgnored
        logs.mkdirs();
        File log = new File(logs, "dragonfury_last.log");
        //noinspection ResultOfMethodCallIgnored
        log.delete();
        if (Os.getenv("REX_LOG_FILE") == null) {
            setEnv("REX_LOG_FILE", log.getAbsolutePath());
        }
    }

    @Override
    protected String[] getLibraries() {
        // libmain.so depende de librexruntime.so, librexgpu-xenos.so e
        // libc++_shared.so, que o linker do Android carrega sozinho.
        return new String[] {"SDL3", "main"};
    }

    /**
     * Le cvars.txt da pasta da app (linhas "nome = valor", # para comentarios) e
     * passa cada uma ao runtime como variavel REX_NOME. Serve para testar opcoes
     * do runtime sem recompilar: editar o ficheiro e reabrir o jogo.
     */
    private static void applyCvarOverrides(File file) {
        if (!file.isFile()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String name = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                setEnv("REX_" + name.toUpperCase(Locale.ROOT), value);
                Log.i(TAG, "cvar " + name + " = " + value);
            }
        } catch (IOException e) {
            Log.e(TAG, "Falha a ler " + file, e);
        }
    }

    /**
     * Driver Vulkan alternativo (ex. Turnip): se a pasta "driver" da pasta da app
     * tiver um .so, copia-o para a pasta interna (o Android nao deixa carregar
     * bibliotecas a partir do armazenamento partilhado) e diz ao runtime para o
     * usar via libadrenotools. Sem .so nessa pasta, usa-se o driver do sistema.
     */
    private void installCustomVulkanDriver(File appDir) {
        File internalDir = new File(getFilesDir(), "driver");
        File[] candidates = new File(appDir, "driver").listFiles(
                (dir, name) -> name.endsWith(".so"));
        if (candidates == null || candidates.length == 0) {
            deleteRecursively(internalDir);
            return;
        }
        File source = candidates[0];
        File target = new File(internalDir, source.getName());
        try {
            if (!target.isFile() || target.length() != source.length()
                    || target.lastModified() != source.lastModified()) {
                deleteRecursively(internalDir);
                //noinspection ResultOfMethodCallIgnored
                internalDir.mkdirs();
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                //noinspection ResultOfMethodCallIgnored
                target.setLastModified(source.lastModified());
            }
            setEnv("REX_VULKAN_DRIVER_DIR", internalDir.getAbsolutePath() + "/");
            setEnv("REX_VULKAN_DRIVER_NAME", target.getName());
            Log.i(TAG, "Driver Vulkan alternativo: " + target.getName());
        } catch (IOException e) {
            Log.e(TAG, "Falha a instalar o driver " + source, e);
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static void setEnv(String name, String value) {
        try {
            Os.setenv(name, value, true);
        } catch (ErrnoException e) {
            Log.e(TAG, "Falha a definir " + name, e);
        }
    }
}
