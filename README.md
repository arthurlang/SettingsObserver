# SettingsObserver
Receives call backs for changes to database(system，global，secure) 

扩展并优化了ContentObserver，提升了ContentObserver监听的性能，优化点：
全部settings值用1个ContentObserver
可1次监听多个settings值，不需要分开写多行监听代码
可指定监听的类型：集成了system，global，secure
注册可自由选择
支持切换用户空间
