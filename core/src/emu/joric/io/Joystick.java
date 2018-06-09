package emu.joric.io;

import java.util.HashMap;

import com.badlogic.gdx.Input.Keys;

/**
 * This class emulates the Oric joystick by listening to key events and 
 * translating the relevant key codes in to joystick signals.
 * 
 * @author Lance Ewing
 */
public class Joystick {
  
  public static enum JoystickType {
    IJK, ALTAI, DKTRONICS, PASE, OPEL, PROTEK, DOWNSWAY, 
    ARROW_KEYS, ZX_SA, AZ_LT_GT;
  };
  
  public static enum JoystickPosition {
    LEFT, RIGHT, UP, DOWN;
  }

  private HashMap<JoystickPosition, Boolean> state;
  
  private Keyboard keyboard;
  
  private JoystickType type;
  
  /**
   * Constructor for Joystick.
   * 
   * @param keyboard 
   * @param type 
   */
  public Joystick(Keyboard keyboard, JoystickType type) {
    this.keyboard = keyboard;
    this.type = type;
    this.state = new HashMap<JoystickPosition, Boolean>();
    this.state.put(JoystickPosition.LEFT, false);
    this.state.put(JoystickPosition.RIGHT, false);
    this.state.put(JoystickPosition.UP, false);
    this.state.put(JoystickPosition.DOWN, false);
  }
  
  /**
   * Invoked when a key has been pressed.
   *
   * @param keycode The keycode of the key that has been pressed.
   */
  public void keyPressed(int keycode) {

  }

  /**
   * Invoked when a key has been released.
   *
   * @param keycode The keycode of the key that has been released.
   */
  public void keyReleased(int keycode) {

  }
  
  /**
   * Checks for significant changes in the joystick position.
   * 
   * @param x
   * @param y
   */
  public void touchPad(float x, float y) {
    if (x > 0.3) {
      // Right
      if (state.get(JoystickPosition.LEFT)) {
        exitLeft();
      }
      if (!state.get(JoystickPosition.RIGHT)) {
        enterRight();
      }
    } else if (x < -0.3) {
      // Left
      if (state.get(JoystickPosition.RIGHT)) {
        exitRight();
      }
      if (!state.get(JoystickPosition.LEFT)) {
        enterLeft();
      }
    } else {
      // Not left or right at the moment.
      if (state.get(JoystickPosition.RIGHT)) {
        exitRight();
      } else if (state.get(JoystickPosition.LEFT)) {
        exitLeft();
      }
    }
    
    if (y > 0.3) {
      // Up
      if (state.get(JoystickPosition.DOWN)) {
        exitDown();
      }
      if (!state.get(JoystickPosition.UP)) {
        enterUp();
      }
    } else if (y < -0.3) {
      // Down
      if (state.get(JoystickPosition.UP)) {
        exitUp();
      }
      if (!state.get(JoystickPosition.DOWN)) {
        enterDown();
      }
    } else {
      // Not left or right at the moment.
      if (state.get(JoystickPosition.UP)) {
        exitUp();
      } else if (state.get(JoystickPosition.DOWN)) {
        exitDown();
      }
    }
  }

  private void enterDown() {
    state.put(JoystickPosition.DOWN, true);
    switch (type) {
      case ARROW_KEYS:
        keyboard.keyPressed(Keys.DOWN);
        break;
      default:
        break;
    }
  }
  
  private void exitDown() {
    state.put(JoystickPosition.DOWN, false);
    switch (type) {
      case ARROW_KEYS:
        keyboard.keyReleased(Keys.DOWN);
        break;
      default:
        break;
    }
  }
  
  private void enterUp() {
    state.put(JoystickPosition.UP, true);
    switch (type) {
      case ARROW_KEYS:
        keyboard.keyPressed(Keys.UP);
        break;
      default:
        break;
    }
  }
  
  private void exitUp() {
    state.put(JoystickPosition.UP, false);
    switch (type) {
      case ARROW_KEYS:
        keyboard.keyReleased(Keys.UP);
        break;
      default:
        break;
    }
  }

  private void enterLeft() {
    state.put(JoystickPosition.LEFT, true);
    switch (type) {
      case ARROW_KEYS:
        keyboard.keyPressed(Keys.LEFT);
        break;
      default:
        break;
    }
  }
  
  private void exitLeft() {
    state.put(JoystickPosition.LEFT, false);
    switch (type) {
      case ARROW_KEYS:
        keyboard.keyReleased(Keys.LEFT);
        break;
      default:
        break;
    }
  }

  private void enterRight() {
    state.put(JoystickPosition.RIGHT, true);
    switch (type) {
      case ARROW_KEYS:
        keyboard.keyPressed(Keys.RIGHT);
        break;
      default:
        break;
    }
  }
  
  private void exitRight() {
    state.put(JoystickPosition.RIGHT, false);
    switch (type) {
      case ARROW_KEYS:
        keyboard.keyReleased(Keys.RIGHT);
        break;
      default:
        break;
    }
  }
}
