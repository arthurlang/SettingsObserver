package com.lj.settingsobserver

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.Settings
import android.util.ArrayMap
import android.util.ArraySet
import android.util.Log
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap
/**
 * 监听手机Settings数据变化：实现思路参考了@see com.android.systemui.tuner.TunerServiceImpl
 * 1、addCallbackForUser
 * 2、addCallbackForData
 * 3、addCallback(callback, type, dataType, user, vararg keys: String)
 *
 * 重要提示：
 * Settings.Global.putInt接口默认使用当前进程uid
 * mCurrentUser 和 UserHandle.USER_ALL取值等价
 *
 */
class SettingsObserverImpl constructor(context: Context, private val bgHandler: Handler)
    : SettingsObserver() {

    private val TAG = "SettingsObserverImpl"
    private val mContentObserver: Observer = Observer()
    private var mContentResolver: ContentResolver? = null
    private var mCurrentUser = 0
    // Map of Uris we listen on to their settings keys.
    private val mListeningUris = ArrayMap<Uri, String>()
    //(setting_key)
    private val mUserMap = ArrayMap<String, Int>()
    //(setting_key, dataType)
    private val mValueTypeMap = ArrayMap<String, Int>()
    //(setting_key, defaultValue)
    private val mDefaultMap = ArrayMap<String, Long>()
    // Map of settings keys to the listener.
    private val mCallbacks = ConcurrentHashMap<String, MutableSet<Callback>>()

    init {
        mContentResolver = context.contentResolver
    }

    /**
     * @param keys = key1，key2, key3
     */
    override fun addCallback(callback: Callback, vararg keys: String) {
        addCallback(callback, SETTINGS_TYPE_SYSTEM, keys = *keys)
    }

    /**
     * @param type = System，Secure,Global
     */
    override fun addCallback(callback: Callback, type: Int, vararg keys: String) {
        addCallback(callback, type, VALUE_TYPE_DEFAULT, mCurrentUser, keys = * keys)
    }

    override fun addCallback(callback: Callback, dataType: Int, default: Long, vararg keys: String) {
        var defaultArrays : Array<Long> = emptyArray()
        if(default != 0L) {
            defaultArrays = arrayOf(default)
        }

        addCallback(callback, SETTINGS_TYPE_SYSTEM, dataType, mCurrentUser, defaultArrays, keys = *keys)
    }

    override fun addCallback(callback: Callback, dataType: Int, defaultArrays: Array<Long>, vararg keys: String) {
        addCallback(callback, SETTINGS_TYPE_SYSTEM, dataType, mCurrentUser, defaultArrays, keys = *keys)
    }

    override fun addCallback(callback: Callback, type: Int, dataType: Int, vararg keys: String) {
        addCallback(callback, type, dataType, mCurrentUser, keys = *keys)
    }

    /**
     * @param defaultArrays = {default1,default2.......}
     */
    override fun addCallback(callback: Callback, type: Int, dataType: Int, userHandle: Int,
                             defaultArrays: Array<Long>, vararg keys: String) {
        keys.forEachIndexed { i, key ->
            //判断是否注册成功
            //1、过滤key对应的相同数据类型
            if (mValueTypeMap.containsKey(key)) {
                log("e", "注册失败已经为同一个key:${key}指定过Modifier  originValue：${mValueTypeMap[key]}")
                return
            } else {
                mValueTypeMap[key] = dataType
            }

            //2、过滤key对应的相同默认值
            if(mDefaultMap.containsKey(key)) {
                log("e", "注册失败已经为同一个key:${key}指定过Modifier  originValue：${mDefaultMap[key]}")
                return
            } else {
                defaultArrays?.let {
                    var def = 0L
                    if(defaultArrays.isNotEmpty() && defaultArrays.size > i) {
                        def = defaultArrays[i]
                    }
                    mDefaultMap[key] = def
                }
            }
        }

        addCallback(callback, type, dataType, keys = *keys)
    }

    /**
     * @param userHandle = UserHandle.USER_ALL,UserHandle.USER_OWNER......
     */
    override fun addCallbackForUser(callback: Callback, userHandle: Int, vararg keys: String) {
        addCallbackForUser(callback, SETTINGS_TYPE_SYSTEM, keys = *keys)
    }

    override fun addCallbackForUser(callback: Callback, type: Int, userHandle: Int, vararg keys: String) {
        for (key in keys) {
            //设置存储空间
            if(mUserMap.containsKey(key)) {
                log("e", "注册失败！已经为key:${key}指定过存储空间，一个key仅能指定一个settings空间，originValue：${mUserMap[key]}")
                return
            } else {
                mUserMap[key] = userHandle
            }
        }

        addCallback(callback, type, VALUE_TYPE_DEFAULT, keys = * keys)
    }

    /**
     * dataType:string, int , long ,float
     */
    override fun addCallbackForType(callback: Callback, dataType: Int, vararg keys: String) {
        addCallback(callback, SETTINGS_TYPE_SYSTEM, dataType, mCurrentUser, keys = *keys)
    }

    override fun addCallbackForType(callback: Callback, type: Int, dataType: Int, vararg keys: String) {
        addCallback(callback, type, dataType, mCurrentUser, keys = *keys)
    }

    fun addCallback(callback: Callback, type: Int, dataType: Int, userHandle: Int, vararg keys: String) {
        repeat(keys.count()) {
            log( "addCallback keys:${keys[it]} type:$type dataType:$dataType userid:$userHandle  ")
        }

        for (key in keys) {
            if (!mCallbacks.containsKey(key)) {
                mCallbacks[key] = ArraySet()
            }
            mCallbacks[key]!!.add(callback)
            val uri = when (type) {
                SETTINGS_TYPE_SECURE -> Settings.Secure.getUriFor(key)
                SETTINGS_TYPE_GLOBAL -> Settings.Global.getUriFor(key)
                else -> Settings.System.getUriFor(key)
            }
            if (!mListeningUris.containsKey(uri)) {
                mListeningUris[uri] = key
                mContentResolver!!.registerContentObserver(uri, false, mContentObserver)
            }
            // Send the first state.
            callback.onContentChanged(key, emptyIfNull(getFirstValue(key, type)))
        }
    }

    override fun removeCallback(callback: Callback?) {
        for (list in mCallbacks.values) {
            list.remove(callback)
        }
    }

    override fun getIntValue(settingKey: String, type: Int, def: Int): Int {
        return getInt(settingKey, type, def)
    }

    override fun getValue(settingKey: String, type: Int, def: String?): String? {
        return getString(settingKey, type, def)
    }

    override fun setValue(settingKey: String, stringValue: String?, type: Int): Boolean {
        return putString(settingKey, stringValue, type)
    }

    override fun setValue(settingKey: String, intValue: Int, type: Int): Boolean {
       return putInt(settingKey, intValue, type)
    }

    private fun getFirstValue(key: String, type: Int): String? {
        var firstValue: String? = ""
        var userHandle = getSpecifiedUserId(key)
        var dataType: Int = getSpecifiedDataType(key)
        var def: Long = getSpecifiedDefaults(key)
        try {
            when (type) {
                SETTINGS_TYPE_SECURE -> {
                    when(dataType) {
                        VALUE_TYPE_STRING -> firstValue = Settings.Secure.getString(mContentResolver, key)
                        VALUE_TYPE_INT -> firstValue = Settings.Secure.getInt(mContentResolver, key, def.toInt()).toString()
                        VALUE_TYPE_LONG -> firstValue = Settings.Secure.getLong(mContentResolver, key, def).toString()
                        VALUE_TYPE_FLOAT -> firstValue = Settings.Secure.getFloat(mContentResolver, key, def.toFloat()).toString()
                        else -> {
                            firstValue = Settings.Secure.getString(mContentResolver, key)
                        }
                    }
                }
                SETTINGS_TYPE_GLOBAL -> {
                    when (dataType) {
                        VALUE_TYPE_STRING -> firstValue = Settings.Global.getString(mContentResolver, key)
                        VALUE_TYPE_INT -> firstValue = Settings.Global.getInt(mContentResolver, key, def.toInt()).toString()
                        VALUE_TYPE_LONG -> firstValue = Settings.Global.getLong(mContentResolver, key, def).toString()
                        VALUE_TYPE_FLOAT -> firstValue = Settings.Global.getFloat(mContentResolver, key, def.toFloat()).toString()
                        else -> {
                            firstValue = Settings.Global.getString(mContentResolver, key)
                        }
                    }
                }
                else -> {
                        when(dataType) {
                            VALUE_TYPE_STRING -> firstValue = Settings.System.getString(mContentResolver, key)
                            VALUE_TYPE_INT -> firstValue = Settings.System.getInt(mContentResolver, key, def.toInt()).toString()
                            VALUE_TYPE_LONG -> firstValue = Settings.System.getLong(mContentResolver, key, def).toString()
                            VALUE_TYPE_FLOAT -> firstValue = Settings.System.getFloat(mContentResolver, key, def.toFloat()).toString()
                            else -> {
                                firstValue = Settings.System.getString(mContentResolver, key)
                            }
                        }
                }
            }
            log("getFirstValue  firstValue:$firstValue key:$key type:$type dataType:$dataType userHandle:$userHandle ")
        }
        catch (e: Exception) {
            log("getFirstValue  throwException${e.cause}")
        }
        finally {
            return firstValue
        }
    }

    private fun getValueByUri(uri: Uri, key: String?): String {
        var value = ""
        var userHandle = getSpecifiedUserId(key)
        var dataType = getSpecifiedDataType(key)
        var def = getSpecifiedDefaults(key)
        try {
            when {
                uri.toString().contains(Settings.Secure.CONTENT_URI.toString()) -> {
                    //miui等机型上可以区分用户userHandle
                    when(dataType) {
                        VALUE_TYPE_STRING -> value = Settings.Secure.getString(mContentResolver, key)
                        VALUE_TYPE_INT -> value = Settings.Secure.getInt(mContentResolver, key, def.toInt()).toString()
                        VALUE_TYPE_LONG -> value = Settings.Secure.getLong(mContentResolver, key, def).toString()
                        VALUE_TYPE_FLOAT -> value = Settings.Secure.getFloat(mContentResolver, key, def.toFloat()).toString()
                        else -> {
                            value = Settings.Secure.getString(mContentResolver, key)
                        }
                    }
//                    when(dataType) {
//                        VALUE_TYPE_STRING -> value = Settings.Secure.getString(mContentResolver, key)
//                        VALUE_TYPE_INT -> value = Settings.Secure.getInt(mContentResolver, key, def.toInt()).toString()
//                        VALUE_TYPE_LONG -> value = Settings.Secure.getLong(mContentResolver, key, def).toString()
//                        VALUE_TYPE_FLOAT -> value = Settings.Secure.getFloat(mContentResolver, key, def.toFloat()).toString()
//                        else -> {
//                            value = Settings.Secure.getString(mContentResolver, key)
//                        }
//                    }
                }
                uri.toString().contains(Settings.Global.CONTENT_URI.toString()) -> {
                    when(dataType) {
                        VALUE_TYPE_STRING -> value = Settings.Global.getString(mContentResolver, key)
                        VALUE_TYPE_INT -> value = Settings.Global.getInt(mContentResolver, key, def.toInt()).toString()
                        VALUE_TYPE_LONG -> value = Settings.Global.getLong(mContentResolver, key, def).toString()
                        VALUE_TYPE_FLOAT -> value = Settings.Global.getFloat(mContentResolver, key, def.toFloat()).toString()
                        else -> {
                            value = Settings.Global.getString(mContentResolver, key)
                        }
                    }
                }
                else -> {
                    when(dataType) {
                        VALUE_TYPE_STRING -> value = Settings.System.getString(mContentResolver, key)
                        VALUE_TYPE_INT -> value = Settings.System.getInt(mContentResolver, key, def.toInt()).toString()
                        VALUE_TYPE_LONG -> value = Settings.System.getLong(mContentResolver, key, def).toString()
                        VALUE_TYPE_FLOAT -> value = Settings.System.getFloat(mContentResolver, key, def.toFloat()).toString()
                        else -> {
                            value = Settings.System.getString(mContentResolver, key)
                        }
                    }
                }
            }
            log("getValueByUri  firstValue:$value  uri:$uri key:$key def:$def dataType:$dataType userHandle:$userHandle ")
        }
        catch (e: Settings.SettingNotFoundException) {
            log("getValueByUri  throwException${e.cause}")
        }
        finally {
            return value
        }
    }

    private fun getInt(key: String?, type: Int, def: Int) : Int {
        var userHandle = getSpecifiedUserId(key)
        return when (type) {
            SETTINGS_TYPE_SECURE -> Settings.Secure.getInt(mContentResolver, key)
            SETTINGS_TYPE_GLOBAL -> Settings.Global.getInt(mContentResolver, key, def)
            else -> Settings.System.getInt(mContentResolver, key)
        }
    }

    private fun getString(key: String?, type: Int, def: String?) : String? {
        var userHandle = getSpecifiedUserId(key)
        return when (type) {
            SETTINGS_TYPE_SECURE -> Settings.Secure.getString(mContentResolver, key)
            SETTINGS_TYPE_GLOBAL -> Settings.Global.getString(mContentResolver, key)
            else -> Settings.System.getString(mContentResolver, key)
        } ?: return def
    }

    private fun putString(settingKey: String?, value: String?, type: Int) : Boolean{
        var userHandle = getSpecifiedUserId(settingKey)
        return when (type) {
            SETTINGS_TYPE_SECURE -> Settings.Secure.putString(mContentResolver, settingKey, value)
            SETTINGS_TYPE_GLOBAL -> Settings.Global.putString(mContentResolver, settingKey, value)
            else -> Settings.System.putString(mContentResolver, settingKey, value)
        }
    }

    private fun putInt(settingKey: String?, value: Int, type: Int) : Boolean{
        var userHandle = getSpecifiedUserId(settingKey)
        return when (type) {
            SETTINGS_TYPE_SECURE -> Settings.Secure.putInt(mContentResolver, settingKey, value)
            SETTINGS_TYPE_GLOBAL -> Settings.Global.putInt(mContentResolver, settingKey, value)
            else -> Settings.System.putInt(mContentResolver, settingKey, value)
        }
    }

    fun reloadSetting(uri: Uri) {
        val key = mListeningUris[uri]
        val callbacks = mCallbacks[key] ?: return
        var newValue = emptyIfNull(getValueByUri(uri, key))
        MainScope().launch {
            for (callback in callbacks) {
                callback.onContentChanged(key, newValue)
            }
        }
    }

    private fun reloadAllSetting() {
        for (key in mCallbacks.keys) {
            var firstValue = ""
            mListeningUris ?.let {
                it.filterValues { it_value -> it_value == key }.keys.forEach { uri: Uri ->
                    firstValue = emptyIfNull(getValueByUri(uri, key))
                    log("reloadAllSetting uri:${uri} key:${key} firstValue:$firstValue ")
                }
            }

            for (callback in mCallbacks[key]!!) {
                callback.onContentChanged(key, firstValue)
            }
        }
    }

    private fun registerAllSettingObserver() {
        mContentResolver!!.unregisterContentObserver(mContentObserver)
        mListeningUris ?.let { it.forEach { (uri, key) -> mContentResolver!!.registerContentObserver(
                uri, false, mContentObserver) } }
    }

    private inner class Observer : ContentObserver(bgHandler) {
        override fun onChange(selfChange: Boolean, uris: MutableCollection<Uri>, flags: Int) {
            for (u in uris) {
                reloadSetting(u)
                log("ContentObserver onChange keys:${u}  selfChange:${selfChange}")
            }
        }
    }

    private fun getSpecifiedUserId(key: String?): Int {
        return key?.let {
            log("getSpecifiedUserId user=$mUserMap[key] key:$key")
            mUserMap[key] ?: mCurrentUser
        } ?: mCurrentUser
    }

    private fun getSpecifiedDataType (key: String?) : Int {
        return key?.let { mValueTypeMap[key] ?: VALUE_TYPE_DEFAULT } ?: VALUE_TYPE_DEFAULT
    }

    private fun getSpecifiedDefaults (key: String?) : Long {
        return key?.let { mDefaultMap[key] ?: DEFAULT_INT_0 } ?: DEFAULT_INT_0
    }

    private fun log(str:String?) {
        str?.let { Log.d(TAG, it) }
    }


    private fun log(tag: String, str: String) {
        when(tag) {
            "e" -> {
                Log.e(TAG, str)
            }
            "d" -> {
                log(str)
            }
            "i" -> {
                Log.i(TAG, str)
            }
        }
    }

    fun emptyIfNull(inputString: String?): String {
        var temp = ""
        if (inputString != null) {
            temp = inputString
        }
        return temp
    }
}