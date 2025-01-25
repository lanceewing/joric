package emu.joric.gwt;

import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;

import com.akjava.gwt.jszip.JSFile;
import com.akjava.gwt.jszip.JSZip;
import com.akjava.gwt.jszip.Uint8Array;
import com.google.gwt.core.client.JsArrayString;

import emu.joric.Program;
import emu.joric.ProgramLoader;
import emu.joric.config.AppConfigItem;

public class GwtProgramLoader implements ProgramLoader {

    @Override
    public void fetchProgram(AppConfigItem appConfigItem, Consumer<Program> programConsumer) {
        logToJSConsole("Fetching program '" + appConfigItem.getName() + "'");
        
        // For configs such as BASIC, there is no file path, so return without program.
        if ("".equals(appConfigItem.getFilePath())) {
            programConsumer.accept(null);
            return;
        }
        
        Program program = null;
        byte[] programData = null;
        
        String binaryStr = getBinaryResource(appConfigItem.getFilePath());
        if (binaryStr != null) {
            // Use the data to identify the type of program.
            byte[] data = convertBinaryStringToBytes(binaryStr);
            
            if ((data != null) && (data.length >= 4)) {
                if ((data[0] == 0x16) && (data[1] == 0x16) && (data[2] == 0x16)) {
                    // At least 3 0x16 bytes followed by a 0x24 is a tape file.
                    appConfigItem.setFileType("TAPE");
                    programData = data;
                }
                else if ((data[0] == 0x4D) && (data[1] == 0x46) && (data[2] == 0x4D)) {
                    // MFM_DISK - 4D 46 4D 5F 44 49 53 4B
                    appConfigItem.setFileType("DISK");
                    programData = data;
                }
                else if ((data[0] == 0x50) && (data[1] == 0x4B) && (data[2] == 0x03) && (data[3] == 0x04)) {
                    // ZIP starts with: 50 4B 03 04
                    logToJSConsole("Scanning ZIP file...");
                    
                    JSZip jsZip = JSZip.loadFromArray(Uint8Array.createUint8(data));
                    JsArrayString files = jsZip.getFiles();
    
                    for (int i=0; i < files.length(); i++) {
                        String fileName = files.get(i);
                        JSFile file = jsZip.getFile(fileName);
                        byte[] fileData = file.asUint8Array().toByteArray();
                        if (isDiskFile(fileData)) {
                            programData = fileData;
                            appConfigItem.setFileType("DISK");
                            break;
                        }
                        if (isTapeFile(fileData)) {
                            programData = fileData;
                            appConfigItem.setFileType("TAPE");
                            break;
                        }
                    }
                }
                else {
                    logToJSConsole("Sorry, the URL provided does not appear to be for a recognised Oric program file format.");
                    appConfigItem.setFileType("UNK");
                }
            }
            else {
                logToJSConsole("Sorry, the URL provided does not appear to be for a recognised Oric program file format.");
                appConfigItem.setFileType("UNK");
            }
        }
        
        if (programData != null) {
            if (!"UNK".equals(appConfigItem.getFileType())) {
                logToJSConsole("Identified " + appConfigItem.getFileType() + " image in program data.");
            }
            program = new Program();
            program.setProgramData(programData);
        }
        
        programConsumer.accept(program);
    }
    
    private boolean isTapeFile(byte[] data) {
        return ((data != null) && (data.length > 3) && 
                (data[0] == 0x16) && (data[1] == 0x16) && (data[2] == 0x16));
    }
    
    private boolean isDiskFile(byte[] data) {
        return ((data != null) && (data.length > 3) && 
                (data[0] == 0x4D) && (data[1] == 0x46) && (data[2] == 0x4D));
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
