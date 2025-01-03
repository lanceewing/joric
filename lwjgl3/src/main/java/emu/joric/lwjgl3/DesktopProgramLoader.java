package emu.joric.lwjgl3;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.function.Consumer;

import emu.joric.PixelData;
import emu.joric.Program;
import emu.joric.ProgramLoader;
import emu.joric.config.AppConfigItem;

public class DesktopProgramLoader implements ProgramLoader {

    public DesktopProgramLoader(PixelData pixelData) {
        // TODO Auto-generated constructor stub
    }

    @Override
    public void fetchProgram(AppConfigItem appConfigItem, Consumer<Program> programConsumer) {
        Program program = null;
        BufferedInputStream bis = null;
        
        try {
            URL url = new URL(appConfigItem.getFilePath());
            URLConnection connection = url.openConnection();
            
            int b = 0;
            bis = new BufferedInputStream(connection.getInputStream());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            while ((b = bis.read()) != -1 ) {
                out.write(b);
            }
            
            program = new Program(appConfigItem, out.toByteArray());
            
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
        
        programConsumer.accept(program);
    }

}
