package me.rhunk.snapenhance.data.wrapper.impl.media.opera

import me.rhunk.snapenhance.data.wrapper.AbstractWrapper
import me.rhunk.snapenhance.util.ReflectionHelper

class Layer(obj: Any?) : AbstractWrapper(obj) {
    val paramMap: ParamMap
        get() {
            val layerControllerField = ReflectionHelper.searchFieldContainsToString(
                instanceNonNull()::class.java,
                instance,
                "OperaPageModel"
            )!!

            val paramsMapHashMap = ReflectionHelper.searchFieldStartsWithToString(
                layerControllerField.type,
                layerControllerField[instance] as Any, "OperaPageModel"
            )!!
            return ParamMap(paramsMapHashMap[layerControllerField[instance]]!!)
        }
}
