package emu.joric.memory;

import com.badlogic.gdx.Gdx;

import emu.joric.cpu.Cpu6502;
import emu.joric.io.Disk;
import emu.joric.io.Via;
import emu.joric.snap.Snapshot;
import emu.joric.video.Ula;

/**
 * This class emulators the Oric's memory.
 * 
 * @author Lance Ewing
 */
public class Memory {
  
  /**
   * Holds the machines memory.
   */
  private int mem[];

  /**
   * Holds an array of references to instances of MemoryMappedChip where each
   * instance determines the behaviour of reading or writing to the given memory
   * address.
   */
  private MemoryMappedChip memoryMap[];
  
  /**
   * The type of BASIC ROM being used.
   */
  private RomType romType;
  
  /**
   * The Cpu6502 that will be accessing this Memory.
   */
  private Cpu6502 cpu;
  
  /**
   * Whether the BASIC ROM is disabled or not.
   */
  private boolean basicRomDisabled;
  
  /**
   * Whether the Microdisc ROM is enabled or not.
   */
  private boolean diskRomEnabled;
  
  /**
   * The 16 KB content of the loaded BASIC ROM.
   */
  private int[] basicRom;
  
  /**
   * The 8 KB content of the loaded Microdisk ROM.
   */
  private int[] microdiscRom;
  
  /**
   * Constructor for Memory. Mainly available for unit testing.
   * 
   * @param cpu The CPU that will access this Memory.
   * @param snapshot Optional snapshot of the machine state to start with.
   * @param allRam true if memory should be initialised to all RAM; otherwise false.
   */
  public Memory(Cpu6502 cpu, Snapshot snapshot, boolean allRam) {
    if (snapshot != null) {
      this.mem = snapshot.getMemoryArray();
    } else {
      this.mem = new int[65536];
    }
    this.memoryMap = new MemoryMappedChip[65536];
    this.cpu = cpu;
    cpu.setMemory(this);
    if (allRam) {
      mapChipToMemory(new RamChip(), 0x0000, 0xFFFF);
    }
  }
  
  /**
   * Constructor for Memory.
   * 
   * @param cpu The CPU that will access this Memory.
   * @param ula The ULA chip to map to memory.
   * @param via The VIA chip to map to memory.
   * @param microdisc The MicroDisc device to map to memory.
   * @param snapshot Optional snapshot of the machine state to start with.
   */
  public Memory(Cpu6502 cpu, Ula ula, Via via, Disk microdisc, Snapshot snapshot) {
    this(cpu, snapshot, false);
    ula.setMemory(this);
    initOricMemory(ula, via, microdisc);
  }

  /**
   * Sets whether the BASIC ROM is disabled or not.
   * 
   * @param basicRomDisabled Whether the BASIC ROM is disabled or not.
   */
  public void setBasicRomDisable(boolean basicRomDisabled) {
    this.basicRomDisabled = basicRomDisabled;
  }
  
  /**
   * Sets whether the Microdisk ROM is enabled or not.
   * 
   * @param diskRomEnabled Whether the Microdisk ROM is enabled or not.
   */
  public void setDiskRomEnabled(boolean diskRomEnabled) {
    this.diskRomEnabled = diskRomEnabled;
  }

  /**
   * Initialise the Oric's memory.
   * 
   * @param ula The ULA chip to map to memory.
   * @param via The VIA #1 chip to map to memory.
   * @param microdisc The MicroDisc device to map to memory.
   */
  private void initOricMemory(Ula ula, Via via, Disk microdisc) {
    // The initial RAM pattern cannot simply be all zeroes, otherwise Sedoric will not 
    // work. It does a checksum across the overlay RAM and isn't expecting it all to be 0.
    for (int i = 0; i <= 0xFFFF; ++i) {
      this.mem[i] = ((i & 128) != 0 ? 0xFF : 0);
    }
    
    //    0000-00FF Page Zero
    //    0100-01FF Stack
    //    0200-02FF Page 2
    mapChipToMemory(new RamChip(), 0x0000, 0x02FF);
    
    //    0300-03FF I/O Area
    //        0300-030F Internal VIA 6522
    //        0310-0310 DK'tronics Joystick Interface (left port)
    //        0310-0313 Microdisc FDC WD1793
    //        0310-031F Pravetz FDC
    //        0314-031B Microdisc additionnal I/O registers
    //        031C-031F Internal ACIA 6551 (Telestrat)
    //        0320-032F RS232 extension (Atmos)
    //        0320-0320 DK'tronics Joystick Interface (right port)
    //        0320-032F Second VIA 6522 (Telestrat)
    //        0320-03FF Pravetz ROM
    //        0330-035F Spare Memory
    //        0360-0371 RTC ICM7170 (Telestrat and Atmos)
    //        0380-03DF Spare Memory
    //        03E0-03E1 Oric Lightpen
    //        03E2-03F3 Spare Memory
    //        03F4-03FF Jasmin FDC WD1773
    mapChipToMemory(via, 0x0300, 0x030F);
    // For these 12 addresses, the microdisc controller disables VIA (using IO_Control pin) and maps itself.
    mapChipToMemory(microdisc, 0x0310, 0x031B);
    // All other addresses in the $0x3XX range without current device emulation will hit the VIA.
    mapChipToMemory(via, 0x031C, 0x03FF);
    //    0400-04FF Sedoric Code
    //    0500-B3FF BASIC Program RAM
    //    A000-BFDF HIRES Screen
    //    B400-B4FF Spare Memory
    //    B500-B7FF Standard Character Set
    //    B800-B8FF Spare Memory
    //    B900-BB7F Alternate Character Set
    //    BB80-BF3F TEXT Screen
    //    BF40-BF67 Spare Memory
    //    BF68-BFDF TEXT Bottom Screen
    //    BFE0-BFFF Spare Memory
    mapChipToMemory(new RamChip(), 0x0400, 0xBFFF);
    
    basicRom = convertByteArrayToIntArray(Gdx.files.internal("roms/basic11b.rom").readBytes());
    
    mapChipToMemory(new MemoryMappedChip() {
      public int readMemory(int address) {
        if (basicRomDisabled) {
          return mem[address];
        } else {
          return basicRom[address - 0xC000];
        }
      }
      public void writeMemory(int address, int value) {
        if (basicRomDisabled) {
          mem[address] = value;
        } else {
          // Ignore. It's ROM, so can't write.
        }
      }
    }, 0xC000, 0xDFFF);
    
    microdiscRom = convertByteArrayToIntArray(Gdx.files.internal("roms/microdis.rom").readBytes());
    
    mapChipToMemory(new MemoryMappedChip() {
      public int readMemory(int address) {
        if (basicRomDisabled) {
          if (diskRomEnabled) {
            return microdiscRom[address - 0xE000];
          } else {
            return mem[address];
          }
        } else {
          return basicRom[address - 0xC000];
        }
      }
      public void writeMemory(int address, int value) {
        if (basicRomDisabled && !diskRomEnabled) {
          mem[address] = value;
        }
      }
    }, 0xE000, 0xFFFF);
    
    // Determine ROM version by looking at RESET vector.
    int resetVector = (basicRom[0x3FFC] | ((basicRom[0x3FFD] << 8) & 0xFF00));
    switch(resetVector) {
      case 0xF88F:
        romType = RomType.ATMOS;
        break;
      case 0xF42D:
        romType = RomType.ORIC1;
        break;
      default:
        romType = RomType.CUSTOM;
        break;
    }
  }
  
  /**
   * Converts a byte array into an int array.
   * 
   * @param data The byte array to convert.
   * 
   * @return The int array.
   */
  private int[] convertByteArrayToIntArray(byte[] data) {
    int[] convertedData = new int[data.length];
    for (int i=0; i<data.length; i++) {
      convertedData[i] = ((int)data[i]) & 0xFF;
    }
    return convertedData;
  }
  
  /**
   * Maps the given chip instance at the given address range.
   * 
   * @param chip The chip to map at the given address range.
   * @param startAddress The start of the address range.
   * @param endAddress The end of the address range.
   */
  private void mapChipToMemory(MemoryMappedChip chip, int startAddress, int endAddress) {
    mapChipToMemory(chip, startAddress, endAddress, null);
  }
  
  /**
   * Maps the given chip instance at the given address range, optionally loading the
   * given initial state data into that address range. This state data is intended to be
   * used for things such as ROM images (e.g. char, kernel, basic).
   * 
   * @param chip The chip to map at the given address range.
   * @param startAddress The start of the address range.
   * @param endAddress The end of the address range.
   * @param state byte array containing initial state (can be null).
   */
  private void mapChipToMemory(MemoryMappedChip chip, int startAddress, int endAddress, byte[] state) {
    int statePos = 0;
    
    // Load the initial state into memory if provided.
    if (state != null) {
      for (int i=startAddress; i<=endAddress; i++) {
        mem[i] = (state[statePos++] & 0xFF);
      }
    }
    
    // Configure the chip into the memory map between the given start and end addresses.
    for (int i = startAddress; i <= endAddress; i++) {
      memoryMap[i] = chip;
    }

    chip.setMemory(this);
  }
  
  /**
   * Gets the BASIC ROM type being used.
   * 
   * @return The BASIC ROM type being used.
   */
  public RomType getRomType() {
    return romType;
  }
  
  /**
   * Enum representing the different BASIC ROM types supported by JOric. It holds
   * the details that are specific to a particular ROM type, such as start addresses
   * of various ROM routines or RAM addresses used by those ROM routines.
   */
  public static enum RomType {
    
    ATMOS(0xC592, 0xE6C9, 0xE735, 0x027F, 0x0293, 0x02A7, 0xE65E, 0xE75E, 0x024D, 0xE6FB), 
    ORIC1(0xC5A2, 0xE630, 0xE696, 0x0035, 0x0049, 0x005D, 0xE5C6, 0xE6BE, 0x0067, 0xE65D),  
    CUSTOM(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),  
    DISABLED(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    
    private int addressOfInputLineFromKeyboard;   // Used to automatically run small snippets of BASIC
    private int addressOfReadByteFromTape;
    private int addressOfGetInSyncWithTapeData;
    private int addressOfFileToLoadFromTape; 
    private int addressOfFileLoadedFromTape;
    private int addressOfTapeHeader;
    private int addressOfOutputByteToTape;
    private int addressOfOutputTapeLeader;
    private int addressOfTapeSpeed;                // 0 = fast, >= 1 = slow
    private int addressOfRTS;                      // Used by subroutine traps as a miscellaneous RTS address to position PC at after running trap.
    
    /**
     * Constructor for RomType.
     * 
     * @param addressOfInputLineFromKeyboard
     * @param addressOfReadByteFromTape
     * @param addressOfGetInSyncWithTapeData
     * @param addressOfFileToLoadFromTape
     * @param addressOfFileLoadedFromTape
     * @param addressOfTapeHeader
     * @param addressOfOutputByteToTape
     * @param addressOfOutputTapeLeader
     * @param addressOfTapeSpeed
     * @param addressOfRTS
     */
    RomType(int addressOfInputLineFromKeyboard, int addressOfReadByteFromTape, 
            int addressOfGetInSyncWithTapeData, int addressOfFileToLoadFromTape, 
            int addressOfFileLoadedFromTape, int addressOfTapeHeader,
            int addressOfOutputByteToTape, int addressOfOutputTapeLeader,
            int addressOfTapeSpeed, int addressOfRTS) {
      
      this.addressOfInputLineFromKeyboard = addressOfInputLineFromKeyboard;
      this.addressOfReadByteFromTape = addressOfReadByteFromTape;
      this.addressOfGetInSyncWithTapeData = addressOfGetInSyncWithTapeData;
      this.addressOfFileToLoadFromTape = addressOfFileToLoadFromTape;
      this.addressOfFileLoadedFromTape = addressOfFileLoadedFromTape;
      this.addressOfTapeHeader = addressOfTapeHeader;
      this.addressOfOutputByteToTape = addressOfOutputByteToTape;
      this.addressOfOutputTapeLeader = addressOfOutputTapeLeader;
      this.addressOfTapeSpeed = addressOfTapeSpeed;
      this.addressOfRTS = addressOfRTS;
    }

    public int getAddressOfInputLineFromKeyboard() {
      return addressOfInputLineFromKeyboard;
    }

    public int getAddressOfReadByteFromTape() {
      return addressOfReadByteFromTape;
    }

    public int getAddressOfGetInSyncWithTapeData() {
      return addressOfGetInSyncWithTapeData;
    }

    public int getAddressOfFileToLoadFromTape() {
      return addressOfFileToLoadFromTape;
    }

    public int getAddressOfFileLoadedFromTape() {
      return addressOfFileLoadedFromTape;
    }

    public int getAddressOfTapeHeader() {
      return addressOfTapeHeader;
    }

    public int getAddressOfOutputByteToTape() {
      return addressOfOutputByteToTape;
    }

    public int getAddressOfOutputTapeLeader() {
      return addressOfOutputTapeLeader;
    }

    public int getAddressOfTapeSpeed() {
      return addressOfTapeSpeed;
    }

    public int getAddressOfRTS() {
      return addressOfRTS;
    }
  }
  
  /**
   * Loads a ROM file from the given byte array in to the BASIC ROM area.
   * 
   * @param romData The byte array containing the ROM program data to load.
   */
  public void loadCustomRom(byte[] romData) {
    mapChipToMemory(new RomChip(), 0xC000, 0xC000 + (romData.length - 1), romData);
  }
  
  /**
   * Gets the int array that represents the Oric's memory.
   * 
   * @return an int array represents the Oric memory.
   */
  public int[] getMemoryArray() {
    return mem;
  }

  /**
   * Gets the array of memory mapped devices. 
   * 
   * @return The array of memory mapped devices.
   */
  public MemoryMappedChip[] getMemoryMap() {
    return memoryMap;
  }
  
  /**
   * Forces a write to a memory address, even if it is ROM. This is used mainly
   * for setting emulation traps.
   * 
   * @param address The address to write the value to.
   * @param value The value to write to the given address.
   */
  public void forceWrite(int address, int value) {
    if (address < 0xC000) {
      writeMemory(address, value);
    } else {
      if (basicRomDisabled) {
        if (diskRomEnabled) {
          microdiscRom[address - 0xE000] = value;
        } else {
          mem[address] = value;
        }
      } else {
        basicRom[address - 0xC000] = value;
      }
    }
  }
  
  /**
   * Reads the value of the given Oric memory address.
   * 
   * @param address The address to read the byte from.
   * 
   * @return The contents of the memory address.
   */
  public int readMemory(int address) {
    return (memoryMap[address].readMemory(address));
  }

  /**
   * Writes a value to the give Oric memory address.
   * 
   * @param address The address to write the value to.
   * @param value The value to write to the given address.
   */
  public void writeMemory(int address, int value) {
    memoryMap[address].writeMemory(address, value);
  }
}
