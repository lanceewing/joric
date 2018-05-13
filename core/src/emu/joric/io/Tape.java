package emu.joric.io;

import java.io.ByteArrayInputStream;
import java.util.concurrent.Callable;

import com.badlogic.gdx.files.FileHandle;

import emu.joric.cpu.Cpu6502;
import emu.joric.memory.Memory;
import emu.joric.memory.Memory.RomType;

/**
 * This class emulates the tape functionality. 
 * 
 * @author Lance Ewing
 */
public class Tape {

  private Cpu6502 cpu;
  
  private RomType romType;
  
  private int[] mem;
  
  private ByteArrayInputStream tapeIn;

  private FileHandle folderHandle;
  
  /**
   * Constructor for Tape.
   * 
   * @param cpu
   * @param memory
   */
  public Tape(Cpu6502 cpu, Memory memory) {
    this.cpu = cpu;
    this.mem = memory.getMemoryArray();
    this.romType = memory.getRomType();
    if ((romType == RomType.ATMOS) || (romType == RomType.ORIC1)) {
      this.registerQuickLoadTraps();
    }
  }
  
  /**
   * Registers CPU traps for handling the quick load routines.
   */
  private void registerQuickLoadTraps() {
    // This trap will read a byte of data from the tape byte array if it is available.
    cpu.registerTrapRoutine(romType.getAddressOfReadByteFromTape(), new Callable<Integer>() {
      public Integer call() {
        int accum = cpu.getAccumulator();
        if (tapeIn != null) {
          accum = tapeIn.read();
          if (accum == -1) {
            System.err.println("Tape read returned -1 to indicate end of stream!");
          }
        }
        cpu.setAccumulator(accum);
        cpu.setCarryFlag(false);
        cpu.setZeroResultFlag(accum == 0? true : false);
        return romType.getAddressOfRTS();
      }
    });
    
    cpu.registerTrapRoutine(romType.getAddressOfGetInSyncWithTapeData(), new Callable<Integer>() {
      public Integer call() {
        boolean foundSynchro = false;
        boolean alreadyOpenedOnce = false;

        while (!foundSynchro && !alreadyOpenedOnce) {
          if (tapeIn != null) {
            int nextByte = tapeIn.read();
            if (nextByte == 0x16) foundSynchro = true;
            if (nextByte == -1) tapeIn = null;
          } else {
            int fileNameLength;
            int addressOfFileName = romType.getAddressOfFileToLoadFromTape();
            for (fileNameLength = 0; mem[addressOfFileName + fileNameLength] != 0; ++fileNameLength) {}
            String fileName = new String(mem, addressOfFileName, fileNameLength);
            try {
              if (folderHandle.child(fileName.toLowerCase() + ".tap").exists()) {
                tapeIn = new ByteArrayInputStream(folderHandle.child(fileName.toLowerCase() + ".tap").readBytes());
              } else if (folderHandle.child(fileName.toLowerCase() + ".TAP").exists()) {
                tapeIn = new ByteArrayInputStream(folderHandle.child(fileName.toLowerCase() + ".TAP").readBytes());
              } else if (folderHandle.child(fileName.toUpperCase() + ".tap").exists()) {
                tapeIn = new ByteArrayInputStream(folderHandle.child(fileName.toUpperCase() + ".tap").readBytes());
              } else if (folderHandle.child(fileName.toUpperCase() + ".TAP").exists()) {
                tapeIn = new ByteArrayInputStream(folderHandle.child(fileName.toUpperCase() + ".TAP").readBytes());
              } else {
                String capitalisedFileName = fileName.length() == 0 ? "" : fileName.substring(0, 1).toUpperCase() + fileName.substring(1).toLowerCase();
                if (folderHandle.child(capitalisedFileName + ".tap").exists()) {
                  tapeIn = new ByteArrayInputStream(folderHandle.child(capitalisedFileName + ".tap").readBytes());
                } else if (folderHandle.child(capitalisedFileName + ".TAP").exists()) {
                  tapeIn = new ByteArrayInputStream(folderHandle.child(capitalisedFileName + ".TAP").readBytes());
                }
              }
              alreadyOpenedOnce = true;
              mem[addressOfFileName] = 0;
            } catch (Exception e) {
              System.err.println("Failed to open tape file: " + fileName + ", error: " + e.getMessage());
              break;
            }
          }
        }
        
        cpu.setIndexRegisterX(0x00);
        cpu.setZeroResultFlag(true);
        
        return romType.getAddressOfRTS();
      }
    });
  }
  
  /**
   * Loads a TAPE file ready to be read by the emulator.
   * 
   * @param tapeData The byte array containing the TAPE data to be loaded.
   * @param folderHandle 
   */
  public void loadTape(byte[] tapeData, FileHandle folderHandle) {
    if ((romType == RomType.ATMOS) || (romType == RomType.ORIC1)) {
      // Store handle to the folder in which this tape file resides.
      this.folderHandle = folderHandle;
      
      // Create input stream for the tape data. Makes it available for synchro and reading routines.
      tapeIn = new ByteArrayInputStream(tapeData);
      
      // This trap automatically enters CLOAD" on the input line and executes. This will
      // automatically trigger the tape loading process within the BASIC ROM.
      cpu.registerTrapRoutine(romType.getAddressOfInputLineFromKeyboard(), new Callable<Integer>() {
        public Integer call() {
          // This is a call once trap, so we deregister it immediately.
          cpu.deregisterTrapRoutine(romType.getAddressOfInputLineFromKeyboard());
          
          // 0x35 is start of input buffer. 0xBC9A is the text screen memory.
          mem[0x35] = mem[0xBC9A] = 67;    // 'C'
          mem[0x36] = mem[0xBC9B] = 76;    // 'L'
          mem[0x37] = mem[0xBC9C] = 79;    // 'O'
          mem[0x38] = mem[0xBC9D] = 65;    // 'A'
          mem[0x39] = mem[0xBC9E] = 68;    // 'D'
          mem[0x3A] = mem[0xBC9F] = 34;    // '"'
          mem[0x3B] = 0;                  // Marks end of entered input.

          // Sets X and Y as if it were the real input line subroutine that ran.
          cpu.setIndexRegisterX(0x34);
          cpu.setIndexRegisterY(0x00);
          
          return romType.getAddressOfRTS();
        }
      });
    }
  }
}
