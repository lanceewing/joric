package emu.joric;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;

import emu.joric.sound.AYPSG;
import emu.joric.ui.ConfirmHandler;

/**
 * The main entry point in to the cross-platform part of the JOric emulator. A multi-screen
 * libGDX application needs to extend the Game class, which is what we do here. It allows 
 * us to have other screens, such as various menu screens.
 * 
 * @author Lance Ewing
 */
public class JOric extends Game {
  
  /**
   * This is the screen that is used to show the running emulation.
   */
  private MachineScreen machineScreen;
  
  /**
   * This is the screen that shows the boot options and programs to load.
   */
  private HomeScreen homeScreen;
  
  /**
   * Invoked by JOric whenever it would like the user to confirm an action.
   */
  private ConfirmHandler confirmHandler;
  
  /**
   * The device specific AY-3-8912 implementation to use. Libgdx sound isn't the best, so
   * we define android and desktop implementations ourselves and pass them in. 
   */
  private AYPSG psg;
  
  /**
   * Constructor for JOric.
   * 
   * @param confirmHandler
   * @param psg 
   */
  public JOric(ConfirmHandler confirmHandler, AYPSG psg) {
    this.confirmHandler = confirmHandler;
    this.psg = psg;
  }
  
  @Override
  public void create () {
    machineScreen = new MachineScreen(this, confirmHandler, psg);
    homeScreen = new HomeScreen(this, confirmHandler);
    setScreen(homeScreen);
    
    // Stop the back key from immediately exiting the app.
    Gdx.input.setCatchBackKey(true);
  }
  
  /**
   * Gets the MachineScreen.
   * 
   * @return The MachineScreen.
   */
  public MachineScreen getMachineScreen() {
    return machineScreen;
  }
  
  /**
   * Gets the HomeScreen.
   * 
   * @return the HomeScreen.
   */
  public HomeScreen getHomeScreen() {
    return homeScreen;
  }
  
  @Override
  public void dispose () {
    super.dispose();
    
    // For now we'll dispose the MachineScreen here. As the emulator grows and
    // adds more screens, this may be managed in a different way. Note that the
    // super dispose does not call dispose on the screen.
    machineScreen.dispose();
    homeScreen.dispose();
  }
}
