package emu.joric.gwt;

import com.google.gwt.core.client.JavaScriptObject;

import emu.joric.worker.Worker;

/**
 * Creates and manages the AudioWorklet for playing the AY-3-8912 sample data.
 */
public class PSGAudioWorklet {

    /**
     * The SharedQueue that the worker puts the samples in to.
     */
    private SharedQueue sampleSharedQueue;
    
    /**
     * The GWT JOricRunner, which is the client side, i.e. UI thread. The 
     * PSGAudioWorklet uses it to get hold of the current Worker reference, 
     * which changes between game executions.
     */
    private GwtJOricRunner gwtJOricRunner;
    
    /**
     * Constructor for PSGAudioWorklet.
     * 
     * @param sampleSharedQueue SharedQueue that the worker puts the samples in.
     * @param gwtJOricRunner 
     */
    public PSGAudioWorklet(SharedQueue sampleSharedQueue, GwtJOricRunner gwtJOricRunner) {
        this.sampleSharedQueue = sampleSharedQueue;
        this.gwtJOricRunner = gwtJOricRunner;
        
        initialise(sampleSharedQueue.getSharedArrayBuffer());
    }

    /**
     * Initialises the PSGAudioWorkler, by creating the AudioContext, then 
     * registering an AudioWorkletProcessor, sending it the SharedArrayBuffer
     * that will contain the audio sample data, and then creating an
     * AudioWorkletNode, connected to the standard audio output destination,
     * to make use of the AudioWorkletProcessor. If this is run outside of a
     * user gesture, then the resume handler is not invoked until the next 
     * call to resume within a user gesture.
     * 
     * @param audioBufferSAB The SharedArrayBuffer to get the sound sample data from.
     */
    private native void initialise(JavaScriptObject audioBufferSAB)/*-{
        this.ready = false;
    
        // Store for later use by resume handler.
        this.audioBufferSAB = audioBufferSAB;
        
        // If this is not executing within a user gesture, then it will be suspended.
        this.audioContext = new AudioContext({sampleRate: 22050});
        
        // Set up the initial resume handler.
        var that = this;
        this.audioContext.resume().then(function() {
            if (that.audioContext.state === "running") {
                // If the AudioWorkletNode has not yet been set up, then do so.
                if (!that.audioWorkletNode) {
                    console.log("Adding AudioWorkletProcessor module...");
                
                    that.audioContext.audioWorklet.addModule('/sound-renderer.js').then(function() {
                        that.audioWorkletNode = new AudioWorkletNode(
                            that.audioContext, 
                            "sound-renderer",
                            {
                                numberOfInputs: 0,
                                numberOfOutputs: 1, 
                                outputChannelCount: [1]
                            }
                        );
                        
                        that.audioWorkletNode.port.onmessage = function() {
                            // There is only one message we can receive, which is ready.
                            console.log("Received ready message from AudioWorkletProcessor.");
                            that.@emu.joric.gwt.PSGAudioWorklet::notifyAudioReady()();
                            that.ready = true;
                        };
                        
                        console.log("Sending audio buffer SAB to AudioWorkletProcessor...");
                        
                        // Send SharedArrayBuffer for SharedQueue to audio worklet processor.
                        that.audioWorkletNode.port.postMessage({audioBufferSAB: that.audioBufferSAB});
                        
                        console.log("Connecting AudioWorklet to audio output destination...");
                        
                        // The AudioWorkletNode has only the output connection. The 'input' is
                        // read directly from the SharedArrayBuffer by the AudioWorkletProcessor.
                        that.audioWorkletNode.connect(that.audioContext.destination);
                    });
                } else {
                    console.log("AudioWorkletProcessor is already registered.");
                }
            }
        });
    }-*/;
    
    /**
     * Notifies the web worker that the audio worklet is ready for sample data.
     */
    public void notifyAudioReady() {
        Worker worker = gwtJOricRunner.getCurrentWorker();
        if (worker != null) {
            logToJSConsole("Sending AudioWorkletReady message to web worker...");
            worker.postObject("AudioWorkletReady", JavaScriptObject.createObject());
        }
    }
    
    public native boolean isReady()/*-{
        return this.ready;
    }-*/;
    
    /**
     * This is invoked whenever the sound output should be resumed.
     */
    public native void resume()/*-{
        if (this.audioContext && (this.audioContext.state === "suspended")) {
            this.audioContext.resume();
        }
    }-*/;
    
    /**
     * Suspends the output of audio.
     */
    public native void suspend()/*-{
        if (this.audioContext && (this.audioContext.state === "running")) {
            this.audioContext.suspend();
        }
    }-*/;
    
    private final native void logToJSConsole(String message)/*-{
        console.log(message);
    }-*/;
}
