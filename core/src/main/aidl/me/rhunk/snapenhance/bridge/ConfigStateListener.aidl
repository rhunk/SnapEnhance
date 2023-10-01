package me.rhunk.snapenhance.bridge;

oneway interface ConfigStateListener {
    void onConfigChanged();
    void onRestartRequired();
    void onCleanCacheRequired();
}