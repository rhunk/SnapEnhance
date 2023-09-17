package me.rhunk.snapenhance.bridge.scripting;

import me.rhunk.snapenhance.bridge.scripting.ReloadListener;
import me.rhunk.snapenhance.bridge.scripting.IPCListener;

interface IScripting {
    List<String> getEnabledScripts();

    @nullable String getScriptContent(String path);

    void registerReloadListener(ReloadListener listener);

    void registerIPCListener(String channel, String eventName, IPCListener listener);

    void sendIPCMessage(String channel, String eventName, in String[] args);
}