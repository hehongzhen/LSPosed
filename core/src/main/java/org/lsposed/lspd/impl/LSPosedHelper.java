package org.lsposed.lspd.impl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import io.github.libinstalld.api.InstalldInterface;
import io.github.libinstalld.api.errors.InstalldFailedError;

public class LSPosedHelper {

    @SuppressWarnings("UnusedReturnValue")
    public static <T> InstalldInterface.MethodUnhooker<Method>
    hookMethod(Class<? extends InstalldInterface.Hooker> hooker, Class<T> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            var method = clazz.getDeclaredMethod(methodName, parameterTypes);
            return LSPosedBridge.doHook(method, InstalldInterface.PRIORITY_DEFAULT, hooker);
        } catch (NoSuchMethodException e) {
            throw new InstalldFailedError(e);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public static <T> Set<InstalldInterface.MethodUnhooker<Method>>
    hookAllMethods(Class<? extends InstalldInterface.Hooker> hooker, Class<T> clazz, String methodName) {
        var unhooks = new HashSet<InstalldInterface.MethodUnhooker<Method>>();
        for (var method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                unhooks.add(LSPosedBridge.doHook(method, InstalldInterface.PRIORITY_DEFAULT, hooker));
            }
        }
        return unhooks;
    }

    @SuppressWarnings("UnusedReturnValue")
    public static <T> InstalldInterface.MethodUnhooker<Constructor<T>>
    hookConstructor(Class<? extends InstalldInterface.Hooker> hooker, Class<T> clazz, Class<?>... parameterTypes) {
        try {
            var constructor = clazz.getDeclaredConstructor(parameterTypes);
            return LSPosedBridge.doHook(constructor, InstalldInterface.PRIORITY_DEFAULT, hooker);
        } catch (NoSuchMethodException e) {
            throw new InstalldFailedError(e);
        }
    }
}
