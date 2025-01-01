package emu.joric;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.ipaulpro.afilechooser.utils.FileUtils;

import emu.joric.JOric;
import emu.joric.android.AY38912PSG;
import emu.joric.ui.DialogHandler;
import emu.joric.ui.ConfirmResponseHandler;
import emu.joric.ui.OpenFileResponseHandler;
import emu.joric.ui.TextInputResponseHandler;

public class AndroidLauncher extends AndroidApplication implements DialogHandler {

  @Override
  protected void onCreate (Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
    configuration.useImmersiveMode = true;
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

  @Override
  public void openFileDialog(final String title, final String startPath, final OpenFileResponseHandler openFileResponseHandler) {
    activeOpenFileResponseHandler = openFileResponseHandler;
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        // Use the GET_CONTENT intent from the utility class
        Intent target = FileUtils.createGetContentIntent();
        // Create the chooser Intent
        Intent intent = Intent.createChooser(target, title);
        try {
          startActivityForResult(intent, REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            // The reason for the existence of aFileChooser
        }
      }
    });
  }
  
  // TODO: Has to be a nicer way than using an instance var for this. Can we create a separate Activity rather than using the main one?
  private OpenFileResponseHandler activeOpenFileResponseHandler;
  
  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case REQUEST_CODE:
        // If the file selection was successful
        if (resultCode == RESULT_OK) {
          if (data != null) {
            // Get the URI of the selected file
            final Uri uri = data.getData();
            try {
              // Get the file path from the URI
              final String path = FileUtils.getPath(this, uri);
              activeOpenFileResponseHandler.openFileResult(true, path);
  
            } catch (Exception e) {
              Log.e("FileSelectorTestActivity", "File select error", e);
              activeOpenFileResponseHandler.openFileResult(false, null);
            }
          }
        } else {
          activeOpenFileResponseHandler.openFileResult(false, null);
        }
        break;
    }
    super.onActivityResult(requestCode, resultCode, data);
  }
  
  private static final int REQUEST_CODE = 6384; // onActivityResult request code

  @Override
  public void promptForTextInput(final String message, final String initialValue, final TextInputResponseHandler textInputResponseHandler) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        final EditText inputText = new EditText(AndroidLauncher.this);
        inputText.setText(initialValue != null? initialValue : "");
    
        // Set the default text to a link of the Queen
        inputText.setHint("");
    
        new AlertDialog.Builder(AndroidLauncher.this)
        .setTitle("Please enter value")
        .setMessage(message)
        .setView(inputText)
        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int whichButton) {
            String text = inputText.getText().toString();
            textInputResponseHandler.inputTextResult(true, text);
          }
        })
        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int whichButton) {
            textInputResponseHandler.inputTextResult(false, null);
          }
        }).show();
      }
    });
  }
}
