package emu.joric.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Manages the Viewports that are used for rendering the UI components.
 * 
 * @author Lance Ewing
 */
public class ViewportManager {

    private static final int PORTRAIT_WORLD_WIDTH = 1080;
    private static final int LANDSCAPE_WORLD_WIDTH = 1920;

    /**
     * Viewport to use when in portrait mode.
     */
    private Viewport portraitViewport;

    /**
     * Camera to use when in portrait mode.
     */
    private Camera portraitCamera;

    /**
     * Viewport to use when in landscape mode.
     */
    private Viewport landscapeViewport;

    /**
     * Camera to use when in landscape mode.
     */
    private Camera landscapeCamera;

    /**
     * Whether the current Viewport is the portrait Viewport or not.
     */
    private boolean portrait;

    /**
     * Singleton instance of ViewportManager.
     */
    private static final ViewportManager instance = new ViewportManager();

    /**
     * Constructor for ViewportManager.
     */
    private ViewportManager() {
        portraitCamera = new OrthographicCamera();
        portraitViewport = new ExtendViewport(PORTRAIT_WORLD_WIDTH, 1, portraitCamera);
        landscapeCamera = new OrthographicCamera();
        landscapeViewport = new ExtendViewport(LANDSCAPE_WORLD_WIDTH, 1, landscapeCamera);
        portrait = true;
    }

    /**
     * Returns the singleton instance of ViewportManager.
     * 
     * @return The singleton instance of ViewportManager.
     */
    public static ViewportManager getInstance() {
        return instance;
    }

    /**
     * Invoked when the MachineScreen is resized so that the UI viewport and camera
     * can be updated and switched for a possible change in orientation.
     * 
     * @param width  The new screen width.
     * @param height The new screen height.
     */
    public void update(int width, int height) {
        // 1.13 when icons are no longer overlapping
        // 1.00 square
        // 0.80 where keyboard top matches screen base
        // TODO: This is from AGILE. Needs tweaking for Oric.
        portrait = (height > (width / 0.80f));
        //portrait = (height > width);
        getCurrentViewport().update(width, height, true);
    }

    /**
     * Forces an update on the current Viewport using the current screen's size.
     */
    public void update() {
        update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    /**
     * Returns true if the orientation is currently portrait; otherwise false.
     * 
     * @return true if the orientation is currently portrait; otherwise false.
     */
    public boolean isPortrait() {
        return portrait;
    }
    
    /**
     * Returns true if the orientation is currently landscape; otherwise false.
     * 
     * @return true if the orientation is currently landscape; otherwise false.
     */
    public boolean isLandscape() {
        return !portrait;
    }
    
    /**
     * Tests where the Oric screen exactly fits the width of the viewport.
     * 
     * @return
     */
    public boolean doesScreenFitWidth() {
        return !(getHeight() > (getWidth() / 1.25f));
    }

    /**
     * Calculates and returns the width of the Oric screen using the current 
     * viewport height.
     * 
     * @return
     */
    public float getOricScreenWidth() {
        return (getHeight() * 1.25f);
    }
    
    /**
     * Calculates and returns the width of the padding either side of the Oric
     * screen, when in landscape mode.
     * 
     * @return The width of the padding either side of the Oric screen.
     */
    public float getSidePaddingWidth() {
        float sidePaddingWidth = 0;
        if (doesScreenFitWidth()) {
            sidePaddingWidth = ((getWidth() - getOricScreenWidth()) / 2);
        }
        return sidePaddingWidth;
    }
    
    /**
     * Gets the Y value of the base of the Oric screen.
     * 
     * @return The Y value of the base of the Oric screen.
     */
    public int getOricScreenBase() {
        return (int)(getHeight() - (getWidth() / 1.25));
    }
    
    /**
     * Gets the current Camera to use when rendering UI components.
     * 
     * @return The current Camera to use when rendering UI components.
     */
    public Camera getCurrentCamera() {
        return (portrait ? portraitCamera : landscapeCamera);
    }

    /**
     * Gets the current Viewport to use when rendering UI components.
     * 
     * @return The current Viewport to use when rendering UI components.
     */
    public Viewport getCurrentViewport() {
        return (portrait ? portraitViewport : landscapeViewport);
    }

    /**
     * Gets the Viewport used for the portrait orientation.
     * 
     * @return The Viewport used for the portrait orientation.
     */
    public Viewport getPortraitViewport() {
        return portraitViewport;
    }

    /**
     * Gets the Viewport used for the landscape orientation.
     * 
     * @return The Viewport used for the landscape orientation.
     */
    public Viewport getLandscapeViewport() {
        return landscapeViewport;
    }

    /**
     * Gets the width of the current Viewport.
     * 
     * @return The width of the current Viewport.
     */
    public float getWidth() {
        return getCurrentViewport().getWorldWidth();
    }

    /**
     * Gets the height of the current Viewport.
     * 
     * @return The height of the current Viewport.
     */
    public float getHeight() {
        return getCurrentViewport().getWorldHeight();
    }

    /**
     * Unprojects the given screen coordinates to world coordinates using the
     * current Viewport.
     * 
     * @param screenX The X part of the screen position to unproject.
     * @param screenY The Y part of the screen position to unproject.
     * 
     * @return The unprojected screen coordinates in world coordinates.
     */
    public Vector2 unproject(int screenX, int screenY) {
        return getCurrentViewport().unproject(new Vector2(screenX, screenY));
    }
}