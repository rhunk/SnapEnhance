package me.rhunk.snapenhance.common.scripting.bindings

import me.rhunk.snapenhance.common.scripting.ScriptRuntime
import me.rhunk.snapenhance.common.scripting.type.ModuleInfo

class BindingsContext(
    val moduleInfo: ModuleInfo,
    val runtime: ScriptRuntime
)