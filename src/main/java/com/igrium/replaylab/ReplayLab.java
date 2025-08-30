package com.igrium.replaylab;

import lombok.Getter;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class ReplayLab implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger(ReplayLab.class);

    @Getter
    private static ReplayLab instance;

    public ReplayLab() {
        instance = this;
    }

    @Override
    public void onInitialize() {
    }

    public void openReplay(File replayFile) {
    }
}