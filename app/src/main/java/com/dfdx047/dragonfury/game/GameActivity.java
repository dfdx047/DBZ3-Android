package com.dfdx047.dragonfury.game;

import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
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

        requestSixtyHz();
        // Jogo com carga constante: pede ao sistema para nao deixar a GPU/CPU cair em
        // estados de poupanca entre frames (buracos de 10-15 ms na GPU, logs de
        // 2026-10-13). "env:DF_NO_SUSTAINED = 1" no cvars.txt desliga, para A/B.
        if (!"1".equals(Os.getenv("DF_NO_SUSTAINED"))) {
            try {
                getWindow().setSustainedPerformanceMode(true);
                Log.i(TAG, "Modo de desempenho sustentado: ligado");
            } catch (RuntimeException e) {
                Log.w(TAG, "Modo de desempenho sustentado indisponivel", e);
            }
        }

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
        if (mOverlay != null) {
            mOverlay.onPhysicalInput(event);
        }
        // Back (o botao Back do Odin e de outros portateis, ou o gesto de voltar):
        // com um menu aberto volta um nivel; com o jogo a correr abre o menu.
        if (mOverlay != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                if (mOverlay.isMenuOpen()) {
                    mOverlay.onBackPressed();
                } else {
                    mOverlay.openMenu();
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (mOverlay != null) {
            mOverlay.onPhysicalInput(event);
        }
        return super.dispatchGenericMotionEvent(event);
    }

    /** Controles no modo automatico: o primeiro toque na tela fa-los aparecer. */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mOverlay != null && mOverlay.onScreenTouch(event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    /**
     * O jogo gera 60 frames/s por um relogio proprio (o vblank do Xenos), que nao
     * esta preso ao ecra. Num ecra de 120 Hz com FIFO cada frame fica 2 vblanks
     * na tela, mas basta o relogio oscilar 1 ms para alguns ficarem 1 ou 3
     * (8 ms / 25 ms): e o "stutter" que se ve mesmo com o contador nos 60. O
     * preferredRefreshRate sozinho e so uma sugestao que varios fabricantes
     * ignoram, por isso pedimos o modo de 60 Hz pelo id e, alem disso, marcamos
     * a superficie do SDL como fonte fixa de 60 FPS (API 30).
     */
    private void requestSixtyHz() {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.preferredRefreshRate = 60.0f;
        StringBuilder modes = new StringBuilder();
        try {
            Display display = getWindowManager().getDefaultDisplay();
            Display.Mode current = display.getMode();
            Display.Mode best = null;
            for (Display.Mode m : display.getSupportedModes()) {
                modes.append(String.format(Locale.ROOT, " %d:%dx%d@%.2f", m.getModeId(),
                        m.getPhysicalWidth(), m.getPhysicalHeight(), m.getRefreshRate()));
                if (m.getPhysicalWidth() != current.getPhysicalWidth()
                        || m.getPhysicalHeight() != current.getPhysicalHeight()) {
                    continue;
                }
                if (Math.abs(m.getRefreshRate() - 60.0f) < 1.0f) {
                    best = m;
                }
            }
            if (best != null) {
                params.preferredDisplayModeId = best.getModeId();
            }
            Log.i(TAG, String.format(Locale.ROOT, "Ecra: atual %.2f Hz, modo pedido %s, modos:%s",
                    display.getRefreshRate(), best != null ? String.valueOf(best.getModeId()) : "nenhum de 60 Hz",
                    modes));
        } catch (RuntimeException e) {
            Log.w(TAG, "Ecra: nao foi possivel escolher o modo de 60 Hz", e);
        }
        getWindow().setAttributes(params);

        if (mSurface != null) {
            mSurface.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    try {
                        holder.getSurface().setFrameRate(60.0f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
                    } catch (RuntimeException e) {
                        Log.w(TAG, "Surface.setFrameRate falhou", e);
                    }
                    // O sistema muda o modo com algum atraso: regista o que ficou.
                    mSurface.postDelayed(() -> {
                        Display d = getWindowManager().getDefaultDisplay();
                        Log.i(TAG, String.format(Locale.ROOT, "Ecra durante o jogo: %.2f Hz", d.getRefreshRate()));
                    }, 3000);
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                }
            });
        }
    }

    /** Orientacao escolhida no launcher: paisagem, paisagem invertida ou automatica. */
    private void applyOrientation() {
        @SuppressWarnings("deprecation")
        SharedPreferences prefs = getSharedPreferences("dragonfury", MODE_MULTI_PROCESS);
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
        // As partes rodadas (.1.log ...) sao de sessoes antigas: so confundiam a
        // exportacao de logs (20 MB de uma sessao com o log detalhado).
        File[] old = logs.listFiles();
        if (old != null) {
            for (File f : old) {
                String n = f.getName();
                if (n.startsWith("dragonfury_last.") && !n.equals("dragonfury_last.log")) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
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
                if (name.startsWith("env:")) {
                    // "env:TU_DEBUG = sysmem": variavel crua para o driver (testes A/B
                    // do Turnip, MESA_*, etc.), sem o prefixo REX_.
                    String env = name.substring(4).trim();
                    setEnv(env, value);
                    Log.i(TAG, "env " + env + " = " + value);
                    continue;
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
