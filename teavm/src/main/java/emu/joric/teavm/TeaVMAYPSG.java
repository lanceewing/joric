package emu.joric.teavm;

import org.teavm.jso.typedarrays.Float32Array;
import org.teavm.jso.typedarrays.SharedArrayBuffer;

import emu.joric.io.Keyboard;
import emu.joric.io.Via;
import emu.joric.snap.Snapshot;
import emu.joric.sound.AYPSG;

/**
 * TeaVM implementation of the AY-3-8912 PSG interface. Ports the GwtAYPSG
 * to use TeaVM APIs (SharedArrayBuffer, Float32Array) instead of GWT APIs.
 */
public class TeaVMAYPSG implements AYPSG {

    // The Oric runs at 1 MHz.
    private static final int CLOCK_1MHZ = 1000000;
    private static final int CYCLES_PER_SECOND = 1000000;

    public static final int MAX_SAMPLE_RATE = 48000;
    public static final int FALLBACK_SAMPLE_RATE = 22050;
    public static final int SAMPLE_LATENCY_MS = 140;

    private int sampleRate;
    private double cyclesPerSample;
    private int sampleLatency;

    private static final double[] CHANNEL_RES = {
            15950, 15350, 15090, 14760, 14275, 13620, 12890, 11370,
            10600,  8590,  7190,  5985,  4820,  3945,  3017,  2345 };
    private static final double RES_R_UP = 800000;
    private static final double RES_R_DOWN = 8000000;
    private static final double ORIC_LOAD_R = 838;
    private static final double CONDUCTANCE_SCALE = 2.32;

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

    private static final int A = 0;
    private static final int B = 1;
    private static final int C = 2;
    private static final int NOISE = 3;
    private static final int ENVELOPE = 4;

    private int[] output;
    private int[] count;
    private int[] period;

    private int volumeA;
    private int volumeB;
    private int volumeC;
    private int volumeEnvelope;

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
    private TeaVMSharedQueue sampleSharedQueue;

    private static final float DC_BLOCKER_CORNER_HZ = 17.5f;
    private float dcBlockerR;
    private float dcBlockerX1;
    private float dcBlockerY1;

    private boolean writeSamplesEnabled;

    private TeaVMPSGAudioWorklet audioWorklet;
    private TeaVMJOricRunner joricRunner;

    private Via via;

    /**
     * Constructor for TeaVMAYPSG (invoked by the UI thread).
     * The audioWorklet is attached later via attachAudioWorklet().
     */
    public TeaVMAYPSG() {
        this((SharedArrayBuffer) null, FALLBACK_SAMPLE_RATE);
    }

    /**
     * Constructor for TeaVMAYPSG (invoked by the web worker).
     *
     * @param audioBufferSAB SharedArrayBuffer for the audio ring buffer.
     * @param sampleRate     The sample rate of the AudioContext on the UI thread.
     */
    public TeaVMAYPSG(SharedArrayBuffer audioBufferSAB, int sampleRate) {
        if (audioBufferSAB == null) {
            audioBufferSAB = TeaVMSharedQueue.getStorageForCapacity(MAX_SAMPLE_RATE);
        }
        this.sampleSharedQueue = new TeaVMSharedQueue(audioBufferSAB);
        this.sampleBuffer = Float32Array.create(512);
        this.sampleBufferOffset = 0;
        configureSampleRate(sampleRate);
    }

    private void configureSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
        this.cyclesPerSample = ((double) CYCLES_PER_SECOND) / sampleRate;
        this.sampleLatency = (sampleRate * SAMPLE_LATENCY_MS) / 1000;
        this.dcBlockerR = 1.0f - (float)((2 * Math.PI * DC_BLOCKER_CORNER_HZ) / sampleRate);
    }

    @Override
    public void init(Via via, Keyboard keyboard, Snapshot snapshot) {
        this.via = via;
        keyboard.setPsg(this);

        updateStep = (int) (((long) step * 8L * (long) sampleRate) / (long) CLOCK_1MHZ);
        output = new int[] { 0, 0, 0, 0xFF };
        count = new int[] { updateStep, updateStep, updateStep, 0x7fff, updateStep };
        period = new int[] { updateStep, updateStep, updateStep, updateStep, updateStep };
        registers = new int[16];

        volumeA = volumeB = volumeC = volumeEnvelope = 0;
        disableToneA = disableToneB = disableToneC = disableAllNoise = false;
        countEnv = hold = alternate = attack = 0;
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

    public void enableWriteSamples() {
        writeSamplesEnabled = true;
    }

    public void disableWriteSamples() {
        writeSamplesEnabled = false;
    }

    public boolean isWriteSamplesEnabled() {
        return writeSamplesEnabled;
    }

    @Override
    public void emulateCycle() {
        busControl1 = via.getCa2();
        busDirection = via.getCb2();

        if (busDirection == 1) {
            if (busControl1 == 1) {
                addressLatch = (via.getPortAPins() & 0x0f);
            } else {
                writeRegister(addressLatch, via.getPortAPins());
            }
        }

        if (--cyclesToNextSample <= 0) {
            cyclesToNextSample += cyclesPerSample;
            if (writeSamplesEnabled) {
                writeSample();
            }
        }
    }

    @Override
    public void pauseSound() {
        if (audioWorklet != null) {
            audioWorklet.suspend();
        }
    }

    @Override
    public void resumeSound() {
        if (sampleSharedQueue != null) {
            if (!sampleSharedQueue.isEmpty()) {
                TeaVMWorkerGlobalScope.logToJSConsole("Clearing sample queue...");
                int totalCleared = 0;
                int itemsRead;
                Float32Array data = Float32Array.create(1024);
                do {
                    itemsRead = sampleSharedQueue.pop(data);
                    totalCleared += itemsRead;
                } while (itemsRead == 1024);
                TeaVMWorkerGlobalScope.logToJSConsole("Cleared " + totalCleared + " old samples.");

                int silentSampleCount = sampleLatency - (sampleRate / 60);
                sampleSharedQueue.push(Float32Array.create(silentSampleCount));
            }
        }
        if (audioWorklet != null) {
            TeaVMWorkerGlobalScope.logToJSConsole("Resuming PSGAudioWorklet...");
            audioWorklet.resume();
            if (audioWorklet.isReady() && (joricRunner != null)) {
                joricRunner.notifyAudioWorkletReady();
            }
        }
    }

    @Override
    public boolean isSoundOn() {
        return (audioWorklet != null) ? audioWorklet.isRunning() : false;
    }

    @Override
    public void dispose() {
        writeSamplesEnabled = false;
        sampleBufferOffset = 0;
        if (audioWorklet != null) {
            audioWorklet.suspend();
        }
    }

    @Override
    public int getIOPortA() {
        return registers[14];
    }

    void attachAudioWorklet(TeaVMJOricRunner joricRunner) {
        this.joricRunner = joricRunner;
        if (audioWorklet == null) {
            audioWorklet = new TeaVMPSGAudioWorklet(sampleSharedQueue, joricRunner);
            configureSampleRate(audioWorklet.getSampleRate());
        }
    }

    public TeaVMSharedQueue getSampleSharedQueue() {
        return sampleSharedQueue;
    }

    SharedArrayBuffer getSharedArrayBuffer() {
        return sampleSharedQueue.getSharedArrayBuffer();
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getSampleLatency() {
        return sampleLatency;
    }

    public double getCyclesPerSample() {
        return cyclesPerSample;
    }

    public int readRegister(int address) {
        return registers[address];
    }

    public void writeRegister(int address, int value) {
        registers[address] = value;

        switch (address) {

        case 0x00:
        case 0x01:
        case 0x02:
        case 0x03:
        case 0x04:
        case 0x05: {
            address >>= 1;
            int val = (((registers[(address << 1) + 1] & 0x0f) << 8) | registers[address << 1]) * updateStep;
            int last = period[address];
            period[address] = val = ((val < 0x8000) ? 0x8000 : val);
            int newCount = count[address] + (val - last);
            count[address] = newCount < 1 ? 1 : newCount;
            break;
        }

        case 0x06: {
            int val = (value & 0x1f) * updateStep;
            val = (val == 0 ? updateStep : val);
            val *= 2;
            int last = period[NOISE];
            period[NOISE] = val;
            int newCount = count[NOISE] + (val - last);
            count[NOISE] = newCount < 1 ? 1 : newCount;
            break;
        }

        case 0x07:
            enable = value;
            disableToneA = (enable & 0x01) != 0;
            disableToneB = (enable & 0x02) != 0;
            disableToneC = (enable & 0x04) != 0;
            disableAllNoise = (enable & 0x38) == 0x38;
            break;

        case 0x08:
            volumeA = (((value & 0x10) == 0) ? value & 0x0f : volumeEnvelope);
            break;

        case 0x09:
            volumeB = (((value & 0x10) == 0) ? value & 0x0f : volumeEnvelope);
            break;

        case 0x0A:
            volumeC = (((value & 0x10) == 0) ? value & 0x0f : volumeEnvelope);
            break;

        case 0x0B:
        case 0x0C: {
            int val = (((registers[0x0C] << 8) | registers[0x0B]) * updateStep) << 1;
            val = (val == 0 ? updateStep : val);
            int last = period[ENVELOPE];
            period[ENVELOPE] = val;
            int newCount = count[ENVELOPE] + (val - last);
            count[ENVELOPE] = newCount < 1 ? 1 : newCount;
            break;
        }

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

        float x = sample / 16384.0f;
        float y = Math.max(-1f, Math.min(1f,
                        x - dcBlockerX1 + dcBlockerR * dcBlockerY1));
        dcBlockerX1 = x;
        dcBlockerY1 = y;
        sampleBuffer.set(sampleBufferOffset, y);

        if ((sampleBufferOffset++) == sampleBuffer.getLength()) {
            sampleSharedQueue.push(sampleBuffer);
            sampleBufferOffset = 0;
        }
    }
}
