package emu.joric.lwjgl3;

import java.util.function.Consumer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

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
        FileHandle fileHandle = Gdx.files.internal(appConfigItem.getFilePath());
        if (fileHandle != null) {
            if (fileHandle.exists()) {
                program = new Program(appConfigItem, fileHandle.readBytes());
            }
        }
        
        programConsumer.accept(program);
    }

}
