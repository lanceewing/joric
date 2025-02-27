package emu.joric;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.Input.Keys;

/**
 * Handles the input of keyboard events, mapping them to a form that the JOric
 * emulator can query as required. The storage of the state is platform dependent,
 * which is why this class is abstract. This is primarily due to the HTML5/GWT/web 
 * implementation needing to store that state in a form that can be immediately 
 * accessed by both the UI thread (for updates) and the web worker thread (for
 * querying).
 */
public abstract class KeyboardMatrix extends InputAdapter {

    // This is as the keyboard layout is shown in technical docs:
    //
    // SHFT  LEFT  DOWN  SPACE UP    RIGHT <,    >.
    //       A     S     Y     E     W     H     G
    // SHFT  RTN         *8    ?/    +=    L     )0
    // FCN   DEL   }]    U     P     {[    I     O
    //             |\    K     £-    "'    (9    :;
    //       !1    X     &7    V     #3    N     %5
    //       ESC   Q     J     F     D     T     R
    // CTRL  Z     @2    M     $4    C     ^6    B
    //
    // But that is the physical layout. Logically its like this:
    //
    // #3    X     !1          V     %5    N     &7
    // D     Q     ESC         F     R     T     J
    // C     @2    Z     CTRL  $4    B     ^6    M
    // "'    |\                £-    :;    (9    K
    // RIGHT DOWN  LEFT  SHIFT UP    >.    <,    SPACE
    // {[    }]    DEL   FCN   P     O     I     U
    // W     S     A           E     G     H     Y
    // +=          RET         ?/    )0    L     *8
    
    /**
     * Data used to convert Java keypresses into Oric keypresses.
     */
    private static int keyConvMapArr[][] = {
      
          // #3    X     !1          V     %5    N     &7
          {Keys.NUM_3, 0, 128},
          {Keys.X, 0, 64},
          {Keys.NUM_1, 0, 32},
          // column 16 is not mapped
          {Keys.V, 0, 8},
          {Keys.NUM_5, 0, 4},
          {Keys.N, 0, 2},
          {Keys.NUM_7, 0, 1},
          
          // D     Q     ESC         F     R     T     J
          {Keys.D, 1, 128},
          {Keys.Q, 1, 64},
          {Keys.ESCAPE, 1, 32},
          // column 16 is not mapped
          {Keys.F, 1, 8},
          {Keys.R, 1, 4},
          {Keys.T, 1, 2},
          {Keys.J, 1, 1},
          
          // C     @2    Z     CTRL  $4    B     ^6    M
          {Keys.C, 2, 128},
          {Keys.NUM_2, 2, 64},
          {Keys.Z, 2, 32},
          {Keys.CONTROL_LEFT, 2, 16},
          {Keys.CONTROL_RIGHT, 2, 16},
          {Keys.NUM_4, 2, 8},
          {Keys.B, 2, 4},
          {Keys.NUM_6, 2, 2},
          {Keys.M, 2, 1},
            
          // "'    |\                £-    :;    (9    K
          {Keys.APOSTROPHE, 3, 128},
          {Keys.AT, 3, 128},
          {Keys.BACKSLASH, 3, 64},
          // column 32 not mapped
          // column 16 not mapped
          {Keys.MINUS, 3, 8},
          {Keys.SEMICOLON, 3, 4},
          {Keys.COLON, 3, 4},
          {Keys.NUM_9, 3, 2},
          {Keys.K, 3, 1},
          
          // RIGHT DOWN  LEFT  SHIFT UP    >.    <,    SPACE
          {Keys.RIGHT, 4, 128},
          {Keys.DOWN, 4, 64},
          {Keys.LEFT, 4, 32},
          {Keys.SHIFT_LEFT, 4, 16}, 
          {Keys.UP, 4, 8},
          {Keys.PERIOD, 4, 4},
          {Keys.COMMA, 4, 2},
          {Keys.SPACE, 4, 1},
          
          // {[    }]    DEL   ALT   P     O     I     U
          {Keys.LEFT_BRACKET, 5, 128},
          {Keys.RIGHT_BRACKET, 5, 64},
          {Keys.DEL, 5, 32},
          {Keys.BACKSPACE, 5, 32},
          {Keys.ALT_LEFT, 5, 16},   // Using ALTs for FUNCT
          {Keys.ALT_RIGHT, 5, 16},
          {Keys.P, 5, 8},
          {Keys.O, 5, 4},
          {Keys.I, 5, 2},
          {Keys.U, 5, 1},
          
          // W     S     A           E     G     H     Y
          {Keys.W, 6, 128},
          {Keys.S, 6, 64},
          {Keys.A, 6, 32},
          // column 16 not mapped
          {Keys.E, 6, 8},
          {Keys.G, 6, 4},
          {Keys.H, 6, 2},
          {Keys.Y, 6, 1},
          
          // +=          RET         ?/    )0    L     *8
          {Keys.EQUALS, 7, 128},
          // column 64 not mapped
          {Keys.ENTER, 7, 32},
          {Keys.SHIFT_RIGHT, 7, 16}, 
          {Keys.SLASH, 7, 8},
          {Keys.NUM_0, 7, 4},
          {Keys.L, 7, 2},
          {Keys.NUM_8, 7, 1}
    };
    
    /**
     * HashMap used to store mappings between Java key events and Oric
     * keyboard scan codes.
     */
    private HashMap<Integer, int[]> keyConvHashMap;
    
    /**
     * Holds the last time that the key was pressed down, or 0 if it has since been released.
     */
    private long minKeyReleaseTimes[] = new long[512];
    
    /**
     * Holds a queue of keycodes whose key release processing has been delayed. This is
     * supported primarily for use with the Android virtual keyboard on some devices, 
     * where the key pressed and release both get fired on release of the key, so have
     * virtually no time between them.
     */
    private TreeMap<Long, Integer> delayedReleaseKeys = new TreeMap<Long, Integer>();
    
    private int lastKeyDownKeycode;
    
    /**
     * Constructor for UserInput.
     */
    public KeyboardMatrix() {
        // Create the hash map for fast lookup.
        keyConvHashMap = new HashMap<Integer, int[]>();
        
        // Initialise the hashmap.
        for (int i=0; i<keyConvMapArr.length; i++) {
            int[] keyDetails = keyConvMapArr[i];
            keyConvHashMap.put(new Integer(keyDetails[0]), keyDetails);
        }
    }

    public boolean keyDown(int keycode) {
        if (keycode == 0) {
            // The framework wasn't able to identify the key, so we'll have to 
            // deduce it from the key typed character.
        }
        else {
            // Store the minimum expected release time for this key, i.e. current time + 50ms.
            minKeyReleaseTimes[keycode] = TimeUtils.nanoTime() + 50000000;
            
            // Update the key matrix to indicate to the Oric that this key is down.
            int keyDetails[] = (int[]) keyConvHashMap.get(new Integer(keycode));
            if (keyDetails != null) {
                int currentRowValue = getKeyMatrixRow(keyDetails[1]);
                setKeyMatrixRow(keyDetails[1], currentRowValue | keyDetails[2]);
            } else {
                // Special keycodes without direct mappings.
            }
        }
        lastKeyDownKeycode = keycode;
        return true;
    }

    public boolean keyUp(int keycode) {
        if (keycode != 0) {
            long currentTime = TimeUtils.nanoTime();
            long minKeyReleaseTime = minKeyReleaseTimes[keycode];
            minKeyReleaseTimes[keycode] = 0;
            
            if (currentTime < minKeyReleaseTime) {
                // Key hasn't been down long enough (possibly due to it being an Android virtual 
                // keyboard or something similar that doesn't reflect the actual time the key 
                // is down), so let's add this keycode to the delayed release list.
                synchronized(delayedReleaseKeys) {
                    delayedReleaseKeys.put(minKeyReleaseTime, keycode);
                }
                
            } else {
                // Otherwise we process the release by updating the key matrix that the Oric polls.
                int keyDetails[] = (int[]) keyConvHashMap.get(new Integer(keycode));
                if (keyDetails != null) {
                    int currentRowValue = getKeyMatrixRow(keyDetails[1]);
                    setKeyMatrixRow(keyDetails[1], currentRowValue & ~keyDetails[2]);
                } else {
                    // Special keycodes.
        
                }
            }
        }

        return true;
    }

    public boolean keyTyped(char ch) {
        // The keyTyped method is invoked within a millisecond of the keyDown
        // method, so it is very likely that the last keyDown was for the same key.
        int keycode = 0;

        if (lastKeyDownKeycode == 0) {
            // Last keyDown call had an unrecognised keycode.
            if ((ch == '\\') || (ch == '|')) {
                keycode = Keys.BACKSLASH;
            } else if (ch == '_') {
                keycode = Keys.MINUS;
            } else if (ch == '^') {
                keycode = Keys.NUM_6;
            } else if ((ch == '\'') || (ch == '@')) {
                keycode = Keys.APOSTROPHE;
            }
        }

        if (keycode != 0) {
            keyDown(keycode);
            keyUp(keycode);
            return true;

        } else {
            return false;
        }
    }
    
    /**
     * Checks if there are any keys whose release processed has been delayed that
     * are now able to be processed due to the minimum release time having been
     * passed.
     */
    public void checkDelayedReleaseKeys() {
        if (!delayedReleaseKeys.isEmpty()) {
            synchronized (delayedReleaseKeys) {
                List<Long> processedReleases = new ArrayList<Long>();
                processedReleases.addAll(delayedReleaseKeys.headMap(TimeUtils.nanoTime()).keySet());
                for (Long keyReleaseTime : processedReleases) {
                    int delayedReleaseKeyCode = delayedReleaseKeys.remove(keyReleaseTime);
                    keyUp(delayedReleaseKeyCode);
                }
            }
        }
    }
    
    public abstract int getKeyMatrixRow(int row);
    
    public abstract void setKeyMatrixRow(int row, int value);
}
