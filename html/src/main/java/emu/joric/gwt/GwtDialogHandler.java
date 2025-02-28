package emu.joric.gwt;

import com.badlogic.gdx.Gdx;
import com.google.gwt.typedarrays.shared.Int8Array;
import com.google.gwt.typedarrays.shared.TypedArrays;

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
        dialogOpen = true;
        showHtmlOpenFileDialog(new GwtOpenFileResultsHandler() {
            @Override
            public void onFileResultsReady(GwtOpenFileResult[] openFileResultArray) {
                // There should be only one file.
                if (openFileResultArray.length == 1) {
                    GwtOpenFileResult result = openFileResultArray[0];
                    Int8Array fileDataInt8Array = TypedArrays.createInt8Array(result.getFileData());
                    byte[] fileByteArray = new byte[fileDataInt8Array.byteLength()];
                    for (int index=0; index<fileDataInt8Array.byteLength(); index++) {
                        fileByteArray[index] = fileDataInt8Array.get(index);
                    }
                    openFileResponseHandler.openFileResult(true, result.getFileName(), fileByteArray);
                } else {
                     // No files selected.
                    openFileResponseHandler.openFileResult(false, null, null);
                }
                
                dialogOpen = false;
            }
        });
    }
    
    private final native void showHtmlOpenFileDialog(GwtOpenFileResultsHandler resultsHandler)/*-{
        var fileInputElem = document.createElement('input');
        fileInputElem.type = 'file';
        fileInputElem.accept = '.tap,.dsk,.zip,.rom';
        
        document.body.appendChild(fileInputElem);
        
        // The change event occurs after a file is chosen.
        fileInputElem.addEventListener("change", function(event) {
            document.body.removeChild(fileInputElem);
        
            if (this.files.length === 0) {
                // No file was selected, so nothing more to do.
                resultsHandler.@emu.joric.gwt.GwtOpenFileResultsHandler::onFileResultsReady([Lemu/joric/gwt/GwtOpenFileResult;)([]);
            }
            else {
                // We do not allow multiple files to be selected.
                Promise.all([].map.call(this.files, function (file) {
                    return new Promise(function (resolve, reject) {
                        var reader = new FileReader();
                        // NOTE 1: loadend called regards of whether it was successful or not.
                        // NOTE 2: file has .name, .size and .lastModified fields.
                        reader.addEventListener("loadend", function (event) {
                            resolve({
                                fileName: file.name,
                                filePath: file.webkitRelativePath? file.webkitRelativePath : '',
                                fileData: reader.result
                            });
                        });
                        reader.readAsArrayBuffer(file);
                    });
                })).then(function (results) {
                    // The results param is an array of result objects
                    resultsHandler.@emu.joric.gwt.GwtOpenFileResultsHandler::onFileResultsReady([Lemu/joric/gwt/GwtOpenFileResult;)(results);
                });
            }
        });
        
        fileInputElem.addEventListener("cancel", function(event) {
            document.body.removeChild(fileInputElem);
            
            // No file was selected, so nothing more to do.
            resultsHandler.@emu.joric.gwt.GwtOpenFileResultsHandler::onFileResultsReady([Lemu/joric/gwt/GwtOpenFileResult;)([]);
        });
        
        // Trigger the display of the open file dialog.
        fileInputElem.click();
    }-*/;
    
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
