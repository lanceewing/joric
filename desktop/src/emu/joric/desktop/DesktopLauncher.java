package emu.joric.desktop;

import javax.swing.JOptionPane;

import com.badlogic.gdx.Gdx;
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
      public void confirm(final String message, final ConfirmResponseHandler responseHandler) {
        Gdx.app.postRunnable(new Runnable() {
          @Override
          public void run() {
            int output = JOptionPane.showConfirmDialog(null, "Please confirm", message, JOptionPane.YES_NO_OPTION);
            if (output != 0) {
              responseHandler.no();
            } else {
              responseHandler.yes();
            }
          }
        });
      }
      
    }, new AY38912PSG()), config);    // Passing null instead of a AY38912PSG will use libgdx sound instead.
	}
}
