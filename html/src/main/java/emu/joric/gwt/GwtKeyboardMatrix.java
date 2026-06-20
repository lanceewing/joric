package emu.joric.gwt;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.typedarrays.shared.Uint8ClampedArray;

import emu.joric.KeyboardMatrix;

/**
 * GWT implementation of the KeyboardMatrix interface. It uses a SharedArrayBuffer
 * so that both the UI thread and web worker see the same shared data. It does not
 * need to worry about synchronising the SharedArrayBuffer access with Atomics either,
 * as one side is always reading and will not modify.
 */
public class GwtKeyboardMatrix extends KeyboardMatrix {
    
    private Uint8ClampedArray keyMatrix;

    /**
     * Constructor for GwtKeyboardMatrix (used by UI thread)
     */
    public GwtKeyboardMatrix() {
        keyMatrix = createKeyMatrixArray();
        // Install a DOM-level Meta-key watcher. See
        // registerMetaKeyWatcher's Javadoc for the macOS keyup-
        // suppression behaviour it works around. Only the UI-thread
        // constructor calls this - the web-worker constructor below
        // has no DOM to listen to.
        registerMetaKeyWatcher();
    }
    
    /**
     * Constructor for GwtKeyboardMatrix (used by web worker).
     * 
     * @param sharedArrayBuffer The same SharedArrayBuffer used by the UI thread.
     */
    public GwtKeyboardMatrix(JavaScriptObject sharedArrayBuffer) {
        keyMatrix = createKeyMatrixArray(sharedArrayBuffer);
    }

    private native Uint8ClampedArray createKeyMatrixArray(JavaScriptObject sharedArrayBuffer)/*-{
        return new Uint8ClampedArray(sharedArrayBuffer);
    }-*/;
    
    private native Uint8ClampedArray createKeyMatrixArray()/*-{
        var sharedArrayBuffer = new SharedArrayBuffer(8);
        return new Uint8ClampedArray(sharedArrayBuffer);
    }-*/;
    
    public native JavaScriptObject getSharedArrayBuffer()/*-{
        var keyMatrix = this.@emu.joric.gwt.GwtKeyboardMatrix::keyMatrix;
        return keyMatrix.buffer;
    }-*/;

    /**
     * Installs DOM-level keydown/keyup listeners that synthesise
     * keyup events for non-Meta keys whose natural keyup was
     * swallowed by macOS while a Meta key was held.
     *
     * macOS suppresses keyUp events for non-modifier keys whose keyUp
     * would occur while a Meta (CMD) key is held. THis is an OS-level
     * behaviour inherited by Chrome, Safari and Firefox (open
     * browser bugs dating back to 2011, still unresolved as of
     * writing). The consequence in libGDX's GWT backend
     * (DefaultGwtInput.java) is that its internal pressedKeys[]
     * array gets stuck `true` for the un-released key. Typical
     * symptom for Oric input behaviour: after CMD+X, X stays pressed,
     * generating auto-repeat X inputs until the next plain X press.
     *
     * The workaround is to listen at the DOM level (capture phase, so 
     * we run before libGDX's own document-level bubble-phase listener).
     * On every keydown/keyup, observe event.metaKey to track Meta
     * pressed/released. On the released transition, dispatch a
     * synthetic keyup for every non-Meta key whose keydown we saw
     * without a subsequent keyup. The synthetic events flow through
     * libGDX's normal keyup handler, which clears its internal
     * pressedKeys[] state. (Note that in the case where a non-Meta
     * key is still held down when Meta is released, MacOS appears
     * to generate suitable key events so they effectively appear as
     * new key presses without the Meta modifier.)
     */
    private native void registerMetaKeyWatcher()/*-{
        var metaPressed = false;
        // DOM keyCodes of non-Meta keys we've seen keydown but not
        // (yet) keyup for. Excludes the Meta key DOM codes (91, 92,
        // 93 - values vary slightly across browsers and key
        // locations - plus 224 for some Firefox variants).
        var trackedKeys = {};

        var update = function(e) {
            var wasMetaPressed = metaPressed;
            var isMetaPressed = !!e.metaKey;
            metaPressed = isMetaPressed;
            var code = e.keyCode;

            // Track DOM press/release for non-Meta keys.
            if (code !== 91 && code !== 92 && code !== 93 && code !== 224) {
                if (e.type === 'keydown') {
                    trackedKeys[code] = true;
                } else if (e.type === 'keyup') {
                    delete trackedKeys[code];
                }
            }

            // Meta key just released. Synthesise keyup events for any
            // still-tracked keys so libGDX's internal pressedKeys[]
            // gate is cleared. Snapshot before iterating because the
            // synthetic dispatch re-enters this handler (the keyup
            // branch above) and would otherwise mutate trackedKeys
            // mid-iteration.
            if (wasMetaPressed && !isMetaPressed) {
                var snapshot = [];
                for (var k in trackedKeys) snapshot.push(k);
                trackedKeys = {};
                for (var i = 0; i < snapshot.length; i++) {
                    var kc = +snapshot[i];
                    // keyCode and which aren't part of KeyboardEvent's init dict,
                    // so passing them to the constructor has no effect. Override
                    // via Object.defineProperty after construction. libGDX's
                    // keyForCode reads keyCode via NativeEvent.getKeyCode().
                    var ke = new KeyboardEvent('keyup', { bubbles: true, cancelable: true });
                    Object.defineProperty(ke, 'keyCode', { value: kc });
                    Object.defineProperty(ke, 'which',   { value: kc });
                    $doc.dispatchEvent(ke);
                }
            }
        };

        $doc.addEventListener('keydown', update, true);
        $doc.addEventListener('keyup',   update, true);
    }-*/;

    @Override
    public int getKeyMatrixRow(int row) {
        return keyMatrix.get(row);
    }

    @Override
    public void setKeyMatrixRow(int row, int value) {
        keyMatrix.set(row, value);
    }
}
