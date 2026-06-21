package emu.joric.teavm;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.SharedArrayBuffer;

final class TeaVMWorkerInterop {

    private TeaVMWorkerInterop() {
    }

    @JSFunctor
    interface WorkerMessageCallback extends JSObject {
        void handle(JSObject data);
    }

    @JSFunctor
    interface WorkerErrorCallback extends JSObject {
        void handle(String message);
    }

    @JSBody(params = "url", script = "return new Worker(url);")
    static native JSObject createWorker(String url);

    @JSBody(params = { "worker", "callback" }, script = "worker.onmessage = function(event) { callback(event.data); };")
    static native void setOnMessage(JSObject worker, WorkerMessageCallback callback);

    @JSBody(params = { "worker", "callback" }, script = "worker.onerror = function(event) { var message = (event && event.message) ? event.message : 'Worker error'; var nl = String.fromCharCode(10); if (event && event.filename) { message += nl + event.filename + ':' + (event.lineno || 0) + ':' + (event.colno || 0); } if (event && event.error && event.error.stack) { message += nl + event.error.stack; } callback(message); };")
    static native void setOnError(JSObject worker, WorkerErrorCallback callback);

    @JSBody(params = { "worker", "name", "object" }, script = "worker.postMessage({name: name, object: object});")
    static native void postObject(JSObject worker, String name, JSObject object);

    @JSBody(params = { "worker", "name", "buffer", "object" }, script = "worker.postMessage({name: name, buffer: buffer, object: object}, [buffer]);")
    static native void postArrayBufferAndObject(JSObject worker, String name, ArrayBuffer buffer, JSObject object);

    @JSBody(params = "worker", script = "worker.terminate();")
    static native void terminate(JSObject worker);

    @JSBody(script = "return {};")
    static native JSObject createEmptyObject();

    @JSBody(params = { "keyMatrixSAB", "pixelDataSAB", "audioDataSAB", "frameCounterSAB", "sampleRate" }, script = "return { keyMatrixSAB: keyMatrixSAB, pixelDataSAB: pixelDataSAB, audioDataSAB: audioDataSAB, frameCounterSAB: frameCounterSAB, sampleRate: sampleRate };")
    static native JSObject createInitialiseObject(SharedArrayBuffer keyMatrixSAB,
            SharedArrayBuffer pixelDataSAB, SharedArrayBuffer audioDataSAB,
            SharedArrayBuffer frameCounterSAB, int sampleRate);

    @JSBody(params = { "name", "filePath", "fileType", "machineType", "ramType", "programDataLength" }, script = "return { name: name, filePath: filePath, fileType: fileType, machineType: machineType, ramType: ramType, programDataLength: programDataLength };")
    static native JSObject createStartObject(String name, String filePath, String fileType,
            String machineType, String ramType, int programDataLength);

    @JSBody(params = "data", script = "return data && data.name ? data.name : ''; ")
    static native String getEventType(JSObject data);

    @JSBody(params = { "data", "fieldName" }, script = "return (data && data.object) ? data.object[fieldName] : null;")
    static native JSObject getNestedObject(JSObject data, String fieldName);

    @JSBody(params = { "data", "fieldName" }, script = "return (data && data.object && data.object[fieldName] != null) ? data.object[fieldName] : '';")
    static native String getNestedString(JSObject data, String fieldName);

    @JSBody(params = { "data", "fieldName" }, script = "return (data && data.object && data.object[fieldName] != null) ? data.object[fieldName] : 0;")
    static native int getNestedInt(JSObject data, String fieldName);

    @JSBody(params = { "data", "fieldName" }, script = "return (data && data.object && data.object[fieldName] != null) ? data.object[fieldName] : 0;")
    static native double getNestedDouble(JSObject data, String fieldName);

    @JSBody(params = "data", script = "return data ? data.buffer : null;")
    static native ArrayBuffer getArrayBuffer(JSObject data);
}
