package emu.joric.android;

import emu.joric.JOricRunner;
import emu.joric.KeyboardMatrix;
import emu.joric.PixelData;
import emu.joric.config.AppConfigItem;
import emu.joric.sound.AYPSG;

public class AndroidJOricRunner extends JOricRunner {

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

    }

    @Override
    public void reset() {

    }

    @Override
    public boolean hasStopped() {
        return false;
    }

    @Override
    public boolean hasTouchScreen() {
        return false;
    }

    @Override
    public boolean isMobile() {
        return false;
    }

    @Override
    public String slugify(String input) {
        return "";
    }

    @Override
    public void cancelImport() {

    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public void sendNmi() {

    }
}
