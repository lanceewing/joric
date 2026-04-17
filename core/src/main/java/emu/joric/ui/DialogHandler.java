package emu.joric.ui;

/**
 * An interface that the different platforms can implement to provide different
 * types of dialog window, e.g. confirm dialog, file chooser, etc.
 * 
 * @author Lance Ewing
 */
public interface DialogHandler {

  /**
   * Invoked when JOric wants to confirm with the user that they really want
   * to continue with a particular action.
   * 
   * @param message The message to be displayed to the user.
   * @param confirmResponseHandler The handler to be invoked with the user's response.
   */
  public void confirm(String message, ConfirmResponseHandler confirmResponseHandler);
  
  /**
   * Invoked when JOric wants the user to choose a file to open.
   * 
   * @param title Title for the open file dialog.
   * @param startPath The starting path.
   * @param openFileResponseHandler The handler to be invoked with the chosen file (if chosen).
   */
  public void openFileDialog(String title, String startPath, OpenFileResponseHandler openFileResponseHandler);

  /**
   * Invoked when AGILE wants to ask what type of game import to perform.
   * 
   * @param appConfigItem Optional selected game that is being imported.
   * @param importTypeResponseHandler The handler to be invoked with the user's response.
   */
  public void promptForTextInput(String message, String initialValue, TextInputResponseHandler textInputResponseHandler);
  
  /**
   * Shows the About AGILE message dialog.
   *
   * @param aboutMessage The About message to display.
   * @param textInputResponseHandler Optional state management button response.
   */
  public void showAboutDialog(String aboutMessage, TextInputResponseHandler textInputResponseHandler);

  /**
   * Invoked when JOric wants the user to choose one option from a list.
   *
   * @param title The dialog title (may be "" if not needed).
   * @param message A label / instruction shown above the list of options.
   * @param options The available option strings.
   * @param currentSelection The currently-selected option string (may be null).
   * @param textInputResponseHandler The handler to be invoked with the user's
   *        response. On success, text is the chosen option (one of the strings
   *        in options). On cancel, success is false and text is null.
   */
  public void promptForOption(String title, String message, String[] options,
          String currentSelection, TextInputResponseHandler textInputResponseHandler);
  
  /**
   * Returns true if a dialog is currently open.
   * 
   * @return true if a dialog is currently open.
   */
  public boolean isDialogOpen();
  
}
