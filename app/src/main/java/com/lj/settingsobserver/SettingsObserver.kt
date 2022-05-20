package com.lj.settingsobserver

abstract class SettingsObserver {

    @JvmOverloads open fun getIntValue(settingKey: String, type: Int = SETTINGS_TYPE_SYSTEM, def: Int = 0): Int {return def}
    @JvmOverloads open fun getValue(settingKey: String, type: Int = SETTINGS_TYPE_SYSTEM, def: String? = ""): String? {return def}

    @JvmOverloads open fun setValue(settingKey: String, stringValue: String?, type: Int = SETTINGS_TYPE_SYSTEM) : Boolean{return true}
    @JvmOverloads open fun setValue(settingKey: String, intValue: Int, type: Int = SETTINGS_TYPE_SYSTEM) : Boolean {return true}

    @JvmOverloads open fun addCallback(callback: Callback, type: Int, vararg keys: String) {}
    @JvmOverloads open fun addCallback(callback: Callback, vararg keys: String) {}
    @JvmOverloads open fun addCallback(callback: Callback, type: Int, valueType: Int, vararg keys: String) {}
    @JvmOverloads open fun addCallback(callback: Callback, valueType: Int, def: Long, vararg keys: String) {}
    @JvmOverloads open fun addCallback(callback: Callback, valueType: Int, defaultArrays: Array<Long>, vararg keys: String) {}
    @JvmOverloads open fun addCallback(callback: Callback, type: Int, valueType: Int,  userHandle: Int, defaultArrays: Array<Long>, vararg keys: String) {}
    @JvmOverloads open fun addCallbackForType(callback: Callback, type: Int, valueType: Int, vararg keys: String) {}
    @JvmOverloads open fun addCallbackForType(callback: Callback, valueType: Int, vararg keys: String) {}

    @JvmOverloads open fun addCallbackForUser(callback: Callback, type: Int, userHandle: Int, vararg keys: String) {}
    @JvmOverloads open fun addCallbackForUser(callback: Callback, userHandle: Int, vararg keys: String) {}

    abstract fun removeCallback(callback: Callback?)

    interface Callback {
        /**
         *@param newValue 如果是null，返回“”
         */
        fun onContentChanged(key: String?, newValue: String?)
    }

    companion object {
        //settings数据库类型
        const val SETTINGS_TYPE_SYSTEM: Int = 0
        const val SETTINGS_TYPE_SECURE: Int = 1
        const val SETTINGS_TYPE_GLOBAL: Int = 2

        //监听数据类型
        const val VALUE_TYPE_STRING = 0
        const val VALUE_TYPE_INT = 1
        const val VALUE_TYPE_LONG = 2
        const val VALUE_TYPE_FLOAT = 3
        const val VALUE_TYPE_DEFAULT = VALUE_TYPE_STRING

        //监听数据默认值
        const val DEFAULT_INT_0 = 0L
        const val DEFAULT_INT_1 = 1L
    }
}