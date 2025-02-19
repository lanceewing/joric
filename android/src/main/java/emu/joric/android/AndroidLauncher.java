package emu.joric.android;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.EditText;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

import java.util.HashMap;
import java.util.Map;

import emu.joric.JOric;
import emu.joric.ui.DialogHandler;
import emu.joric.ui.ConfirmResponseHandler;
import emu.joric.ui.OpenFileResponseHandler;
import emu.joric.ui.TextInputResponseHandler;

public class AndroidLauncher extends AndroidApplication implements DialogHandler {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration configuration = new AndroidApplicationConfiguration();
        configuration.useImmersiveMode = true;
        Map<String, String> argsMap = new HashMap<>();
        AndroidJOricRunner androidJOricRunner = new AndroidJOricRunner(
                new AndroidKeyboardMatrix(), new AndroidPixelData(),
                new AY38912PSG()
        );
        initialize(new JOric(androidJOricRunner, this, argsMap), configuration);
    }

    @Override
    public void confirm(final String message, final ConfirmResponseHandler responseHandler) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(AndroidLauncher.this).setTitle("Please confirm").setMessage(message)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                responseHandler.yes();
                                dialog.cancel();
                            }
                        }).setNegativeButton("No", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                responseHandler.no();
                                dialog.cancel();
                            }
                        }).create().show();
            }
        });
    }

    @Override
    public void openFileDialog(String title, String startPath, OpenFileResponseHandler openFileResponseHandler) {
        // TODO: Implement when open file is supported.
    }

    @Override
    public void promptForTextInput(final String message, final String initialValue,
            final TextInputResponseHandler textInputResponseHandler) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final EditText inputText = new EditText(AndroidLauncher.this);
                inputText.setText(initialValue != null ? initialValue : "");

                // Set the default text to a link of the Queen
                inputText.setHint("");

                new AlertDialog.Builder(AndroidLauncher.this).setTitle("Please enter value").setMessage(message)
                        .setView(inputText).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                String text = inputText.getText().toString();
                                textInputResponseHandler.inputTextResult(true, text);
                            }
                        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                textInputResponseHandler.inputTextResult(false, null);
                            }
                        }).show();
            }
        });
    }

    @Override
    public void showAboutDialog(String aboutMessage, TextInputResponseHandler textInputResponseHandler) {
        // TODO: Implement.
    }

    @Override
    public boolean isDialogOpen() {
        return false;
    }
}
