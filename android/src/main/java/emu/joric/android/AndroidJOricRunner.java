package emu.joric.android;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.TimeUtils;

import java.text.Normalizer;

import emu.joric.JOricRunner;
import emu.joric.KeyboardMatrix;
import emu.joric.Machine;
import emu.joric.MachineType;
import emu.joric.PixelData;
import emu.joric.Program;
import emu.joric.config.AppConfigItem;
import emu.joric.cpu.Cpu6502;
import emu.joric.memory.RamType;
import emu.joric.sound.AYPSG;

public class AndroidJOricRunner extends JOricRunner {

    private Thread machineThread;

    private Machine machine;

    /**
     * Constructor for AndroidJOricRunner.
     *
     * @param keyboardMatrix
     * @param pixelData
     * @param psg
     */
    public AndroidJOricRunner(KeyboardMatrix keyboardMatrix, PixelData pixelData, AYPSG psg) {
        super(keyboardMatrix, pixelData, psg);
    }

    @Override
    public void start(AppConfigItem appConfigItem) {
        machineThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runProgram(appConfigItem);
            }
        });
        machineThread.start();
    }

    private void runProgram(AppConfigItem appConfigItem) {
        // Start by loading game. We deliberately do this within the thread and
        // not in the main libgdx UI thread.
        AndroidProgramLoader programLoader = new AndroidProgramLoader();

        // We fetch the files via a generic callback mechanism, mainly to support GWT,
        // but no reason we can't code it for Desktop as well.
        programLoader.fetchProgram(appConfigItem, p -> runProgram(appConfigItem, p));
    }

    private void runProgram(AppConfigItem appConfigItem, Program program) {
        // Create the Machine instance that will run the Oric program.
        machine = new Machine(psg, keyboardMatrix, pixelData);

        // Load the ROM files.
        byte[] basicRom = Gdx.files.internal("roms/basic11b.rom").readBytes();
        byte[] microdiscRom = Gdx.files.internal("roms/microdis.rom").readBytes();

        machine.init(basicRom, microdiscRom, program,
                MachineType.valueOf(appConfigItem.getMachineType()),
                RamType.valueOf(appConfigItem.getRam()));

        long lastTime = TimeUtils.nanoTime();

        while (true) {
            if (paused) {
                synchronized (this) {
                    try {
                        while (paused) {
                            wait();
                        }
                    } catch (InterruptedException e) {
                        // Nothing to do.
                    }

                    if (!exit) {
                        // An unknown amount of time will have passed. So reset timing.
                        lastTime = TimeUtils.nanoTime();
                    }
                }
            }

            if (exit) {
                // Returning from the method will stop the thread cleanly.
                pixelData.clearPixels();
                break;
            }

            // Updates the Machine's state for a frame.
            machine.update(warpSpeed);

            if (!warpSpeed) {
                // Throttle at expected FPS. Note that the PSG naturally throttles at 50 FPS
                // without the yield.
                while (TimeUtils.nanoTime() - lastTime <= 0L) {
                    Thread.yield();
                }
                lastTime += NANOS_PER_FRAME;
            } else {
                lastTime = TimeUtils.nanoTime();
            }
        }

        machine = null;
    }

    @Override
    public void stop() {
        super.stop();

        if ((machineThread != null) && machineThread.isAlive()) {
            // If the thread is still running, and is either waiting on the wait() above,
            // or it is sleeping within the UserInput or TextGraphics classes, then this
            // interrupt call will wake it up, the QuitAction will be thrown, and then the
            // thread will cleanly and safely stop.
            machineThread.interrupt();
        }
    }

    @Override
    public void resume() {
        synchronized (this) {
            super.resume();
            this.notifyAll();
        }
    }

    @Override
    public void reset() {
        exit = false;
        machineThread = null;
        machine = null;
    }

    @Override
    public boolean hasStopped() {
        return ((machineThread != null) && !machineThread.isAlive());
    }

    @Override
    public boolean hasTouchScreen() {
        // We assume this for Android.
        return true;
    }

    @Override
    public boolean isMobile() {
        // Android is obviously mobile.
        return true;
    }

    @Override
    public String slugify(String input) {
        if ((input == null) | (input.isEmpty())) {
            return "";
        }

        // Make lower case and trim.
        String slug = input.toLowerCase().trim();

        // Remove accents from characters.
        slug = Normalizer.normalize(slug, Normalizer.Form.NFD).replaceAll("[\u0300-\u036f]", "");

        // Remove invalid chars.
        slug = slug.replaceAll("[^a-z0-9\\s-]", "").trim();

        // Replace multiple spaces or hyphens with a single hyphen.
        slug = slug.replaceAll("[\\s-]+", "-");

        return slug;
    }

    @Override
    public void cancelImport() {
        // Nothing to do for Desktop.
    }

    @Override
    public boolean isRunning() {
        return (machineThread != null);
    }

    @Override
    public void sendNmi() {
        if (machine != null) {
            machine.getCpu().setInterrupt(Cpu6502.S_NMI);
        }
    }
}
