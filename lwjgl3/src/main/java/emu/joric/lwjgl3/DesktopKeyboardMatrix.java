package emu.joric.lwjgl3;

import emu.joric.KeyboardMatrix;

public class DesktopKeyboardMatrix extends KeyboardMatrix {

    private int[] keyMatrix = new int[8];
    
    @Override
    public int getKeyMatrixRow(int row) {
        return keyMatrix[row];
    }

    @Override
    public void setKeyMatrixRow(int row, int value) {
        keyMatrix[row] = value;
    }
}
