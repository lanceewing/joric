package emu.joric;

import java.util.Map;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

import emu.joric.config.AppConfigItem;
import emu.joric.ui.DialogHandler;

/**
 * The main entry point in to the cross-platform part of the JOric emulator. A
 * multi-screen libGDX application needs to extend the Game class, which is what
 * we do here. It allows us to have other screens, such as various menu screens.
 * 
 * @author Lance Ewing
 */
public class JOric extends Game {

    /**
     * This is the screen that is used to show the running emulation.
     */
    private MachineScreen machineScreen;

    /**
     * This is the screen that shows the boot options and programs to load.
     */
    private HomeScreen homeScreen;

    /**
     * Platform specific JOricRunner implementation.
     */
    private JOricRunner joricRunner;

    /**
     * Invoked by JOric whenever it would like to show a dialog, such as when it
     * needs the user to confirm an action, or to choose a file.
     */
    private DialogHandler dialogHandler;

    /**
     * For desktop, contains command line args. For HTML5, the hash and/or query parameters.
     */
    private Map<String, String> args;

    /**
     * JOric's saved preferences.
     */
    private Preferences preferences;

    /**
     * Constructor for JOric.
     * 
     * @param joricRunner
     * @param dialogHandler
     * @param args Map of arguments to launch joric with.
     */
    public JOric(JOricRunner joricRunner, DialogHandler dialogHandler, Map<String, String> args) {
        this.joricRunner = joricRunner;
        this.dialogHandler = dialogHandler;
        this.args = args;
    }

    @Override
    public void create() {
        preferences = Gdx.app.getPreferences("joric.preferences");
        machineScreen = new MachineScreen(this, joricRunner, dialogHandler);
        homeScreen = new HomeScreen(this, dialogHandler);

        AppConfigItem appConfigItem = null;
        
        if ((args != null) && (args.size() > 0)) {
            if (args.containsKey("uri")) {
                // Start by checking to see if the programs.json has an entry.
                appConfigItem = homeScreen.getAppConfigItemByProgramUri(args.get("uri"));
            }
            else if (args.containsKey("url")) {
                String programUrl = args.get("url");
                AppConfigItem adhocProgram = new AppConfigItem();
                adhocProgram.setName("Adhoc Oric Program");
                adhocProgram.setFilePath(getFilePathForProgramUrl(programUrl));
                adhocProgram.setMachineType("PAL");
                adhocProgram.setRam("RAM_48K");
                appConfigItem = adhocProgram;
            }
        }
        
        setScreen(homeScreen);
        
        if (appConfigItem != null) {
            homeScreen.processProgramSelection(appConfigItem);
        }
        
        // Stop the back key from immediately exiting the app.
        Gdx.input.setCatchBackKey(true);
    }

    /**
     * Gets the filePath to use for the given program URL, when used with the
     * ?url request parameter.
     * 
     * @param programUrl
     * 
     * @return
     */
    private String getFilePathForProgramUrl(String programUrl) {
        if ((programUrl.startsWith("http://localhost/")) || 
            (programUrl.startsWith("http://localhost:")) || 
            (programUrl.startsWith("https://localhost/")) || 
            (programUrl.startsWith("https://localhost:")) ||
            (programUrl.startsWith("http://127.0.0.1/")) || 
            (programUrl.startsWith("http://127.0.0.1:")) || 
            (programUrl.startsWith("https://127.0.0.1/")) || 
            (programUrl.startsWith("https://127.0.0.1:"))) {
            return programUrl;
        } else {
            return ("https://oric.games/programs?url=" + programUrl);
        }
    }
    
    /**
     * Gets the MachineScreen.
     * 
     * @return The MachineScreen.
     */
    public MachineScreen getMachineScreen() {
        return machineScreen;
    }

    /**
     * Gets the HomeScreen.
     * 
     * @return the HomeScreen.
     */
    public HomeScreen getHomeScreen() {
        return homeScreen;
    }
    
    /**
     * Gets the JOricRunner.
     * 
     * @return the JOricRunner.
     */
    public JOricRunner getJOricRunner() {
        return joricRunner;
    }

    /**
     * Gets the Preferences for JOric.
     * 
     * @return The Preferences for JOric.
     */
    public Preferences getPreferences() {
        return preferences;
    }
    
    @Override
    public void dispose() {
        super.dispose();

        // For now we'll dispose the MachineScreen here. As the emulator grows and
        // adds more screens, this may be managed in a different way. Note that the
        // super dispose does not call dispose on the screen.
        machineScreen.dispose();
        homeScreen.dispose();

        // Save the preferences when the emulator is closed.
        preferences.flush();
    }

    /**
     * Invoked when a program file (DSK, TAP or ZIP) is dropped onto the home screen.
     * 
     * @param filePath
     * @param fileData
     */
    public void fileDropped(String filePath, byte[] fileData) {
        // File drop is only 
        if (getScreen() == homeScreen) {
            AppConfigItem appConfigItem = new AppConfigItem();
            appConfigItem.setName("Adhoc Oric Program");
            appConfigItem.setFilePath(filePath);
            appConfigItem.setFileType("ABSOLUTE");
            appConfigItem.setMachineType("PAL");
            appConfigItem.setRam("RAM_48K");
            appConfigItem.setFileData(fileData);
            homeScreen.processProgramSelection(appConfigItem);
        }
    }
}
