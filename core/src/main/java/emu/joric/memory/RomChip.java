package emu.joric.memory;

/**
 * This class emulates a ROM chip.
 *
 * @author Lance Ewing
 */
public class RomChip extends MemoryMappedChip {

  /**
   * Reads the value of the given memory address.
   *
   * @param address the address to read the byte from.
   *
   * @return the contents of the memory address.
   */
  public int readMemory(int address) {
    return mem[address];
  }

  /**
   * Writes a value to the given memory address.
   *
   * @param address the address to write the value to.
   * @param value the value to write to the given address.
   */
  public void writeMemory(int address, int value) {
    // Has no effect.
  }
}
