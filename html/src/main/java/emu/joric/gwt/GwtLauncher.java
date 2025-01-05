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
                logToJSConsole("Attempting to load program from URL: " + programUrl);
                if ((programUrl != null) && (!programUrl.trim().equals(""))) {
                    if (isProgramURLValid(programUrl)) {
                        argsMap.put("url", programUrl);
                    } else {
                        // Remove the url param if the value is not valid.
                        String cleanURL = Window.Location.createUrlBuilder()
                                .removeParameter("url")
                                .buildString();
                        updateURLWithoutReloading(cleanURL);
                    }
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
    
    private boolean isProgramURLValid(String url) {
        String lcProgramURL = url.toLowerCase();
        if ((lcProgramURL.endsWith(".dsk")) || (lcProgramURL.endsWith(".tap"))) {
            // If the extension looks fine, then check if the URL itself is valid.
            return isURLValid(url);
        } else {
            if (lcProgramURL.endsWith(".zip")) {
                logToJSConsole("Sorry, JOric does not support ZIP files yet, but will do soon.");
            } else if (lcProgramURL.endsWith(".tgz")) {
                logToJSConsole("Sorry, JOric does not support tgz files.");
            } else {
                logToJSConsole("Sorry, the URL provided does not appear to be for a recognised Oric program file format.");
            }
            return false;
        }
    }
    
    private final native boolean isURLValid(String url)/*-{
        try {
            new URL(url);
            return true;
        } catch (err) {
            console.log("Sorry, the program URL does not appear to be well formed.");
            return false;
        }
    }-*/;
    
    // NOTE: This version does not add anything to the history.
    private static native void updateURLWithoutReloading(String newURL) /*-{
        $wnd.history.replaceState(newURL, "", newURL);
    }-*/;
    
    private final native void logToJSConsole(String message)/*-{
        console.log(message);
    }-*/;
}
