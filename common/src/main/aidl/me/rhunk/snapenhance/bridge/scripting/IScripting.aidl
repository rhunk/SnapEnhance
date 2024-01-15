package me.rhunk.snapenhance.bridge.scripting;

import me.rhunk.snapenhance.bridge.scripting.IPCListener;
import me.rhunk.snapenhance.bridge.scripting.AutoReloadListener;

interface IScripting {
    List<String> getEnabledScripts();

    @nullable String getScriptContent(String path);

    oneway void registerIPCListener(String channel, String eventName, IPCListener listener);

    oneway void sendIPCMessage(String channel, String eventName, in String[] args);

    @nullable String configTransaction(String module, String action, @nullable String key, @nullable String value, boolean save);

    oneway void registerAutoReloadListener(in AutoReloadListener listener);
}