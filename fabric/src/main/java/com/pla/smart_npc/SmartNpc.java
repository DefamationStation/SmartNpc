package com.pla.smart_npc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Shared identity; platform startup lives in SmartNpcFabric. */
public final class SmartNpc {
    public static final String MODID = "smart_npc";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    private SmartNpc() {}
}
