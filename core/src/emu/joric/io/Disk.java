package emu.joric.io;

import com.badlogic.gdx.Gdx;

import emu.joric.cpu.Cpu6502;
import emu.joric.io.Disk.MfmDiskImage.Sector;
import emu.joric.memory.MemoryMappedChip;

/**
 * Emulates the Oric Microdisc controller. The WD1793 write and read code is based 
 * heavily on Peter Gordon's Oricutron disk emulation. I thank Peter for doing the
 * hard work of figuring out all the intricacies.
 */
public class Disk extends MemoryMappedChip {
  
  private static final int SR_IRQ_ENABLE         = 0x01;
  private static final int SR_BASIC_ROM_DISABLE  = 0x02;
  private static final int SR_FDDS_CD            = 0x0C;    // FDC9216 clock divisor bit 0 and 1
  private static final int SR_DENSITY            = 0x08;
  private static final int SR_SIDE               = 0x10;    // Goes straight out to shugart connector SIDE pin, i.e. bypasses WD1793.
  private static final int SR_DRIVE              = 0x60;
  private static final int SR_MD_ROM_SELECT      = 0x80;    // NOTE: Only when written. Read returns IRQ value.
  
  /**
   * The MicroDisc status register, mapped to 0x0314.
   * 
   *  bit 7:   Eprom select (active low)
   *  bit 5-6: drive select (0 to 3)
   *  bit 4:   side select
   *  bit 3:   double density enable (0: double density, 1: single density)
   *  bit 2:   along with bit 3, selects the data separator clock divisor            (1: double density, 0: single-density)
   *  bit 1:   ROMDIS (active low). When 0, internal Basic rom is disabled.
   *  bit 0:   enable FDC INTRQ to appear on read location $0314 and to drive cpu IRQ
   *  
   *  Read of location 0314 (only bit 7 connected) :
   *  
   *  bit 7: INTRQ state (only if bit 0 above has been set to 1) in negative logic so it's 0 if FDC requests an Interrupt.
   */
  private int status;
  
  /**
   * The 6502 CPU in which to raise the IRQs against.
   */
  private Cpu6502 cpu;
  
  /**
   * Reflects WD1793 interrupt request state.
   */
  private boolean interruptRequest;
  
  /**
   * Reflects WD1793 data request state.
   */
  private boolean dataRequest;
  
  /**
   * The currently selected side.
   */
  private int side;
  
  /**
   * The currently selected drive. Microdisc supports up to four.
   */
  private int drive;
  
  /**
   * Double density enable (0: double density, 1: single density) 
   */
  private int density;
  
  /**
   * FDC9216 clock divisor
   */
  private int fddsClockDivisor;
  
  /**
   * Represents the data stored in an MFM disk image.
   */
  private MfmDiskImage diskImage;
  
  /**
   * The WD1793 that performs most of the FDC functionality.
   */
  private WD1793 wd1793;
  
  /**
   * Constructor for Disk.
   * 
   * @param cpu The 6502 CPU in which to raise the IRQs against.
   */
  public Disk(Cpu6502 cpu) {
    this.cpu = cpu;
    this.wd1793 = new WD1793();
  }
  
  /**
   * Inserts the given disk image into the MicroDisk drive.
   * 
   * @param programName
   * @param programData
   */
  public void insertDisk(String programName, byte[] programData) {
    this.diskImage = new MfmDiskImage(programName, programData, 0);
    this.memory.setBasicRomDisable(true);
    this.memory.setDiskRomEnabled(true);
  }
  
  /**
   * Reads a value from one of the memory locations mapped to the Microdisc.
   * 
   * @param address The address to read from.
   * 
   * @return The value read from the specified address.
   */
  @Override
  public int readMemory(int address) {
    int value = 0;
    
    switch (address) {
      case 0x310: // WD1793 chip's registers.
      case 0x311:
      case 0x312:
      case 0x313:
        value = wd1793.read(address & 3);
        break;
        
      case 0x314: // Microdisc status register, for read only the IRQ.
      case 0x315:
      case 0x316:
      case 0x317:
        value = (interruptRequest? 0x7F : 0xFF);
        break;
        
      case 0x318: // DRQ value
      case 0x319:
      case 0x31A:
      case 0x31B:
        value = (dataRequest? 0x7F : 0xFF);
        break;
    }

    return value;
  }
  
  /**
   * Writes a value to an address mapped to the Microdisc.
   * 
   * @param address The address to write to.
   * @param value The value to write to the specified address.
   */
  @Override
  public void writeMemory(int address, int value) {
    switch (address) {
      case 0x310: // WD1793 chip's registers. - Command Reg.
      case 0x311: // Track Register
      case 0x312: // Sector Register
      case 0x313: // Data Register
        wd1793.write(address & 3, value);
        break;
        
      case 0x314: // Microdisc status register.
      case 0x315:
      case 0x316:
      case 0x317:
        status = value;
        if (((value & SR_IRQ_ENABLE) != 0) && interruptRequest) {
          cpu.setInterrupt(Cpu6502.S_IRQ);
        } else {
          cpu.clearInterrupt(Cpu6502.S_IRQ);
        }
        
        side = ((value & SR_SIDE) >> 4);
        drive = ((value & SR_DRIVE) >> 5);
        density = ((value & SR_DENSITY) >> 3);
        fddsClockDivisor = (1 << ((value & SR_FDDS_CD) >> 2));
        
        //  bit 7:   Eprom select (active low)
        //  bit 1:   ROMDIS (active low). When 0, internal Basic rom is disabled.
        memory.setDiskRomEnabled((value & SR_MD_ROM_SELECT) == 0);
        memory.setBasicRomDisable((value & SR_BASIC_ROM_DISABLE) == 0);
        break;
        
      case 0x318:
      case 0x319:
      case 0x31A:
      case 0x31B:
        // This doesn't appear to do anything on a read, from looking at the schematic.
        dataRequest = true;    // This is what Oricutron does.
        break;
    }
  }

  /**
   * Sets the IRQ value for the Microdisc. If the IRQ enable is active, then the IRQ
   * line on the 6502 is raised.
   */
  public void raisedIntrq() {
    interruptRequest = true;
    if ((status & SR_IRQ_ENABLE) != 0) {
      cpu.setInterrupt(Cpu6502.S_IRQ);
    }
  }

  /**
   * Sets the DRQ value for the Microdisc.
   */
  public void raisedDrq() {
    dataRequest = true;
  }

  /**
   * Clears the IRQ value for the Microdisc. This flows through to the 6502 IRQ pin.
   */
  public void loweredIntrq() {
    interruptRequest = false;
    cpu.clearInterrupt(Cpu6502.S_IRQ);
  }

  /**
   * Clears the DRQ value for the Microdisc.
   */
  public void loweredDrq() {
    dataRequest = false;
  } 
  
  /**
   * Emulates a single cycle for the Microdisc.
   */
  public void emulateCycle() {
    wd1793.emulateCycle();
  }

  /**
   * Emulates the WD1793 Floppy Disk Controller chip.
   */
  public class WD1793 {
    
    // WD17xx status bits
    private static final int WSB_BUSY     = 0;
    private static final int WSF_BUSY     = (1<<WSB_BUSY);
    private static final int WSBI_PULSE   = 1;
    private static final int WSFI_PULSE   = (1<<WSBI_PULSE);     // Type I only
    private static final int WSB_DRQ      = 1;
    private static final int WSF_DRQ      = (1<<WSB_DRQ);
    private static final int WSBI_TRK0    = 2;
    private static final int WSFI_TRK0    = (1<<WSBI_TRK0);      // Type I only
    private static final int WSB_LOSTDAT  = 2;
    private static final int WSF_LOSTDAT  = (1<<WSB_LOSTDAT);
    private static final int WSB_CRCERR   = 3;
    private static final int WSF_CRCERR   = (1<<WSB_CRCERR);
    private static final int WSBI_SEEKERR = 4;
    private static final int WSFI_SEEKERR = (1<<WSBI_SEEKERR);   // Type I only
    private static final int WSB_RNF      = 4;
    private static final int WSF_RNF      = (1<<WSB_RNF);
    private static final int WSBI_HEADL   = 5;
    private static final int WSFI_HEADL   = (1<<WSBI_HEADL);     // Type I only
    private static final int WSBR_RECTYP  = 5;
    private static final int WSFR_RECTYP  = (1<<WSBR_RECTYP);    // Read sector only
    private static final int WSB_WRITEERR = 5;
    private static final int WSF_WRITEERR = (1<<WSB_WRITEERR);
    private static final int WSB_WRPROT   = 6;
    private static final int WSF_WRPROT   = (1<<WSB_WRPROT);
    private static final int WSB_NOTREADY = 7;
    private static final int WSF_NOTREADY = (1<<WSB_NOTREADY); 
    
    // Constants for the command operations.
    private static final int COP_NUFFINK = 0;            // Not doing anything, guv
    private static final int COP_READ_TRACK = 1;         // Reading a track
    private static final int COP_READ_SECTOR = 2;        // Reading a sector
    private static final int COP_READ_SECTORS = 3;       // Reading multiple sectors
    private static final int COP_WRITE_TRACK = 4;        // Writing a track
    private static final int COP_WRITE_SECTOR = 5;       // Writing a sector
    private static final int COP_WRITE_SECTORS = 6;      // Writing multiple sectors
    private static final int COP_READ_ADDRESS = 7;       // Reading a sector header
    
    private int statusRegister;          // Status register
    private int trackRegister;           // Track register
    private int sectorRegister;          // Sector register
    private int dataRegister;            // Data register
    private int commandRegister;         // Command register
    private int currentTrack;            // Currently selected track
    private int currentSectorId;         // Currently selected sector ID
    private int sectorType;              // When reading a sector, this is used to remember if it was marked as deleted
    private boolean lastStepIn;          // Set to TRUE if the last seek operation stepped the head inwards
    private int currentOperation;        // Current operation in progress
    private Sector currentSector;        // Pointers to the current sector in the disk image being used by an active read or write operation
    private int currentSectorLength;     // The length of the current sector
    private int currentSectorOffset;     // Current offset into the above sector
    private int delayedIrqCounter;       // A cycle counter for simulating a delay before INTRQ is asserted
    private int delayedDrqCounter;       // A cycle counter for simulating a delay before DRQ is asserted
    private int delayedIrqStatus;        // The new contents for r_status when delayedint expires (or -1 to leave it untouched)
    private int delayedDrqStatus;        // The new contents for r_status when delayeddrq expires (or -1 to leave it untouched)
    private int crc;                     // The calculated CRC value for the data.
    
    /**
     * Constructor for WD1793.
     */
    public WD1793() {
      statusRegister = 0;
      trackRegister = 0;
      sectorRegister = 0;
      dataRegister = 0;
      currentTrack = 0;
      currentSectorId = 0;
      lastStepIn = false;
      currentOperation = COP_NUFFINK;
      delayedIrqCounter = 0;
      delayedDrqCounter = 0;
      delayedIrqStatus = -1;
      delayedDrqStatus = -1;
    }
    
    /**
     * Emulates a single cycle of WD1793 activity.
     */
    public void emulateCycle() {
      // Is there a pending INTRQ?
      if (delayedIrqCounter > 0) {
        // Count down the INTRQ timer!
        delayedIrqCounter--;

        // Time to assert INTRQ?
        if (delayedIrqCounter <= 0) {
          // Yep! Stop timing.
          delayedIrqCounter = 0;

          // Need to update the status register?
          if (delayedIrqStatus != -1) {
            // Yep. Do so.
            statusRegister = delayedIrqStatus;
            delayedIrqStatus = -1;
          }

          // Assert INTRQ
          raisedIntrq();
        }
      }

      // Is there a pending DRQ?
      if (delayedDrqCounter > 0) {
        // Count down the DRQ timer!
        delayedDrqCounter--;

        // Time to assert DRQ?
        if (delayedDrqCounter <= 0) {
          // Yep! Stop timing.
          delayedDrqCounter = 0;

          // Need to update the status register?
          if (delayedDrqStatus != -1) {
            // Yep. Do so.
            statusRegister = delayedDrqStatus;
            delayedDrqStatus = -1;
          }

          // Assert DRQ
          statusRegister |= WSF_DRQ;
          raisedDrq();
        }
      }
    }
    
    /**
     * Calculates the CRC value given the current CRC value and the new value to add to it.
     * 
     * @param crc The current CRC value.
     * @param value The new value to include in the calculated CRC value.
     * 
     * @return The new CRC value.
     */
    public int calculateCRC(int crc, int value) {
      crc = ((crc >> 8) & 0xff) | (crc << 8);
      crc ^= value;
      crc ^= (crc & 0xff) >> 4;
      crc ^= (crc << 8) << 4;
      crc ^= ((crc & 0xff) << 4) << 1;
      return crc;
    }

    /**
     * Reads from one of the four WD1793 registers.
     * 
     * @param address The address of the register to read.
     * 
     * @return The value of the specified register.
     */
    public int read(int address) {
      // Which register?!
      switch (address) {
        case 0: // Status register
          loweredIntrq(); // Reading the status register clears INTRQ
          return statusRegister;
    
        case 1: // Track register
          return trackRegister;
    
        case 2: // Sector register
          return sectorRegister;
    
        case 3: // Data register
          // What are we currently doing?
          switch (currentOperation) {
          case COP_READ_SECTOR:
          case COP_READ_SECTORS:
            // We somehow started a sector read operation without a valid sector.
            if (currentSector == null) {
              // Abort.
              statusRegister &= ~WSF_DRQ;
              statusRegister |= WSF_RNF;
              loweredDrq();
              currentOperation = COP_NUFFINK;
              break;
            }
    
            // If this is the first read of a read operation, remember the record type for later
            if (currentSectorOffset == 0) {
              sectorType = (currentSector.read(currentSectorOffset++) == 0xf8) ? WSFR_RECTYP : 0x00;
            }
    
            // Get the next byte from the sector
            dataRegister = currentSector.read(currentSectorOffset++);
            crc = calculateCRC(crc, dataRegister);
    
            // Clear any previous DRQ
            statusRegister &= ~WSF_DRQ;
            loweredDrq();
    
            // Has the whole sector been read?
            if (currentSectorOffset > currentSectorLength) {
              // We've got to the end of the current sector. IF it is a multiple sector
              // operation, we need to move on!
              if (currentOperation == COP_READ_SECTORS) {
                // Get the next sector, and carry on!
                sectorRegister++;
                currentSectorOffset = 0;
                currentSector = findSector(sectorRegister);
                crc = 0xe295;
    
                // If we hit the end of the track, thats fine, it just means the operation is finished.
                if (currentSector == null) {
                  delayedIrqCounter = 20;            // Assert INTRQ in 20 cycles time
                  delayedIrqStatus = sectorType;     // ...and when doing so, set the status to reflect the record type
                  currentOperation = COP_NUFFINK;    // No longer in the middle of an operation
                  statusRegister &= (~WSF_DRQ);      // Clear DRQ (no data to read)
                  loweredDrq();
                  break;
                }
    
                // We've got the next sector lined up. Assert DRQ in 180 cycles time (simulate a bit of a delay
                // between sectors. Note that most of these values have been pulled out of thin air and might need
                // adjusting for some pickier loaders).
                delayedDrqCounter = 180;
                break;
              }
    
              // Just reading one sector so..
              delayedIrqCounter = 32;          // INTRQ in a little while because we're finished
              delayedIrqStatus = sectorType;   // Set the status accordingly
              currentOperation = COP_NUFFINK;  // Finished the op
              statusRegister &= (~WSF_DRQ);    // Clear DRQ (no more data)
              loweredDrq();
            } else {
              delayedDrqCounter = 32;          // More data ready. DRQ to let them know!
            }
            break;
    
          case COP_READ_ADDRESS:
            if (currentSector == null) {
              statusRegister &= ~WSF_DRQ;
              loweredDrq();
              currentOperation = COP_NUFFINK;
              break;
            }
            if (currentSectorOffset == 0) {
              // The Track Address of the ID field is written into the Sector Register.
              sectorRegister = currentSector.trackNum;
            }
            dataRegister = diskImage.rawImage[++currentSectorOffset];  // TODO: Does this really read from raw image? Or should it be sector?
            statusRegister &= ~WSF_DRQ;
            loweredDrq();
            if (currentSectorOffset >= 6) {
              delayedIrqCounter = 20;
              delayedIrqStatus = 0;
              currentOperation = COP_NUFFINK;
            } else {
              delayedDrqCounter = 32;
            }
            break;
        }

        return dataRegister;
      }

      return 0;
    }

    /**
     * Writes to one of the four WD1793 registers.
     * 
     * @param address The address of the register to write to.
     * @param data The data to write to the specified register.
     */
    public void write(int address, int data) {
      switch (address) {
        case 0: // Command register
          commandRegister = data;
          loweredIntrq();
          switch (data & 0xe0) {
            case 0x00: // Restore or seek
              switch (data & 0x10) {
                case 0x00: // Restore (Type I)
                  statusRegister = WSF_BUSY;
                  if ((data & 8) != 0) {
                    statusRegister |= WSFI_HEADL;
                  }
                  seekTrack(0);
                  currentOperation = COP_NUFFINK;
                  break;
        
                case 0x10: // Seek (Type I)
                  statusRegister = WSF_BUSY;
                  if ((data & 8) != 0) {
                    statusRegister |= WSFI_HEADL;
                  }
                  seekTrack(dataRegister);
                  currentOperation = COP_NUFFINK;
                  break;
            }
            break;
    
          case 0x20: // Step (Type I)
            statusRegister = WSF_BUSY;
            if ((data & 8) != 0) {
              statusRegister |= WSFI_HEADL;
            }
            if (lastStepIn) {
              seekTrack(currentTrack + 1);
            } else {
              seekTrack(currentTrack > 0 ? currentTrack - 1 : 0);
            }
            currentOperation = COP_NUFFINK;
            break;
    
          case 0x40: // Step-in (Type I)
            statusRegister = WSF_BUSY;
            if ((data & 8) != 0) {
              statusRegister |= WSFI_HEADL;
            }
            seekTrack(currentTrack + 1);
            lastStepIn = true;
            currentOperation = COP_NUFFINK;
            break;
    
          case 0x60: // Step-out (Type I)
            statusRegister = WSF_BUSY;
            if ((data & 8) != 0)
              statusRegister |= WSFI_HEADL;
            if (currentTrack > 0) {
              seekTrack(currentTrack - 1);
            }
            lastStepIn = false;
            currentOperation = COP_NUFFINK;
            break;
    
          case 0x80: // Read sector (Type II)
            currentSectorOffset = 0;
            currentSector = findSector(sectorRegister);
            if (currentSector == null) {
              statusRegister = WSF_RNF;
              loweredDrq();
              raisedIntrq();
              currentOperation = COP_NUFFINK;
              break;
            }
    
            currentSectorLength = currentSector.sectorSize;
            statusRegister = WSF_BUSY | WSF_NOTREADY;
            delayedDrqCounter = 60;
            currentOperation = ((data & 0x10) != 0)? COP_READ_SECTORS : COP_READ_SECTOR;
            crc = 0xe295;
            break;
    
          case 0xa0: // Write sector (Type II)
            currentSectorOffset = 0;
            currentSector = findSector(sectorRegister);
            if (currentSector == null) {
              statusRegister = WSF_RNF;
              loweredDrq();
              raisedIntrq();
              currentOperation = COP_NUFFINK;
              break;
            }
    
            currentSectorLength = currentSector.sectorSize;
            statusRegister = WSF_BUSY | WSF_NOTREADY;
            delayedDrqCounter = 500;
            currentOperation = ((data & 0x10) != 0)? COP_WRITE_SECTORS : COP_WRITE_SECTOR;
            crc = 0xe295;
            break;
    
          case 0xc0: // Read address / Force IRQ
            switch (data & 0x10) {
            case 0x00: // Read address (Type III)
              currentSectorOffset = 0;
              if (currentSector == null) {
                currentSector = firstSector();
              }
              else {
                currentSector = nextSector();
              }
              if (currentSector == null) {
                statusRegister = WSF_RNF;
                loweredDrq();
                currentOperation = COP_NUFFINK;
                raisedIntrq();
                break;
              }
    
              statusRegister = WSF_NOTREADY | WSF_BUSY | WSF_DRQ;
              raisedDrq();
              currentOperation = COP_READ_ADDRESS;
              break;
    
            case 0x10: // Force Interrupt (Type IV)
              statusRegister = 0;
              loweredDrq();
              raisedIntrq();
              delayedIrqCounter = 0;
              delayedDrqCounter = 0;
              currentOperation = COP_NUFFINK;
              break;
            }
            break;
    
          case 0xe0: // Read track / Write track
            switch (data & 0x10) {
              case 0x00: // Read track (Type III)
                currentOperation = COP_READ_TRACK;
                break;
      
              case 0x10: // Write track (Type III)
                currentOperation = COP_WRITE_TRACK;
                break;
            }
            break;
          }
          break;
    
        case 1: // Track register
          trackRegister = data;
          break;
    
        case 2: // Sector register
          sectorRegister = data;
          break;
    
        case 3: // Data register
          dataRegister = data;
    
          switch (currentOperation) {
            case COP_WRITE_SECTOR:
            case COP_WRITE_SECTORS:
              if (currentSector == null) {
                statusRegister &= ~WSF_DRQ;
                statusRegister |= WSF_RNF;
                loweredDrq();
                currentOperation = COP_NUFFINK;
                break;
              }
              if (currentSectorOffset == 0) {
                currentSector.write(currentSectorOffset++, 0xFB);
              }
              currentSector.write(currentSectorOffset++, dataRegister);
              crc = calculateCRC(crc, dataRegister);
              statusRegister &= ~WSF_DRQ;
              loweredDrq();
      
              if (currentSectorOffset > currentSectorLength) {
                currentSector.write(currentSectorOffset++, ((crc >> 8) & 0xFF));
                currentSector.write(currentSectorOffset++, (crc & 0xFF));
                if (currentOperation == COP_WRITE_SECTORS) {
                  // Get the next sector, and carry on!
                  sectorRegister++;
                  currentSectorOffset = 0;
                  currentSector = findSector(sectorRegister);
                  crc = 0xe295;
      
                  if (currentSector == null) {
                    delayedIrqCounter = 20;
                    delayedIrqStatus = sectorType;
                    currentOperation = COP_NUFFINK;
                    statusRegister &= (~WSF_DRQ);
                    loweredDrq();
                    break;
                  }
                  delayedDrqCounter = 180;
                  break;
                }
      
                delayedIrqCounter = 32;
                delayedIrqStatus = sectorType;
                currentOperation = COP_NUFFINK;
                statusRegister &= (~WSF_DRQ);
                loweredDrq();
              } else {
                delayedDrqCounter = 32;
              }
              break;
          }
          break;
      }
    }
    
    /**
     * Seek to the specified track. Used by the SEEK and STEP commands.
     * 
     * @param track The track to seek to.
     */
    public void seekTrack(int track) {
      // Is there a disk in the drive?
      if (diskImage != null) {
        // Yes. If we are trying to seek to a non-existant track, just seek as far as we can
        if (track >= diskImage.getNumOfTracks()) {
          track = (diskImage.getNumOfTracks() > 0) ? diskImage.getNumOfTracks() - 1 : 0;
          delayedIrqStatus = WSFI_HEADL | WSFI_SEEKERR;
        } else {
          delayedIrqStatus = WSFI_HEADL | WSFI_PULSE;
        }

        // Update our status
        currentTrack = track;
        currentSectorId = 0;
        trackRegister = track;

        // Assert INTRQ in 20 cycles time and update the status accordingly
        // (note: 20 cycles is waaaaaay faster than any real drive could seek. The actual
        // delay would depend how far the head had to seek, and what stepping speed was
        // currently set).
        delayedIrqCounter = 20;
        if (currentTrack == 0) {
          delayedIrqStatus |= WSFI_TRK0;
        }
        return;
      }

      // No disk in drive
      // Set INTRQ because the operation has finished.
      raisedIntrq();
      trackRegister = 0;

      // Set error state
      statusRegister = WSF_NOTREADY | WSFI_SEEKERR;
    }
    
    /**
     * Looks for the sector with the specified ID in the current track. It returns
     * null if there is no such sector, or a Sector object containing the ID field
     * details and the offset to data. It also includes a read and write method for
     * reading bytes from a specified sector position.
     * 
     * @param sectorId The ID of the sector to find.
     * 
     * @return The found Sector, or null if it wasn't found.
     */
    public Sector findSector(int sectorId) {
      Sector[] sectors = diskImage.getAllTracks()[side][currentTrack];
      
      currentSectorId = sectorId;

      // Found the required sector?
      Sector sector = sectors[currentSectorId];
      if (sector.sectorNum == sectorId) {
        return sector;
      }

      // The search failed.
      return null;
    }
    
    /**
     * Returns the first valid sector in the current track, or null if there aren't
     * any sectors.
     * 
     * @return The first Sector in the current track, or null if not found.
     */
    public Sector firstSector() {
      // We're at the first sector!
      currentSectorId = 0;
      statusRegister = WSFI_PULSE;

      // Return the sector
      return diskImage.getAllTracks()[side][currentTrack][currentSectorId];
    }
    
    /**
     * Move on to the next sector and return it.
     * 
     * @return The next Sector.
     */
    public Sector nextSector() {
      // Get the next sector number
      currentSectorId = (currentSectorId + 1) % diskImage.getNumOfSectors();

      // If we are at the start of the track, set the pulse bit
      if (currentSectorId == 0) statusRegister |= WSFI_PULSE;

      // Return the sector
      return diskImage.getAllTracks()[side][currentTrack][currentSectorId];
    }
  }
  
  /**
   * Represents the data stored in an MFM disk image. An MFM disk image is based on the IBM
   * System 34 MFM floppy disk format. Some technical notes from the Internet included below.
   * 
   * FOR THE "MFM" FORMAT, there is always a 256-byte header as follows:
   * 
   * - an 8-byte signature: MFM_DISK
   * - the number of sides (always 32 bits little-endian)
   * - the number of tracks (32 bits)
   * - the type of geometry (32 bits)
   * - the rest of the header is currently unused but reserved for possible future use 
   * 
   * Next come the contents of the tracks: implicitly each track contains 6250 bytes, completed
   * by unused bytes to have a size multiple of 256, that is 6400. The type of geometry
   * indicates in which order the tracks come: geometry 1 gives first all the tracks of the
   * first side, then that of the second, etc. ; geometry 2 first gives the tracks of the
   * side 0, then those of the side 1, 2, 3, etc. (Geometry 1 is the one used by Oric OS, 
   * Geometry 2 is used in the non-Oric world).
   */
  public class MfmDiskImage {

    private int driveNum;              // The drive this disk is inserted into, or -1
    private int numOfTracks;           // Number of tracks per side
    private int numOfSides;            // Number of sides in the image
    private int geometry;              // Geometry type. See javadoc above.
    private int numOfSectors;          // Number of sectors cached (= number of valid sectors in the current track)
    private int[] rawImage;            // The raw disk image file loaded into memory
    private String diskImageName;
    private boolean loadFailed;
    private Sector[][][] allTracks;
    
    /**
     * Constructor for MfmDiskImage.
     * 
     * @param diskImageName
     * @param rawImage
     * @param drive
     */
    public MfmDiskImage(String diskImageName, byte[] rawImage, int drive) {
      // Read in the full disk image data if it wasn't provided.
      if (rawImage == null) {
        rawImage = Gdx.files.internal("disks/" + diskImageName).readBytes();
      }
    
      this.rawImage = convertByteArrayToIntArray(rawImage);
      
      // Check for the signature. Is it an MFM format disk image? 
      String signature = new String(rawImage, 0, 8);
      if (!signature.equals("MFM_DISK")) {
        System.err.println("MFM_DISK signature not found!!");
        this.loadFailed = true;
        return;
      }
      
      this.driveNum = drive;
      this.numOfSides = getIntFromRawImage(8);
      this.numOfTracks = getIntFromRawImage(12);
      this.geometry = getIntFromRawImage(16);
      this.diskImageName = diskImageName;
      this.numOfSectors = 17;
      
      // Load all tracks.
      allTracks = new Sector[numOfSides][numOfTracks][32];
      for (int side=0; side < numOfSides; side++) {
        for (int track=0; track < numOfTracks; track++) {
          allTracks[side][track] = loadTrack(side, track); 
        }
      }
    }
    
    /**
     * Loads a full track of sectors from the identified side and track number.
     * 
     * @param side The side of the disk to load the track from.
     * @param track The track number of the track to load.
     * 
     * @return Array of Sectors for the track that was loaded.
     */
    private Sector[] loadTrack(int side, int track) {
      Sector[] sectors = new Sector[numOfSectors + 1];
      
      // Find the start and end locations of the track within the disk image. This
      // works because Oric disks always use the same geometry setting, tracks are
      // always aligned every 6400 bytes, and the header of a disk is 256 bytes.
      int trackStart = (side * this.numOfTracks + track) * 6400 + 256;
      int trackEnd = trackStart + 6400;

      // Scan through the track looking for sectors
      int offset = trackStart;
      int startOfSector = 0;

      while (offset < trackEnd) {
        startOfSector = offset;
        
        // Search for ID mark
        while ((offset < trackEnd) && (((int)rawImage[offset] & 0xFF) != 0xFE)) offset++;

        // Don't exceed the bounds of this track.
        if (offset >= trackEnd) break;

        // FE
        // Track number
        // Side
        // Sector number 
        // Number of bytes per sector (1=256 bytes, 2=512 bytes, 3 = 1024 bytes, 4 = 2048 bytes)
        // 2 bytes CRC
        
        // Store ID pointer and details.
        Sector sector = new Sector();
        sector.idOffset = offset;
        sector.trackNum = rawImage[offset + 1];
        sector.side = rawImage[offset + 2];
        sector.sectorNum = rawImage[offset + 3];
        sector.sectorSize = (1 << (rawImage[offset + 4] + 7));
        sectors[sector.sectorNum] = sector; 
        
        // Skip ID field and CRC
        offset += 7;

        // Search for data ID.
        //  A data record has the form:
        //  
        //    FB
        //    256 or 512 bytes with the actual data
        //    2 bytes CRC
        //    FB indicates ordinary data. IBM also permits F8 as an alternative, meaning deleted data.
        //    The WD177X used by both the Microdisc and the Jasmin will read track data with either 
        //    mark and indicate whether the sector was flagged as deleted via its status register.
        //  
        //    This is followed by the data gap, which is:  FF or 4E (*54)
        while ((offset < trackEnd) && (((int)rawImage[offset] & 0xFF) != 0xFB) && (((int)rawImage[offset] & 0xFF) != 0xF8)) offset++;

        // Don't exceed the bounds of this track.
        if (offset >= trackEnd) break;

        // Store pointer. At this point the offset is at the 0xFB, so one position before the data.
        sector.dataOffset = offset;

        // Skip data field and ID
        offset += sector.sectorSize + 3;
      }
      
      // Some disks appear to be missing a properly formatted last sector of each track, and yet the code
      // attempts to read from it. So we make an educated guess about what the offsets and settings are.
      if (sectors[16] == null) {
        Sector sector = new Sector();
        sector.idOffset = startOfSector + 55;
        sector.trackNum = track;
        sector.side = side;
        sector.sectorNum = 17;
        sector.sectorSize = 256;
        sector.dataOffset = startOfSector + 99;
        sectors[16] = sector;
      }
      
      return sectors;
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
     * Gets a 32-bit integer value from the specified offset in the raw disk image.
     * 
     * @param offset The offset to read the 32-bite integer value from.
     * 
     * @return The 32-bit integer value at the given offset into the raw disk image.
     */
    private int getIntFromRawImage(int offset) {
      return (rawImage[offset + 3] << 24) | 
             (rawImage[offset + 2] << 16) |
             (rawImage[offset + 1] << 8)  | 
             (rawImage[offset + 0] << 0);
    }

    /**
     * This class represents a Sector within the MFM disk image. It stores details such as the
     * sector ID, offset of the data for the sector, and the track and side of the disk where 
     * the sector resides. It also provides methods for reading and writing to/from a specified
     * sector position.
     */
    public class Sector {
      int idOffset;
      int trackNum;
      int side;
      int sectorNum;     // This is the sector ID.
      int sectorSize;    // Should be the same for every sector on the disk.
      int dataOffset;
      
      public int read(int sectorPos) {
        int value = rawImage[dataOffset + sectorPos];
        return value;
      }
      
      public void write(int sectorPos, int data) {
        // TODO: This is just updating an array in memory. Need to add writing back to disk at some point.
        rawImage[dataOffset + sectorPos] = (byte)data;
      }
      
      public String toString() {
        return String.format("Sector - side#: %d, track#: %d, sector#: %d, size: %d, idOffset: %d, dataOffset: %d", side, trackNum, sectorNum, sectorSize, idOffset, dataOffset);
      }
    }
    
    /**
     * @return the numOfTracks
     */
    public int getNumOfTracks() {
      return numOfTracks;
    }

    /**
     * @return the numOfSides
     */
    public int getNumOfSides() {
      return numOfSides;
    }

    /**
     * @return the numOfSectors
     */
    public int getNumOfSectors() {
      return numOfSectors;
    }

    public Sector[][][] getAllTracks() {
      return this.allTracks;
    }
  }
}
