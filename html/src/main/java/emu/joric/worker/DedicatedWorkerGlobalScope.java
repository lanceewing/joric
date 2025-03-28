package emu.joric.worker;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.typedarrays.shared.ArrayBuffer;

/**
 * Extends the gwt-webworker DedicatedWorkerGlobalScope class to support 
 * transferable objects, normal objects, and SharedArrayBuffers.
 */
public class DedicatedWorkerGlobalScope extends com.google.gwt.webworker.client.DedicatedWorkerGlobalScope {

    public static native DedicatedWorkerGlobalScope get() /*-{
        return $self;
    }-*/;
    
    protected DedicatedWorkerGlobalScope() {
        // Constructors must be protected in JavaScriptObject overlays.
    };

    /**
     * This method can be used to send simple objects or a SharedArrayBuffer.
     * 
     * @param name The name of the object. Used for identification of the object.
     * @param object The JS object to send in the postMessage call.
     */
    public final native void postObject(String name, JavaScriptObject object) /*-{
        this.postMessage({name: name, object: object});
    }-*/;
    
    /**
     * This method can be used to send transferable objects, such as ArrayBuffer and
     * ImageBitmap.
     * 
     * @param name The name of the object. Used for identification of the object.
     * @param object The JS object to send in the postMessage call.
     */
    public final native void postTransferableObject(String name, JavaScriptObject object) /*-{
        this.postMessage({name: name, object: object}, [object]);
    }-*/;
    
    /**
     * This method can be used to transfer an ArrayBuffer.
     * 
     * @param name The name of the object. Used for identification of the object.
     * @param buffer The JS ArrayByffer to send in the postMessage call.
     */
    public final native void postArrayBuffer(String name, ArrayBuffer buffer) /*-{
        this.postMessage({name: name, buffer: buffer}, [buffer]);
    }-*/;
    
    /**
     * This method can be used to transfer an ArrayBuffer along with a normal JavaScript
     * object in the same message.
     * 
     * @param name The name of the object. Used for identification of the object.
     * @param buffer The JS ArrayByffer to send in the postMessage call.
     * @param object The normal JavaScript object to send with the ArrayBuffer.
     */
    public final native void postArrayBufferAndObject(String name, ArrayBuffer buffer, JavaScriptObject object) /*-{
        this.postMessage({name: name, buffer: buffer, object: object}, [buffer]);
    }-*/;
    
    public final native void setOnMessage(MessageHandler messageHandler) /*-{
        this.onmessage = function(event) {
            messageHandler.@emu.joric.worker.MessageHandler::onMessage(Lemu/joric/worker/MessageEvent;)(event);
        }
    }-*/;
}
