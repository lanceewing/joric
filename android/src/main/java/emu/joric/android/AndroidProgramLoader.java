package emu.joric.android;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import emu.joric.Program;
import emu.joric.ProgramLoader;
import emu.joric.config.AppConfigItem;

public class AndroidProgramLoader implements ProgramLoader {

    @Override
    public void fetchProgram(AppConfigItem appConfigItem, Consumer<Program> programConsumer) {
        // For configs such as BASIC, there is no file path, so return without program.
        if ("".equals(appConfigItem.getFilePath())) {
            programConsumer.accept(null);
            return;
        }

        Program program = null;
        BufferedInputStream bis = null;
        byte[] programData = null;

        if (!appConfigItem.getFilePath().startsWith("http")) {
            FileHandle fileHandle = Gdx.files.internal(appConfigItem.getFilePath());
            if (fileHandle != null) {
                if (fileHandle.exists()) {
                    program = new Program(appConfigItem, fileHandle.readBytes());
                }
            }
        }
        else {
            try {
                URL url = new URL(appConfigItem.getFilePath());
                URLConnection connection = url.openConnection();

                int b = 0;
                bis = new BufferedInputStream(connection.getInputStream());
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                while ((b = bis.read()) != -1 ) {
                    out.write(b);
                }

                byte[] data = out.toByteArray();

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
                        ByteArrayInputStream bais = new ByteArrayInputStream(data);
                        ZipInputStream zis = new ZipInputStream(bais);
                        ZipEntry zipEntry = zis.getNextEntry();

                        while (zipEntry != null) {
                            try {
                                if (!zipEntry.isDirectory()) {
                                    byte[] fileData = readBytesFromInputStream(zis);
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
                            } catch (IOException e) {
                                throw new RuntimeException("IO error reading zip entry: " + zipEntry.getName(), e);
                            }

                            zipEntry = zis.getNextEntry();
                        }
                    }
                    else {
                        appConfigItem.setFileType("UNK");
                    }
                }
                else {
                    appConfigItem.setFileType("UNK");
                }

                if (programData != null) {
                    if (!"UNK".equals(appConfigItem.getFileType())) {
                        System.out.println("Identified " + appConfigItem.getFileType() + " image in program data.");
                    }
                    program = new Program(appConfigItem, programData);
                }

            } catch (Exception e) {
                // Ignore.
            } finally {
                if (bis != null) {
                    try {
                        bis.close();
                    } catch (Exception e2) {
                        // Ignore.
                    }
                }
            }
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

    private byte[] readBytesFromInputStream(InputStream is) throws IOException {
        int numOfBytesReads;
        byte[] data = new byte[256];
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while ((numOfBytesReads = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, numOfBytesReads);
        }
        return buffer.toByteArray();
    }
}
