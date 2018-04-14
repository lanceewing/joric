package emu.joric.desktop;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;

import emu.joric.JOric;
import emu.joric.ui.ConfirmHandler;
import emu.joric.ui.ConfirmResponseHandler;

public class DesktopLauncher {
	public static void main (String[] arg) {
    LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
    config.width = 540;
    config.height = 960;
    config.title = "JOric  v0.1";
    new LwjglApplication(new JOric(new ConfirmHandler() {

      @Override
      public void confirm(String message, ConfirmResponseHandler confirmResponseHandler) {
        // TODO: Implement confirmation dialog for desktop version.
      }
      
    }, new AY38912PSG()), config);    // Passing null instead of a AY38912PSG will use libgdx sound instead.
	}
}
