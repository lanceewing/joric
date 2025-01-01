package emu.joric;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad;
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad.TouchpadStyle;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import emu.joric.config.AppConfigItem;
import emu.joric.ui.DialogHandler;
import emu.joric.ui.MachineInputProcessor;
import emu.joric.ui.ViewportManager;

/**
 * The main screen in the JOric emulator, i.e. the one that shows the video
 * output of the ULA.
 * 
 * @author Lance Ewing
 */
public class MachineScreen implements Screen {

    /**
     * The Game object for JOric. Allows us to easily change screens.
     */
    private JOric joric;

    /**
     * Platform specific JOricRunner implementation.
     */
    private JOricRunner joricRunner;

    /**
     * The InputProcessor for the MachineScreen. Handles the key and touch input.
     */
    private MachineInputProcessor machineInputProcessor;

    /**
     * This is an InputMultiplexor, which includes both the Stage and the
     * MachineScreen.
     */
    private InputMultiplexer portraitInputProcessor;
    private InputMultiplexer landscapeInputProcessor;

    /**
     * SpriteBatch shared by all rendered components.
     */
    private SpriteBatch batch;

    // Currently in use components to support rendering of the Oric screen. The
    // objects that these references point to will change depending on the MachineType.
    private Pixmap screenPixmap;
    private Viewport viewport;
    private Camera camera;
    private Texture[] screens;
    private int drawScreen = 1;
    private int updateScreen = 0;

    // Screen resources for each MachineType.
    private Map<MachineType, Pixmap> machineTypePixmaps;
    private Map<MachineType, Camera> machineTypeCameras;
    private Map<MachineType, Viewport> machineTypeViewports;
    private Map<MachineType, Texture[]> machineTypeTextures;

    // UI components.
    private Texture joystickIcon;
    private Texture keyboardIcon;
    private Texture backIcon;

    private ViewportManager viewportManager;

    // Touchpad
    private Stage portraitStage;
    private Stage landscapeStage;
    private Touchpad portraitTouchpad;
    private Touchpad landscapeTouchpad;

    /**
     * Details about the application currently running.
     */
    private AppConfigItem appConfigItem;

    /**
     * Whether or not the game was started by a user interaction.
     */
    private boolean startedByUser;

    /**
     * PAL or NTSC. Currently we only support PAL, as NTSC is super rare for Oric.
     * They were never sold in NTSC countries, so there are unlikely to be NTSC
     * specific dependencies in the available programs.
     */
    private MachineType machineType;

    /**
     * Constructor for MachineScreen.
     * 
     * @param joric
     * @param joricRunner
     * @param dialogHandler
     */
    public MachineScreen(JOric joric, JOricRunner joricRunner, DialogHandler dialogHandler) {
        this.joric = joric;
        this.joricRunner = joricRunner;

        // We only support PAL output, so can initialise the screen size up front.
        machineType = MachineType.PAL;

        joricRunner.init(this, machineType.getTotalScreenWidth(), machineType.getTotalScreenHeight());

        batch = new SpriteBatch();

        machineTypePixmaps = new HashMap<MachineType, Pixmap>();
        machineTypeTextures = new HashMap<MachineType, Texture[]>();
        machineTypeViewports = new HashMap<MachineType, Viewport>();
        machineTypeCameras = new HashMap<MachineType, Camera>();

        createScreenResourcesForMachineType(MachineType.PAL);

        keyboardIcon = new Texture("png/keyboard_icon.png");
        joystickIcon = new Texture("png/joystick_icon.png");
        backIcon = new Texture("png/back_arrow.png");

        // Create the portrait and landscape joystick touchpads.
        portraitTouchpad = createTouchpad(300);
        landscapeTouchpad = createTouchpad(200);

        viewportManager = ViewportManager.getInstance();

        // Create a Stage and add TouchPad
        portraitStage = new Stage(viewportManager.getPortraitViewport(), batch);
        portraitStage.addActor(portraitTouchpad);
        landscapeStage = new Stage(viewportManager.getLandscapeViewport(), batch);
        landscapeStage.addActor(landscapeTouchpad);

        // Create and register an input processor for keys, etc.
        machineInputProcessor = new MachineInputProcessor(this, dialogHandler);
        portraitInputProcessor = new InputMultiplexer();
        portraitInputProcessor.addProcessor(joricRunner.getKeyboardMatrix());
        portraitInputProcessor.addProcessor(portraitStage);
        portraitInputProcessor.addProcessor(machineInputProcessor);
        landscapeInputProcessor = new InputMultiplexer();
        landscapeInputProcessor.addProcessor(joricRunner.getKeyboardMatrix());
        landscapeInputProcessor.addProcessor(landscapeStage);
        landscapeInputProcessor.addProcessor(machineInputProcessor);
    }

    protected Touchpad createTouchpad(int size) {
        Skin touchpadSkin = new Skin();
        touchpadSkin.add("touchBackground", new Texture("png/touchBackground.png"));
        touchpadSkin.add("touchKnob", new Texture("png/touchKnob.png"));
        TouchpadStyle touchpadStyle = new TouchpadStyle();
        Drawable touchBackground = touchpadSkin.getDrawable("touchBackground");
        Drawable touchKnob = touchpadSkin.getDrawable("touchKnob");
        touchpadStyle.background = touchBackground;
        touchpadStyle.knob = touchKnob;
        Touchpad touchpad = new Touchpad(10, touchpadStyle);
        touchpad.setBounds(15, 15, size, size);
        return touchpad;
    }

    /**
     * Initialises the Machine with the given AppConfigItem. This will represent an
     * app that was selected on the HomeScreen. As part of this initialisation, it
     * creates the Pixmap, screen Textures, Camera and Viewport required to render
     * the Oric screen at the size needed for the MachineType being emulated.
     * 
     * @param appConfigItem The configuration for the app that was selected on the HomeScreen.
     * @param startedByUser Whether the program is being started via a user interaction, or not.
     */
    public void initMachine(AppConfigItem appConfigItem, boolean startedByUser) {
        this.appConfigItem = appConfigItem;
        this.startedByUser = startedByUser;

        // NOTE: The Machine is recreated and initialised by the start method in
        // JOricRunner.

        // Switch libGDX screen resources used by the Oric screen to the size required
        // by the MachineType.
        MachineType machineType = MachineType.valueOf(appConfigItem.getMachineType());
        screenPixmap = machineTypePixmaps.get(machineType);
        screens = machineTypeTextures.get(machineType);
        camera = machineTypeCameras.get(machineType);
        viewport = machineTypeViewports.get(machineType);

        drawScreen = 1;
        updateScreen = 0;
    }

    /**
     * Creates the libGDX screen resources required for the given MachineType.
     * 
     * @param machineType The MachineType to create the screen resources for.
     */
    private void createScreenResourcesForMachineType(MachineType machineType) {
        // Create the libGDX screen resources used by the Oric screen to the size
        // required by the MachineType.
        Pixmap screenPixmap = new Pixmap(machineType.getTotalScreenWidth(), machineType.getTotalScreenHeight(), 
                Pixmap.Format.RGBA8888);
        Texture[] screens = new Texture[3];
        screens[0] = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
        screens[0].setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        screens[1] = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
        screens[1].setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        screens[2] = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
        screens[2].setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        
        Camera camera = new OrthographicCamera();
        Viewport viewport = new ExtendViewport(((machineType.getVisibleScreenHeight() / 4) * 5),
                machineType.getVisibleScreenHeight(), camera);
        machineTypePixmaps.put(machineType, screenPixmap);
        machineTypeTextures.put(machineType, screens);
        machineTypeCameras.put(machineType, camera);
        machineTypeViewports.put(machineType, viewport);
    }

    private long renderCount;

    @Override
    public void render(float delta) {
        long fps = Gdx.graphics.getFramesPerSecond();
        boolean draw = false;

        if (joricRunner.isPaused()) {
            // When paused, we limit the draw frequency since there isn't anything to change.
            draw = ((fps < 30) || ((renderCount % (fps / 30)) == 0));

        } else {
            // TODO: See if we can check if there is a new frame ready before copying??
            copyPixels();
            draw = true;
        }

        if (draw) {
            draw(delta);
        }

        renderCount++;
    }
    
    public boolean copyPixels() {
        joricRunner.updatePixmap(screenPixmap);
        screens[updateScreen].draw(screenPixmap, 0, 0);
        updateScreen = (updateScreen + 1) % 3;
        drawScreen = (drawScreen + 1) % 3;
        return true;
    }
    
    private void draw(float delta) {
        // Get the KeyboardType currently being used by the MachineScreenProcessor.
        KeyboardType keyboardType = machineInputProcessor.getKeyboardType();

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Render the Oric screen.
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        batch.disableBlending();
        batch.begin();
        Color c = batch.getColor();
        batch.setColor(c.r, c.g, c.b, 1f);
        batch.draw(screens[drawScreen], 0, 0, ((machineType.getVisibleScreenHeight() / 4) * 5),
                machineType.getVisibleScreenHeight(), machineType.getHorizontalOffset(),
                machineType.getVerticalOffset(), machineType.getVisibleScreenWidth(),
                machineType.getVisibleScreenHeight(), false, false);
        batch.end();

        // Render the UI elements, e.g. the keyboard and joystick icons.
        viewportManager.getCurrentCamera().update();
        batch.setProjectionMatrix(viewportManager.getCurrentCamera().combined);
        batch.enableBlending();
        batch.begin();
        if (keyboardType.equals(KeyboardType.JOYSTICK)) {
            if (viewportManager.isPortrait()) {
                // batch.draw(keyboardType.getTexture(KeyboardType.LEFT), 0, 0);
                batch.draw(keyboardType.getTexture(KeyboardType.RIGHT), viewportManager.getWidth() - 135, 0);
            } else {
                // batch.draw(keyboardType.getTexture(KeyboardType.LEFT), 0, 0, 201, 201);
                batch.draw(keyboardType.getTexture(KeyboardType.RIGHT), viewportManager.getWidth() - 135, 0);
            }
        } else if (keyboardType.isRendered()) {
            batch.setColor(c.r, c.g, c.b, keyboardType.getOpacity());
            batch.draw(keyboardType.getTexture(), 0, keyboardType.getRenderOffset());
        } else if (keyboardType.equals(KeyboardType.OFF)) {
            // The keyboard and joystick icons are rendered only when an input type isn't
            // showing.
            batch.setColor(c.r, c.g, c.b, 0.5f);
            if (viewportManager.isPortrait()) {
                batch.draw(joystickIcon, 0, 0);
                if (Gdx.app.getType().equals(ApplicationType.Android)) {
                    // Main Oric keyboard on the left.
                    batch.draw(keyboardIcon, viewportManager.getWidth() - 145, 0);
                    // Mobile keyboard for debug purpose. Wouldn't normally make this available.
                    batch.setColor(c.r, c.g, c.b, 0.15f);
                    batch.draw(keyboardIcon, viewportManager.getWidth() - viewportManager.getWidth() / 2 - 70, 0);

                } else {
                    // Desktop puts Oric keyboard button in the middle.
                    batch.draw(keyboardIcon, viewportManager.getWidth() - viewportManager.getWidth() / 2 - 70, 0);
                    // and the back button on the right.
                    batch.draw(backIcon, viewportManager.getWidth() - 145, 0);
                }

            } else {
                batch.draw(joystickIcon, 0, viewportManager.getHeight() - 140);
                batch.draw(keyboardIcon, viewportManager.getWidth() - 150, viewportManager.getHeight() - 125);
                batch.draw(backIcon, viewportManager.getWidth() - 150, 0);
            }
        }
        batch.end();
        if (keyboardType.equals(KeyboardType.JOYSTICK)) {
            float joyX = 0;
            float joyY = 0;
            if (viewportManager.isPortrait()) {
                portraitStage.act(delta);
                portraitStage.draw();
                joyX = portraitTouchpad.getKnobPercentX();
                joyY = portraitTouchpad.getKnobPercentY();
            } else {
                landscapeStage.act(delta);
                landscapeStage.draw();
                joyX = landscapeTouchpad.getKnobPercentX();
                joyY = landscapeTouchpad.getKnobPercentY();
            }
            // TODO: Handle joystick input.
            // machine.getJoystick().touchPad(joyX, joyY);
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, false);

        // Align Oric screen's top edge to top of the viewport.
        int screenWidth = ((machineType.getVisibleScreenHeight() / 4) * 5);
        int screenHeight = machineType.getVisibleScreenHeight();
        Camera camera = viewport.getCamera();
        camera.position.x = screenWidth / 2;
        camera.position.y = screenHeight - viewport.getWorldHeight() / 2;
        camera.update();

        machineInputProcessor.resize(width, height);
        viewportManager.update(width, height);

        if (viewportManager.isPortrait()) {
            Gdx.input.setInputProcessor(portraitInputProcessor);
        } else {
            Gdx.input.setInputProcessor(landscapeInputProcessor);
        }
    }

    @Override
    public void pause() {
        // On Android, this is also called when the "Home" button is pressed.
        joricRunner.pause();
    }

    @Override
    public void resume() {
        KeyboardType.init();
        joricRunner.resume();
    }

    @Override
    public void show() {
        KeyboardType.init();
        
        if (viewportManager.isPortrait()) {
            Gdx.input.setInputProcessor(portraitInputProcessor);
        } else {
            Gdx.input.setInputProcessor(landscapeInputProcessor);
        }
        
        joricRunner.resume();
        
        if (appConfigItem != null) {
            joricRunner.start(appConfigItem);
        }
    }

    @Override
    public void hide() {
        // On Android, this is also called when the "Back" button is pressed.
        KeyboardType.dispose();
    }

    @Override
    public void dispose() {
        KeyboardType.dispose();
        keyboardIcon.dispose();
        joystickIcon.dispose();
        batch.dispose();
        joricRunner.stop();
        disposeScreens();
    }

    /**
     * Disposes the libGDX screen resources for each MachineType.
     */
    private void disposeScreens() {
        for (Pixmap pixmap : machineTypePixmaps.values()) {
            pixmap.dispose();
        }
        for (Texture[] screens : machineTypeTextures.values()) {
            screens[0].dispose();
            screens[1].dispose();
            screens[2].dispose();
        }
    }

    /**
     * Gets the JOricRunner implementation instance that is running the Oric game.
     * 
     * @return
     */
    public JOricRunner getJoricRunner() {
        return joricRunner;
    }

    /**
     * Returns user to the Home screen.
     */
    public void exit() {
        joric.setScreen(joric.getHomeScreen());
    }
}
