package emu.joric.gwt;

import com.badlogic.gdx.Gdx;

import emu.joric.ui.ConfirmResponseHandler;
import emu.joric.ui.DialogHandler;
import emu.joric.ui.OpenFileResponseHandler;
import emu.joric.ui.TextInputResponseHandler;

/**
 * The GWT implementation of the DialogHandler interface.
 */
public class GwtDialogHandler implements DialogHandler {

    private boolean dialogOpen;
    
    /**
     * Constructor for GwtDialogHandler.
     */
    public GwtDialogHandler() {
        initDialog();
    }
    
    private final native void initDialog()/*-{
        this.dialog = new $wnd.Dialog();
    }-*/;
    
    @Override
    public void confirm(String message, ConfirmResponseHandler confirmResponseHandler) {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                dialogOpen = true;
                showHtmlConfirmBox(message, confirmResponseHandler);
            }
        });
    }

    private final native void showHtmlConfirmBox(String message, ConfirmResponseHandler confirmResponseHandler)/*-{
        var that = this;
        this.dialog.confirm(message).then(function (res) {
            if (res) {
                confirmResponseHandler.@emu.joric.ui.ConfirmResponseHandler::yes()();
            } else {
                confirmResponseHandler.@emu.joric.ui.ConfirmResponseHandler::no()();
            }
            that.@emu.joric.gwt.GwtDialogHandler::dialogOpen = false;
        });
    }-*/;
    
    @Override
    public void openFileDialog(String title, String startPath, OpenFileResponseHandler openFileResponseHandler) {
        // TODO: Not implemented.
        openFileResponseHandler.openFileResult(false, null);
    }
    
    @Override
    public void promptForTextInput(String message, String initialValue,
            TextInputResponseHandler textInputResponseHandler) {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                dialogOpen = true;
                showHtmlPromptBox(message, initialValue, textInputResponseHandler);
            }
        });
    }

    private final native void showHtmlPromptBox(String message, String initialValue, TextInputResponseHandler textInputResponseHandler)/*-{
        var that = this;
        this.dialog.prompt(message, initialValue).then(function (res) {
            if (res) {
                textInputResponseHandler.@emu.joric.ui.TextInputResponseHandler::inputTextResult(ZLjava/lang/String;)(true, res.prompt);
            } else {
                textInputResponseHandler.@emu.joric.ui.TextInputResponseHandler::inputTextResult(ZLjava/lang/String;)(false, null);
            }
            that.@emu.joric.gwt.GwtDialogHandler::dialogOpen = false;
        });
    }-*/;
    
    @Override
    public void showAboutDialog(String aboutMessage, TextInputResponseHandler textInputResponseHandler) {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                dialogOpen = true;
                showHtmlAboutDialog(aboutMessage, textInputResponseHandler);
            }
        });
    }
    
    private final native void showHtmlAboutDialog(String message, TextInputResponseHandler textInputResponseHandler)/*-{
        var that = this;
        message = message.replace(/(?:\r\n|\r|\n)/g, "<br>");
        message = message.replace(/www.oric.org/g, "<a href='https://www.oric.org'  target='_blank'>www.oric.org</a>");
        message = message.replace(/forum.defence-force.org/g, "<a href='https://forum.defence-force.org/'  target='_blank'>forum.defence-force.org</a>");
        message = message.replace(/www.defence-force.org/g, "<a href='https://www.defence-force.org/'  target='_blank'>defence-force.org</a>");
        message = message.replace(/https:\/\/github.com\/lanceewing\/joric/g, "<a href='https://github.com/lanceewing/joric' target='_blank'>https://github.com/lanceewing/joric</a>");
        this.dialog.alert('', { 
                showStateButtons: false, 
                template:  '<b>' + message + '</b>'
            }).then(function (res) {
                if (res) {
                    if (res === true) {
                       // OK button.
                        textInputResponseHandler.@emu.joric.ui.TextInputResponseHandler::inputTextResult(ZLjava/lang/String;)(true, "OK");
                    }
                    else {
                        textInputResponseHandler.@emu.joric.ui.TextInputResponseHandler::inputTextResult(ZLjava/lang/String;)(true, res);
                    }
                }
                else {
                    textInputResponseHandler.@emu.joric.ui.TextInputResponseHandler::inputTextResult(ZLjava/lang/String;)(false, null);
                }
                that.@emu.joric.gwt.GwtDialogHandler::dialogOpen = false;
            });
    }-*/;
    
    @Override
    public boolean isDialogOpen() {
        return dialogOpen;
    }
    
}
