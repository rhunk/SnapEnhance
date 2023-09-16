package me.rhunk.snapenhance.bridge.scripting;

import me.rhunk.snapenhance.bridge.scripting.ReloadListener;

interface IScripting {
    List<String> getEnabledScriptPaths();

    String getScriptContent(String path);

    void registerReloadListener(ReloadListener listener);
}