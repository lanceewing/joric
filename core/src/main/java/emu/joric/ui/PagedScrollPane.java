package emu.joric.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.WindowedMean;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Table.Debug;
import com.badlogic.gdx.utils.Array;

import emu.joric.config.AppConfig;
import emu.joric.config.AppConfigItem;

public class PagedScrollPane extends ScrollPane {

    private boolean wasPanDragFling = false;

    private float lastScrollX = 0;

    private WindowedMean scrollXDeltaMean = new WindowedMean(5);

    private Table content;

    private int currentSelectionIndex;
    
    private AppConfig appConfig;
    
    public PagedScrollPane() {
        super(null);
        setup();
    }

    public PagedScrollPane(Skin skin) {
        super(null, skin);
        setup();
    }

    public PagedScrollPane(Skin skin, String styleName) {
        super(null, skin, styleName);
        setup();
    }

    public PagedScrollPane(Actor widget, ScrollPaneStyle style) {
        super(null, style);
        setup();
    }

    public void setAppConfig(AppConfig appConfig) {
        this.appConfig = appConfig;
    }
    
    private void setup() {
        content = new Table();
        content.defaults().space(50);
        super.setWidget(content);
        Button.debugCellColor = new Color(1, 1, 1, 0.5f);
    }

    public void addPages(Actor... pages) {
        for (Actor page : pages) {
            content.add(page).expandY().fillY();
        }
    }

    public void addPage(Actor page) {
        content.add(page).expandY().fillY();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (wasPanDragFling && !isPanning() && !isDragging() && !isFlinging()) {
            wasPanDragFling = false;
            scrollToPage();
            scrollXDeltaMean.clear();

        } else {
            if (isPanning() || isDragging() || isFlinging()) {
                wasPanDragFling = true;
                scrollXDeltaMean.addValue(getScrollX() - lastScrollX);
                lastScrollX = getScrollX();
            }
        }
    }

    @Override
    public void setWidth(float width) {
        super.setWidth(width);
        if (content != null) {
            for (Cell cell : content.getCells()) {
                cell.width(width);
            }
            content.invalidate();
        }
    }

    public void setPageSpacing(float pageSpacing) {
        if (content != null) {
            content.defaults().space(pageSpacing);
            for (Cell cell : content.getCells()) {
                cell.space(pageSpacing);
            }
            content.invalidate();
        }
    }

    public void setLastScrollX(float lastScrollX) {
        this.lastScrollX = lastScrollX;
    }
    
    public void reset() {
        this.scrollXDeltaMean.clear();
        this.lastScrollX = 0;
        this.wasPanDragFling = false;
        setScrollX(0);
    }
    
    /**
     * Gets the number of programs on each page.
     * 
     * @return The number of programs on each page.
     */
    public int getProgramsPerPage() {
        int gamesPerPage = 0;
        if (content.getChildren().notEmpty()) {
            // Checks the second page, so as to ignore title page.
            Table secondPage = (Table)content.getChild(1);
            gamesPerPage = secondPage.getColumns() * secondPage.getRows();
        }
        return gamesPerPage;
    }
    
    /**
     * Gets the number of columns in each page.
     * 
     * @return The number of columns in each page.
     */
    public int getNumOfColumns() {
        int numOfColumns = 0;
        if (content.getChildren().notEmpty()) {
            Table secondPage = (Table)content.getChild(1);
            numOfColumns = secondPage.getColumns();
        }
        return numOfColumns;
    }
    
    /**
     * Gets the number of rows in each page.
     * 
     * @return The number of rows in each page.
     */
    public int getNumOfRows() {
        int numOfRows = 0;
        if (content.getChildren().notEmpty()) {
            Table secondPage = (Table)content.getChild(1);
            numOfRows = secondPage.getRows();
        }
        return numOfRows;
    }
    
    /**
     * Gets the total number of pages on this paged scroll pane.
     * 
     * @return The total number of pages.
     */
    public int getNumOfPages() {
        return content.getChildren().size;
    }
    
    /**
     * Gets the page number of the page currently being seen in this paged scroll pane.
     * 
     * @return The page number of the page currently being seen.
     */
    public int getCurrentPageNumber() {
        int pageNumber = 0;
        if (content.getChildren().notEmpty()) {
            int pageWidth = (int)(content.getChild(0).getWidth() + 50);
            pageNumber = Math.round(getScrollX() / pageWidth);
        }
        return pageNumber;
    }
    
    /**
     * Gets the Table representing the given page.
     * 
     * @param pageNum The number of the page to get the Table for.
     * 
     * @return The Table representing the specified page.
     */
    public Table getPage(int pageNum) {
        return ((Table)content.getChild(pageNum));
    }
    
    /**
     * Gets the total number of programs on this paged scroll pane.
     * 
     * @return The total number of programs.
     */
    public int getNumOfPrograms() {
        int numOfPrograms = 0;
        for (AppConfigItem appConfigItem : appConfig.getApps()) {
            if ((appConfigItem.getIconPath() != null) && (!appConfigItem.getIconPath().equals(""))) {
                numOfPrograms++;
            }
        }
        return numOfPrograms;
    }
    
    /**
     * Gets the Button at the given program index.
     * 
     * @param programIndex The index to get the program Button for.
     * 
     * @return The program Button at the given index.
     */
    public Button getProgramButton(int programIndex) {
        int programsPerPage = getProgramsPerPage();
        int page = programIndex / programsPerPage;
        int programOnPageIndex = programIndex - (page * programsPerPage);
        Table pageTable = (Table)content.getChild(page + 1);
        
        // First page is the title page, so we protect against that.
        if (pageTable.getChildren().size > programOnPageIndex) {
            if (pageTable.getChild(programOnPageIndex) instanceof Button) {
                return (Button)pageTable.getChild(programOnPageIndex);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }
    
    /**
     * Gets the currently selected program index.
     * 
     * @return The currently selected program index.
     */
    public int getCurrentSelectionIndex() {
        return currentSelectionIndex;
    }
    
    /**
     * Gets the program Button for the currently selected program.
     * 
     * @return The program Button for the currently selected program.
     */
    public Button getCurrentlySelectedProgramButton() {
        return getProgramButton(currentSelectionIndex);
    }
    
    /**
     * Attempts to update the selected program to the one identified by the given
     * program index, i.e. index into the list of program icons within this paged
     * scroll pane.
     * 
     * @param newSelectionIndex The index of the program icon to select.
     */
    public void updateSelection(int newSelectionIndex) {
        updateSelection(newSelectionIndex, true);
    }
    
    /**
     * Attempts to update the selected program to the one identified by the given
     * program index, i.e. index into the list of program icons within this paged
     * scroll pane.
     * 
     * @param newSelectionIndex The index of the program icon to select.
     * @param showPage Whether to scroll to show the page the program is on, or not.
     */
    public void updateSelection(int newSelectionIndex, boolean showPage) {
        int numberOfPrograms = getNumOfPrograms();
        
        // Bounds checks.
        if (newSelectionIndex < 0) {
            newSelectionIndex = 0;
        }
        if (newSelectionIndex > numberOfPrograms) {
            newSelectionIndex = numberOfPrograms;
        }
        
        if (newSelectionIndex != currentSelectionIndex) {
            // Remove highlight from previously selected program.
            updateSelectionHighlight(currentSelectionIndex, false);
            
            // Add highlight to newly selected program.
            updateSelectionHighlight(newSelectionIndex, true);
            currentSelectionIndex = newSelectionIndex;
            
            if (showPage) {
                // Move to the page that the program is on, if required. 
                showProgramPage(currentSelectionIndex);
            }
        } else {
            // In some scenarios, the currently selected one isn't highlighted, 
            // so we always apply the highlight, which may not change anything.
            updateSelectionHighlight(currentSelectionIndex, true);
        }
    }
    
    /**
     * Updates the highlight status of the program icon identified by the
     * given index. It uses the libGDX built in "debug" highlighted feature
     * as a simple way to highlight.
     * 
     * @param programIndex The index of the program to update the highlight for.
     * @param highlight Whether or not to highlight the icon.
     */
    public void updateSelectionHighlight(int programIndex, boolean highlight) {
        Button programButton = getProgramButton(programIndex);
        if (programButton != null) {
            programButton.debug(highlight? Debug.cell : Debug.none);
        }
    }
    
    /**
     * Navigates to the next program.
     */
    public void nextProgram() {
        updateSelection(currentSelectionIndex + 1);
    }
    
    /**
     * Navigates to the previous program.
     */
    public void prevProgram() {
        updateSelection(currentSelectionIndex - 1);
    }
    
    /**
     * Navigates to the next row of programs.
     */
    public void nextProgramRow() {
        updateSelection(currentSelectionIndex + getNumOfColumns());
    }
    
    /**
     * Navigates to the previous row of programs.
     */
    public void prevProgramRow() {
        updateSelection(currentSelectionIndex - getNumOfColumns());
    }
    
    /**
     * This method is used by the key navigation, i.e. when it has calculated a specific
     * program index to move to. The navigation keys are used to navigation +/- one
     * item at a time, or page up/down at a time, then after the new index has been
     * calculated, the PagedScrollPane moves to the new page, if required.
     * 
     * @param programIndex The index of the program to move to.
     */
    private void showProgramPage(int programIndex) {
        // Work out how far to move from far left to get to program's page.
        int programsPerPage = getProgramsPerPage();
        float pageWidth = ViewportManager.getInstance().isPortrait()? 1130.0f : 1970.0f;
        float newScrollX = pageWidth * (programIndex / programsPerPage) + pageWidth;
        
        setScrollX(newScrollX);
        setLastScrollX(newScrollX);
    }
    
    /**
     * This method is used by the fling mechanism and not by key navigation.
     */
    private void scrollToPage() {
        final float width = getWidth();
        final float scrollX = getScrollX();
        final float maxX = getMaxX();

        if (scrollX >= maxX || scrollX <= 0)
            return;

        Array<Actor> pages = content.getChildren();
        float pageX = 0;
        float pageWidth = 0;

        float scrollXDir = scrollXDeltaMean.getMean();
        if (scrollXDir == 0) {
            scrollXDir = scrollXDeltaMean.getLatest();
        }

        for (Actor a : pages) {
            pageX = a.getX();
            pageWidth = a.getWidth();
            if (scrollXDir > 0) {
                if (scrollX < (pageX + pageWidth * 0.1)) {
                    break;
                }
            } else if (scrollXDir < 0) {
                if (scrollX < (pageX + pageWidth * 0.9)) {
                    break;
                }
            } else {
                if (scrollX < (pageX + pageWidth * 0.5)) {
                    break;
                }
            }
        }

        float newScrollX = MathUtils.clamp(pageX - (width - pageWidth) / 2, 0, maxX);
        setScrollX(newScrollX);
        this.lastScrollX = newScrollX;
    }
}
