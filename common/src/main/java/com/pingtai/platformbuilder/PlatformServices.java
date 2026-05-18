package com.pingtai.platformbuilder;

public class PlatformServices {

    public static PlatformHelper PLATFORM;

    public static void init(PlatformHelper platform) {
        if (PLATFORM != null) {
            throw new IllegalStateException("PlatformHelper already initialized");
        }
        PLATFORM = platform;
    }
}
