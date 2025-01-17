package emu.joric.sound;

import emu.joric.io.Keyboard;
import emu.joric.io.Via;
import emu.joric.snap.Snapshot;

/**
 * Interface defining the operations required of an AY-3-8912 PSG implementation. This
 * has been defined mainly so that a j2se, libgdx, and android implementation can be 
 * built but the joric-core depends only on this interface.
 * 
 * @author Lance Ewing
 */
public interface AYPSG {
  
  public void init(Via via, Keyboard keyboard, Snapshot snapshot);
  
  public int getIOPortA();

  public void emulateCycle();
  
  public void pauseSound();
  
  public void resumeSound();
  
  public boolean isSoundOn();

  public void dispose();
  
}
