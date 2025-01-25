package emu.joric;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.viewport.Viewport;

import emu.joric.config.AppConfig;
import emu.joric.config.AppConfigItem;
import emu.joric.ui.DialogHandler;
import emu.joric.ui.ConfirmResponseHandler;
import emu.joric.ui.PagedScrollPane;
import emu.joric.ui.PaginationWidget;
import emu.joric.ui.TextInputResponseHandler;
import emu.joric.ui.ViewportManager;

/**
 * The Home screen of the JOric emulator, i.e. the one that shows all of the
 * available boot options and programs to load. Reminiscent of the Android home
 * screen.
 * 
 * @author Lance Ewing
 */
public class HomeScreen extends InputAdapter implements Screen {

    /**
     * The Game object for JOric. Allows us to easily change screens.
     */
    private JOric joric;

    private Skin skin;
    private Stage portraitStage;
    private Stage landscapeStage;
    private ViewportManager viewportManager;
    private Map<String, AppConfigItem> appConfigMap;
    private Map<String, Texture> buttonTextureMap;
    private Texture backgroundLandscape;
    private Texture backgroundPortrait;
    private PaginationWidget portraitPaginationWidget;
    private PaginationWidget landscapePaginationWidget;
    private Texture titleTexture;

    /**
     * Invoked by JOric whenever it would like to show a dialog, such as when it
     * needs the user to confirm an action, or to choose a file.
     */
    private DialogHandler dialogHandler;

    /**
     * The InputProcessor for the Home screen. This is an InputMultiplexor, which
     * includes both the Stage and the HomeScreen.
     */
    private InputMultiplexer portraitInputProcessor;
    private InputMultiplexer landscapeInputProcessor;

    /**
     * Holds a reference to the special app config item for BASIC.
     */
    private AppConfigItem basicAppConfigItem;
    
    /**
     * Holds a reference to the AppConfigItem for the last program that was
     * launched.
     */
    private AppConfigItem lastProgramLaunched;

    /**
     * Constructor for HomeScreen.
     * 
     * @param joric         The Joric instance.
     * @param dialogHandler
     */
    public HomeScreen(JOric joric, DialogHandler dialogHandler) {
        this.joric = joric;
        this.dialogHandler = dialogHandler;

        // Load the app meta data.
        Json json = new Json();
        String appConfigJson = Gdx.files.internal("data/programs.json").readString();
        AppConfig appConfig = json.fromJson(AppConfig.class, appConfigJson);
        removeProgramsWithIcons(appConfig);
        basicAppConfigItem = buildBasicAppConfigItem();
        appConfigMap = new TreeMap<String, AppConfigItem>();
        for (AppConfigItem appConfigItem : appConfig.getApps()) {
            appConfigMap.put(appConfigItem.getName(), appConfigItem);
        }

        buttonTextureMap = new HashMap<String, Texture>();
        skin = new Skin(Gdx.files.internal("data/uiskin.json"));
        skin.add("top", skin.newDrawable("default-round", new Color(0, 0, 0, 0)), Drawable.class);
        skin.add("empty", skin.newDrawable("default-round", new Color(1f, 1f, 1f, 0.1f)), Drawable.class);

        titleTexture = new Texture("png/joric_title_3.png");
        backgroundLandscape = new Texture("jpg/atmos_red_back_3.jpg");
        backgroundLandscape.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        backgroundPortrait = new Texture("jpg/atmos_red_back_3l.jpg");
        backgroundPortrait.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        viewportManager = ViewportManager.getInstance();
        portraitPaginationWidget = new PaginationWidget(this, 1080);
        landscapePaginationWidget = new PaginationWidget(this, 1920);
        portraitStage = createStage(viewportManager.getPortraitViewport(), portraitPaginationWidget, appConfig);
        landscapeStage = createStage(viewportManager.getLandscapeViewport(), landscapePaginationWidget, appConfig);

        // The stage handles most of the input, but we need to handle the BACK button
        // separately.
        portraitInputProcessor = new InputMultiplexer();
        portraitInputProcessor.addProcessor(portraitStage);
        portraitInputProcessor.addProcessor(this);
        landscapeInputProcessor = new InputMultiplexer();
        landscapeInputProcessor.addProcessor(landscapeStage);
        landscapeInputProcessor.addProcessor(this);
    }

    /**
     * Removes programs from the AppConfig where the icon path is not set.
     * 
     * @param appConfig The AppConfig to remove the programs from.
     */
    private void removeProgramsWithIcons(AppConfig appConfig) {
        if ((appConfig != null) && (appConfig.getApps() != null)) {
            ArrayList<AppConfigItem> modifiedApps = new ArrayList<>();
            for (AppConfigItem appConfigItem : appConfig.getApps()) {
                if ((appConfigItem.getIconPath() != null) && (!appConfigItem.getIconPath().equals(""))) {
                    modifiedApps.add(appConfigItem);
                }
            }
            appConfig.setApps(modifiedApps);
        }
    }
    
    private AppConfigItem buildBasicAppConfigItem() {
        AppConfigItem basicAppConfigItem = new AppConfigItem();
        basicAppConfigItem.setName("BASIC");
        basicAppConfigItem.setFilePath("");
        basicAppConfigItem.setFileType("");
        basicAppConfigItem.setIconPath("screenshots/B/Basic/Basic.png");
        basicAppConfigItem.setMachineType("PAL");
        basicAppConfigItem.setRam("RAM_48K");
        return basicAppConfigItem;
    }
    
    private Stage createStage(Viewport viewport, PaginationWidget paginationWidget, AppConfig appConfig) {
        Stage stage = new Stage(viewport);
        addAppButtonsToStage(stage, paginationWidget, appConfig);
        return stage;
    }

    private void addAppButtonsToStage(Stage stage, PaginationWidget paginationWidget, AppConfig appConfig) {
        Table container = new Table();
        stage.addActor(container);
        container.setFillParent(true);

        Table currentPage = new Table().pad(0, 0, 0, 0);
        Image title = new Image(titleTexture);
        
        float viewportWidth = viewportManager.getWidth();
        float viewportHeight = viewportManager.getHeight();
        
        int sidePadding = (viewportHeight > (viewportWidth / 1.32f))? 15 : 85;
        
        int availableHeight = (int)(viewportHeight - PAGINATION_HEIGHT);
        int columns = (int)((viewportWidth - sidePadding) / ICON_IMAGE_WIDTH);
        int rows = (int)(availableHeight / (ICON_IMAGE_HEIGHT + ICON_LABEL_HEIGHT + 10));
        
        int totalHorizPadding = 0;
        int horizPaddingUnit = 0;

        Button infoButton = buildButton("INFO", null, "png/info.png", 96, 96, null, null);
        currentPage.add().expandX();
        currentPage.add(infoButton).pad(30, 0, 0, 20).align(Align.right).expandX();
        currentPage.row();
        currentPage.add().expandX();
        
        if (viewportManager.isLandscape()) {
            // Landscape.
            container.setBackground(new Image(backgroundLandscape).getDrawable());
            totalHorizPadding = 1920 - (ICON_IMAGE_WIDTH * columns) - (sidePadding * 2);
            horizPaddingUnit = totalHorizPadding / (columns * 2);
            int titleWidth = 428;
            float titlePadding = ((1920 - titleWidth) / 2);
            currentPage.add(title).width(titleWidth).height(197).pad(-7, titlePadding, 112 - 19, titlePadding).expand();
        } else {
            // Portrait.
            container.setBackground(new Image(backgroundPortrait).getDrawable());
            totalHorizPadding = 1080 - (ICON_IMAGE_WIDTH * columns) - (sidePadding * 2);
            horizPaddingUnit = totalHorizPadding / (columns * 2);
            int titleWidth = 428;
            float titlePadding = ((1080 - titleWidth) / 2);
            currentPage.add(title).width(titleWidth).height(197).pad(-7, titlePadding, 112 - 19, titlePadding).expand();
        }
        
        PagedScrollPane pagedScrollPane = new PagedScrollPane();
        pagedScrollPane.setHomeScreen(this);
        pagedScrollPane.setFlingTime(0.01f);

        int itemsPerPage = columns * rows;
        int pageItemCount = 0;

        // Set up first page, which is mainly empty.
        pagedScrollPane.addPage(currentPage);
        
        currentPage = new Table().pad(0, sidePadding, 0, sidePadding);
        currentPage.defaults().pad(0, horizPaddingUnit, 0, horizPaddingUnit);

        // Add entry at the start for BASIC that will always be present
        currentPage.add(buildAppButton(basicAppConfigItem)).expand().fill();
        pageItemCount++;
        
        for (AppConfigItem appConfigItem : appConfig.getApps()) {
            // Every itemsPerPage apps, add a new page.
            if (pageItemCount == itemsPerPage) {
                pagedScrollPane.addPage(currentPage);
                pageItemCount = 0;
                currentPage = new Table().pad(0, sidePadding, 0, sidePadding);
                currentPage.defaults().pad(0, horizPaddingUnit, 0, horizPaddingUnit);
            }

            // Every number of columns apps, add a new row to the current page.
            if ((pageItemCount % columns) == 0) {
                currentPage.row();
            }

            // Currently, we're using the presence of an icon path to decide whether to add it.
            if ((appConfigItem.getIconPath() != null) && (!appConfigItem.getIconPath().equals(""))) {
                currentPage.add(buildAppButton(appConfigItem)).expand().fill();
                pageItemCount++;
            }
        }

        // Add the last page of apps.
        if (pageItemCount <= itemsPerPage) {
            AppConfigItem appConfigItem = new AppConfigItem();
            for (int i = pageItemCount; i < itemsPerPage; i++) {
                if ((i % columns) == 0) {
                    currentPage.row();
                }
                currentPage.add(buildAppButton(appConfigItem)).expand().fill();
            }
            pagedScrollPane.addPage(currentPage);
            if (pageItemCount == itemsPerPage) {
                currentPage = new Table().pad(0, sidePadding, 0, sidePadding);
                currentPage.defaults().pad(0, horizPaddingUnit, 0, horizPaddingUnit);
                for (int i = 0; i < itemsPerPage; i++) {
                    if ((i % columns) == 0) {
                        currentPage.row();
                    }
                    currentPage.add(buildAppButton(appConfigItem)).expand().fill();
                }
                pagedScrollPane.addPage(currentPage);
            }
        }

        container.add(pagedScrollPane).expand().fill();
        
        container.row();
        container.add(paginationWidget).maxHeight(PAGINATION_HEIGHT).minHeight(PAGINATION_HEIGHT);
        stage.addActor(paginationWidget);
    }
    
    @Override
    public void show() {
        Gdx.input.setInputProcessor(portraitInputProcessor);
        if (!Gdx.app.getType().equals(ApplicationType.WebGL)) {
            Gdx.graphics.setTitle("JOric");
        }
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (viewportManager.isPortrait()) {
            portraitStage.act(delta);
            portraitStage.draw();
        } else {
            landscapeStage.act(delta);
            landscapeStage.draw();
        }
    }

    @Override
    public void resize(int width, int height) {
        viewportManager.update(width, height);
        if (viewportManager.isPortrait()) {
            Gdx.input.setInputProcessor(portraitInputProcessor);
        } else {
            Gdx.input.setInputProcessor(landscapeInputProcessor);
        }
        updateHomeScreenButtonStages();
        
        // Screen is resized after returning from MachineScreen, so we need to scroll
        // back to the page that the program that was running was on.
        if (lastProgramLaunched != null) {
            showProgramPage(lastProgramLaunched, true);
            lastProgramLaunched = null;
        }
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void hide() {

    }

    @Override
    public void dispose() {
        portraitStage.dispose();
        landscapeStage.dispose();
        skin.dispose();
        disposeButtonTextureMap();
        landscapePaginationWidget.dispose();
        portraitPaginationWidget.dispose();
        titleTexture.dispose();
    }
    
    private void disposeButtonTextureMap() {
        if ((buttonTextureMap != null) && (!buttonTextureMap.isEmpty())) {
            for (Texture texture : buttonTextureMap.values()) {
                texture.dispose();
            }
            buttonTextureMap.clear();
        }
    }

    /**
     * Called when a key was released
     * 
     * @param keycode one of the constants in {@link Input.Keys}
     * 
     * @return whether the input was processed
     */
    public boolean keyUp(int keycode) {
        boolean modifierDown = 
                Gdx.input.isKeyPressed(Keys.SHIFT_LEFT) ||
                Gdx.input.isKeyPressed(Keys.SHIFT_RIGHT) ||
                Gdx.input.isKeyPressed(Keys.CONTROL_LEFT) ||
                Gdx.input.isKeyPressed(Keys.CONTROL_RIGHT) ||
                Gdx.input.isKeyPressed(Keys.ALT_LEFT) ||
                Gdx.input.isKeyPressed(Keys.ALT_RIGHT);
        
        float pageWidth = 0.0f;
        PagedScrollPane pagedScrollPane = null;
        if (viewportManager.isPortrait()) {
            Table table = (Table)portraitStage.getActors().get(0);
            pagedScrollPane = (PagedScrollPane)table.getChild(0);
            pageWidth = 1130.0f;
        }
        else {
            Table table = (Table)landscapeStage.getActors().get(0);
            pagedScrollPane = (PagedScrollPane)table.getChild(0);
            pageWidth = 1970.0f;
        }
        
        if (keycode == Keys.BACK) {
            if (Gdx.app.getType().equals(ApplicationType.Android)) {
                dialogHandler.confirm("Do you really want to Exit?", new ConfirmResponseHandler() {
                    public void yes() {
                        // Pressing BACK from the home screen will leave AGILE on Android.
                        Gdx.app.exit();
                    }

                    public void no() {
                        // Ignore. We're staying on the Home screen.
                    }
                });
                return true;
            }
        }
        else if (!modifierDown && !dialogHandler.isDialogOpen()) {
            if (keycode == Keys.PAGE_UP) {
                float newScrollX = MathUtils.clamp(pagedScrollPane.getScrollX() - pageWidth, 0, pagedScrollPane.getMaxX());
                pagedScrollPane.setScrollX(newScrollX);
                pagedScrollPane.setLastScrollX(newScrollX);
            }
            else if (keycode == Keys.PAGE_DOWN) {
                float newScrollX = MathUtils.clamp(pagedScrollPane.getScrollX() + pageWidth, 0, pagedScrollPane.getMaxX());
                pagedScrollPane.setScrollX(newScrollX);
                pagedScrollPane.setLastScrollX(newScrollX);
            }
            else if (keycode == Keys.HOME) {
                pagedScrollPane.setScrollX(0.0f);
                pagedScrollPane.setLastScrollX(0.0f);
            }
            else if (keycode == Keys.END) {
                pagedScrollPane.setScrollX(pagedScrollPane.getMaxX());
                pagedScrollPane.setLastScrollX(pagedScrollPane.getMaxX());
            }
            else if (keycode == Keys.UP) {
                pagedScrollPane.prevProgramRow();
            }
            else if (keycode == Keys.LEFT) {
                if (pagedScrollPane.getCurrentSelectionIndex() == 0) {
                    float newScrollX = MathUtils.clamp(pagedScrollPane.getScrollX() - pageWidth, 0, pagedScrollPane.getMaxX());
                    pagedScrollPane.setScrollX(newScrollX);
                    pagedScrollPane.setLastScrollX(newScrollX);
                } else {
                    pagedScrollPane.prevProgram();
                }
            }
            else if (keycode == Keys.RIGHT) {
                if (pagedScrollPane.getCurrentPageNumber() == 0) {
                    float newScrollX = MathUtils.clamp(pagedScrollPane.getScrollX() + pageWidth, 0, pagedScrollPane.getMaxX());
                    pagedScrollPane.updateSelectionHighlight(0, true);
                    pagedScrollPane.setScrollX(newScrollX);
                    pagedScrollPane.setLastScrollX(newScrollX);
                } else {
                    pagedScrollPane.nextProgram();
                }
            }
            else if (keycode == Keys.DOWN) {
                pagedScrollPane.nextProgramRow();
            }
            else if ((keycode == Keys.SPACE) || (keycode == Keys.ENTER)) {
                Button button = pagedScrollPane.getCurrentlySelectedProgramButton();
                if (button != null) {
                    String appName = button.getName();
                    if ((appName != null) && (!appName.equals(""))) {
                        final AppConfigItem appConfigItem = (appName.equals("BASIC")?
                                basicAppConfigItem : appConfigMap.get(appName));
                        if (appConfigItem != null) {
                            processProgramSelection(appConfigItem);
                        }
                    }
                }
            }
            else if ((keycode >= Keys.A) && (keycode <= Keys.Z)) {
                // Shortcut keys for accessing games that start with each letter.
                // Keys.A is 29, Keys.Z is 54. ASCII is A=65, Z=90. So we add 36.
                int gameIndex = getIndexOfFirstProgramStartingWithChar((char)(keycode + 36));
                if (gameIndex > -1) {
                    // Add one to allow for the "BASIC" icon in the first slot.
                    showProgramPage(gameIndex + 1, false);
                }
            }
        }
        return false;
    }

    /**
     * Generates an identicon for the given text, of the given size. An identicon is
     * an icon image that will always look the same for the same text but is highly
     * likely to look different from the icons generates for all other text strings.
     * Its therefore an
     * 
     * @param text
     * @param iconWidth
     * @param iconHeight
     * 
     * @return The generated icon image for the given text.
     */
    public Texture generateIdenticon(String text, int iconWidth, int iconHeight) {
        // Generate a message hash from the text string, then use it as a seed for a
        // random sequence.
        int[] hashRand = new int[20];
        try {
            byte[] textHash = MessageDigest.getInstance("SHA1").digest(text.getBytes("UTF-8"));
            long seed = ((((int) textHash[0] & 0xFF) << 0) | (((int) textHash[1] & 0xFF) << 8)
                    | (((int) textHash[2] & 0xFF) << 16) | (((int) textHash[3] & 0xFF) << 24)
                    | (((int) textHash[4] & 0xFF) << 32) | (((int) textHash[5] & 0xFF) << 40)
                    | (((int) textHash[6] & 0xFF) << 48) | (((int) textHash[7] & 0xFF) << 56));
            Random random = new Random(seed);
            for (int i = 0; i < hashRand.length; i++) {
                hashRand[i] = random.nextInt();
            }
        } catch (Exception e) {
            /* Ignore. We know it will never happen. */}

        // Create Pixmap and Texture of required size, and define
        Pixmap pixmap = new Pixmap(iconWidth, iconHeight, Pixmap.Format.RGBA8888);
        Texture texture = new Texture(pixmap, Pixmap.Format.RGBA8888, false);
        Color background = new Color(0, 0, 0, 0);
        Color foreground = null;
        if ((text == null) || (text.equals(""))) {
            foreground = new Color(1f, 1f, 1f, 0.3f);
        } else {
            foreground = new Color(((int) hashRand[0] & 0xFF) / 255f, ((int) hashRand[1] & 0xFF) / 255f,
                    ((int) hashRand[2] & 0xFF) / 255f, 0.9f);
        }

        int blockDensityX = 17; // 39 x 37 is pretty good. 17 x 17 is okay as well.
        int blockDensityY = 17;
        int blockWidth = iconWidth / blockDensityX;
        int blockHeight = iconHeight / blockDensityY;
        int blockMidX = ((blockDensityX + 1) / 2);
        int blockMidY = ((blockDensityY + 1) / 2);

        for (int x = 0; x < blockDensityX; x++) {
            int i = x < blockMidX ? x : (blockDensityX - 1) - x;
            for (int y = 0; y < blockDensityY; y++) {
                Color pixelColor;
                int j = y < blockMidY ? y : (blockDensityY - 1) - y;
                if ((hashRand[i] >> j & 1) == 1) {
                    pixelColor = foreground;
                } else {
                    pixelColor = background;
                }
                pixmap.setColor(pixelColor);
                pixmap.fillRectangle(x * blockWidth, y * blockHeight, blockWidth, blockHeight);
            }
        }

        texture.draw(pixmap, 0, 0);
        return texture;
    }

    /**
     * Draws and returns the icon to be used for game slots when we don't have
     * a proper screenshot icon for the identified game.
     * 
     * @param iconWidth
     * @param iconHeight
     * 
     * @return The Texture for the drawn icon.
     */
    public Texture drawEmptyIcon(int iconWidth, int iconHeight) {
        Pixmap pixmap = new Pixmap(iconWidth, iconHeight, Pixmap.Format.RGBA8888);
        Texture texture = new Texture(pixmap, Pixmap.Format.RGBA8888, false);
        pixmap.setColor(1.0f, 1.0f, 1.0f, 0.10f);
        pixmap.fill();
        texture.draw(pixmap, 0, 0);
        return texture;
    }
    
    private static final int ICON_IMAGE_WIDTH = 240;
    private static final int ICON_IMAGE_HEIGHT = 224;
    private static final int ICON_LABEL_HEIGHT = 90;
    private static final int PAGINATION_HEIGHT = 60;

    /**
     * Creates a button to represent the given AppConfigItem.
     * 
     * @param appConfigItem
     * 
     * @return
     */
    private Button buildAppButton(AppConfigItem appConfigItem) {
        return buildButton(
                appConfigItem.getName(), 
                appConfigItem.getDisplayName() != null? appConfigItem.getDisplayName() : "", 
                appConfigItem.getIconPath() != null? appConfigItem.getIconPath() : "", 
                ICON_IMAGE_WIDTH, ICON_IMAGE_HEIGHT,
                appConfigItem.getFileType(),
                appConfigItem.getGameId());
    }
    
    /**
     * Creates a button using the given parameters.
     * 
     * @param name 
     * @param displayName 
     * @param iconPath 
     * @param type 
     * @param gameId
     * 
     * @return The created Button.
     */
    private Button buildButton(String name, String labelText, String iconPath, int width, int height, String type, String gameId) {
        Button button = new Button(skin);
        ButtonStyle style = button.getStyle();
        style.up = style.down = null;

        // An app button can contain an optional icon.
        Image icon = null;
        
        Texture iconTexture = buttonTextureMap.get(iconPath);
        if (iconTexture == null) {
            if (!iconPath.isEmpty()) {
                try {
                    // See if there is screenshot icon in the assets folder.
                    Pixmap iconPixmap = new Pixmap(Gdx.files.internal(iconPath));
                    
                    // If there is, then it's expected to be 320x200, so we scale it to right aspect ratio.
                    Pixmap iconStretchedPixmap = new Pixmap(width, height, iconPixmap.getFormat());
                    iconStretchedPixmap.drawPixmap(iconPixmap,
                            0, 0, iconPixmap.getWidth(), iconPixmap.getHeight(),
                            0, 0, iconStretchedPixmap.getWidth(), iconStretchedPixmap.getHeight()
                    );
                    iconTexture = new Texture(iconStretchedPixmap);
                    iconPixmap.dispose();
                    iconStretchedPixmap.dispose();
                    
                    iconTexture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
                    buttonTextureMap.put(iconPath, iconTexture);
                    icon = new Image(iconTexture);
                    icon.setAlign(Align.center);
                } catch (Exception e) {
                    icon = new Image(drawEmptyIcon(width, height));
                }
            } else {
                icon = new Image(drawEmptyIcon(width, height));
            }
        } else {
            icon = new Image(iconTexture);
            icon.setAlign(Align.center);
        }
            
        if (icon != null) {
            Container<Image> iconContainer = new Container<Image>();
            iconContainer.setActor(icon);
            iconContainer.align(Align.center);
            button.stack(new Image(skin.getDrawable("top")), iconContainer).width(width)
                    .height(height);
        }
        
        if (labelText != null) {
            button.row();
            Label label = null;
            if (labelText.trim().isEmpty()) {
                if ((gameId != null) && "ADD_GAME".equals(gameId)) {
                    label = new Label("Add Game", skin);
                } else {
                    label = new Label("", skin);
                }
                label.setColor(new Color(1f, 1f, 1f, 0.6f));
            } else {
                label = new Label(labelText, skin);
            }
            label.setFontScale(2f);
            label.setAlignment(Align.top);
            label.setWrap(false);
            button.add(label).width(150).height(90).padTop(10);
        }
        
        button.setName(name);
        button.addListener(appClickListener);
        return button;
    }

    /**
     * If there is a program in the AppConfigItem Map that has the given URI, then
     * that is returned; otherwise returns false.
     * 
     * @param programUri The URI of the program to get the AppConfigItem for, if it
     *                   exists.
     * 
     * @return
     */
    public AppConfigItem getAppConfigItemByProgramUri(String programUri) {
        for (AppConfigItem appConfigItem : appConfigMap.values()) {
            String uri = joric.getJOricRunner().slugify(appConfigItem.getName());
            if (uri.equalsIgnoreCase(programUri)) {
                return appConfigItem;
            }
        }
        return null;
    }

    /**
     * Converts the given Map of AppConfigItems to an AppConfig instance.
     * 
     * @param appConfigMap The Map of AppConfigItems to convert.
     * 
     * @return The AppConfig.
     */
    private AppConfig convertAppConfigItemMapToAppConfig(Map<String, AppConfigItem> appConfigMap) {
        AppConfig appConfig = new AppConfig();
        for (String appName : appConfigMap.keySet()) {
            AppConfigItem item = appConfigMap.get(appName);
            if ((item.getFileType() == null) || (item.getFileType().trim().isEmpty())) {
                // BASIC start icon. Add these at the start of the JSON file.
                appConfig.getApps().add(item);
            }
        }
        for (String appName : appConfigMap.keySet()) {
            AppConfigItem item = appConfigMap.get(appName);
            if ((item.getFileType() != null) && (!item.getFileType().trim().isEmpty())) {
                // Tape or Disk file.
                appConfig.getApps().add(item);
            }
        }
        return appConfig;
    }

    /**
     * Updates the application buttons on the home screen Stages to reflect the
     * current AppConfigItem Map.
     */
    public void updateHomeScreenButtonStages() {
        AppConfig appConfig = convertAppConfigItemMapToAppConfig(appConfigMap);
        portraitStage.clear();
        landscapeStage.clear();
        addAppButtonsToStage(portraitStage, portraitPaginationWidget, appConfig);
        addAppButtonsToStage(landscapeStage, landscapePaginationWidget, appConfig);
    }

    /**
     * Handle clicking an app button. This will start the Machine and run the
     * selected app.
     */
    public ClickListener appClickListener = new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
            Actor actor = event.getListenerActor();
            String appName = actor.getName();
            if ((appName != null) && (!appName.equals(""))) {
                final AppConfigItem appConfigItem = (appName.equals("BASIC")?
                        basicAppConfigItem : appConfigMap.get(appName));
                if (appConfigItem != null) {
                    processProgramSelection(appConfigItem);
                } else if (appName.equals("INFO")) {
                    showAboutJOricDialog();
                }
            } else {
                // Add miscellaneous program option (i.e. the plus icon).
                // TODO: importProgram(null);
            }
        }
    };

    public void processProgramSelection(AppConfigItem appConfigItem) {
        lastProgramLaunched = appConfigItem;
        MachineScreen machineScreen = joric.getMachineScreen();
        machineScreen.initMachine(appConfigItem, true);
        joric.setScreen(machineScreen);
    }
    
    private void showAboutJOricDialog() {
        dialogHandler.showAboutDialog(
                "JOric v1.0.0\n\n" + 
                "To start, simply swipe or click to the right.\n\n" + 
                "Or use the ?url= request parameter to point directly to a .dsk or .tap file.\n\n" + 
                "Most games are available on www.oric.org.\n\n" + 
                "Source code: https://github.com/lanceewing/joric\n\n",
                new TextInputResponseHandler() {
                    @Override
                    public void inputTextResult(boolean success, String button) {
                        if (success && !button.equals("OK")) {
                            // State management.
                            switch (button) {
                                case "EXPORT":
                                    exportState();
                                    break;
                                case "IMPORT":
                                    importState();
                                    break;
                                case "CLEAR":
                                    clearState();
                                    break;
                                case "RESET":
                                    resetState();
                                    break;
                                default:
                                    // Nothing to do.
                                    break;
                            }
                        }
                    }
                });
    }
    
    private void exportState() {
        
    }
    
    private void importState() {
        
    }
    
    private void clearState() {
        
    }
    
    private void resetState() {
        
    }
    
    private int getIndexOfFirstProgramStartingWithChar(char letter) {
        int programIndex = 0;
        
        for (AppConfigItem appConfigItem : appConfigMap.values()) {
            String programName = appConfigItem.getName();
            if (programName.toUpperCase().startsWith("" + letter)) {
                return programIndex;
            }
            programIndex++;
        }
        
        return -1;
    }
    
    private int getProgramIndex(AppConfigItem program) {
        int programIndex = 0;
        
        for (AppConfigItem appConfigItem : appConfigMap.values()) {
            programIndex++;
            if (appConfigItem.getName().equals(program.getName())) {
                return programIndex;
            }
        }
        
        // NOTE: BASIC will return 0, as it isn't in the Map.
        return 0;
    }
    
    private void showProgramPage(AppConfigItem appConfigItem, boolean skipScroll) {
        showProgramPage(getProgramIndex(appConfigItem), skipScroll);
    }
    
    private void showProgramPage(int programIndex, boolean skipScroll) {
        // Apply scroll X without animating, i.e. move immediately to the page.
        Stage currentStage = viewportManager.isPortrait()? portraitStage : landscapeStage;
        PagedScrollPane pagedScrollPane = (PagedScrollPane)
                ((Table)currentStage.getActors().get(0)).getChild(0);
        currentStage.act(0f);
        
        // Work out how far to move from far left to get to program's page.
        int programsPerPage = pagedScrollPane.getProgramsPerPage();
        float pageWidth = viewportManager.isPortrait()? 1130.0f : 1970.0f;
        float newScrollX = pageWidth * (programIndex / programsPerPage) + pageWidth;
        
        // Set program highlight to the program with the specified index.
        pagedScrollPane.updateSelection(programIndex, false);
        
        pagedScrollPane.setScrollX(newScrollX);
        pagedScrollPane.setLastScrollX(newScrollX);
        if (skipScroll) {
            pagedScrollPane.updateVisualScroll();
        }
    }
    
    public PagedScrollPane getPagedScrollPane() {
        Stage currentStage = viewportManager.isPortrait()? portraitStage : landscapeStage;
        if (currentStage.getActors().notEmpty()) {
            return (PagedScrollPane)((Table)currentStage.getActors().get(0)).getChild(0);
        } else {
            return null;
        }
    }
    
    public boolean isMobile() {
        return joric.getJOricRunner().isMobile();
    }
    
    public Map<String, AppConfigItem> getAppConfigMap() {
        return appConfigMap;
    }
}
