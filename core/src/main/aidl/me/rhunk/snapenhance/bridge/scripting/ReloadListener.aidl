package me.rhunk.snapenhance.bridge.scripting;

oneway interface ReloadListener {
    void reloadScript(String path, String content);
}
