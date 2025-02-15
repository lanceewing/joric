package emu.joric.worker;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.typedarrays.shared.ArrayBuffer;
import com.google.gwt.core.client.GWT.UncaughtExceptionHandler;

/**
 * The gwt-webworker module works well for web workers that are sending simple
 * messages in each direction, but it does not provide support for Transferable objects
 * and SharedArrayBuffers. So we will use the linker that comes with gwt-webworker but
 * provide our own native JavaScript for the actual web worker code, so that we can
 * send these more complex objects.
 */
public class Worker extends com.google.gwt.webworker.client.Worker {

    public static native Worker create(String url) /*-{
        return new Worker(url);
    }-*/;
    
    protected Worker() {
        // constructors must be protected in JavaScriptObject overlays.
    }
    
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
    
    private static void onMessageImpl(MessageHandler messageHandler, MessageEvent event) {
        UncaughtExceptionHandler ueh = GWT.getUncaughtExceptionHandler();
        if (ueh != null) {
            try {
                messageHandler.onMessage(event);
            } catch (Exception ex) {
                ueh.onUncaughtException(ex);
            }
        } else {
            messageHandler.onMessage(event);
        }
    }
    
    public final native void setOnMessage(MessageHandler messageHandler) /*-{
        this.onmessage = function(event) {
            @emu.joric.worker.Worker::onMessageImpl(Lemu/joric/worker/MessageHandler;Lemu/joric/worker/MessageEvent;)(messageHandler, event);
        }
    }-*/;
}
