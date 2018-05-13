package emu.joric.desktop;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;

import emu.joric.JOric;
import emu.joric.ui.DialogHandler;
import emu.joric.ui.ConfirmResponseHandler;
import emu.joric.ui.OpenFileResponseHandler;
import emu.joric.ui.TextInputResponseHandler;

public class DesktopLauncher {
	public static void main (String[] args) {
    LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
    config.width = 540;
    config.height = 960;
    config.title = "JOric  v0.1";
    new LwjglApplication(new JOric(new DialogHandler() {

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

      @Override
      public void openFileDialog(String title, String startPath, final OpenFileResponseHandler openFileResponseHandler) {
        Gdx.app.postRunnable(new Runnable() {
          
          @Override
          public void run() {
            JFileChooser jfc = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
            //JFileChooser jfc = new JFileChooser(startPath);
            jfc.setDialogTitle("Select a tape or disk file");
            jfc.setAcceptAllFileFilterUsed(false);
            FileNameExtensionFilter filter = new FileNameExtensionFilter("TAP and DSK files", "tap", "dsk");
            jfc.addChoosableFileFilter(filter);
    
            int returnValue = jfc.showOpenDialog(null);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
              System.out.println(jfc.getSelectedFile().getPath());
              openFileResponseHandler.openFileResult(true, jfc.getSelectedFile().getPath());
            } else {
              openFileResponseHandler.openFileResult(false, null);
            }
          }
        });
      }

      @Override
      public void promptForTextInput(final String message, final String initialValue, final TextInputResponseHandler textInputResponseHandler) {
        Gdx.app.postRunnable(new Runnable() {
          @Override
          public void run() {
            String text = (String)JOptionPane.showInputDialog(null,
              message, "Please enter value",
              JOptionPane.INFORMATION_MESSAGE, null,
              null, initialValue != null? initialValue : "");
            
            if (text != null) {
              textInputResponseHandler.inputTextResult(true, text);
            } else {
              textInputResponseHandler.inputTextResult(false, null);
            }
          }
        });
      }
    },
    new AY38912PSG(), args),
    //null),  // Passing null instead of a AY38912PSG will use libgdx sound instead.
    config);
	}
}
