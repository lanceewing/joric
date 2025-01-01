package emu.joric;

import java.util.Map;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

import emu.joric.config.AppConfigItem;
import emu.joric.memory.RamType;
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
            if (args.containsKey("id")) {
                // TODO: Implement lookup by id
                //appConfigItem = homeScreen.getAppConfigItemByGameId(args.get("id"));
            }
            if (args.containsKey("uri")) {
                // TODO: Implement lookup by uri
                //appConfigItem = homeScreen.getAppConfigItemByGameUri(args.get("uri"));
            }
            if (args.containsKey("path")) {
                String filePath = args.get("path");
                appConfigItem = new AppConfigItem();
                appConfigItem.setFilePath(filePath);
                if (filePath.toLowerCase().endsWith(".dsk")) {
                    appConfigItem.setFileType("DISK");
                }
                if (filePath.toLowerCase().endsWith(".tap")) {
                    appConfigItem.setFileType("TAPE");
                }
                appConfigItem.setMachineType(MachineType.PAL.name());
                appConfigItem.setRam(RamType.RAM_48K.name());
            }
        }
        
        setScreen(homeScreen);
        
        if (appConfigItem != null) {
            // TODO: add processGameSelection
            //homeScreen.processGameSelection(appConfigItem);
        }
        
        // Stop the back key from immediately exiting the app.
        Gdx.input.setCatchBackKey(true);
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
}
