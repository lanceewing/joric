package emu.joric;

/**
 * An enum that represents the two types of Oric machine, i.e. PAL and NTSC.
 *  
 * @author Lance Ewing
 */
public enum MachineType {
  
  PAL(240, 224, 240, 224, 0, 0, 50),
  NTSC(260, 261, 204, 252, 6, 10, 60);
  
  private int totalScreenWidth;
  private int totalScreenHeight;
  private int visibleScreenWidth;
  private int visibleScreenHeight;
  private int horizontalOffset;
  private int verticalOffset;
  private int framesPerSecond;
  
  /**
   * Constructor for MachineType.
   * 
   * @param totalScreenWidth
   * @param totalScreenHeight
   * @param visibleScreenWidth
   * @param visibleScreenHeight
   * @param horizontalOffset
   * @param verticalOffset
   * @param framesPerSecond
   */
  MachineType(int totalScreenWidth, int totalScreenHeight, int visibleScreenWidth, int visibleScreenHeight, 
      int horizontalOffset, int verticalOffset, int framesPerSecond) {
    this.totalScreenWidth = totalScreenWidth;
    this.totalScreenHeight = totalScreenHeight;
    this.visibleScreenWidth = visibleScreenWidth;
    this.visibleScreenHeight = visibleScreenHeight;
    this.horizontalOffset = horizontalOffset;
    this.verticalOffset = verticalOffset;
    this.framesPerSecond = framesPerSecond;
  }

  /**
   * @return the totalScreenWidth
   */
  public int getTotalScreenWidth() {
    return totalScreenWidth;
  }

  /**
   * @return the totalScreenHeight
   */
  public int getTotalScreenHeight() {
    return totalScreenHeight;
  }

  /**
   * @return the visibleScreenWidth
   */
  public int getVisibleScreenWidth() {
    return visibleScreenWidth;
  }

  /**
   * @return the visibleScreenHeight
   */
  public int getVisibleScreenHeight() {
    return visibleScreenHeight;
  }

  /**
   * @return the horizontalOffset
   */
  public int getHorizontalOffset() {
    return horizontalOffset;
  }

  /**
   * @return the verticalOffset
   */
  public int getVerticalOffset() {
    return verticalOffset;
  }
  
  /**
   * @return the framesPerSecond
   */
  public int getFramesPerSecond() {
    return framesPerSecond;
  }
}
