package lsl;

import com.sun.jna.platform.win32.COM.util.IUnknown;
import com.sun.jna.platform.win32.COM.util.annotation.ComInterface;
import com.sun.jna.platform.win32.COM.util.annotation.ComMethod;

@ComInterface(iid="{Your-OxySoft-Interface-ID}")
public interface OxyApplication extends IUnknown {
    @ComMethod
    void WriteEvent(String marker, String condition);
}
