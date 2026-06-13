package emu.joric.gwt;

import com.badlogic.gdx.utils.TimeUtils;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.typedarrays.shared.Float32Array;
import com.google.gwt.typedarrays.shared.TypedArrays;

import emu.joric.io.Keyboard;
import emu.joric.io.Via;
import emu.joric.snap.Snapshot;
import emu.joric.sound.AYPSG;

/**
 * GWT/HTML5/Web implementation of the AY-3-8912 interface. Uses the Web Audio API, 
 * specifically an AudioWorklet.
 */
public class GwtAYPSG implements AYPSG {

    // The Oric runs at 1 MHz.
    private static final int CLOCK_1MHZ = 1000000;
    private static final int CYCLES_PER_SECOND = 1000000;

    // Samples are generated at the AudioContext's native rate, to avoid the
    // browser resampling at the context boundary. The cap exists because the
    // chip emulation's fixed point maths overflows a 32-bit int above 48 kHz
    // (the envelope period reaches (0xFFFF * updateStep) << 1, which is ~77%
    // of Integer.MAX_VALUE at 48 kHz), and rates above 48 kHz would provide no
    // real audible benefit anyway. The fallback rate is used only if creation 
    // of the AudioContext fails, in which case there is no audio output at all,
    // but the sample generation maths must remain sane. 22050 was the fixed
    // rate that was used before host-native rate support was added.
    public static final int MAX_SAMPLE_RATE = 48000;
    public static final int FALLBACK_SAMPLE_RATE = 22050;

    // Target time by which sample generation runs ahead of audio output. This
    // protects against scheduling delays in the web worker (e.g. a skipped
    // animation frame) and jitter between us and the hosts audio system, at 
    // the cost of latency between the emulation writing a register and the 
    // result being heard. The equivalent number of samples is derived from
    // this in sampleLatency.
    public static final int SAMPLE_LATENCY_MS = 140;

    // The actual sample rate, and values derived from it. See configureSampleRate.
    private int sampleRate;
    private double cyclesPerSample;
    private int sampleLatency;
    
    // The three channels' output stages are connected in parallel on the Oric,
    // into a load of R4 (1K) in parallel with the R2 + R3 branch (4K7 + 470),
    // so the channels interact: a loud channel pulls the shared output node
    // harder and suppresses the contribution of the others. This is modelled
    // as a resistor network. Each volume level presents a different effective
    // pull-up resistance at the channel output; the values below are from
    // bench measurements of a real AY chip (as fitted in MAME's ay8910.cpp,
    // BSD-3-Clause, derived from Matthew Westcott's December 2001 public
    // domain voltage measurements).
    private static final double[] CHANNEL_RES = {
            15950, 15350, 15090, 14760, 14275, 13620, 12890, 11370,
            10600,  8590,  7190,  5985,  4820,  3945,  3017,  2345 };
    private static final double RES_R_UP = 800000;
    private static final double RES_R_DOWN = 8000000;
    private static final double ORIC_LOAD_R = 838;

    // Calibration of the channels' drive strength against real Oric-1
    // hardware (June 2026): with one and then two channels output disabled
    // with their volume parked at 15, the playing channel's measured 
    // acoustic level dropped by around 6.2 dB and 10.7 dB respectively.
    // Applying a conductance scale factor of 2.32 to the resistor network
    // channel conductances reproduces both measurements (and, as independent
    // corroboration, brings the model's solo channel volume curve to within
    // 0.3 dB of Westcott's bench-measured DAC levels across the audible range).
    // The measurements of the Oric-1 were carried out with relatively basic
    // equipment - so there is scope for refining these numbers further in
    // future if greater accuracy is ever desired.
    private static final double CONDUCTANCE_SCALE = 2.32;

    // The mixed output level for every combination of the three channels'
    // volume levels, in sample units, baseline subtracted. Normalised so that
    // a single channel at volume 15 (with the others silent) produces the
    // same sample value as it always has (10920); the network model then
    // makes a full three channel chord come out around 5 dB quieter than the
    // simple mathematical sum of the three would.
    private static final float[] MIX_TABLE = buildMixTable();

    private static double mixNode(int a, int b, int c) {
        int n = (a != 0 ? 1 : 0) + (b != 0 ? 1 : 0) + (c != 0 ? 1 : 0);
        double gw = n / RES_R_UP;
        double gt = n / RES_R_UP + 3.0 / RES_R_DOWN + 1.0 / ORIC_LOAD_R;
        double g;
        g = CONDUCTANCE_SCALE / CHANNEL_RES[a]; gw += g; gt += g;
        g = CONDUCTANCE_SCALE / CHANNEL_RES[b]; gw += g; gt += g;
        g = CONDUCTANCE_SCALE / CHANNEL_RES[c]; gw += g; gt += g;
        return gw / gt;
    }

    private static float[] buildMixTable() {
        float[] table = new float[16 * 16 * 16];
        double base = mixNode(0, 0, 0);
        double scale = 10920.0 / (mixNode(15, 0, 0) - base);
        for (int a = 0; a < 16; a++) {
            for (int b = 0; b < 16; b++) {
                for (int c = 0; c < 16; c++) {
                    table[(a << 8) | (b << 4) | c] =
                            (float) ((mixNode(a, b, c) - base) * scale);
                }
            }
        }
        return table;
    }

    private static float lerp(float from, float to, float weight) {
        return from + (to - from) * weight;
    }

    // Constants for index values into output, count, and period arrays.
    private static final int A = 0;
    private static final int B = 1;
    private static final int C = 2;
    private static final int NOISE = 3;
    private static final int ENVELOPE = 4;

    private int[] output; // A, B, C and Noise
    private int[] count; // A, B, C, Noise and Envelope counters
    private int[] period; // A, B, C, Noise and Envelope periods

    // Channel volumes. Envelope volume takes effect depending on volume mode bit.
    private int volumeA;
    private int volumeB;
    private int volumeC;
    private int volumeEnvelope;

    // Current mixer disable/enable settings.
    private int enable;
    private boolean disableToneA;
    private boolean disableToneB;
    private boolean disableToneC;
    private boolean disableAllNoise;

    private int outNoise;
    private int random = 1;

    private int countEnv;
    private int hold;
    private int alternate;
    private int attack;
    private int holding;

    private int updateStep;
    private final int step = 0x8000;

    private int busControl1 = 0;
    private int busDirection = 0;
    private int addressLatch = 0;
    private int[] registers;

    private Float32Array sampleBuffer;
    private int sampleBufferOffset = 0;
    private double cyclesToNextSample;
    private SharedQueue sampleSharedQueue;

    // One-pole DC-blocker state and corner frequency used to model the AC coupling
    // capacitor on Oric audio output to remove the DC offset that the audio chip's
    // emulated unipolar signal would otherwise carry into the AudioWorklet output.
    // The filter coefficient R is derived from the sample rate (in configureSampleRate)
    // to keep the -3 dB corner at ~17.5 Hz regardless of rate, which should be below
    // any expected normally audible Oric content. (17.5 Hz is equivalent to the
    // R = 0.995 that was used when the sample rate was fixed at 22050 Hz.)
    // Note that this corner is lower than the genuine Oric speaker path, whose
    // coupling works out at ~90Hz based on the available schematics - so the
    // real machine had a shorter decay tail after DC level steps (its line/DIN
    // output corner was much lower, ~3Hz). Either way, a high-pass passes the
    // step transient itself at full height; the corner only shapes the tail.
    private static final float DC_BLOCKER_CORNER_HZ = 17.5f;
    private float dcBlockerR;
    private float dcBlockerX1;
    private float dcBlockerY1;

    // TODO: Remove these after debugging timing issue.
    private long cycleCount;
    private long startTime;
    private long sampleCount;
    
    private boolean writeSamplesEnabled;
    
    private PSGAudioWorklet audioWorklet;
    
    /**
     * The AY-3-8912 in the Oric gets its data from the 6522 VIA chip.
     */
    private Via via;

    /**
     * Constructor for GwtAYPSG (invoked by the UI thread).
     * 
     * @param gwtJOricRunner 
     */
    public GwtAYPSG(GwtJOricRunner gwtJOricRunner) {
        this((JavaScriptObject)null, FALLBACK_SAMPLE_RATE);
        initialiseAudioWorklet(gwtJOricRunner);
        // Now that the AudioContext exists, reconfigure for whatever rate it
        // actually opened at.
        configureSampleRate(audioWorklet.getSampleRate());
    }

    /**
     * Constructor for GwtAYPSG (invoked by the web worker).
     *
     * @param audioBufferSAB SharedArrayBuffer for the audio ring buffer.
     * @param sampleRate The sample rate of the AudioContext created by the UI thread.
     */
    public GwtAYPSG(JavaScriptObject audioBufferSAB, int sampleRate) {
        this.startTime = TimeUtils.millis();

        if (audioBufferSAB == null) {
            // Sized to hold 1 second at the maximum supported sample rate. The
            // capacity is primarily headroom; the latency that is heard is 
            // governed by SAMPLE_LATENCY_MS.
            audioBufferSAB = SharedQueue.getStorageForCapacity(MAX_SAMPLE_RATE);
        }
        this.sampleSharedQueue = new SharedQueue(audioBufferSAB);

        // Samples are pushed to the shared queue in chunks of 512, i.e. 4 of
        // the AudioWorklet's fixed 128-sample render quanta. This is push
        // granularity only, so is independent of sample rate; the latency that
        // is heard is governed by SAMPLE_LATENCY_MS.
        this.sampleBuffer = TypedArrays.createFloat32Array(512);
        this.sampleBufferOffset = 0;

        configureSampleRate(sampleRate);
    }

    /**
     * Sets the sample rate and the values derived from it. For the UI thread
     * instance, this is the rate of the AudioContext it created. For the web
     * worker instance, it is the rate received in the Initialise message.
     *
     * @param sampleRate The sample rate that samples will be played at.
     */
    private void configureSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
        this.cyclesPerSample = ((double) CYCLES_PER_SECOND) / sampleRate;
        this.sampleLatency = (sampleRate * SAMPLE_LATENCY_MS) / 1000;
        this.dcBlockerR = 1.0f - (float)((2 * Math.PI * DC_BLOCKER_CORNER_HZ) / sampleRate);
    }
    
    /**
     * Initialise the AY38912 PSG.
     * 
     * @param via      The 6522 VIA chip that the register data comes from.
     * @param keyboard The Keyboard that the AY-3-8912 Port A is connected to.
     * @param snapshot
     */
    public void init(Via via, Keyboard keyboard, Snapshot snapshot) {
        // Via and Keyboard are used with PORT A for scanning keyboard.
        this.via = via;
        keyboard.setPsg(this);

        updateStep = (int) (((long) step * 8L * (long) sampleRate) / (long) CLOCK_1MHZ);
        output = new int[] { 0, 0, 0, 0xFF };
        count = new int[] { updateStep, updateStep, updateStep, 0x7fff, updateStep };
        // Every period must be non-zero: writeSample's counter catch-up loops
        // never terminate on a zero period.
        period = new int[] { updateStep, updateStep, updateStep, updateStep, updateStep };
        registers = new int[16];

        volumeA = volumeB = volumeC = volumeEnvelope = 0;
        disableToneA = disableToneB = disableToneC = disableAllNoise = false;
        countEnv = hold = alternate = attack = 0;
        // The envelope starts out holding, as if the reset-default shape 0
        // (decay then hold) had already completed its decay to 0. It starts
        // moving when a program first writes the envelope shape register.
        holding = 1;
        enable = 0;
        outNoise = 0;
        random = 1;
        busControl1 = 0;
        busDirection = 0;
        addressLatch = 0;

        cyclesToNextSample = cyclesPerSample;

        dcBlockerX1 = 0f;
        dcBlockerY1 = 0f;
    }

    /**
     * Turns on sample writing to the sample buffer.
     */
    public void enableWriteSamples() {
        logToJSConsole("Enabling writing of samples...");
        writeSamplesEnabled = true;
    }
    
    /**
     * Returns whether the sample writing is currently enabled.
     * 
     * @return true if the sample writing is currently enabled, otherwise false.
     */
    public boolean isWriteSamplesEnabled() {
        return writeSamplesEnabled;
    }
    
    /**
     * Turn off sample writing to the sample buffer.
     */
    public void disableWriteSamples() {
        writeSamplesEnabled = false;
    }
    
    /**
     * Emulates a single cycle of activity for the AY-3-8912. This involves
     * responding to the CA2 and CB2 lines coming in from the 6522 VIA chip
     */
    public void emulateCycle() {
        cycleCount++;
        
        // Bus Control 1 is connected to the VIA CA2 line in the Oric.
        busControl1 = via.getCa2();

        // Bus Direction is connected to the VIA CB2 line in the Oric.
        busDirection = via.getCb2();

        if (busDirection == 1) {
            if (busControl1 == 1) {
                // Address latch write is occurring.
                addressLatch = (via.getPortAPins() & 0x0f);

            } else {
                // Write register. Probably needs a "has it changed" check.
                writeRegister(addressLatch, via.getPortAPins());
            }
        } else {
            if (busControl1 == 1) {
                // Read occurred. Not sure how often this happens in the Oric. Log for now so
                // we'll find out.
                System.out.println("AY-3-8912: Read from " + via.getPortAPins());
            }
        }

        // If enough cycles have elapsed since the last sample, then output another.
        if (--cyclesToNextSample <= 0) {
            
            cyclesToNextSample += cyclesPerSample;
            
            // No point writing samples until we know that the AudioWorklet is ready.
            if (writeSamplesEnabled) {
                writeSample();
            }
        }
    }

    /**
     * Pauses the sound output. Invoked when the Machine is paused.
     */
    public void pauseSound() {
        if (audioWorklet != null) {
            audioWorklet.suspend();
        }
    }

    /**
     * Resumes the sound output. Invoked when the Machine is (re)created or unpaused.
     */
    public void resumeSound() {
        if (sampleSharedQueue != null) {
            if (!sampleSharedQueue.isEmpty()) {
                // Clear out the old data from when it was last playing.
                logToJSConsole("Clearing sample queue...");
                int totalCleared = 0;
                int itemsRead = 0;
                Float32Array data = TypedArrays.createFloat32Array(1024);
                do {
                    itemsRead = sampleSharedQueue.pop(data);
                    totalCleared += itemsRead;
                } while (itemsRead == 1024);
                logToJSConsole("Cleared " + totalCleared + " old samples.");
                
                // Now fill with silence, so that we do not slow down emulation rate.
                int silentSampleCount = sampleLatency - (sampleRate / 60);
                sampleSharedQueue.push(TypedArrays.createFloat32Array(silentSampleCount));
            }
        }
        if (audioWorklet != null) {
            logToJSConsole("Resuming PSGAudioWorker...");
            audioWorklet.resume();
            if (audioWorklet.isReady()) {
                audioWorklet.notifyAudioReady();
            }
        }
    }
    
    /**
     * Returns true if sound is currently being produce; otherwise false.
     * 
     * @return
     */
    @Override
    public boolean isSoundOn() {
        logToJSConsole("Audio worklet running? : " + audioWorklet.isRunning());
        return audioWorklet.isRunning();
    }

    /**
     * Stops and closes the audio line.
     */
    public void dispose() {
        // TODO: Replace with web worker audio generating equivalent.
        // if (audioLine != null) {
        // audioLine.stop();
        // audioLine.close();
        // }
    }

    /**
     * Gets the current value stored in PORT A. The Keyboard instance will call this
     * method to get the selected columns when testing if key(s) are pressed.
     * 
     * @return The current value stored in PORT A (the AY-3-8912 only has one port).
     */
    public int getIOPortA() {
        return registers[14];
    }

    /**
     * Reads the value stored in an AY-3-8912 register.
     * 
     * @param address The address of the register to read from.
     * 
     * @return The value stored in the register.
     */
    public int readRegister(int address) {
        return registers[address];
    }

    /**
     * Writes a value to an AY-3-8912 register.
     * 
     * @param address The address of the register to write to.
     * @param value   The value to write to the register.
     */
    public void writeRegister(int address, int value) {
        registers[address] = value;

        switch (address) {

        case 0x00: // Fine tune A
        case 0x01: // Coarse tune A
        case 0x02: // Fine tune B
        case 0x03: // Coarse tune B
        case 0x04: // Fine tune C
        case 0x05: { // Coarse tune C
            address >>= 1;
            int val = (((registers[(address << 1) + 1] & 0x0f) << 8) | registers[address << 1]) * updateStep;
            int last = period[address];
            period[address] = val = ((val < 0x8000) ? 0x8000 : val);
            // Adjust the time remaining to the next flip-flop toggle so that the
            // time already elapsed since the last toggle is preserved, i.e. the
            // period write moves only the target, as on the real chip. If the
            // elapsed time already exceeds the new period, the clamp below makes
            // the overdue toggle happen straight away.
            int newCount = count[address] + (val - last);
            count[address] = newCount < 1 ? 1 : newCount;
            break;
        }

        // Noise period.
        case 0x06: {
            int val = (value & 0x1f) * updateStep;
            // A noise period of 0 behaves the same as a noise period of 1: the
            // data sheet notes that the lowest period value is 1 for both tone
            // and noise, and this behaviour has been verified using original
            // Oric-1 hardware.
            val = (val == 0 ? updateStep : val);
            val *= 2;
            int last = period[NOISE];
            period[NOISE] = val;
            int newCount = count[NOISE] + (val - last);
            count[NOISE] = newCount < 1 ? 1 : newCount;
            break;
        }

        // Voice enable (i.e Mixer)
        case 0x07:
            enable = value;
            disableToneA = (enable & 0x01) != 0;
            disableToneB = (enable & 0x02) != 0;
            disableToneC = (enable & 0x04) != 0;
            disableAllNoise = (enable & 0x38) == 0x38;
            break;

        // Channel A volume
        case 0x08:
            volumeA = (((value & 0x10) == 0) ? value & 0x0f : volumeEnvelope);
            break;

        // Channel B volume
        case 0x09:
            volumeB = (((value & 0x10) == 0) ? value & 0x0f : volumeEnvelope);
            break;

        // Channel C volume
        case 0x0A:
            volumeC = (((value & 0x10) == 0) ? value & 0x0f : volumeEnvelope);
            break;

        // Envelope Fine & Coarse tune
        case 0x0B:
        case 0x0C: {
            int val = (((registers[0x0C] << 8) | registers[0x0B]) * updateStep) << 1;
            // On the real chip an envelope period register value of 0 runs at
            // twice the speed of period 1, i.e. a full 16-step envelope cycle
            // in 128us at 1 MHz. (This is unlike the tone and noise periods,
            // where 0 behaves the same as 1.) Period 1 is (updateStep << 1)
            // here, so period 0 maps to half of that, which is updateStep.
            val = (val == 0 ? updateStep : val);
            int last = period[ENVELOPE];
            period[ENVELOPE] = val;
            int newCount = count[ENVELOPE] + (val - last);
            count[ENVELOPE] = newCount < 1 ? 1 : newCount;
            break;
        }

        // Envelope shape
        case 0x0D: {
            attack = (value & 0x04) == 0 ? 0 : 0x0f;
            if ((value & 0x08) == 0) {
                hold = 1;
                alternate = attack;
            } else {
                hold = value & 0x01;
                alternate = value & 0x02;
            }
            count[ENVELOPE] = period[ENVELOPE];
            countEnv = 0x0f;
            holding = 0;
            int vol = volumeEnvelope = attack ^ 0x0f;
            if ((registers[0x08] & 0x10) != 0) {
                volumeA = vol;
            }
            if ((registers[0x09] & 0x10) != 0) {
                volumeB = vol;
            }
            if ((registers[0x0A] & 0x10) != 0) {
                volumeC = vol;
            }
            break;
        }

        default:
            break;
        }
    }

    /**
     * Writes a single sample to the sample buffer. If the buffer is full after
     * writing the sample, then the whole buffer is written out to the
     * SourceDataLine.
     */
    public void writeSample() {
        if (disableToneA) {
            if (count[A] <= step) {
                count[A] += step;
            }
            output[A] = 1;
        }
        if (disableToneB) {
            if (count[B] <= step) {
                count[B] += step;
            }
            output[B] = 1;
        }
        if (disableToneC) {
            if (count[C] <= step) {
                count[C] += step;
            }
            output[C] = 1;
        }
        outNoise = output[NOISE] | enable;
        if (disableAllNoise) {
            if (count[NOISE] <= step) {
                count[NOISE] += step;
            }
        }

        int[] cnt = new int[3];
        int left = step;
        do {
            int add = count[NOISE] < left ? count[NOISE] : left;
            for (int channel = A; channel <= C; channel++) {
                int channelCount = count[channel];
                if ((outNoise & (0x08 << channel)) != 0) {
                    int val = output[channel] == 0 ? cnt[channel] : cnt[channel] + channelCount;
                    if ((channelCount -= add) <= 0) {
                        int channelPeriod = period[channel];
                        while (true) {
                            if ((channelCount += channelPeriod) > 0) {
                                if ((output[channel] ^= 0x01) != 0) {
                                    val += channelPeriod - channelCount;
                                }
                                break;
                            }
                            val += channelPeriod;
                            if ((channelCount += channelPeriod) > 0) {
                                if (output[channel] == 0) {
                                    val -= channelCount;
                                }
                                break;
                            }
                        }
                    } else if (output[channel] != 0) {
                        val -= channelCount;
                    }
                    cnt[channel] = val;
                } else {
                    if ((channelCount -= add) <= 0) {
                        int channelPeriod = period[channel];
                        while (true) {
                            if ((channelCount += channelPeriod) > 0) {
                                output[channel] ^= 0x01;
                                break;
                            }
                            if ((channelCount += channelPeriod) > 0) {
                                break;
                            }
                        }
                    }
                }
                count[channel] = channelCount;
            }

            if ((count[NOISE] -= add) <= 0) {
                int val = random + 1;
                if ((val & 0x02) != 0) {
                    outNoise = (output[NOISE] ^= 0xff) | enable;
                }
                random = (random & 0x01) == 0 ? random >> 1 : (random ^ 0x28000) >> 1;
                count[NOISE] += period[NOISE];
            }

            left -= add;
        } while (left > 0);

        if (holding == 0) {
            if ((count[ENVELOPE] -= step) <= 0) {
                int ce = countEnv;
                int envelopePeriod = period[ENVELOPE];
                do {
                    ce--;
                } while ((count[ENVELOPE] += envelopePeriod) <= 0);

                if (ce < 0) {
                    if (hold != 0) {
                        if (alternate != 0) {
                            attack ^= 0x0f;
                        }
                        holding = 1;
                        ce = 0;
                    } else {
                        if (alternate != 0 && (ce & 0x10) != 0) {
                            attack ^= 0x0f;
                        }
                        ce &= 0x0f;
                    }
                }
                countEnv = ce;
                int vol = volumeEnvelope = ce ^ attack;
                if ((registers[0x08] & 0x10) != 0) {
                    volumeA = vol;
                }
                if ((registers[0x09] & 0x10) != 0) {
                    volumeB = vol;
                }
                if ((registers[0x0A] & 0x10) != 0) {
                    volumeC = vol;
                }
            }
        }

        // Each channel spent some fraction of this sample with its output gate
        // high (cnt / step). The output is the time weighted average of the
        // mix table's value over the eight on/off combinations of the three
        // channels, i.e. a trilinear blend between the table entries for each
        // channel being silent (index 0) or at its volume level. Averaging
        // the (non-linear) network output over the states is slightly more
        // faithful than evaluating it once at the averages.
        float wA = cnt[A] * (1.0f / 32768.0f);
        float wB = cnt[B] * (1.0f / 32768.0f);
        float wC = cnt[C] * (1.0f / 32768.0f);
        int ia = volumeA << 8;
        int ib = volumeB << 4;
        int ic = volumeC;
        float aLow = lerp(lerp(MIX_TABLE[0], MIX_TABLE[ic], wC),
                          lerp(MIX_TABLE[ib], MIX_TABLE[ib | ic], wC), wB);
        float aHigh = lerp(lerp(MIX_TABLE[ia], MIX_TABLE[ia | ic], wC),
                           lerp(MIX_TABLE[ia | ib], MIX_TABLE[ia | ib | ic], wC), wB);
        int sample = (int) lerp(aLow, aHigh, wA);

        // Use a simple DC blocker to convert to -1.0 to 1.0, which is what the
        // AudioWorkletProcessor needs. The output clamp is folded into the same
        // expression as the filter, so on the rare transient that hits the rail
        // (e.g. a register write flipping the chip from full silence to full output
        // in one sample), the clamped value is what gets fed back into the filter
        // state. (Clamping the filter state is arguably less mathematically accurate,
        // because the clamping behaviour is non-linear. But this approach brings us
        // out of the clipping state and into more normal behaviour faster, and is
        // likely a closer approximation of the original hardware circuit bahaviour.)
        float x = sample / 16384.0f;
        float y = Math.max(-1f, Math.min(1f,
                        x - dcBlockerX1 + dcBlockerR * dcBlockerY1));
        dcBlockerX1 = x;
        dcBlockerY1 = y;
        sampleBuffer.set(sampleBufferOffset, y);

        // Increment total sample count, so that we can keep in sync with cycle count.
        sampleCount++;
        
        // If the sample buffer is full, write it out to the shared queue.
        if ((sampleBufferOffset++) == sampleBuffer.length()) {
            sampleSharedQueue.push(sampleBuffer);
            sampleBufferOffset = 0;
            
            //float elapsedTimeInSecs = (TimeUtils.millis() - this.startTime) / 1000.0f;
            //float cyclesPerSecond = cycleCount / elapsedTimeInSecs;
            
            //this.frameCount += sampleBuffer.length();
            //logToJSConsole("GwtAYPSG - Sample rate = " + (frameCount / elapsedTimeInSecs) + 
            //        ", Cycle rate = " + cyclesPerSecond);
            
            //logToJSConsole("GwtAYPSG - elapsedTimeInSecs = " + elapsedTimeInSecs + 
            //        ", cycle rate = " + cyclesPerSecond + 
            //        ", audio time = " + sampleSharedQueue.getCurrentTime());
            
            //logToJSConsole("GwtAYPSG - volumeA: " + volumeA + 
            //        ", volumeB: " + volumeB + ", volumeC: " + volumeC + 
            //        ", cntA: " + cnt[A] + ", cntB: " + cnt[B] + 
            //        ", cntC: " + cnt[C] + ", sample: " + sample);
        }
    }

    public SharedQueue getSampleSharedQueue() {
        return sampleSharedQueue;
    }

    /**
     * Returns the sample rate that samples are being generated at.
     *
     * @return The sample rate that samples are being generated at.
     */
    public int getSampleRate() {
        return sampleRate;
    }

    /**
     * Returns the target number of samples for the shared queue, i.e.
     * SAMPLE_LATENCY_MS worth of samples at the current sample rate.
     *
     * @return The target number of samples for the shared queue.
     */
    public int getSampleLatency() {
        return sampleLatency;
    }

    /**
     * Returns the number of cycles it takes to generate a single sample.
     *
     * @return The number of cycles it takes to generate a single sample.
     */
    public double getCyclesPerSample() {
        return cyclesPerSample;
    }
    
    JavaScriptObject getSharedArrayBuffer() {
        return sampleSharedQueue.getSharedArrayBuffer();
    }

    private void initialiseAudioWorklet(GwtJOricRunner gwtJOricRunner) {
        this.audioWorklet = new PSGAudioWorklet(sampleSharedQueue, gwtJOricRunner);
    }
    
    private final native void logToJSConsole(String message)/*-{
        console.log(message);
    }-*/;
}
