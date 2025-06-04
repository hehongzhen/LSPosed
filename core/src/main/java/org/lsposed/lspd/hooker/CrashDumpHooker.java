package org.lsposed.lspd.hooker;


import org.lsposed.lspd.impl.LSPosedBridge;

import io.github.libinstalld.api.InstalldInterface;
import io.github.libinstalld.api.annotations.BInvocation;
import io.github.libinstalld.api.annotations.InstalldHooker;

@InstalldHooker
public class CrashDumpHooker implements InstalldInterface.Hooker {

    @BInvocation
    public static void beforeHookedMethod(InstalldInterface.BeforeHookCallback callback) {
        try {
            var e = (Throwable) callback.getArgs()[0];
        } catch (Throwable ignored) {
        }
    }
}
