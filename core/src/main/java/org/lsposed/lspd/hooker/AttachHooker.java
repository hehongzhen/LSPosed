package org.lsposed.lspd.hooker;

import android.app.ActivityThread;

import de.robv.android.xposed.XposedInit;
import io.github.libinstalld.api.InstalldInterface;
import io.github.libinstalld.api.annotations.AInvocation;
import io.github.libinstalld.api.annotations.InstalldHooker;

@InstalldHooker
public class AttachHooker implements InstalldInterface.Hooker {

    @AInvocation
    public static void afterHookedMethod(InstalldInterface.AfterHookCallback callback) {
        XposedInit.loadModules((ActivityThread) callback.getThisObject());
    }
}
