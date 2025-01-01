package emu.joric;

import emu.joric.cpu.Cpu6502;
import emu.joric.io.Disk;
import emu.joric.io.Joystick;
import emu.joric.io.Joystick.JoystickType;
import emu.joric.io.Keyboard;
import emu.joric.io.Tape;
import emu.joric.io.Via;
import emu.joric.memory.Memory;
import emu.joric.memory.RamType;
import emu.joric.snap.Snapshot;
import emu.joric.sound.AYPSG;
import emu.joric.sound.libgdx.AY38912PSG;
import emu.joric.video.Ula;

/**
 * Represents the Oric machine.
 * 
 * @author Lance Ewing
 */
public class Machine {

    // Machine components.
    private Memory memory;
    private Ula ula;
    private Via via;
    private AYPSG psg;
    private Cpu6502 cpu;

    // Peripherals.
    private Keyboard keyboard;
    private Joystick joystick;
    private Tape tape;
    private Disk microdisc;

    // Platform specific component (technically, the PSG object also is)
    private KeyboardMatrix keyboardMatrix;
    private PixelData pixelData;

    private boolean paused = true;
    private boolean lastWarpSpeed = false;

    private MachineType machineType;

    // These control what part of the generate pixel data is rendered to the screen.
    private int screenLeft;
    private int screenRight;
    private int screenTop;
    private int screenBottom;
    private int screenWidth;
    private int screenHeight;

    /**
     * Constructor for Machine. 
     * 
     * @param psg
     * @param keyboardMatrix
     * @param pixelData
     */
    public Machine(AYPSG psg, KeyboardMatrix keyboardMatrix, PixelData pixelData) {
        if (psg != null) {
            this.psg = psg;
        } else {
            this.psg = new AY38912PSG();
        }
        this.keyboardMatrix = keyboardMatrix;
        this.pixelData = pixelData;
    }

    /**
     * Initialises the machine, and optionally loads the given program file (if
     * provided).
     * 
     * @param basicRom     The BASIC ROM to load into memory.
     * @param microdiscRom The microdisc ROM to load into memory when using disks.
     * @param program      Optional program to run.
     * @param machineType  The type of Oric machine, i.e. PAL or NTSC.
     * @param ramType      The RAM configuration to use.
     */
    public void init(
            byte[] basicRom, byte[] microdiscRom, Program program, 
            MachineType machineType, RamType ramType) {
        
        Snapshot snapshot = null;
        
        this.machineType = machineType;

        // Create the microprocessor.
        cpu = new Cpu6502(snapshot);

        // Create the ULA chip and configure it as per the current TV type.
        ula = new Ula(pixelData, machineType, snapshot);

        // Create the peripherals.
        keyboard = new Keyboard(keyboardMatrix, psg);
        joystick = new Joystick(keyboard, JoystickType.ARROW_KEYS);

        // Create the VIA chip.
        via = new Via(cpu, keyboard, snapshot);

        // Initialise the AY-3-8912 PSG
        psg.init(via, keyboard, snapshot);

        // Create Microdisc disk controller.
        microdisc = new Disk(cpu);

        // Now we create the memory, which will include mapping the ULA chip,
        // the VIA chips, and the creation of RAM chips and ROM chips.
        memory = new Memory(cpu, ula, via, microdisc, basicRom, microdiscRom, snapshot);

        tape = new Tape(cpu, memory);

        // Set up the screen dimensions based on the ULA chip settings. Aspect ratio of
        // 5:4.
        screenWidth = ((machineType.getVisibleScreenHeight() / 4) * 5);
        screenHeight = machineType.getVisibleScreenHeight();
        screenLeft = machineType.getHorizontalOffset();
        screenRight = screenLeft + machineType.getVisibleScreenWidth();
        screenTop = machineType.getVerticalOffset();
        screenBottom = screenTop + machineType.getVisibleScreenHeight();

        // Check if the resource parameters have been set.
        byte[] programData = (program != null? program.getProgramData() : null);
        if ((programData != null) && (programData.length > 0)) {
            String programType = program.getProgramType();
            String programFile = program.getFilePath();
            if ("ROM".equals(programType)) {
                // Loads the ROM file over top of the default BASIC ROM.
                memory.loadCustomRom(programData);

            } else if ("TAPE".equals(programType)) {
                // Sets up the tape data to be loaded automatically at BASIC startup.
                // TODO: Not sure if the parent folder is required or not.
                tape.loadTape(programData, null);
            } else if ("DISK".equals(programType)) {
                // Insert the disk ready to be booted.
                microdisc.insertDisk(programFile, programData);
            }
        }

        // If the state of the machine was not loaded from a snapshot file, then we
        // begin with a reset.
        if (snapshot == null) {
            cpu.reset();
        }
    }

    /**
     * Updates the state of the machine of the machine until a frame is complete
     * 
     * @param warpSpeed true If the machine is running at warp speed.
     */
    public void update(boolean warpSpeed) {
        boolean frameComplete = false;
        if (warpSpeed && !lastWarpSpeed) {
            // We pause sound during warp speed
            psg.pauseSound();
        } else if (lastWarpSpeed && !warpSpeed) {
            // And resume sound when warp speed ends.
            psg.resumeSound();
        }
        lastWarpSpeed = warpSpeed;
        do {
            frameComplete |= ula.emulateCycle();
            cpu.emulateCycle();
            via.emulateCycle();
            microdisc.emulateCycle();
            if (!warpSpeed) {
                psg.emulateCycle();
            }
        } while (!frameComplete);
    }

    /**
     * Gets whether the last frame was updated at warp speed, or not.
     * 
     * @return true if the last frame was updated at warp speed; otherwise false.
     */
    public boolean isLastWarpSpeed() {
        return lastWarpSpeed;
    }

    /**
     * @return the screenLeft
     */
    public int getScreenLeft() {
        return screenLeft;
    }

    /**
     * @return the screenRight
     */
    public int getScreenRight() {
        return screenRight;
    }

    /**
     * @return the screenTop
     */
    public int getScreenTop() {
        return screenTop;
    }

    /**
     * @return the screenBottom
     */
    public int getScreenBottom() {
        return screenBottom;
    }

    /**
     * @return the screenWidth
     */
    public int getScreenWidth() {
        return screenWidth;
    }

    /**
     * @return the screenHeight
     */
    public int getScreenHeight() {
        return screenHeight;
    }

    /**
     * Emulates a single machine cycle.
     * 
     * @return true If the ULA chip has indicated that a frame should be rendered.
     */
    public boolean emulateCycle() {
        boolean render = ula.emulateCycle();
        cpu.emulateCycle();
        via.emulateCycle();
        microdisc.emulateCycle();
        psg.emulateCycle();
        return render;
    }

    /**
     * Pauses and resumes the Machine.
     * 
     * @param paused true to pause the machine, false to resume.
     */
    public void setPaused(boolean paused) {
        this.paused = paused;

        // Pass this on to the AY-3-8912 so that it can stop the SourceDataLine.
        if (paused) {
            this.psg.pauseSound();
        } else {
            this.psg.resumeSound();
        }
    }

    /**
     * Returns whether the Machine is paused or not.
     * 
     * @return true if the machine is paused; otherwise false.
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Invoked when the Machine is being terminated.
     */
    public void dispose() {
        // Tell the PSG to free up its sound resources.
        this.psg.dispose();
    }

    /**
     * Gets the MachineType of this Machine, i.e. either PAL or NTSC.
     * 
     * @return The MachineType of this Machine, i.e. either PAL or NTSC.
     */
    public MachineType getMachineType() {
        return machineType;
    }

    /**
     * Gets the Keyboard of this Machine.
     * 
     * @return The Keyboard of this Machine.
     */
    public Keyboard getKeyboard() {
        return keyboard;
    }

    /**
     * Gets the Joystick of this Machine.
     * 
     * @return The Joystick of this Machine.
     */
    public Joystick getJoystick() {
        return joystick;
    }

    /**
     * Gets the Cpu6502 of this Machine.
     * 
     * @return The Cpu6502 of this Machine.
     */
    public Cpu6502 getCpu() {
        return cpu;
    }
}
