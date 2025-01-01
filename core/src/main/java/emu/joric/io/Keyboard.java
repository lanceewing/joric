package emu.joric.io;

import emu.joric.KeyboardMatrix;
import emu.joric.sound.AYPSG;

/**
 * This class emulates the Oric keyboard by listening to key events, translating
 * them in to Oric "key codes", and then responding to Oric keyboard scans when
 * they are invoked. The keyboard event listening and translation is handled by
 * the UserInput abstract class, which provides a platform specific way to store
 * the keyboard matrix state.
 * 
 * @author Lance Ewing
 */
public class Keyboard {

    /**
     * The AY_3_8912 chip provides the column value when a key's state needs to be
     * checked.
     */
    private AYPSG psg;

    /**
     * Platform specific interface through which to get keyboard matrix.
     */
    private KeyboardMatrix keyboardMatrix;

    /**
     * Constructor for Keyboard.
     * 
     * @param keyboardMatrix 
     */
    public Keyboard(KeyboardMatrix keyboardMatrix) {
        this.keyboardMatrix = keyboardMatrix;
    }
    
    /**
     * Constructor for Keyboard.
     * 
     * @param keyboardMatrix
     * @param psg
     */
    public Keyboard(KeyboardMatrix keyboardMatrix, AYPSG psg) {
        this(keyboardMatrix);
        setPsg(psg);
    }

    public void setPsg(AYPSG psg) {
        this.psg = psg;
    }

    /**
     * Tests if key(s) are pressed on the selected row and columns of the keyboard
     * matrix.
     * 
     * @param selectedRow The row of the keyboard matrix to check.
     * 
     * @return
     */
    public boolean isKeyPressed(int selectedRow) {
        int selectedColumns = (~psg.getIOPortA()) & 0xFF;

        boolean keypressed = (keyboardMatrix.getKeyMatrixRow(selectedRow) & selectedColumns) != 0;

        return keypressed;
    }
}
