package emu.joric;

import java.util.function.Consumer;

import emu.joric.config.AppConfigItem;

public interface ProgramLoader {

    void fetchProgram(AppConfigItem appConfigItem, Consumer<Program> programConsumer);
    
}
