package com.dimalab.storymodengine.common.model;

/** A lazily-loaded model's current status — see {@code LazyModelLoader}. */
public enum ModelLoadState {
    NOT_LOADED,
    LOADING,
    LOADED,
    FAILED
}
