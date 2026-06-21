package emu.joric.teavm;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Pixmap;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.SharedArrayBuffer;
import org.teavm.jso.typedarrays.Uint8Array;

import emu.joric.JOricRunner;
import emu.joric.Program;
import emu.joric.RomConfig;
import emu.joric.config.AppConfigItem;

public class TeaVMJOricRunner extends JOricRunner {

    private JSObject worker;
    private boolean stopped;
    private final TeaVMFrameCounter frameCounter;
    private int lastConsumedFrameCount;

    public TeaVMJOricRunner() {
        super(new TeaVMKeyboardMatrix(), new TeaVMPixelData(), new TeaVMAYPSG());
        frameCounter = new TeaVMFrameCounter();
        ((TeaVMAYPSG)psg).attachAudioWorklet(this);
        TeaVMBrowser.registerPopStateListener(this::onPopState);
    }

    @Override
    public void start(AppConfigItem appConfigItem) {
        if ((TeaVMBrowser.getQueryParameter("url") == null)
                && (!"Adhoc Oric Program".equals(appConfigItem.getName()))
                && (TeaVMBrowser.getHash().indexOf('=') < 0)
                && (TeaVMBrowser.getHash().indexOf('/') < 0)) {
            TeaVMBrowser.pushState(buildProgramUrl(appConfigItem));
        }

        TeaVMProgramLoader programLoader = new TeaVMProgramLoader();
        programLoader.fetchProgram(appConfigItem, program -> createWorker(appConfigItem, program));
    }

    private void createWorker(AppConfigItem appConfigItem, Program program) {
        int programDataLength = (program != null) ? program.getProgramData().length : 0;
        ArrayBuffer programArrayBuffer = convertProgramToArrayBuffer(appConfigItem, program);

        worker = TeaVMWorkerInterop.createWorker("./scripts/joric-worker.js");
        TeaVMWorkerInterop.setOnMessage(worker, this::handleWorkerMessage);
        TeaVMWorkerInterop.setOnError(worker, this::handleWorkerError);

        TeaVMKeyboardMatrix teaVMKeyboardMatrix = (TeaVMKeyboardMatrix)keyboardMatrix;
        TeaVMPixelData teaVMPixelData = (TeaVMPixelData)pixelData;
        teaVMPixelData.clearPixels();
        TeaVMAYPSG teaVMAYPSG = (TeaVMAYPSG)psg;
        frameCounter.reset();
        lastConsumedFrameCount = 0;

        SharedArrayBuffer keyMatrixSAB = teaVMKeyboardMatrix.getSharedArrayBuffer();
        SharedArrayBuffer pixelDataSAB = teaVMPixelData.getSharedArrayBuffer();
        SharedArrayBuffer audioDataSAB = teaVMAYPSG.getSharedArrayBuffer();
        SharedArrayBuffer frameCounterSAB = frameCounter.getSharedArrayBuffer();

        TeaVMWorkerInterop.postObject(worker, "Initialise",
                TeaVMWorkerInterop.createInitialiseObject(keyMatrixSAB, pixelDataSAB,
                        audioDataSAB, frameCounterSAB, teaVMAYPSG.getSampleRate()));
        TeaVMWorkerInterop.postArrayBufferAndObject(worker, "Start", programArrayBuffer,
                TeaVMWorkerInterop.createStartObject(
                        appConfigItem.getName(),
                        appConfigItem.getFilePath(),
                        appConfigItem.getFileType(),
                        appConfigItem.getMachineType(),
                        appConfigItem.getRam(),
                        programDataLength));

        stopped = false;
        paused = false;
        psg.resumeSound();
    }

    private ArrayBuffer convertProgramToArrayBuffer(AppConfigItem appConfigItem, Program program) {
        int programDataLength = (program != null) ? program.getProgramData().length : 0;

        // ROM layout: basicRom (16384) + microdiscRom (8192) + programData
        ArrayBuffer programArrayBuffer = ArrayBuffer.create(16384 + 8192 + programDataLength);
        Uint8Array programUint8Array = Uint8Array.create(programArrayBuffer);
        int index = 0;

        RomConfig.Option romOpt = RomConfig.resolveRom(
                appConfigItem, Gdx.app.getPreferences("joric.preferences"));
        byte[] basicRom = Gdx.files.internal("roms/" + romOpt.filename).readBytes();
        for (byte b : basicRom) {
            programUint8Array.set(index++, (short)(b & 0xFF));
        }

        byte[] microdiscRom = Gdx.files.internal("roms/microdis.rom").readBytes();
        for (byte b : microdiscRom) {
            programUint8Array.set(index++, (short)(b & 0xFF));
        }

        if (program != null) {
            for (byte b : program.getProgramData()) {
                programUint8Array.set(index++, (short)(b & 0xFF));
            }
        }

        return programArrayBuffer;
    }

    private void handleWorkerMessage(JSObject eventObject) {
        switch (TeaVMWorkerInterop.getEventType(eventObject)) {
            case "QuitGame":
                stop();
                break;

            case "WorkerError":
                Gdx.app.error("TeaVM worker detail",
                        TeaVMWorkerInterop.getNestedString(eventObject, "message"));
                break;

            default:
                break;
        }
    }

    private void handleWorkerError(String message) {
        Gdx.app.error("TeaVM worker", message);
    }

    private String buildProgramUrl(AppConfigItem appConfigItem) {
        String currentUrl = TeaVMBrowser.getHref();
        String baseUrl = currentUrl.split("[?]")[0];
        int hashIndex = baseUrl.indexOf('#');
        if (hashIndex >= 0) {
            baseUrl = baseUrl.substring(0, hashIndex);
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl + "#/" + slugify(appConfigItem.getName());
        }
        return baseUrl + "/#/" + slugify(appConfigItem.getName());
    }

    private void onPopState() {
        String programHashId = TeaVMBrowser.getHash();
        if ((programHashId == null) || programHashId.trim().isEmpty()) {
            if (isRunning()) {
                stop();
            }
        } else {
            TeaVMBrowser.reload();
        }
    }

    @Override
    public void reset() {
        exit = false;
        paused = false;
        stopped = false;
        frameCounter.reset();
        lastConsumedFrameCount = 0;
        TeaVMBrowser.replaceState(TeaVMBrowser.buildCleanUrl());
        worker = null;
        Gdx.graphics.setTitle("JOric - The web-based Oric emulator built with libGDX");
    }

    @Override
    public void updatePixmap(Pixmap pixmap) {
        super.updatePixmap(pixmap);
        lastConsumedFrameCount = frameCounter.get();
    }

    public boolean hasNewFrame() {
        return frameCounter.get() != lastConsumedFrameCount;
    }

    @Override
    public boolean hasStopped() {
        return ((worker != null) && stopped);
    }

    @Override
    public boolean hasTouchScreen() {
        return Gdx.input.isPeripheralAvailable(Input.Peripheral.MultitouchScreen);
    }

    @Override
    public boolean isMobile() {
        return isMobileHtml();
    }

    @JSBody(script = "if (navigator.userAgentData) {"
            + " return !!navigator.userAgentData.mobile;"
            + "} else {"
            + " var platform = navigator.platform || '';"
            + " if (platform.indexOf('Win') !== -1) return false;"
            + " if (platform.indexOf('Mac') !== -1) return false;"
            + " if (platform.indexOf('Android') !== -1) return true;"
            + " if (platform.indexOf('iPhone') !== -1) return true;"
            + " if (platform.indexOf('iPad') !== -1) return true;"
            + " if ('maxTouchPoints' in navigator) {"
            + "   return navigator.maxTouchPoints > 0;"
            + " } else if ('msMaxTouchPoints' in navigator) {"
            + "   return navigator.msMaxTouchPoints > 0;"
            + " } else {"
            + "   return false;"
            + " }"
            + "}")
    private static native boolean isMobileHtml();

    @Override
    public String slugify(String input) {
        if ((input == null) || input.isEmpty()) {
            return "";
        }
        String slug = input.toLowerCase().trim();
        slug = slug.replaceAll("[^a-z0-9\\s-]", "").trim();
        slug = slug.replaceAll("[\\s-]+", "-");
        return slug;
    }

    @Override
    public void cancelImport() {
        TeaVMBrowser.replaceState(TeaVMBrowser.buildCleanUrl());
    }

    @Override
    public boolean isRunning() {
        return worker != null;
    }

    @Override
    public void sendNmi() {
        if (worker != null) {
            TeaVMWorkerInterop.postObject(worker, "SendNMI", TeaVMWorkerInterop.createEmptyObject());
        }
    }

    @Override
    public void stop() {
        paused = false;
        if (worker != null) {
            TeaVMWorkerInterop.terminate(worker);
        }
        psg.pauseSound();
        stopped = true;
    }

    @Override
    public void pause() {
        super.pause();
        if (worker != null) {
            TeaVMWorkerInterop.postObject(worker, "Pause", TeaVMWorkerInterop.createEmptyObject());
        }
    }

    @Override
    public void resume() {
        super.resume();
        if (worker != null) {
            TeaVMWorkerInterop.postObject(worker, "Unpause", TeaVMWorkerInterop.createEmptyObject());
        }
    }

    @Override
    public void changeSound(boolean soundOn) {
        super.changeSound(soundOn);
        if (!soundOn && (worker != null)) {
            TeaVMWorkerInterop.postObject(worker, "SoundOff", TeaVMWorkerInterop.createEmptyObject());
        }
    }

    @Override
    public void toggleWarpSpeed() {
        super.toggleWarpSpeed();
        if (worker != null) {
            TeaVMWorkerInterop.postObject(worker,
                    warpSpeed ? "WarpSpeedOn" : "WarpSpeedOff",
                    TeaVMWorkerInterop.createEmptyObject());
        }
    }

    void notifyAudioWorkletReady() {
        if (worker != null) {
            TeaVMWorkerInterop.postObject(worker, "AudioWorkletReady", TeaVMWorkerInterop.createEmptyObject());
            if (getMachineInputProcessor() != null) {
                getMachineInputProcessor().setSpeakerOn(true);
            }
        } else {
            psg.pauseSound();
            if (getMachineInputProcessor() != null) {
                getMachineInputProcessor().setSpeakerOn(false);
            }
        }
    }
}
