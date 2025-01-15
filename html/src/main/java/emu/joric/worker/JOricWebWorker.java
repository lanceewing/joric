package emu.joric.worker;

import com.badlogic.gdx.utils.TimeUtils;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.typedarrays.shared.ArrayBuffer;
import com.google.gwt.typedarrays.shared.TypedArrays;
import com.google.gwt.typedarrays.shared.Uint8Array;
import com.google.gwt.webworker.client.DedicatedWorkerEntryPoint;

import emu.joric.Machine;
import emu.joric.MachineType;
import emu.joric.Program;
import emu.joric.config.AppConfigItem;
import emu.joric.gwt.GwtAYPSG;
import emu.joric.gwt.GwtKeyboardMatrix;
import emu.joric.gwt.GwtPixelData;
import emu.joric.gwt.GwtProgramLoader;
import emu.joric.memory.RamType;

public class JOricWebWorker extends DedicatedWorkerEntryPoint implements MessageHandler {

    private DedicatedWorkerGlobalScope scope;

    // The web worker has its own instance of each of these. It is not the same instance
    // as in the JOricWorker. Instead part of the data is either shared, or transferred
    // between the client and worker.
    private GwtKeyboardMatrix keyboardMatrix;
    private GwtPixelData pixelData;
    private GwtProgramLoader programLoader;
    private GwtAYPSG psg;
    
    /**
     * The actual Machine that runs the program.
     */
    private Machine machine;
    
    /**
     * The number of nanoseconds per frame.
     */
    private int nanosPerFrame;
    
    /**
     * Whether or not the machine is paused.
     */
    private boolean paused;
    
    /**
     * Whether or not the machine is running in warp speed mode.
     */
    private boolean warpSpeed;
    
    private double lastTime = -1;
    private long deltaTime;
    
    private double startTime;
    private long cycleCount;
    
    @Override
    public void onMessage(MessageEvent event) {
        JavaScriptObject eventObject = event.getDataAsObject();
        
        switch (getEventType(eventObject)) {
            case "Initialise":
                JavaScriptObject keyMatrixSAB = getNestedObject(eventObject, "keyMatrixSAB");
                JavaScriptObject pixelDataSAB = getNestedObject(eventObject, "pixelDataSAB");
                JavaScriptObject audioDataSAB = getNestedObject(eventObject, "audioDataSAB");
                keyboardMatrix = new GwtKeyboardMatrix(keyMatrixSAB);
                pixelData = new GwtPixelData(pixelDataSAB);
                psg = new GwtAYPSG(audioDataSAB);
                break;
                
            case "Start":
                AppConfigItem appConfigItem = buildAppConfigItemFromEventObject(eventObject); 
                ArrayBuffer programArrayBuffer = getArrayBuffer(eventObject);
                byte[] basicRom = extractBytesFromArrayBuffer(programArrayBuffer, 0, 16384);
                byte[] microdiscRom = extractBytesFromArrayBuffer(programArrayBuffer, 16384, 8192);
                Program program = extractProgram(programArrayBuffer);
                if (program != null) {
                    program.setAppConfigItem(appConfigItem);
                }
                MachineType machineType = MachineType.valueOf(appConfigItem.getMachineType());
                RamType ramType = RamType.valueOf(appConfigItem.getRam());
                nanosPerFrame = (1000000000 / machineType.getFramesPerSecond());
                machine = new Machine(psg, keyboardMatrix, pixelData);
                machine.init(basicRom, microdiscRom, program, machineType, ramType);
                // TODO: lastTime = TimeUtils.nanoTime() - nanosPerFrame;
                performAnimationFrame(0);
                break;
                
            case "AudioWorkletReady":
                logToJSConsole("Enabling PSG sample writing...");
                psg.enableWriteSamples();
                break;
                
            default:
                // Unknown message. Ignore.
        }
    }
    
    private byte[] extractBytesFromArrayBuffer(ArrayBuffer programDataBuffer,
            int offset, int length) {
        Uint8Array array = TypedArrays.createUint8Array(programDataBuffer);
        byte[] data = new byte[length];
        for (int index=offset, i=0; i<length; index++, i++) {
            data[i] = (byte)(array.get(index) & 0xFF);
        }
        return data;
    }
    
    private Program extractProgram(ArrayBuffer programDataBuffer) {
        Program program = null;
        int programOffset = 16384 + 8192;   // Allow for ROMs (basic and microdisc)
        int totalDataLength = programDataBuffer.byteLength();
        if (totalDataLength > programOffset) {
            int programLength = (totalDataLength - programOffset);
            byte[] programData = extractBytesFromArrayBuffer(programDataBuffer,
                    programOffset, programLength);
            program = new Program();
            program.setProgramData(programData);
            
            // TODO: Remove. Debug code. Outputs program data in hex to JS console.
            //String hexStr = "";
            //int offset = 0;
            //int byteCount = 0;
            //for (int i=0; i<programData.length; i++) {
            //    hexStr += (StringUtils.padLeftZeros(
            //            Integer.toHexString((int)programData[i] & 0xFF),
            //            2) + " ");
            //    byteCount++;
            //    offset++;
            //    if (byteCount >= 16) {
            //        hexStr += ("\n" + Integer.toHexString(offset) + " ");
            //        byteCount = 0;
            //    }
            //}
            //logToJSConsole(hexStr);
            
        }
        return program;
    }

    private AppConfigItem buildAppConfigItemFromEventObject(JavaScriptObject eventObject) {
        AppConfigItem appConfigItem = new AppConfigItem();
        appConfigItem.setName(getNestedString(eventObject, "name"));
        appConfigItem.setFilePath(getNestedString(eventObject, "filePath"));
        appConfigItem.setFileType(getNestedString(eventObject, "fileType"));
        appConfigItem.setMachineType(getNestedString(eventObject, "machineType"));
        appConfigItem.setRam(getNestedString(eventObject, "ramType"));
        return appConfigItem;
    }
    
    public void performAnimationFrame(double timestamp) {
        // TODO: Instead of using currentTime, use availableRead up to 3072.
        
        // Audio currentTime is in seconds, so multiply by 1000 to get ms.
        double currentAudioTime = psg.getSampleSharedQueue().getCurrentTime() * 1000;
        
        // Emulate up to 140000 cycles ahead of audio time.
        timestamp = (currentAudioTime > 0? (currentAudioTime + 140) : timestamp);
        
        if (lastTime >= 0) {
            double elapsedTime = (timestamp - startTime);
            long expectedCycleCount = Math.round(elapsedTime * 1000);
            
            if ((currentAudioTime > 0) && (psg.isWriteSamplesEnabled())) {
                // If the AudioWorklet is running, then adjust expected cycle count
                // to a value that would leave the available samples in the queue 
                // at a roughly fixed number. This is to avoid under or over generating
                // samples, being always a given number of samples ahead in the buffer.
                int currentBufferSize = psg.getSampleSharedQueue().availableRead();
                int samplesToGenerate = (currentBufferSize >= 3072? 0 : 3072 - currentBufferSize);
                
                long cyclesToGenerateSamples = (int)(samplesToGenerate * GwtAYPSG.CYCLES_PER_SAMPLE);
                expectedCycleCount = cycleCount + cyclesToGenerateSamples;
            }
            
            // Emulate the required number of cycles.
            do {
                machine.emulateCycle();
                cycleCount++;
            } while (cycleCount <= expectedCycleCount);
            
        }
        
        lastTime = timestamp;
        
        requestNextAnimationFrame();
    }
    
    public void performAnimationFrame3(double timestamp) {
        // TODO: Decide between passed in timestamp and performance.now()
        timestamp = getPerformanceNowTimestamp();
        
        if (lastTime >= 0) {
            float deltaTime = (float)(timestamp - lastTime);
            
            // There are 1,000,000 cycles per second, so the delta, which is in
            // milliseconds, so deltaTime * 1000 is the number of cycles to emulate.
            // TODO: Should it round the value, either up or down.
            long cyclesToEmulate = Math.round(deltaTime * 1000);
            do {
                machine.emulateCycle();
                cyclesToEmulate--;
            } while (cyclesToEmulate > 0);
        }
        
        lastTime = timestamp;
        
        requestNextAnimationFrame();
    }
    
    public void performAnimationFrame2(double timestamp) {
        // Calculate the time since the last call.
        long currentTime = TimeUtils.nanoTime();
        deltaTime += (currentTime - lastTime);
        lastTime = currentTime;
        
        // If we haven't been called for a while, it was probably because the web page
        // was in the background. We apply a limit mainly to handle this scenario.
        deltaTime = Math.min(deltaTime, (nanosPerFrame << 2));
        
        // We can't be certain that this method is being invoked at exactly 60 times a
        // second, or that a call hasn't been skipped, so we adjust as appropriate based
        // on the delta time and play catch up if needed. This should avoid drift in the
        // oric clock and keep the graphics animation smooth.
        while (deltaTime > nanosPerFrame) {
            deltaTime -= nanosPerFrame;
            
            if (!paused) {
                machine.update(warpSpeed);
            }
        }
        
        requestNextAnimationFrame();
    }
    
    public native void exportPerformAnimationFrame() /*-{
        var that = this;
        $self.performAnimationFrame = $entry(function(timestamp) {
            that.@emu.joric.worker.JOricWebWorker::performAnimationFrame(D)(timestamp);
        });
    }-*/;

    private native void requestNextAnimationFrame()/*-{
        $self.requestAnimationFrame($self.performAnimationFrame);
    }-*/;

    private native double getPerformanceNowTimestamp()/*-{
        return performance.now();
    }-*/;
    
    private native String getEventType(JavaScriptObject obj)/*-{
        return obj.name;
    }-*/;

    private native JavaScriptObject getNestedObject(JavaScriptObject obj, String fieldName)/*-{
        return obj.object[fieldName];
    }-*/;

    private native String getNestedString(JavaScriptObject obj, String fieldName)/*-{
        return obj.object[fieldName];
    }-*/;

    private native ArrayBuffer getArrayBuffer(JavaScriptObject obj)/*-{
        return obj.buffer;
    }-*/;

    protected final void postObject(String name, JavaScriptObject object) {
        getGlobalScope().postObject(name, object);
    }

    protected final void postTransferableObject(String name, JavaScriptObject object) {
        getGlobalScope().postTransferableObject(name, object);
    }

    @Override
    protected DedicatedWorkerGlobalScope getGlobalScope() {
        return scope;
    }

    protected final void setOnMessage(MessageHandler messageHandler) {
        getGlobalScope().setOnMessage(messageHandler);
    }

    @Override
    public void onWorkerLoad() {
        exportPerformAnimationFrame();
    
        this.scope = DedicatedWorkerGlobalScope.get();            
        this.setOnMessage(this);
    }

    private final native void logToJSConsole(String message)/*-{
        console.log(message);
    }-*/;
}
