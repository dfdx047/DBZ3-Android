package pt.dfdx047.dbz3;

import android.os.Bundle;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.view.WindowManager;

import org.libsdl.app.SDLActivity;

import java.io.File;

/**
 * Atividade do jogo. O SDLActivity carrega libSDL3.so e libmain.so e chama
 * SDL_main (windowed_app_main_sdl.cpp) numa thread propria.
 *
 * Antes disso definimos as variaveis de ambiente que o runtime le no Android:
 *   REX_APP_DIR            pasta de dados (jogo, configuracoes, logs, cache)
 *   REX_NATIVE_LIB_DIR     pasta das .so extraidas (plugin da GPU)
 *   REX_DBZ3_SKIP_LAUNCHER salta o launcher ImGui do PC e arranca o jogo
 */
public class MainActivity extends SDLActivity {
    private static final String TAG = "dbz3";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        File appDir = getExternalFilesDir(null);
        if (appDir == null) {
            appDir = getFilesDir();
        }
        //noinspection ResultOfMethodCallIgnored
        appDir.mkdirs();

        setEnv("REX_APP_DIR", appDir.getAbsolutePath());
        setEnv("REX_NATIVE_LIB_DIR", getApplicationInfo().nativeLibraryDir);
        setEnv("REX_DBZ3_SKIP_LAUNCHER", "true");

        Log.i(TAG, "Pasta do jogo: " + appDir.getAbsolutePath() + "/DBZ3");
        super.onCreate(savedInstanceState);

        // O jogo corre a 60 FPS. Em ecras de 120 Hz o presenter (FIFO) estava a
        // apresentar 120 vezes por segundo o mesmo frame: o dobro do trabalho e
        // do calor para nada. Pedimos ao sistema o modo de 60 Hz.
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.preferredRefreshRate = 60.0f;
        getWindow().setAttributes(params);
    }

    @Override
    protected String[] getLibraries() {
        // libmain.so depende de librexruntime.so, librexgpu-xenos.so e
        // libc++_shared.so, que o linker do Android carrega sozinho.
        return new String[] {"SDL3", "main"};
    }

    private static void setEnv(String name, String value) {
        try {
            Os.setenv(name, value, true);
        } catch (ErrnoException e) {
            Log.e(TAG, "Falha a definir " + name, e);
        }
    }
}
