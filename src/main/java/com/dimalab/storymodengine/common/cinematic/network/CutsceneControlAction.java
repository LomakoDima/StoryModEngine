package com.dimalab.storymodengine.common.cinematic.network;

/** Which playback control a client is requesting — see {@link RequestCutsceneControlPacket}. */
public enum CutsceneControlAction {
    PAUSE, RESUME, SEEK, SPEED, SKIP
}
