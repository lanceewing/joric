package emu.joric.gwt;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;
import com.google.gwt.user.client.Window;

import emu.joric.JOric;
import emu.joric.JOricRunner;

/** Launches the GWT application. */
public class GwtLauncher extends GwtApplication {
    
    @Override
    public GwtApplicationConfiguration getConfig () {
        // Resizable application, uses available space in browser with no padding:
        GwtApplicationConfiguration cfg = new GwtApplicationConfiguration(true);
        cfg.padVertical = 0;
        cfg.padHorizontal = 0;
        return cfg;
        // If you want a fixed size application, comment out the above resizable section,
        // and uncomment below:
        //return new GwtApplicationConfiguration(640, 480);
    }

    @Override
    public ApplicationListener createApplicationListener () {
        Map<String, String> argsMap = new HashMap<>();
        
        String urlPath = Window.Location.getPath();
        
        // JOric supports loading games with a hash path.
        if ("/".equals(urlPath) || "".equals(urlPath)) {
            String hash = Window.Location.getHash().toLowerCase();
            if ((hash != null) && (hash.length() > 0)) {
                if (hash.startsWith("#/")) {
                    String programId = hash.substring(2);
                    argsMap.put("uri", programId);
                }
            } else {
                // JOric also supports loading from a provided URL.
                String programUrl = Window.Location.getParameter("url");
                if ((programUrl != null) && (!programUrl.trim().equals(""))) {
                    argsMap.put("url", programUrl);
                }
            }
        }
        
        GwtDialogHandler gwtDialogHandler = new GwtDialogHandler();
        JOricRunner joricRunner = new GwtJOricRunner(
                new GwtKeyboardMatrix(), 
                new GwtPixelData(),
                new GwtAYPSG());
        return new JOric(joricRunner, gwtDialogHandler, argsMap);
    }
    
    private final native void logToJSConsole(String message)/*-{
        console.log(message);
    }-*/;
}
