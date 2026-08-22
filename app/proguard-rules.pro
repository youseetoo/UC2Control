# Keep usb-serial-for-android driver classes: they are instantiated reflectively
# by ProbeTable (getConstructor / getMethod("probe"|"getSupportedDevices")).
-keep class com.hoho.android.usbserial.driver.** { *; }
-keepclassmembers class com.hoho.android.usbserial.driver.** {
    public static java.util.Map getSupportedDevices();
    public static boolean probe(android.hardware.usb.UsbDevice);
    public <init>(android.hardware.usb.UsbDevice);
}
