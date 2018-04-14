package emu.joric.video;

import emu.joric.BaseChip;
import emu.joric.MachineType;
import emu.joric.snap.Snapshot;

/**
 * This class emulates the HCS10017 ULA chip.
 * 
 * @author Lance Ewing
 */
public class Ula extends BaseChip {

  private static final int VERTICAL_BLANK_LINES = 9;
  private static final int HORIZONTAL_BLANK_CYCLES = 12;
  private static final int WINDOW_CYCLES = 40;
  private static final int WINDOW_LINES = 224;
  private static final int HIRES_LINES = 200;

  private static final short palette[] = {     //RGB565
    (short)0x0000,         // BLACK  0000000000000000
    (short)0xF800,         // RED    1111100000000000
    (short)0x07E0,         // GREEN  0000011111100000
    (short)0xFFE0,         // YELLOW 1111111111100000
    (short)0x001F,         // BLUE   0000000000011111
    (short)0xF81F,         // PURPLE 1111100000011111
    (short)0x07FF,         // CYAN   0000011111111111
    (short)0xFFFF          // WHITE  1111111111111111
  }; 
  
  /**
   * An array of two Frames, one being the one that the ULA is currently writing to,
   * the other being the last one that was completed and ready to blit.
   */
  private Frame[] frames;
  
  /**
   * The index of the active frame within the frames. This will toggle between 0 and 1.
   */
  private int activeFrame;
  
  /**
   * Horizontal counter (unit is 1 MHz cycles)
   */
  private int horizontalCounter;

  /**
   * Vertical counter
   */
  private int verticalCounter;
  
  /**
   * Pixel counter. Current offset into TV frame array.
   */
  private int pixelCounter;
  
  private int windowLine;
  private boolean palFreq;
  private int charline;
  private short ink;
  private short paper;
  private int totalLines;
  private boolean blink;
  private boolean doubleHeight;
  private int blinkMask;
  private int charsetBase;
  private int charset;
  private int charsetAddr;
  private boolean hiresMode;
  private boolean textMode;
  private int lineAddr;
  private int frameCount;
  
  /**
   * Constructor for Ula.
   * 
   * @param machineType
   * @param snapshot
   */
  public Ula(MachineType machineType, Snapshot snapshot) {
    frames = new Frame[2];
    frames[0] = new Frame();
    frames[0].framePixels = new short[(machineType.getTotalScreenWidth() * machineType.getTotalScreenHeight())];
    frames[0].ready = false;
    frames[1] = new Frame();
    frames[1].framePixels = new short[(machineType.getTotalScreenWidth() * machineType.getTotalScreenHeight())];
    frames[1].ready = false;
    
    hiresMode = false;
    palFreq = true;
    
    newFrame();
  }
  
  public boolean emulateSkipCycle() {
    
    
    return false;
  }
  
  public boolean emulateCycle() {
    boolean frameRenderComplete = false;
    short[] framePixels = frames[activeFrame].framePixels;
    
    // PAL TV is 64us per line, which is 64 1 MHz cycles.
    // That is 64 columns, 6 pixels per column, with a 6 MHz pixel clock.
    // PAL TV has 312 lines. Oric ULA supports 60 Hz as well, which I assume is for PAL 60 rather than NTSC
    // 64 * 312 * 50 = 998400
    // 64 * 260 * 60 = 998400
    // 6502 is running at 1 MHz. We also emulate the ULA by emulating 1 MHz cycle on each execute.
    // Given the 1 us interval between emulateCycle invocations, to conform roughly to the PAL standard we have:
    // * Horizontal Blanking starts at cycle 0 of the line and will last 12 cycles
    // * Horizontal Sync starts at cycle 1 and will last 5 cycles
    // * Colour Burst starts at cycle 7 and will last 4 cycles
    // * Visual picture begins at cycle 12 and will last 52 cycles (although only 40 columns of pixels)
    // * Vertical Blanking starts on line 0 and will last 9 lines
    // * Vertical Sync starts on line 3 and will last 3 lines 
    
    if (verticalCounter >= VERTICAL_BLANK_LINES) {
      if (verticalCounter < (VERTICAL_BLANK_LINES + WINDOW_LINES)) {
        if (horizontalCounter >= HORIZONTAL_BLANK_CYCLES) {
          if (horizontalCounter < (HORIZONTAL_BLANK_CYCLES + WINDOW_CYCLES)) {
            
            int screenCode;
            int cellData = screenCode = mem[lineAddr + (horizontalCounter - HORIZONTAL_BLANK_CYCLES)];
            if (textMode) {
              cellData = mem[charsetAddr + ((screenCode & 0x7F) << 3) + charline];
            }
            
            if ((screenCode & 0x60) == 0) {
              cellData = 0;
              
              switch (screenCode & 0x18) {
                case 0x00: {
                  ink = palette[screenCode & 7];
                  break;
                }
                case 0x08: {
                  charset = (screenCode & 1);
                  charsetAddr = charsetBase + (charset << 10);
                  doubleHeight = ((screenCode & 2) != 0);
                  charline = (doubleHeight? ((windowLine & 15) >> 1) : (windowLine & 7));
                  blink = ((screenCode & 4) != 0);
                  blinkMask = blink && (frameCount & 16) != 0 ? 0 : 63;
                  break;
                }
                case 0x10: {
                  paper = palette[screenCode & 7];
                  break;
                }
                case 0x18: {
                  palFreq = (screenCode & 2) != 0;
                  totalLines = palFreq ? 312 : 264;
                  hiresMode = (screenCode & 4) != 0;
                  charsetBase = hiresMode ? 0x9800 : 0xB400;
                  charsetAddr = charsetBase + (charset << 10);
                  textMode = (!hiresMode || (windowLine >= 200));
                  if (textMode) {
                    lineAddr = 0xBB80 + (windowLine >> 3) * 40;
                  } else {
                    lineAddr = 0xA000 + windowLine * 40;
                  }
                  break;
                }
              }
              
            } else {
              cellData &= blinkMask;
            }
            
            short dotInk;
            short dotPaper;
            if ((screenCode & 0x80) != 0) {
              dotInk = (short)(ink ^ 0xFFFF);
              dotPaper = (short)(paper ^ 0xFFFF);
            } else {
              dotInk = ink;
              dotPaper = paper;
            }
            
            framePixels[pixelCounter++] = ((cellData & 0x20) != 0 ? dotInk : dotPaper);
            framePixels[pixelCounter++] = ((cellData & 0x10) != 0 ? dotInk : dotPaper);
            framePixels[pixelCounter++] = ((cellData & 0x08) != 0 ? dotInk : dotPaper);
            framePixels[pixelCounter++] = ((cellData & 0x04) != 0 ? dotInk : dotPaper);
            framePixels[pixelCounter++] = ((cellData & 0x02) != 0 ? dotInk : dotPaper);
            framePixels[pixelCounter++] = ((cellData & 0x01) != 0 ? dotInk : dotPaper);
            
          } else {
            // Outside 40 column area, so no pixels output.
          }
        } else {
          // Horizontal blanking is in progress. No pixels are output during this time.
        }
      } else {
        // Outside 224 line area, so no pixels output.
      }
    } else {
      // Vertical blanking is in progress. No pixels are output during this time.
    }
    
    // Increment horizontal counter for this machine cycle.
    horizontalCounter++;
    
    // If end of line is reached, reset horiz counter and increment vert counter.
    if (horizontalCounter == 64) {
      horizontalCounter = 0;
      
      windowLine = ++verticalCounter - VERTICAL_BLANK_LINES;
      
      if (windowLine < 224) {
        charline = (windowLine & 7);
        ink = palette[7];
        paper = palette[0];
        blink = false;
        blinkMask = 63;
        doubleHeight = false;
        charset = 0;
        charsetAddr = charsetBase;
        if (windowLine == HIRES_LINES) {
          textMode = true;
        }
        lineAddr = (textMode? (0xBB80 + (windowLine >> 3) * 40) : (0xA000 + windowLine * 40));
      
      } else if (verticalCounter == totalLines) {
        newFrame();
        
        synchronized(frames) {
          // Mark the current frame as complete.
          frames[activeFrame].ready = true;
          
          // Toggle the active frame.
          activeFrame = ((activeFrame + 1) % 2);
          frames[activeFrame].ready = false;
        }
        
        frameRenderComplete = true;
      }
    }
    
    return frameRenderComplete;
  }
  
  private void newFrame() {
    horizontalCounter = 0;
    verticalCounter = 0;
    pixelCounter = 0;
    windowLine = verticalCounter - VERTICAL_BLANK_LINES;
    charline = 0;
    ink = palette[7];
    paper = palette[0];
    totalLines = (palFreq? 312 : 264);
    blink = false;
    doubleHeight = false;
    blinkMask = 63;
    charsetBase = (hiresMode? 0x9800 : 0xB400);
    charset = 0;
    charsetAddr = charsetBase;
    textMode = !hiresMode;
    lineAddr = (textMode? 0xBB80 : 0xA000);
    frameCount++;
  }
  
  /**
   * Represents the data for one ULA frame.
   */
  class Frame {
    
    /**
     * Holds the pixel data for the TV frame screen.
     */
    short framePixels[];
    
    /**
     * Says whether this frame is ready to be blitted to the GPU.
     */
    boolean ready;
  }
  
  /**
   * Gets the pixels for the current frame from the Oric ULA chip.
   * 
   * @return The pixels for the current frame. Returns null if there isn't one that is ready.
   */
  public short[] getFramePixels() {
    short[] framePixels = null;
    synchronized (frames) {
      Frame nonActiveFrame = frames[((activeFrame + 1) % 2)];
      if (nonActiveFrame.ready) {
        nonActiveFrame.ready = false;
        framePixels = nonActiveFrame.framePixels;
      }
    }
    return framePixels;
  }
  
  public String toString() {
    StringBuilder str = new StringBuilder();
    str.append("ULA: ");
    str.append("frameCount = ");
    str.append(this.frameCount);
    str.append(", horizontalCounter = ");
    str.append(this.horizontalCounter);
    str.append(", verticalCounter = ");
    str.append(this.verticalCounter);
    return str.toString();
  }
}
