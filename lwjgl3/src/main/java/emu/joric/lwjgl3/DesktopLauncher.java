package emu.joric.lwjgl3;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import emu.joric.JOric;

/** Launches the desktop (LWJGL3) application. */
public class DesktopLauncher {
    
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.
        createApplication(convertArgsToMap(args));
    }

    private static Map<String, String> convertArgsToMap(String[] args) {
        Map<String, String> argsMap = new HashMap<>();
        if ((args != null) && (args.length > 0)) {
            for (String arg : args) {
                int equalsIndex = arg.indexOf('=');
                if (equalsIndex != -1) {
                    String name = arg.substring(0, equalsIndex);
                    String value = arg.endsWith("=")? "" : arg.substring(equalsIndex + 1);
                    argsMap.put(name, value);
                }
            }
        }
        return argsMap;
    }
    
    private static Lwjgl3Application createApplication(Map<String, String> argsMap) {
        DesktopDialogHandler desktopDialogHandler = new DesktopDialogHandler();
        DesktopJOricRunner desktopJOricRunner = new DesktopJOricRunner(
                new DesktopKeyboardMatrix(), new DesktopPixelData(), 
                new AY38912PSG());
        return new Lwjgl3Application(
                new JOric(desktopJOricRunner, desktopDialogHandler, argsMap), 
                getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("JOric");
        configuration.useVsync(true);
        //// Limits FPS to the refresh rate of the currently active monitor.
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate);
        //// If you remove the above line and set Vsync to false, you can get unlimited FPS, which can be
        //// useful for testing performance, but can also be very stressful to some hardware.
        //// You may also need to configure GPU drivers to fully disable Vsync; this can cause screen tearing.
        configuration.setWindowedMode(540, 960);
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        return configuration;
    }
}