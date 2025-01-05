package emu.joric.gwt;

import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;

import emu.joric.Program;
import emu.joric.ProgramLoader;
import emu.joric.config.AppConfigItem;

public class GwtProgramLoader implements ProgramLoader {

    @Override
    public void fetchProgram(AppConfigItem appConfigItem, Consumer<Program> programConsumer) {
        logToJSConsole("Fetching program '" + appConfigItem.getName() + "'");
        
        Program program = null;
        if ((appConfigItem.getFileType() != null) && (appConfigItem.getFileType().length() > 0)) {
            String binaryStr = getBinaryResource(appConfigItem.getFilePath());
            if (binaryStr != null) {
                program = new Program();
                program.setProgramData(convertBinaryStringToBytes(binaryStr));
            }
        }
        
        programConsumer.accept(program);
    }
    
    private byte[] convertBinaryStringToBytes(String binaryStr) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i=0; i<binaryStr.length(); i++) {
            out.write(binaryStr.charAt(i) & 0xFF);
        }
        return out.toByteArray();
    }

    /**
     * Fetches the given relative URL path as binary data returned in an ArrayBuffer.
     * 
     * @param url The relative URL path to fetch.
     * 
     * @return An ArrayBuffer containing the binary data of the resource.
     */
    private static native String getBinaryResource(String url) /*-{
        var req = new XMLHttpRequest();
        req.open("GET", url, false);  // The last parameter determines whether the request is asynchronous -> this case is sync.
        req.overrideMimeType('text/plain; charset=x-user-defined');
        req.send(null);
        if (req.status == 200) {                    
            return req.responseText;
        } else return null
    }-*/;
    
    private final native void logToJSConsole(String message)/*-{
        console.log(message);
    }-*/;
}
