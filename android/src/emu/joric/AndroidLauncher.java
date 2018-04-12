package emu.joric;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

import emu.joric.JOric;
import emu.joric.android.AY38912PSG;
import emu.joric.ui.ConfirmHandler;
import emu.joric.ui.ConfirmResponseHandler;

public class AndroidLauncher extends AndroidApplication implements ConfirmHandler {

  @Override
  protected void onCreate (Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
    initialize(new JOric(this, new AY38912PSG()), config);
  }

  @Override
  public void confirm(final String message, final ConfirmResponseHandler responseHandler) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        new AlertDialog.Builder(AndroidLauncher.this)
          .setTitle("Please confirm")
          .setMessage(message)
          .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                responseHandler.yes();
                dialog.cancel();
              }
            })
          .setNegativeButton("No", new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                responseHandler.no();
                dialog.cancel();
              }
            })
          .create()
          .show();
      }
    });
  }
}
