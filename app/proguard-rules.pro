# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the AGP default proguard file.

# Keep all BroadcastReceiver subclasses (BootReceiver, PowerSaveReceiver must survive shrinking)
-keep public class com.example.browserblock.BootReceiver { *; }
-keep public class com.example.browserblock.PowerSaveReceiver { *; }
-keep public class com.example.browserblock.OpenElsewhereDeviceAdmin { *; }
-keep public class com.example.browserblock.BlockerAccessibilityService { *; }
-keep public class com.example.browserblock.ForegroundPollingService { *; }
-keep public class com.example.browserblock.NotificationListenerService { *; }
