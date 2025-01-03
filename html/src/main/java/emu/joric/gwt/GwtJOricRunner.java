package emu.joric.gwt;

import com.badlogic.gdx.Gdx;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.typedarrays.shared.ArrayBuffer;
import com.google.gwt.typedarrays.shared.TypedArrays;
import com.google.gwt.typedarrays.shared.Uint8Array;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
import com.google.gwt.webworker.client.ErrorEvent;
import com.google.gwt.webworker.client.ErrorHandler;

import emu.joric.JOricRunner;
import emu.joric.KeyboardMatrix;
import emu.joric.PixelData;
import emu.joric.Program;
import emu.joric.config.AppConfigItem;
import emu.joric.sound.AYPSG;
import emu.joric.worker.MessageEvent;
import emu.joric.worker.MessageHandler;
import emu.joric.worker.Worker;

/**
 * GWT implementation of the JOricRunner. It uses a web worker to perform the execution
 * of the AGI interpreter animation ticks.
 */
public class GwtJOricRunner extends JOricRunner {

    /**
     * The web worker that will execute the AGI interpreter in the background.
     */
    private Worker worker;
    
    /**
     * Indicates that the GWT JOricRunner is in the stopped state, i.e. it was previously
     * running a game but the game has now stopped, e.g. due to the user quitting the game.
     */
    private boolean stopped;
    
    /**
     * Constructor for GwtJOricRunner.
     * 
     * @param keyboardMatrix
     * @param pixelData
     * @param psg
     */
    public GwtJOricRunner(KeyboardMatrix keyboardMatrix, PixelData pixelData, AYPSG psg) {
        super(keyboardMatrix, pixelData, psg);
        
        registerPopStateEventHandler();
    }

    private native void registerPopStateEventHandler() /*-{
        var that = this;
        var oldHandler = $wnd.onpopstate;
        $wnd.onpopstate = $entry(function(e) {
            that.@emu.joric.gwt.GwtJOricRunner::onPopState(Lcom/google/gwt/user/client/Event;)(e);
            if (oldHandler) {
                oldHandler();
            }
        });
    }-*/;
    
    private void onPopState(Event e) {
        String newURL = Window.Location.getHref();
        String programHashId = Window.Location.getHash();
        
        logToJSConsole("PopState - newURL: " + newURL + ", programHashId: " + programHashId);
        
        // If the URL does not have a hash, then it has gone back to the home screen.
        if ((programHashId == null) || (programHashId.trim().equals(""))) {
            if (isRunning()) {
                stop();
            }
        } else {
            Window.Location.reload();
        }
    }
    
    @Override
    public void start(AppConfigItem appConfigItem) {
        // The URL Builder doesn't add a / before the #, so we do this ourselves.
        String newURL = Window.Location.createUrlBuilder().setPath("/").setHash(null).buildString();
        if (newURL.endsWith("/")) {
            newURL += "#/";
        } else {
            newURL += "/#/";
        }
        newURL += slugify(appConfigItem.getName());
        
        logToJSConsole("newURL: " + newURL);
        
        updateURLWithoutReloading(newURL);
        
        GwtProgramLoader programLoader = new GwtProgramLoader();
        programLoader.fetchProgram(appConfigItem, p -> createWorker(appConfigItem, p));
    }

    private ArrayBuffer convertProgramToArrayBuffer(Program program) {
        int programDataLength = (program != null? program.getProgramData().length : 0);
        ArrayBuffer programArrayBuffer = TypedArrays.createArrayBuffer(programDataLength + 16384 + 8192);
        Uint8Array programUint8Array = TypedArrays.createUint8Array(programArrayBuffer);
        int index = 0;
        byte[] basicRom = Gdx.files.internal("roms/basic11b.rom").readBytes();
        for (int i=0; i < basicRom.length; index++, i++) {
            programUint8Array.set(index, (basicRom[i] & 0xFF));
        }
        byte[] microdiscRom = Gdx.files.internal("roms/microdis.rom").readBytes();
        for (int i=0; i < microdiscRom.length; index++, i++) {
            programUint8Array.set(index, (microdiscRom[i] & 0xFF));
        }
        if (program != null) {
            for (int i=0; i < programDataLength; index++, i++) {
                programUint8Array.set(index, (program.getProgramData()[i] & 0xFF));
            }
        }
        return programArrayBuffer;
    }
    
    /**
     * Creates a new web worker to run the Oric program.
     * 
     * @param program Contains the raw data of the Oric program to run.
     */
    public void createWorker(AppConfigItem appConfigItem, Program program) {
        // Convert program bytes to ArrayBuffer.
        ArrayBuffer programArrayBuffer = convertProgramToArrayBuffer(program);
        
        worker = Worker.create("/worker/worker.nocache.js");
        
        final MessageHandler webWorkerMessageHandler = new MessageHandler() {
            @Override
            public void onMessage(MessageEvent event) {
                JavaScriptObject eventObject = event.getDataAsObject();
                
                switch (getEventType(eventObject)) {
                    case "QuitGame":
                        // This message is sent from the worker when the program has ended, usually
                        // due to the user quitting the program.
                        stop();
                        break;
                        
                    default:
                        // Unknown. Ignore.
                }
            }
        };

        final ErrorHandler webWorkerErrorHandler = new ErrorHandler() {
            @Override
            public void onError(final ErrorEvent pEvent) {
                Gdx.app.error("client onError", "Received message: " + pEvent.getMessage());
            }
        };

        worker.setOnMessage(webWorkerMessageHandler);
        worker.setOnError(webWorkerErrorHandler);
        
        // In order to facilitate the communication with the worker, we must send
        // all SharedArrayBuffer objects to the webworker.
        GwtKeyboardMatrix gwtKeyboardMatrix = (GwtKeyboardMatrix)keyboardMatrix;
        GwtPixelData gwtPixelData = (GwtPixelData)pixelData;
        // TODO: Add PSG.
        JavaScriptObject keyMatrixSAB = gwtKeyboardMatrix.getSharedArrayBuffer();
        JavaScriptObject pixelDataSAB = gwtPixelData.getSharedArrayBuffer();
        // TODO: Add PSG.
        JavaScriptObject audioDataSAB = null;
        
        // We currently send one message to Initialise, using the SharedArrayBuffers,
        // then another message to Start the machine with the given game data. The 
        // game data is "transferred", whereas the others are not but rather shared.
        worker.postObject("Initialise", createInitialiseObject(
                keyMatrixSAB, 
                pixelDataSAB));
        worker.postArrayBufferAndObject("Start", 
                // TODO: Include ROMS. Need encoder/decoder for ROMs and program.
                programArrayBuffer,
                createStartObject(
                        appConfigItem.getName(),
                        appConfigItem.getFilePath(),
                        appConfigItem.getFileType(),
                        appConfigItem.getMachineType(),
                        appConfigItem.getRam())
                );
    }
    
    /**
     * Creates a JavaScript object, wrapping the objects to send to the web worker to
     * initialise the Machine.
     * 
     * @param keyMatrixSAB 
     * @param pixelDataSAB 
     * 
     * @return The created object.
     */
    private native JavaScriptObject createInitialiseObject(
            JavaScriptObject keyMatrixSAB, 
            JavaScriptObject pixelDataSAB)/*-{
        return { 
            keyMatrixSAB: keyMatrixSAB,
            pixelDataSAB: pixelDataSAB
        };
    }-*/;
    
    /**
     * Creates a JavaScript object using the given parameters to send in the Start
     * message to the web worker.
     * 
     * @param name
     * @param filePath
     * @param fileType
     * @param machineType
     * @param ramType
     * 
     * @return
     */
    private native JavaScriptObject createStartObject(
            String name, String filePath, String fileType, String machineType, 
            String ramType
            )/*-{
        return {
            name: name,
            filePath: filePath,
            fileType: fileType,
            machineType: machineType,
            ramType: ramType
        };
    }-*/;
    
    private native String getEventType(JavaScriptObject obj)/*-{
        return obj.name;
    }-*/;

    private native JavaScriptObject getEmbeddedObject(JavaScriptObject obj)/*-{
        return obj.object;
    }-*/;

    private native ArrayBuffer getArrayBuffer(JavaScriptObject obj)/*-{
        return obj.buffer;
    }-*/;

    private native int getNestedInt(JavaScriptObject obj, String fieldName)/*-{
        return obj.object[fieldName];
    }-*/;
    
    private static native void updateURLWithoutReloading(String newURL) /*-{
        $wnd.history.pushState(newURL, "", newURL);
    }-*/;
    
    private void clearUrl() {
        String newURL = Window.Location.createUrlBuilder()
                .setPath("/")
                .setHash(null)
                .buildString();
        updateURLWithoutReloading(newURL);
    }

    @Override
    public void stop() {
        // Kill off the web worker immediately. Ensure that any playing sound is stopped.
        paused = false;
        worker.terminate();
        // TODO: Stop sound processing.
        stopped = true;
    }
    
    @Override
    public void reset() {
        // Resets to the original state, as if a game has not been previously run.
        paused = false;
        stopped = false;
        worker = null;
        
        clearUrl();
        
        Gdx.graphics.setTitle("AGILE - The web-based Sierra On-Line Adventure Game Interpreter (AGI)");
    }

    @Override
    public boolean hasTouchScreen() {
        return hasTouchScreenHtml();
    }
    
    private native boolean hasTouchScreenHtml() /*-{
        if ("maxTouchPoints" in navigator) {
            return navigator.maxTouchPoints > 0;
        } else if ("msMaxTouchPoints" in navigator) {
            return navigator.msMaxTouchPoints > 0;
        } else {
            return false;
        }
    }-*/;

    @Override
    public boolean isMobile() {
        return isMobileHtml();
    }
    
    private native boolean isMobileHtml() /*-{
        if (navigator.userAgentData) {
            return navigator.userAgentData.mobile;
        } else {
            // Fall back to user-agent parsing, as some browsers don't support above yet.
            if (navigator.platform.indexOf("Win") != -1) return false;
            if (navigator.platform.indexOf("Mac") != -1) return false;
            if (navigator.platform.indexOf("Android") != -1) return true;
            if (navigator.platform.indexOf("iPhone") != -1) return true;
            if (navigator.platform.indexOf("iPad") != -1) return true;
            // For other devices, we'll use touch screen logic.
            if ("maxTouchPoints" in navigator) {
                return navigator.maxTouchPoints > 0;
            } else if ("msMaxTouchPoints" in navigator) {
                return navigator.msMaxTouchPoints > 0;
            } else {
                return false;
            }
        }
    }-*/;

    @Override
    public String slugify(String input) {
        return slugifyHtml(input);
    }
    
    private native String slugifyHtml(String input) /*-{
        if (!input) return '';

        // Make lower case and trim.
        var slug = input.toLowerCase().trim();

        // Remove accents from characters.
        slug = slug.normalize('NFD').replace(/[\u0300-\u036f]/g, '')

        // Replace invalid chars with spaces.
        slug = slug.replace(/[^a-z0-9\s-]/g, '').trim();

        // Replace multiple spaces or hyphens with a single hyphen.
        slug = slug.replace(/[\s-]+/g, '-');

        return slug;
    }-*/;

    @Override
    public void cancelImport() {
        clearUrl();
    }

    @Override
    public boolean hasStopped() {
        return ((worker != null) && stopped);
    }
    
    @Override
    public boolean isRunning() {
        return (worker != null);
    }
    
    private final native void logToJSConsole(String message)/*-{
        console.log(message);
    }-*/;
}
