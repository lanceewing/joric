package emu.joric.teavm;

import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.SharedArrayBuffer;
import org.teavm.jso.typedarrays.Uint8Array;

import emu.joric.Machine;
import emu.joric.MachineType;
import emu.joric.Program;
import emu.joric.config.AppConfigItem;
import emu.joric.cpu.Cpu6502;
import emu.joric.memory.RamType;

public final class TeaVMJOricWebWorker {

    private TeaVMKeyboardMatrix keyboardMatrix;
    private TeaVMPixelData pixelData;
    private TeaVMAYPSG psg;
    private TeaVMFrameCounter frameCounter;
    private Machine machine;
    private boolean paused;
    private boolean warpSpeed;

    private double startTime;
    private long cycleCount;

    public static void main(String[] args) {
        new TeaVMJOricWebWorker().onWorkerLoad();
    }

    private void onWorkerLoad() {
        TeaVMWorkerGlobalScope.setOnMessage(this::onMessage);
    }

    private void onMessage(JSObject eventObject) {
        switch (TeaVMWorkerInterop.getEventType(eventObject)) {
            case "Initialise":
                SharedArrayBuffer keyMatrixSAB = (SharedArrayBuffer)TeaVMWorkerInterop.getNestedObject(eventObject, "keyMatrixSAB");
                SharedArrayBuffer pixelDataSAB = (SharedArrayBuffer)TeaVMWorkerInterop.getNestedObject(eventObject, "pixelDataSAB");
                SharedArrayBuffer audioDataSAB = (SharedArrayBuffer)TeaVMWorkerInterop.getNestedObject(eventObject, "audioDataSAB");
                SharedArrayBuffer frameCounterSAB = (SharedArrayBuffer)TeaVMWorkerInterop.getNestedObject(eventObject, "frameCounterSAB");
                int sampleRate = TeaVMWorkerInterop.getNestedInt(eventObject, "sampleRate");
                keyboardMatrix = new TeaVMKeyboardMatrix(keyMatrixSAB);
                pixelData = new TeaVMPixelData(pixelDataSAB);
                psg = new TeaVMAYPSG(audioDataSAB, sampleRate);
                frameCounter = new TeaVMFrameCounter(frameCounterSAB);
                break;

            case "Start":
                startMachine(eventObject);
                break;

            case "AudioWorkletReady":
                TeaVMWorkerGlobalScope.logToJSConsole("Enabling PSG sample writing...");
                psg.enableWriteSamples();
                break;

            case "SoundOff":
                TeaVMWorkerGlobalScope.logToJSConsole("Disabling PSG sample writing...");
                psg.disableWriteSamples();
                break;

            case "Pause":
                paused = true;
                break;

            case "Unpause":
                paused = false;
                break;

            case "WarpSpeedOn":
                warpSpeed = true;
                break;

            case "WarpSpeedOff":
                warpSpeed = false;
                break;

            case "SendNMI":
                if (machine != null) {
                    machine.getCpu().setInterrupt(Cpu6502.S_NMI);
                }
                break;

            default:
                break;
        }
    }

    private void startMachine(JSObject eventObject) {
        AppConfigItem appConfigItem = buildAppConfigItemFromEventObject(eventObject);
        ArrayBuffer programArrayBuffer = TeaVMWorkerInterop.getArrayBuffer(eventObject);
        int programDataLength = TeaVMWorkerInterop.getNestedInt(eventObject, "programDataLength");

        // ROM layout: basicRom (16384) + microdiscRom (8192) + programData
        byte[] basicRom = extractBytesFromArrayBuffer(programArrayBuffer, 0, 16384);
        byte[] microdiscRom = extractBytesFromArrayBuffer(programArrayBuffer, 16384, 8192);
        Program program = extractProgram(programArrayBuffer, programDataLength);

        if (program != null) {
            program.setAppConfigItem(appConfigItem);
        }

        TeaVMWorkerGlobalScope.logToJSConsole("TeaVM worker: starting machine, name=" + appConfigItem.getName()
                + ", type=" + appConfigItem.getFileType()
                + ", machineType=" + appConfigItem.getMachineType()
                + (program != null ? ", programBytes=" + program.getProgramData().length : ", program=null"));

        MachineType machineType = MachineType.valueOf(appConfigItem.getMachineType());
        RamType ramType = RamType.valueOf(appConfigItem.getRam());

        machine = new Machine(psg, keyboardMatrix, pixelData);
        machine.init(basicRom, microdiscRom, program, machineType, ramType);

        paused = false;
        cycleCount = 0;
        startTime = 0;
        if (frameCounter != null) {
            frameCounter.reset();
        }

        TeaVMWorkerGlobalScope.requestAnimationFrame(this::performAnimationFrame);
    }

    private byte[] extractBytesFromArrayBuffer(ArrayBuffer programDataBuffer, int offset, int length) {
        Uint8Array array = Uint8Array.create(programDataBuffer);
        byte[] data = new byte[length];
        for (int index = offset, dataIndex = 0; dataIndex < length; index++, dataIndex++) {
            data[dataIndex] = (byte)(array.get(index) & 0xFF);
        }
        return data;
    }

    private Program extractProgram(ArrayBuffer programDataBuffer, int programLength) {
        // Program data starts after basicRom (16384) + microdiscRom (8192)
        int programOffset = 16384 + 8192;
        if (programLength <= 0) {
            return null;
        }
        Program program = new Program();
        program.setProgramData(extractBytesFromArrayBuffer(programDataBuffer, programOffset, programLength));
        return program;
    }

    private AppConfigItem buildAppConfigItemFromEventObject(JSObject eventObject) {
        AppConfigItem appConfigItem = new AppConfigItem();
        appConfigItem.setName(TeaVMWorkerInterop.getNestedString(eventObject, "name"));
        appConfigItem.setFilePath(TeaVMWorkerInterop.getNestedString(eventObject, "filePath"));
        appConfigItem.setFileType(TeaVMWorkerInterop.getNestedString(eventObject, "fileType"));
        appConfigItem.setMachineType(TeaVMWorkerInterop.getNestedString(eventObject, "machineType"));
        appConfigItem.setRam(TeaVMWorkerInterop.getNestedString(eventObject, "ramType"));
        return appConfigItem;
    }

    private void performAnimationFrame(double timestamp) {
        long expectedCycleCount = 0;

        if (paused) {
            cycleCount = 0;
            startTime = timestamp;
        } else {
            if (psg.isWriteSamplesEnabled()) {
                cycleCount = 0;
                int currentBufferSize = psg.getSampleSharedQueue().availableRead();
                int samplesToGenerate = (currentBufferSize >= psg.getSampleLatency())
                        ? 0
                        : psg.getSampleLatency() - currentBufferSize;
                expectedCycleCount = (long)(samplesToGenerate * psg.getCyclesPerSample());
                startTime = timestamp;
            } else if (!warpSpeed) {
                double elapsedTime = timestamp - startTime;
                expectedCycleCount = Math.round(elapsedTime * 1000);
            } else {
                expectedCycleCount = 1_000_000;
                cycleCount = 0;
                startTime = timestamp;
            }

            do {
                if (machine.emulateCycle() && (frameCounter != null)) {
                    frameCounter.increment();
                }
                cycleCount++;
            } while (cycleCount <= expectedCycleCount);
        }

        TeaVMWorkerGlobalScope.requestAnimationFrame(this::performAnimationFrame);
    }
}
