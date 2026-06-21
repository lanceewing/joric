package emu.joric.teavm;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Uint8Array;

import emu.joric.Program;
import emu.joric.ProgramLoader;
import emu.joric.config.AppConfigItem;

public class TeaVMProgramLoader implements ProgramLoader {

    @Override
    public void fetchProgram(AppConfigItem appConfigItem, Consumer<Program> programConsumer) {
        try {
            TeaVMBrowser.logToConsole("TeaVM loader: fetchProgram name=" + appConfigItem.getName()
                    + ", path=" + appConfigItem.getFilePath());
            if (appConfigItem.getFileData() != null) {
                byte[] data = appConfigItem.getFileData();
                appConfigItem.setFileData(null);
                processProgramData(appConfigItem, data, programConsumer);
                return;
            } else if ((appConfigItem.getFilePath() == null) || appConfigItem.getFilePath().trim().isEmpty()) {
                programConsumer.accept(null);
                return;
            } else if (!appConfigItem.getFilePath().startsWith("http")) {
                FileHandle fileHandle = Gdx.files.internal(appConfigItem.getFilePath());
                if ((fileHandle != null) && fileHandle.exists()) {
                    byte[] data = fileHandle.readBytes();
                    TeaVMBrowser.logToConsole("TeaVM loader: loaded local asset bytes=" + data.length);
                    processProgramData(appConfigItem, data, programConsumer);
                    return;
                }
                TeaVMBrowser.logToConsole("TeaVM loader: local asset not found: " + appConfigItem.getFilePath());
            } else {
                String resolvedFilePath = applyFilePathOverride(appConfigItem.getFilePath());
                TeaVMBrowser.logToConsole("TeaVM loader: resolved remote path=" + resolvedFilePath);
                TeaVMBrowser.getBinaryResourceArrayBuffer(resolvedFilePath,
                        (arrayBuffer, status, responseUrl, errorMessage) -> {
                            try {
                                byte[] data = null;
                                if (arrayBuffer != null) {
                                    data = convertArrayBufferToBytes(arrayBuffer);
                                    TeaVMBrowser.logToConsole("TeaVM loader: remote bytes=" + data.length);
                                } else {
                                    TeaVMBrowser.logToConsole("TeaVM loader: remote fetch returned null array buffer"
                                            + ", status=" + status
                                            + ", url=" + responseUrl
                                            + (errorMessage != null ? ", error=" + errorMessage : ""));
                                }
                                processProgramData(appConfigItem, data, programConsumer);
                            } catch (Exception e) {
                                TeaVMBrowser.logToConsole(
                                        "TeaVM loader: exception while handling remote response: " + e);
                                programConsumer.accept(null);
                            }
                        });
                return;
            }
        } catch (Exception e) {
            TeaVMBrowser.logToConsole("TeaVM loader: exception while loading program: " + e);
            programConsumer.accept(null);
            return;
        }

        processProgramData(appConfigItem, null, programConsumer);
    }

    private void processProgramData(AppConfigItem appConfigItem, byte[] data, Consumer<Program> programConsumer) {
        Program program = null;

        try {
            byte[] programData = null;

            if ((data != null) && (data.length >= 4)) {
                if (isTapeFile(data)) {
                    appConfigItem.setFileType("TAPE");
                    programData = data;
                } else if (isDiskFile(data)) {
                    appConfigItem.setFileType("DISK");
                    programData = data;
                } else if (isZipFile(data)) {
                    TeaVMBrowser.logToConsole("TeaVM loader: scanning ZIP file...");
                    byte[] extracted = extractFromZip(appConfigItem, data);
                    if (extracted != null) {
                        programData = extracted;
                    }
                } else {
                    // Default to ROM for everything else.
                    appConfigItem.setFileType("ROM");
                    programData = data;
                }
            } else {
                appConfigItem.setFileType("UNK");
            }

            if (programData != null) {
                TeaVMBrowser.logToConsole("TeaVM loader: identified type=" + appConfigItem.getFileType()
                        + ", program bytes=" + programData.length);
                program = new Program();
                program.setProgramData(programData);
            } else {
                TeaVMBrowser.logToConsole("TeaVM loader: no program data identified, fileType="
                        + appConfigItem.getFileType());
            }
        } catch (Exception e) {
            TeaVMBrowser.logToConsole("TeaVM loader: exception while decoding program data: " + e);
        }

        programConsumer.accept(program);
    }

    private byte[] extractFromZip(AppConfigItem appConfigItem, byte[] zipData) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(zipData);
            ZipInputStream zis = new ZipInputStream(bais);
            ZipEntry zipEntry = zis.getNextEntry();

            while (zipEntry != null) {
                if (!zipEntry.isDirectory()) {
                    String entryName = zipEntry.getName().toLowerCase();
                    if (true) {
                        byte[] fileData = readBytesFromZipEntry(zis);
                        if (fileData != null) {
                            if (isTapeFile(fileData)) {
                                appConfigItem.setFileType("TAPE");
                                return fileData;
                            }
                            if (isDiskFile(fileData)) {
                                appConfigItem.setFileType("DISK");
                                return fileData;
                            }
                            // Default to ROM.
                            appConfigItem.setFileType("ROM");
                            return fileData;
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
        } catch (IOException e) {
            TeaVMBrowser.logToConsole("TeaVM loader: IO error reading ZIP: " + e);
        }
        return null;
    }

    private byte[] readBytesFromZipEntry(ZipInputStream zis) {
        try {
            byte[] buffer = new byte[4096];
            int read;
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            while ((read = zis.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private boolean isTapeFile(byte[] data) {
        return (data != null) && (data.length > 3)
                && (data[0] == 0x16) && (data[1] == 0x16) && (data[2] == 0x16);
    }

    private boolean isDiskFile(byte[] data) {
        return (data != null) && (data.length > 3)
                && ((data[0] & 0xFF) == 0x4D) && ((data[1] & 0xFF) == 0x46) && ((data[2] & 0xFF) == 0x4D);
    }

    private boolean isZipFile(byte[] data) {
        return (data != null) && (data.length >= 4)
                && ((data[0] & 0xFF) == 0x50) && ((data[1] & 0xFF) == 0x4B)
                && ((data[2] & 0xFF) == 0x03) && ((data[3] & 0xFF) == 0x04);
    }

    private String applyFilePathOverride(String filePath) {
        if ("localhost".equals(TeaVMBrowser.getHostName())) {
            String localHostBaseUrl = TeaVMBrowser.getProtocol() + "//" + TeaVMBrowser.getHost() + "/";
            if (filePath.startsWith("https://oric.games/")) {
                return filePath.replace("https://oric.games/", localHostBaseUrl);
            }
        }
        return filePath;
    }

    private byte[] convertArrayBufferToBytes(ArrayBuffer arrayBuffer) {
        Uint8Array uint8Array = Uint8Array.create(arrayBuffer);
        byte[] bytes = new byte[uint8Array.getLength()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte)(uint8Array.get(i) & 0xFF);
        }
        return bytes;
    }
}
