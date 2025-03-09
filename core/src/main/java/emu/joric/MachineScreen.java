package emu.joric;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Input.Keys;
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

import emu.joric.config.AppConfigItem;
import emu.joric.ui.DialogHandler;
import emu.joric.ui.MachineInputProcessor;
import emu.joric.ui.MachineInputProcessor.JoystickAlignment;
import emu.joric.ui.MachineInputProcessor.ScreenSize;
import emu.joric.ui.ViewportManager;

/**
 * The main screen in the JOric emulator, i.e. the one that shows the video
 * output of the ULA.
 * 
 * @author Lance Ewing
 */
public class MachineScreen implements Screen {

    // The Oric pixels allegedly have a 5:4 ratio
    private static final int ORIC_SCREEN_WIDTH = 240;
    private static final int ORIC_SCREEN_HEIGHT = 224;
    private static final int ADJUSTED_WIDTH = ((ORIC_SCREEN_HEIGHT / 4) * 5);
    private static final int ADJUSTED_HEIGHT = ORIC_SCREEN_HEIGHT;
    
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
    private ExtendViewport viewport;
    private Camera camera;
    private Texture[] screens;
    private int drawScreen = 1;
    private int updateScreen = 0;
    private int textureOffset = 0;

    // Screen resources for each MachineType.
    private Map<MachineType, Pixmap> machineTypePixmaps;
    private Map<MachineType, Camera> machineTypeCameras;
    private Map<MachineType, ExtendViewport> machineTypeViewports;
    private Map<MachineType, Texture[]> machineTypeTextures;

    // UI components.
    private Texture screenSizeIcon;
    private Texture playIcon;
    private Texture pauseIcon;
    private Texture muteIcon;
    private Texture unmuteIcon;
    private Texture joystickIcon;
    private Texture keyboardIcon;
    private Texture backIcon;
    private Texture fullScreenIcon;
    private Texture nmiIcon;

    private ViewportManager viewportManager;

    // Touchpad
    private Stage portraitStage;
    private Stage landscapeStage;
    private Touchpad portraitTouchpad;
    private Touchpad landscapeTouchpad;
    private int previousDirection;

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
     * The screen size setting during the last draw.
     */
    private ScreenSize lastScreenSize;
    
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
        machineTypeViewports = new HashMap<MachineType, ExtendViewport>();
        machineTypeCameras = new HashMap<MachineType, Camera>();

        createScreenResourcesForMachineType(MachineType.PAL);

        screenSizeIcon = new Texture("png/screen_icon.png");
        playIcon = new Texture("png/play.png");
        pauseIcon = new Texture("png/pause.png");
        muteIcon = new Texture("png/mute_icon.png");
        unmuteIcon = new Texture("png/unmute_icon.png");
        keyboardIcon = new Texture("png/keyboard_icon.png");
        joystickIcon = new Texture("png/joystick_icon.png");
        backIcon = new Texture("png/back_arrow.png");
        fullScreenIcon = new Texture("png/full_screen.png");
        nmiIcon = new Texture("png/nmi.png");

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
        touchpadSkin.add("touchBackground", new Texture("png/joystick_background.png"));
        touchpadSkin.add("touchKnob", new Texture("png/joystick_knob.png"));
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
        Texture[] screens = new Texture[6];
        // First three textures are the "blurred" image and the second three are 
        // for the "sharp" image.
        screens[0] = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
        screens[0].setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        screens[1] = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
        screens[1].setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        screens[2] = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
        screens[2].setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        screens[3] = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
        screens[3].setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        screens[4] = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
        screens[4].setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        screens[5] = new Texture(screenPixmap, Pixmap.Format.RGBA8888, false);
        screens[5].setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        
        Camera camera = new OrthographicCamera();
        ExtendViewport viewport = new ExtendViewport(
                ((machineType.getVisibleScreenHeight() / 4) * 5),
                machineType.getVisibleScreenHeight(), 
                camera);
        
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

        if (joricRunner.hasStopped()) {
            // If game has ended then go back to home screen. It has to be the UI thread
            // that calls the setScreen method. The JOricRunner itself can't do this.
            joricRunner.reset();
            // This makes sure we update the Pixmap one last time before leaving, as that
            // will mean that the AGI game screen starts out black for the next game.
            copyPixels();
            if (Gdx.graphics.isFullscreen()) {
                machineInputProcessor.switchOutOfFullScreen();
            }
            joric.setScreen(joric.getHomeScreen());
            return;
        }
        
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

        // Process any delayed key releases that are pending.
        joricRunner.getKeyboardMatrix().checkDelayedReleaseKeys();
        
        renderCount++;
    }
    
    public boolean copyPixels() {
        joricRunner.updatePixmap(screenPixmap);
        screens[updateScreen + textureOffset].draw(screenPixmap, 0, 0);
        updateScreen = (updateScreen + 1) % 3;
        drawScreen = (drawScreen + 1) % 3;
        return true;
    }
    
    private void draw(float delta) {
        // Get the KeyboardType currently being used by the MachineScreenProcessor.
        KeyboardType keyboardType = machineInputProcessor.getKeyboardType();
        JoystickAlignment joystickAlignment = machineInputProcessor.getJoystickAlignment();

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Render the Oric screen.
        float cameraXOffset = 0;
        float cameraYOffset = 0;
        float sidePaddingWidth = viewportManager.getSidePaddingWidth();
        
        if (viewportManager.doesScreenFitWidth()) {
            // Override default screen centering logic to allow for narrower screens, so 
            // that the joystick can be rendered as a decent size.
            float oricWidthRatio = (viewportManager.getOricScreenWidth() / ADJUSTED_WIDTH);
            if ((sidePaddingWidth > 64) && (sidePaddingWidth < 128)) {
                // Icons on one side.
                // 128 = 2 * min width on sides.
                // 64 = when icon on one side is perfectly centred.
                float unadjustedXOffset = Math.min(128 - sidePaddingWidth, sidePaddingWidth);
                cameraXOffset = (unadjustedXOffset / oricWidthRatio);
                if (joystickAlignment.equals(JoystickAlignment.LEFT)) {
                    cameraXOffset *= -1;
                }
            }
        } else {
            float oricScreenHeight = (viewportManager.getWidth() / 1.25f);
            float oricHeightRatio = (oricScreenHeight / ADJUSTED_HEIGHT);
            float topPadding = ((viewportManager.getHeight() - oricScreenHeight) / 2);
            cameraYOffset = (topPadding / oricHeightRatio);
        }
        machineInputProcessor.setCameraXOffset(cameraXOffset);
        ScreenSize currentScreenSize = machineInputProcessor.getScreenSize();
        if (currentScreenSize != lastScreenSize) {
            resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        }
        lastScreenSize = currentScreenSize;
        camera.position.set((ADJUSTED_WIDTH / 2) + cameraXOffset, (ADJUSTED_HEIGHT / 2) - cameraYOffset, 0.0f);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        batch.disableBlending();
        batch.begin();
        Color c = batch.getColor();
        batch.setColor(c.r, c.g, c.b, 1f);
        batch.draw(
                screens[drawScreen + textureOffset], 
                0, 0, ADJUSTED_WIDTH, ADJUSTED_HEIGHT,
                0, 0, ORIC_SCREEN_WIDTH, ORIC_SCREEN_HEIGHT, 
                false, false);
        batch.end();

        // Render the UI elements, e.g. the keyboard and joystick icons.
        viewportManager.getCurrentCamera().update();
        batch.setProjectionMatrix(viewportManager.getCurrentCamera().combined);
        batch.enableBlending();
        batch.begin();
        
        // The keyboard is always render in portrait mode, as there is space for it,
        // but in landscape mode, it needs to be enabled via the keyboard icon.
        if (keyboardType.isRendered() || viewportManager.isPortrait()) {
            if (keyboardType.getTexture() != null) {
                batch.setColor(c.r, c.g, c.b, keyboardType.getOpacity());
                batch.draw(
                        keyboardType.getTexture(), 
                        0, keyboardType.getRenderOffset(), 
                        keyboardType.getTexture().getWidth(), 
                        keyboardType.getHeight());
            }
        }
        
        batch.setColor(c.r, c.g, c.b, 0.5f);
        
        // Some icons change depending on state.
        Texture speakerIcon = machineInputProcessor.isSpeakerOn()? muteIcon : unmuteIcon;
        Texture pausePlayIcon = joricRunner.isPaused()? playIcon : pauseIcon;
        
        if (viewportManager.isPortrait()) {
            // Portrait
            batch.draw(fullScreenIcon, 20, 20);
            batch.draw(screenSizeIcon, (viewportManager.getWidth() / 6) - 16, 20);
            batch.draw(speakerIcon, (viewportManager.getWidth() / 3) - 32, 20);
            batch.draw(pausePlayIcon, (viewportManager.getWidth() / 2) - 48, 20);
            batch.draw(keyboardIcon, (viewportManager.getWidth() - (viewportManager.getWidth() / 3)) - 64, 20);
            batch.draw(nmiIcon, (viewportManager.getWidth() - (viewportManager.getWidth() / 6)) - 80, 20);
            batch.draw(backIcon, viewportManager.getWidth() - 116, 20);
        } else {
            // Landscape
            if (cameraXOffset == 0) {
                // Middle.
                if ((viewportManager.getOricScreenBase() > 0) || (sidePaddingWidth <= 64)) {
                    // The area between full landscape and full portrait.
                    float leftAdjustment = (viewportManager.getWidth() / 4) - 48;
                    batch.draw(fullScreenIcon, ((viewportManager.getWidth() - ((viewportManager.getWidth() * 6 ) / 12)) - 96) - leftAdjustment, 16);
                    batch.draw(screenSizeIcon, ((viewportManager.getWidth() - ((viewportManager.getWidth() * 5 ) / 12)) - 96) - leftAdjustment, 16);
                    batch.draw(speakerIcon,    ((viewportManager.getWidth() - ((viewportManager.getWidth() * 4 ) / 12)) - 96) - leftAdjustment, 16);
                    batch.draw(pausePlayIcon,  ((viewportManager.getWidth() - ((viewportManager.getWidth() * 3 ) / 12)) - 96) - leftAdjustment, 16);
                    batch.draw(keyboardIcon,   ((viewportManager.getWidth() - ((viewportManager.getWidth() * 2 ) / 12)) - 96) - leftAdjustment, 16);
                    batch.draw(nmiIcon,        ((viewportManager.getWidth() - ((viewportManager.getWidth() * 1 ) / 12)) - 96) - leftAdjustment, 16);
                    batch.draw(backIcon,       ((viewportManager.getWidth() - ((viewportManager.getWidth() * 0 ) / 12)) - 96) - leftAdjustment, 16);
                } else {
                    // Normal landscape.
                    batch.draw(speakerIcon,   16, viewportManager.getHeight() - 112);
                    batch.draw(pausePlayIcon, 16, (viewportManager.getHeight() - (viewportManager.getHeight() / 3)) - 64);
                    // Free slot.
                    batch.draw(keyboardIcon,  16, 0);
                    batch.draw(fullScreenIcon, viewportManager.getWidth() - 112, viewportManager.getHeight() - 112);
                    batch.draw(screenSizeIcon, viewportManager.getWidth() - 112, (viewportManager.getHeight() - (viewportManager.getHeight() / 3)) - 64);
                    batch.draw(nmiIcon,        viewportManager.getWidth() - 112, (viewportManager.getHeight() / 3) - 32);
                    batch.draw(backIcon,       viewportManager.getWidth() - 112, 16);
                }
            } else if (cameraXOffset < 0) {
                // Left
                batch.draw(fullScreenIcon, 16, (viewportManager.getHeight() - 112));
                batch.draw(screenSizeIcon, 16, (viewportManager.getHeight() - (viewportManager.getHeight() / 6)) - 80);
                batch.draw(speakerIcon,    16, (viewportManager.getHeight() - (viewportManager.getHeight() / 3)) - 64);
                batch.draw(pausePlayIcon,  16, (viewportManager.getHeight() / 2) - 48);
                batch.draw(keyboardIcon,   16, (viewportManager.getHeight() / 3) - 32);
                batch.draw(nmiIcon,        16, (viewportManager.getHeight() / 6) - 16);
                batch.draw(backIcon,       16, 16);
            } else if (cameraXOffset > 0) {
                // Right
                batch.draw(fullScreenIcon, viewportManager.getWidth() - 112, (viewportManager.getHeight() - 112));
                batch.draw(screenSizeIcon, viewportManager.getWidth() - 112, (viewportManager.getHeight() - (viewportManager.getHeight() / 6)) - 80);
                batch.draw(speakerIcon,    viewportManager.getWidth() - 112, (viewportManager.getHeight() - (viewportManager.getHeight() / 3)) - 64);
                batch.draw(pausePlayIcon,  viewportManager.getWidth() - 112, (viewportManager.getHeight() / 2) - 48);
                batch.draw(keyboardIcon,   viewportManager.getWidth() - 112, (viewportManager.getHeight() / 3) - 32);
                batch.draw(nmiIcon,        viewportManager.getWidth() - 112, (viewportManager.getHeight() / 6) - 16);
                batch.draw(backIcon,       viewportManager.getWidth() - 112, 16);
            }
        }
        
        batch.end();
        
        // The joystick touch pad is updated and rendered via the Stage.
        if (!joystickAlignment.equals(JoystickAlignment.OFF)) {
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
            processJoystickInput(joyX, joyY);
        }
    }

    private static final int[] DIRECTION_TO_KEY_MAP = new int[] {
        0, 
        Keys.UP, 
        Keys.RIGHT, 
        Keys.DOWN, 
        Keys.LEFT
    };
    
    /**
     * Processes joystick input, converting the touchpad position into an Oric
     * arrow direction and then setting the corresponding direction key.
     * 
     * @param joyX
     * @param joyY
     */
    private void processJoystickInput(float joyX, float joyY) {
        double heading = Math.atan2(-joyY, joyX);
        double distance = Math.sqrt((joyX * joyX) + (joyY * joyY));
        
        int direction = 0;
        
        if (distance > 0.3) {
            if (heading == 0) {
                // Right
                direction = 2;
            }
            else if (heading > 0) {
                // Down
                if (heading < 0.785398) {
                    // Right
                    direction = 2;
                }
                else if (heading < 2.3561946) {
                    // Down
                    direction = 3;
                }
                else {
                    // Left
                    direction = 4;
                }
            }
            else {
                // Up
                if (heading > -0.785398) {
                    // Right
                    direction = 2;
                }
                else if (heading > -2.3561946) {
                    // Up
                    direction = 1;
                }
                else {
                    // Left
                    direction = 4;
                }
            }
        }
        
        KeyboardMatrix keyboardMatrix = joricRunner.getKeyboardMatrix();
        
        if ((previousDirection != 0) && (direction != previousDirection)) {
            keyboardMatrix.keyUp(DIRECTION_TO_KEY_MAP[previousDirection]);
        }
        if ((direction != 0) && (direction != previousDirection)) {
            keyboardMatrix.keyDown(DIRECTION_TO_KEY_MAP[direction]);
        }
                
        previousDirection = direction;
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
            // Screen size reverts back to FIT whenever in portrait mode.
            machineInputProcessor.setScreenSize(ScreenSize.FIT);
            viewport.setMinWorldWidth((machineType.getVisibleScreenHeight() / 4) * 5);
            viewport.setMinWorldHeight(machineType.getVisibleScreenHeight());
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
        backIcon.dispose();
        fullScreenIcon.dispose();
        muteIcon.dispose();
        unmuteIcon.dispose();
        playIcon.dispose();
        pauseIcon.dispose();
        nmiIcon.dispose();
        screenSizeIcon.dispose();
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
            screens[3].dispose();
            screens[4].dispose();
            screens[5].dispose();
        }
    }
    
    /**
     * Changes the blur mode, to either be on or off.
     * 
     * @param blurOn
     */
    public void changeBlur(boolean blurOn) {
        textureOffset = (blurOn? 3 : 0);
    }

    /**
     * Gets the JOricRunner implementation instance that is running the Oric game.
     * 
     * @return
     */
    public JOricRunner getJoricRunner() {
        return joricRunner;
    }

    public MachineInputProcessor getMachineInputProcessor() {
        return machineInputProcessor;
    }
    
    public ExtendViewport getViewport() {
        return viewport;
    }
    
    /**
     * Returns user to the Home screen.
     */
    public void exit() {
        joric.setScreen(joric.getHomeScreen());
    }
}
