package emu.joric.io;

import emu.joric.cpu.Cpu6502;
import emu.joric.memory.MemoryMappedChip;
import emu.joric.snap.Snapshot;

/**
 * This class emulates a 6522 VIA IO/timer chip.
 * 
 * @author Lance Ewing
 */
public class Via extends MemoryMappedChip {

  // Constants for the 16 internal memory mapped registers.
  private static final int VIA_REG_0 = 0;
  private static final int VIA_REG_1 = 1;
  private static final int VIA_REG_2 = 2;
  private static final int VIA_REG_3 = 3;
  private static final int VIA_REG_4 = 4;
  private static final int VIA_REG_5 = 5;
  private static final int VIA_REG_6 = 6;
  private static final int VIA_REG_7 = 7;
  private static final int VIA_REG_8 = 8;
  private static final int VIA_REG_9 = 9;
  private static final int VIA_REG_10 = 10;
  private static final int VIA_REG_11 = 11;
  private static final int VIA_REG_12 = 12;
  private static final int VIA_REG_13 = 13;
  private static final int VIA_REG_14 = 14;
  private static final int VIA_REG_15 = 15;
  
  // Constants for the INTERRUPT bits of the IFR.
  private static final int IRQ_RESET        = 0x7F;
  private static final int TIMER1_RESET     = 0xBF;
  private static final int TIMER2_RESET     = 0xDF;
  private static final int CB1_RESET        = 0xEF;
  private static final int CB2_RESET        = 0xF7;
  private static final int CB1_AND_2_RESET  = (CB1_RESET & CB2_RESET);
  private static final int SHIFT_RESET      = 0xFB;
  private static final int CA1_RESET        = 0xFD;
  private static final int CA2_RESET        = 0xFE;
  private static final int CA1_AND_2_RESET  = (CA1_RESET & CA2_RESET);
  private static final int IRQ_SET          = 0x80;
  private static final int TIMER1_SET       = 0x40;
  private static final int TIMER2_SET       = 0x20;
  private static final int CB1_SET          = 0x10;
  private static final int CB2_SET          = 0x08;
  private static final int SHIFT_SET        = 0x04;
  private static final int CA1_SET          = 0x02;
  private static final int CA2_SET          = 0x01;
  
  // Constants for timer modes.
  private static final int ONE_SHOT         = 0x00;
  
  // CA1 and CB1 control constants.
  private static final int NEGATIVE_EDGE = 0;
  private static final int POSITIVE_EDGE = 1;
  
  // CA2 and CB2 control constants.
  private static final int INPUT_MODE_NEGATIVE_EDGE             = 0;
  private static final int INPUT_MODE_NEGATIVE_EDGE_INDEPENDENT = 1;
  private static final int INPUT_MODE_POSITIVE_EDGE             = 2;
  private static final int INPUT_MODE_POSITIVE_EDGE_INDEPENDENT = 3;
  private static final int OUTPUT_MODE_HANDSHAKE                = 4;
  private static final int OUTPUT_MODE_PULSE                    = 5;
  private static final int OUTPUT_MODE_MANUAL_LOW               = 6;
  private static final int OUTPUT_MODE_MANUAL_HIGH              = 7;
  
  //  3. Shift Register Control
  //
  //  The Shift Register operating mode is selected as follows:
  //
  //  ACR4 ACR3 ACR2 Mode
  //
  //   0    0    0   Shift Register Disabled.
  //   0    0    1   Shift in under control of Timer 2.
  //   0    1    0   Shift in under control of system clock.
  //   0    1    1   Shift in under control of external clock pulses.
  //   1    0    0   Free-running output at rate determined by Timer 2.
  //   1    0    1   Shift out under control of Timer 2.
  //   1    1    0   Shift out under control of the system clock.
  //   1    1    1   Shift out under control of external clock pulses.
  private static final int SHIFT_REGISTER_DISABLED = 0;
  private static final int SHIFT_IN_TIMER_2 = 1;
  private static final int SHIFT_IN_SYSTEM_CLOCK = 2;
  private static final int SHIFT_IN_EXTERNAL_CLOCK = 3;
  private static final int SHIFT_OUT_FREE_RUNNING = 4;
  private static final int SHIFT_OUT_TIMER_2 = 5;
  private static final int SHIFT_OUT_SYSTEM_CLOCK = 6;
  private static final int SHIFT_OUT_EXTERNAL_CLOCK = 7;
  
  // Port B
  protected int outputRegisterB;            // Reg 0 WRITE?   (Reg 15 but no handshake)
  protected int inputRegisterB;             // Reg 0 READ?    (Reg 15 but no handshake)
  protected int portBPins;
  protected int dataDirectionRegisterB;     // Reg 2
  
  // Port A
  protected int outputRegisterA;            // Reg 1
  protected int inputRegisterA;
  protected int portAPins;
  protected int dataDirectionRegisterA;     // Reg 3
  
  // Timer 1
  protected int timer1Counter;
  protected int timer1Latch;
  protected boolean timer1Loaded;
  
  // Timer 2
  protected int timer2Counter;
  protected int timer2Latch;
  protected boolean timer2Loaded;
  
  // Shift Register
  protected int shiftRegister;              // Reg 10
  
  protected int auxiliaryControlRegister;   // Reg 11
  protected int peripheralControlRegister;  // Reg 12
  protected int interruptFlagRegister;      // Reg 13
  protected int interruptEnableRegister;    // Reg 14
  
  protected int timer1PB7Mode;
  protected int timer1Mode;
  protected int timer2Mode;
  protected int shiftRegisterMode;
  protected int portALatchMode;
  protected int portBLatchMode;
  
  protected int ca1ControlMode = 0;
  protected int ca2ControlMode = 0;
  protected int cb1ControlMode = 0;
  protected int cb2ControlMode = 0;
  
  protected int ca1;
  protected int ca2;
  protected int cb1;
  protected int cb2;
  
  /**
   * This flag is set to true when timer1 is operating in the one shot mode and
   * has just decremented through zero, i.e. is 0xFFFF.
   */
  private boolean timer1HasShot;
  
  /**
   * True when timer2 has just reached zero when in one shot mode.
   */
  private boolean timer2HasShot;
  
  /**
   * Whether to reset the IRQ signal when the IRQ flags reset.
   */
  private boolean autoResetIrq;
  
  /**
   * The CPU that the Oric is using. This is where the VIA IRQ signals will be sent.
   */
  private Cpu6502 cpu6502;
  
  /**
   * The Keyboard from which we get the current keyboard state from.
   */
  private Keyboard keyboard;
  
  /**
   * Constructor for VIA6522.
   * 
   * @param cpu6502 The CPU that the Oric is using. This is where VIA IRQ signals will be sent.
   * @param keyboard The Keyboard from which we get the current keyboard state from.
   * @param snapshot Optional snapshot of the machine state to start with.
   */
  public Via(Cpu6502 cpu6502, Keyboard keyboard, Snapshot snapshot) {
    this.autoResetIrq = true;
    this.cpu6502 = cpu6502;
    this.keyboard = keyboard;
    if (snapshot != null) {
      loadSnapshot(snapshot);
    }
  }
  
  /**
   * Writes a byte into one of the 16 VIA registers.
   * 
   * @param address The address to write to.
   * @param value The byte to write into the address.
   */
  public void writeMemory(int address, int value) {
    switch (address & 0x000F) {
      case VIA_REG_0: // ORB/IRB
        outputRegisterB = value;
        updatePortBPins();
        interruptFlagRegister &= CB1_AND_2_RESET;
        updateIFRTopBit();
        break;

      case VIA_REG_1: // ORA/IRA
        outputRegisterA = value;
        updatePortAPins();
        interruptFlagRegister &= CA1_AND_2_RESET;
        updateIFRTopBit();
        break;

      case VIA_REG_2: // DDRB
        dataDirectionRegisterB = value;
        updatePortBPins();
        break;

      case VIA_REG_3: // DDRA
        dataDirectionRegisterA = value;
        updatePortAPins();
        break;
        
      case VIA_REG_4: // Timer 1 low-order counter (WRITE sets low-order latch)
        //timer1LatchLow = value;
        timer1Latch = (timer1Latch & 0xFF00 | (value & 0xFF));
        break;
  
      case VIA_REG_5: // Timer 1 high-order counter
        timer1Latch = (timer1Latch & 0xFF) | ((value << 8) & 0xFF00);
        timer1Counter = timer1Latch;
        timer1Loaded= true;          // Informs emulateCycle that the timer was load this cycle.
        interruptFlagRegister &= TIMER1_RESET;
        updateIFRTopBit();
        timer1HasShot = false;
        break;
  
      case VIA_REG_6: // Timer 1 low-order latches
        timer1Latch = (timer1Latch & 0xff00 | (value & 0xFF));
        break;
  
      case VIA_REG_7: // Timer 1 high-order latches
        timer1Latch = (timer1Latch & 0xFF) | ((value << 8) & 0xFF00);
        interruptFlagRegister &= TIMER1_RESET;
        updateIFRTopBit();
        break;
  
      case VIA_REG_8: // Timer 2 low-order counter
        timer2Latch = (value & 0xFF);
        break;
  
      case VIA_REG_9: // Timer 2 high-order counter
        timer2Counter = timer2Latch | ((value << 8) & 0xFF00);
        timer2Loaded = true;  // Informs emulateCycle that the timer was load this cycle.
        interruptFlagRegister &= TIMER2_RESET;
        updateIFRTopBit();
        timer2HasShot = false;
        break;
  
      case VIA_REG_10: // Shift Register.
        shiftRegister = value;
        interruptFlagRegister &= SHIFT_RESET;
        updateIFRTopBit();
        break;
  
      case VIA_REG_11: // Auxiliary Control Register.
        auxiliaryControlRegister = value;
        timer1PB7Mode = (value & 0x80) >> 7;
        timer1Mode = (value & 0x40) >> 6;
        timer2Mode = (value & 0x20) >> 5;
        shiftRegisterMode = (value & 0x1C) >> 2;
        portALatchMode = (value & 0x01);
        portBLatchMode = (value & 0x02) >> 1;
        break;
  
      case VIA_REG_12: // Peripheral Control Register.
        peripheralControlRegister = value;
        
        // 1. CA1 Control
        // Bit 0 of the Peripheral Control Register selects the active transition of the input signal
        // applied to the CA1 interrupt input pin. If this bit is a logic 0, the CAl interrupt flag will be
        // set by a negative transition (high to low) of the signal on the CAl pin. If PCRO is a logic 1,
        // the CAl interrupt flag will be set by a positive transition (low to high) of this signal.
        ca1ControlMode = (value & 0x01);
        
        // 2. CA2 Control
        // The CA2 pin can be programmed to act as an interrupt input or as a peripheral control
        // output. As an input, CA2 operates in two modes, differing primarily in the methods available
        // for resetting the interrupt flag. Each of these two input modes can operate with either a
        // positive or a negative active transition as described above for CAl.
        // In the output mode, the CA2 pin combines the operations performed on the CA2 and CB2 pins of
        // the MCS6520. This added flexibility allows processor to perform a normal "write" handshaking in
        // a system which uses CBl and CB2 for the serial operations described above. The CA2 operating modes
        // are selected as follows:
        //
        // PCR3 PCR2 PCR1 Mode
        //
        //  0    0    0
        //                Input mode— Set CA2 interrupt flag (IFRO) on a negative
        //                transition of the input signal. Clear IFRO on a read or
        //                write of the Peripheral A Output Register.
        //  0    0    1
        //                Independent interrupt input mode— Set IFRO on a negative
        //                transition of the CA2 input signal. Reading or writing
        //                ORA does not clear the CA2 interrupt flag.
        //  0    1    0
        //                Input mode— Set CA2 interrupt flag on a positive transition
        //                of the CA2 input signal. Clear IFRO with a read or
        //                write of the Peripheral A Output Register.
        //  0    1    1
        //                Independent interrupt input mode— Set IFRO on a positive
        //                transition of the CA2 input signal. Reading or writing
        //                ORA does not clear the CA2 interrupt flag.
        //  1    0    0
        //                Handshake output mode— Set CA2 output low on a read or
        //                write of the Peripheral A Output Register. Reset CA2
        //                high with an active transition on CAl.
        //  1    0    1   
        //                Pulse Output mode— CA2 goes low for one cycle following
        //                a read or write of the Peripheral A Output Register.
        //  1    1    0 
        //                Manual output mode— The CA2 output is held low in this mode.
        //  1    1    1
        //                Manual output mode— The CA2 output is held high in this mode.
        ca2ControlMode = ((value & 0x0E) >> 1);
        if (ca2ControlMode == OUTPUT_MODE_MANUAL_LOW) ca2 = 0;
        if (ca2ControlMode == OUTPUT_MODE_MANUAL_HIGH) ca2 = 1;
        
        // 3. CBl Control
        // Control of the active transition of the CBl input signal operates in exactly the same manner as that
        // described above for CAl. If PCR4 is a logic 0 the CBl interrupt flag (IFR4) will be set by a negative
        // transition of the CBl input signal and cleared by a read or write of the ORB register. If PCR4 is a logic
        // 1, IFR4 will be set by a positive transition of CBl.
        cb1ControlMode = ((value & 0x10) >> 4);
        
        // 4. CB2 Control
        // With the serial port disabled, operation of the CB2 pin is a function of the three high order bits of
        // the PCR. The CB2 modes are very similar to those described previously for CA2. These modes are selected
        // as follows;
        //
        // PCR7 PCR6 PCR5 Mode
        //
        //  0    0    0
        //                 Interrupt input mode— Set CB2 interrupt flag (IFR3) on a
        //                 negative transition of the CB2 input signal. Clear IFR3
        //                 on a read or write of the Peripheral B Output Register.
        //  0    0    1
        //                 Independent interrupt input mode— Set IFR3 on a negative
        //                 transition of the CB2 input signal. Reading or writing ORB
        //                 does not clear the interrupt flag.
        //  0    1    0
        //                 Input mode— Set CB2 interrupt flag on a positive transition
        //                 of the CB2 input signal. Clear the CB2 interrupt flag on a
        //                 read or write of ORB.
        //  0    1    1
        //                 Independent input mode— Set IFR3 on a positive transition
        //                 of the CB2 input signal. Reading or writing ORB does not
        //                 clear the CB2 interrupt flag.
        //  1    0     0
        //                 Handshake output mode— Set CB2 low on a write ORB operation.
        //                 Reset CB2 high with an active transition of the CBl
        //                 input signal.
        //  1    0     1
        //                 Pulse output mode— Set CB2 low for one cycle following a
        //                 write ORB operation.
        //  1    1     0
        //                 Manual output mode— The CB2 output is held low in this
        //                 mode.
        //  1    1     1
        //                 Manual output mode— The CB2 output is held high in this
        //                 mode.
        cb2ControlMode = ((value & 0xE0) >> 5);
        if (cb2ControlMode == OUTPUT_MODE_MANUAL_LOW) cb2 = 0;
        if (cb2ControlMode == OUTPUT_MODE_MANUAL_HIGH) cb2 = 1;
        break;
  
      case VIA_REG_13: // Interrupt Flag Register
        // Note: Top bit cannot be cleared directly
        interruptFlagRegister &= ~(value & 0x7f);
        updateIFRTopBit();
        break;
  
      case VIA_REG_14: // Interrupt Enable Register
        if ((value & 0x80) == 0) {
          interruptEnableRegister &= ~(value & 0xff);
        } else {
          interruptEnableRegister |= (value & 0xff);
        }
        interruptEnableRegister &= 0x7f;
        updateIFRTopBit();
        break;
  
      case VIA_REG_15: // ORA/IRA (no handshake)
        outputRegisterA = value;
        updatePortAPins();
        break;
      }
  }
  
  // 
  private static final int PORTB_INPUT_LATCHING = 0x02;
  private static final int PORTA_INPUT_LATCHING = 0x01;

  /**
   * Reads a value from one of the 16 VIA registers.
   * 
   * @param address The address to read the register value from.
   */
  public int readMemory(int address) {
    int value = 0;

    switch (address & 0x000F) {
      case VIA_REG_0: // ORB/IRB
        if ((auxiliaryControlRegister & PORTB_INPUT_LATCHING) == 0) {
          // If you read a pin on IRB and the pin is set to be an input (with latching
          // disabled), then you will read the current state of the corresponding
          // PB pin.
          value = getPortBPins() & (~dataDirectionRegisterB);
        } else {
          // If you read a pin on IRB and the pin is set to be an input (with latching
          // enabled), then you will read the actual IRB.
          value = inputRegisterB & (~dataDirectionRegisterB);
        }
        // If you read a pin on IRB and the pin is set to be an output, then you will
        // actually read ORB, which contains the last value that was written to
        // port B.
        value = value | (outputRegisterB & dataDirectionRegisterB);
        interruptFlagRegister &= CB1_AND_2_RESET;
        updateIFRTopBit();
        break;
  
      case VIA_REG_1: // ORA/IRA
        if ((auxiliaryControlRegister & PORTA_INPUT_LATCHING) == 0) {
          // If you read a pin on IRA and input latching is disabled for port A,
          // then you will simply read the current state of the corresponding PA
          // pin, REGARDLESS of whether that pin is set to be an input or an
          // output.
          value = getPortAPins();
        } else {
          // If you read a pin on IRA and input latching is enabled for port A,
          // then you will read the actual IRA, which is the last value that was
          // latched into IRA.
          value = inputRegisterA;
        }
        interruptFlagRegister &= CA1_AND_2_RESET;
        updateIFRTopBit();
        break;
  
      case VIA_REG_2: // DDRB
        value = dataDirectionRegisterB;
        break;
  
      case VIA_REG_3: // DDRA
        value = dataDirectionRegisterA;
        break;
        
      case VIA_REG_4: // Timer 1 low-order counter
        value = (timer1Counter & 0xFF);
        interruptFlagRegister &= TIMER1_RESET;
        updateIFRTopBit();
        break;
  
      case VIA_REG_5: // Time 1 high-order counter
        value = ((timer1Counter >> 8) & 0xFF);
        break;
  
      case VIA_REG_6: // Timer 1 low-order latches
        value = (timer1Latch & 0xFF);
        break;
  
      case VIA_REG_7: // Timer 1 high-order latches
        value = ((timer1Latch >> 8) & 0xFF);
        break;
  
      case VIA_REG_8: // Timer 2 low-order counter
        value = (timer2Counter & 0xFF);
        interruptFlagRegister &= TIMER2_RESET;
        updateIFRTopBit();
        break;
  
      case VIA_REG_9: // Timer 2 high-order counter
        value = ((timer2Counter >> 8) & 0xFF);
        break;
  
      case VIA_REG_10: // Shift register
        value = shiftRegister;
        interruptFlagRegister &= SHIFT_RESET;
        updateIFRTopBit();
        break;
  
      case VIA_REG_11: // Auxiliary control register
        value = auxiliaryControlRegister;
        break;
  
      case VIA_REG_12: // Peripheral Control Register
        value = peripheralControlRegister;
        break;
  
      case VIA_REG_13: // Interrupt Flag Register
        updateIFRTopBit();
        value = interruptFlagRegister;
        break;
  
      case VIA_REG_14: // Interrupt Enable Register
        value = (interruptEnableRegister & 0x7F) | 0x80;
        break;
  
      case VIA_REG_15: // ORA/IRA (no handshake)
        if ((auxiliaryControlRegister & PORTA_INPUT_LATCHING) == 0) {
          // If you read a pin on IRA and input latching is disabled for port A,
          // then you will simply read the current state of the corresponding PA
          // pin, REGARDLESS of whether that pin is set to be an input or an
          // output.
          value = getPortAPins();
        } else {
          // If you read a pin on IRA and input latching is enabled for port A,
          // then you will read the actual IRA, which is the last value that was
          // latched into IRA.
          value = inputRegisterA;
        }
        break;
    }

    return value;
  }

  /**
   * Returns a string containing details about the current state of the chip.
   * 
   * @return a string containing details about the current state of the chip.
   */
  public String toString() {
    StringBuffer buf = new StringBuffer();

    buf.append("peripheralControlRegister: ");
    buf.append(Integer.toBinaryString(peripheralControlRegister));
    buf.append("\n");
    buf.append("auxiliaryControlRegister: ");
    buf.append(Integer.toBinaryString(auxiliaryControlRegister));
    buf.append("\n");
    buf.append("timer1 latch: ");
    buf.append(Integer.toHexString(timer1Latch));
    buf.append("\n");
    buf.append("timer1 counter: ");
    buf.append(Integer.toHexString(timer1Counter));
    buf.append("\n");
    buf.append("timer2 latch: ");
    buf.append(Integer.toHexString(timer2Latch));
    buf.append("\n");
    buf.append("timer2 counter: ");
    buf.append(Integer.toHexString(timer2Counter));
    buf.append("\n");
    buf.append("interruptEnableRegister: ");
    buf.append(Integer.toHexString(interruptEnableRegister));
    buf.append("\n");
    buf.append("interruptFlagRegister: ");
    buf.append(Integer.toHexString(interruptFlagRegister));
    buf.append("\n");

    return (buf.toString());
  }

  /**
   * Updates the IFR top bit and sets the IRQ pin if needed.
   */
  protected void updateIFRTopBit() {
    // The top bit says whether any interrupt is active and enabled
    if ((interruptFlagRegister & (interruptEnableRegister & 0x7f)) == 0) {
      interruptFlagRegister &= IRQ_RESET;
      if (autoResetIrq) {
        // TODO: Only do this if IRQ was set last time we checked (i.e. the
        // change is what we react to)???
        updateIrqPin(0);
      }
    } else {
      interruptFlagRegister |= IRQ_SET;
      // TODO: Only do this if IRQ was not set last time we checked (i.e. the
      // change is what we react to????
      updateIrqPin(1);
    }
  }

  /**
   * Notifies the 6502 of the change in the state of the IRQ pin. In this case 
   * it is the 6502's IRQ pin that this 6522 IRQ is connected to.
   * 
   * @param The current state of this VIA chip's IRQ pin (1 or 0).
   */
  protected void updateIrqPin(int pinState) {
    // The VIA IRQ pin goes to the 6502 IRQ
    if (pinState == 1) {
      cpu6502.setInterrupt(Cpu6502.S_IRQ);
    } else {
      cpu6502.clearInterrupt(Cpu6502.S_IRQ);
    }
  }

  /**
   * Emulates a single cycle of this VIA chip.
   */
  public void emulateCycle() {
    // IMPORTANT NOTE: If the timer 1 latch is set to 2 during cycle T0, then on T1 it
    // would have a value of 2, then T2 a value of 1, T3 a value of 0, T4 a value of 0xFFFF
    // and then T5 back to a value of 2 again. So it isn't just 2 cycles it counts but 
    // N + 2 (the interrupt happens at N + 1.5 cycles).
    
    if (!timer1Loaded) {
      // Check if counter was at 0xFFFF at the start of this cycle.
      if (timer1Counter == 0xFFFF) {
        if (timer1Mode == ONE_SHOT) {
          // Timed interrupt each time T1 is loaded (one shot). 
          // Set the interrupt flag only if the timer has been reloaded.
          if (!timer1HasShot) {
            interruptFlagRegister |= TIMER1_SET;
            updateIFRTopBit();
            timer1HasShot = true;
          }
          
          // Counter continues to count down from 0xFFFF.
          timer1Counter = 0xFFFE;
          
        } else {
          // Continuous interrupts (free-running).
          // Reload from latches and raise interrupt.
          timer1Counter = timer1Latch;
          interruptFlagRegister |= TIMER1_SET;
          updateIFRTopBit();
          timer1HasShot = true;
        }
      }
      else {
        // Decrement by one, wrapping around to 0XFFFF after zero.
        timer1Counter = (timer1Counter - 1) & 0xFFFF;
      }
    } else {
      timer1Loaded = false;
    }

    if (!timer2Loaded) {
      if (timer2Mode == ONE_SHOT) {
        // Note: Timer 2 does not behaviour in the same way with regards to when the 
        // interrupt occurs. For Timer 1, it is when the value is 0xFFFF, but for 
        // timer 2, it is when the counter is 0x0000.
        
        if (!timer2HasShot && (timer2Counter == 0)) {
          interruptFlagRegister |= TIMER2_SET;
          updateIFRTopBit();
          timer2HasShot = true;
        }
        
        // Decrement by one, wrapping around to 0XFFFF after zero.
        timer2Counter = (timer2Counter - 1) & 0xFFFF;
        
      } else {
        // TODO: PB6 pulse counting.
      }
    } else {
      timer2Loaded = false;
    }

    // Shift the shift register.
    if (shiftRegisterMode != 0) {
      // TODO: Implement shift register.
      switch (shiftRegisterMode) {
      case 0x00:
        break;
      case 0x01:
        break;
      case 0x02:
        break;
      case 0x03:
        break;
      case 0x04:
        break;
      case 0x05:
        break;
      case 0x06:
        break;
      case 0x07:
        break;
      }
    }
  }
  
  /**
   * Updates the state of the Port A pins based on the current values of the 
   * ORA and DDRA.
   */
  protected void updatePortAPins() {
    // Any pins that are inputs must be left untouched. 
    int inputPins = (portAPins & (~dataDirectionRegisterA));
    
    // Pins that are outputs should be set to 1 or 0 depending on what is in the ORA.
    int outputPins = (outputRegisterA & dataDirectionRegisterA);
    
    portAPins = inputPins | outputPins; 
  }
  
  /**
   * Updates the state of the Port B pins based on the current values of the 
   * ORB and DDRB.
   */
  protected void updatePortBPins() {
    // Any pins that are inputs must be left untouched. 
    int inputPins = (portBPins & (~dataDirectionRegisterB));
    
    // Pins that are outputs should be set to 1 or 0 depending on what is in the ORB.
    int outputPins = (outputRegisterB & dataDirectionRegisterB);
    
    portBPins = inputPins | outputPins;
  }
  
  /**
   * Returns the current values of the Port A pins.
   *
   * @return the current values of the Port A pins.
   */
  public int getPortAPins() {
    return portAPins;
  }
  
  /**
   * Returns the current values of the Port B pins.
   * 
   * @return the current values of the Port B pins.
   */
  protected int getPortBPins() {
    //    Port B
    //    The lowest three bits are used to supply the row when looking at the keyboard
    //    Bit 3 of port B is set to 1 when a key is pressed
    //    Bit 4 is connected to the strobe line on the printer socket – when 0
    //    the printer will expect data to be present on port A.
    //    Bit 6 controls the relay circuit on the cassette socket.
    //    Bit 7 connects to the cassette output circuitry
    
    if (keyboard.isKeyPressed(outputRegisterB & 0x07)) {
      return 0x08 | (portBPins & 0xF7);
      
    } else {
      return portBPins & 0xF7;
    }
  }
  
  /**
   * @return the ca2
   */
  public int getCa2() {
    return ca2;
  }

  /**
   * @return the cb2
   */
  public int getCb2() {
    return cb2;
  }

  /**
   * Initialises the VIA chip with the state stored in the Snapshot.
   * 
   * @param snapshot The Snapshot to load the initial state of the VIA chip from.
   */
  private void loadSnapshot(Snapshot snapshot) {
    // Load Port B state.
    outputRegisterB = snapshot.getVia1OutputRegisterB();
    inputRegisterB = snapshot.getVia1InputRegisterB();
    dataDirectionRegisterB = snapshot.getMemoryArray()[0x0302];
    updatePortBPins();
    
    // Load Port A state.
    outputRegisterA = snapshot.getVia1OutputRegisterA();
    inputRegisterA = snapshot.getVia1InputRegisterA();;
    dataDirectionRegisterA = snapshot.getMemoryArray()[0x0303];
    updatePortAPins();
    
    // Timer 1
    timer1Counter = (snapshot.getMemoryArray()[0x0305] << 8) | (snapshot.getMemoryArray()[0x0304]);
    timer1Latch = (snapshot.getMemoryArray()[0x0307] << 8) | (snapshot.getMemoryArray()[0x0306]);
    
    // Timer 2
    // TODO: Timer 2 latch.
    //timer2Latch = snapshot.getVia1Timer2LatchLow();
    timer2Counter = (snapshot.getMemoryArray()[0x0309] << 8) | (snapshot.getVia1Timer2CounterLow());
    
    // Shift Register
    shiftRegister = snapshot.getMemoryArray()[0x911A];
    
    // Load the Auxilliary Control Register state.
    auxiliaryControlRegister = snapshot.getMemoryArray()[0x030B];
    timer1PB7Mode = (auxiliaryControlRegister & 0x80) >> 7;
    timer1Mode = (auxiliaryControlRegister & 0x40) >> 6;
    timer2Mode = (auxiliaryControlRegister & 0x20) >> 5;
    shiftRegisterMode = (auxiliaryControlRegister & 0x1C) >> 2;
    portALatchMode = (auxiliaryControlRegister & 0x01);
    portBLatchMode = (auxiliaryControlRegister & 0x02) >> 1;
    
    peripheralControlRegister = snapshot.getMemoryArray()[0x030C];
    
    interruptFlagRegister = snapshot.getVia1InterruptFlagRegister();
    interruptEnableRegister = snapshot.getVia1InterruptEnableRegister();
  }
}
