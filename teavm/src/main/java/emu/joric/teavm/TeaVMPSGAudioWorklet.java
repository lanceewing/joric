package emu.joric.teavm;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.SharedArrayBuffer;

final class TeaVMPSGAudioWorklet {

    @JSFunctor
    interface ReadyCallback extends JSObject {
        void handle();
    }

    private final JSObject handle;

    TeaVMPSGAudioWorklet(TeaVMSharedQueue sampleSharedQueue, TeaVMJOricRunner joricRunner) {
        this.handle = createHandle(sampleSharedQueue.getSharedArrayBuffer(),
                TeaVMAYPSG.MAX_SAMPLE_RATE, TeaVMAYPSG.FALLBACK_SAMPLE_RATE,
                joricRunner::notifyAudioWorkletReady);
    }

    int getSampleRate() {
        return getSampleRate(handle);
    }

    boolean isReady() {
        return isReady(handle);
    }

    void resume() {
        resume(handle);
    }

    void suspend() {
        suspend(handle);
    }

    boolean isRunning() {
        return isRunning(handle);
    }

    void resetStats() {
        resetStats(handle);
    }

    @JSBody(params = { "audioBufferSAB", "maxSampleRate", "fallbackSampleRate", "readyCallback" },
            script = "var handle = { ready: false, registering: false, audioBufferSAB: audioBufferSAB, audioWorkletNode: null, audioContext: null, sampleRate: fallbackSampleRate };"
            + "var ua = navigator.userAgent.toLowerCase();"
            + "var isIOS = ((ua.indexOf('iphone') >= 0 && ua.indexOf('like iphone') < 0) || (ua.indexOf('ipad') >= 0 && ua.indexOf('like ipad') < 0) || (ua.indexOf('ipod') >= 0 && ua.indexOf('like ipod') < 0) || (ua.indexOf('mac os x') >= 0 && navigator.maxTouchPoints > 0));"
            + "if (isIOS && navigator.audioSession) { navigator.audioSession.type = 'playback'; }"
            + "try {"
            + "  handle.audioContext = new AudioContext();"
            + "  if (handle.audioContext.sampleRate > maxSampleRate) {"
            + "    console.log('Device sample rate ' + handle.audioContext.sampleRate + ' exceeds max ' + maxSampleRate + ', recreating.');"
            + "    handle.audioContext.close();"
            + "    handle.audioContext = new AudioContext({ sampleRate: maxSampleRate });"
            + "  }"
            + "  handle.sampleRate = Math.round(handle.audioContext.sampleRate);"
            + "  console.log('AudioContext sample rate is ' + handle.sampleRate + '.');"
            + "} catch (e) { console.log('Failed to create AudioContext. Error was: ' + e); }"
            + "if (handle.audioContext && handle.audioContext.state === 'running') { handle.audioContext.suspend(); }"
            + "handle.registerAudioWorklet = function() {"
            + "  if (!handle.audioContext || handle.audioContext.state !== 'running') { return; }"
            + "  if (handle.audioWorkletNode) { readyCallback(); return; }"
            + "  if (handle.registering) { return; }"
            + "  handle.registering = true;"
            + "  handle.audioContext.audioWorklet.addModule('./sound-renderer.js').then(function() {"
            + "    handle.audioWorkletNode = new AudioWorkletNode(handle.audioContext, 'sound-renderer', { numberOfInputs: 0, numberOfOutputs: 1, outputChannelCount: [1] });"
            + "    handle.audioWorkletNode.port.onmessage = function(event) {"
            + "      var message = event.data || {};"
            + "      if (message.ready) { handle.ready = true; readyCallback(); }"
            + "    };"
            + "    handle.audioWorkletNode.port.postMessage({ audioBufferSAB: handle.audioBufferSAB });"
            + "    handle.audioWorkletNode.connect(handle.audioContext.destination);"
            + "  }).catch(function(e) { handle.registering = false; console.log('Failed to register AudioWorkletProcessor. Error was: ' + e); });"
            + "};"
            + "return handle;")
    private static native JSObject createHandle(SharedArrayBuffer audioBufferSAB,
            int maxSampleRate, int fallbackSampleRate, ReadyCallback readyCallback);

    @JSBody(params = "handle", script = "return handle.sampleRate || 22050;")
    private static native int getSampleRate(JSObject handle);

    @JSBody(params = "handle", script = "return !!handle.ready;")
    private static native boolean isReady(JSObject handle);

    @JSBody(params = "handle", script = "var isUserInteraction = (navigator.userActivation && navigator.userActivation.isActive); if (!handle.audioContext) { return; } if (handle.audioContext.state === 'suspended') { if (isUserInteraction) { handle.audioContext.resume().then(function() { handle.registerAudioWorklet(); }).catch(function(e) { console.log('AudioContext was not able to resume. Exception was: ' + e); }); } } else if (handle.audioContext.state === 'running') { handle.registerAudioWorklet(); }")
    private static native void resume(JSObject handle);

    @JSBody(params = "handle", script = "if (handle.audioContext && handle.audioContext.state === 'running') { handle.audioContext.suspend(); }")
    private static native void suspend(JSObject handle);

    @JSBody(params = "handle", script = "return !!(handle.audioContext && handle.audioContext.state === 'running');")
    private static native boolean isRunning(JSObject handle);

    @JSBody(params = "handle", script = "if (handle.audioWorkletNode) { handle.audioWorkletNode.port.postMessage({ type: 'ResetStats' }); }")
    private static native void resetStats(JSObject handle);
}
