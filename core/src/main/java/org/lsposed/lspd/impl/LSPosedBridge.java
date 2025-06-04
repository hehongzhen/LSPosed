package org.lsposed.lspd.impl;


import androidx.annotation.NonNull;

import org.lsposed.lspd.nativebridge.InstalldBridge;

import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.XposedBridge;
import io.github.libinstalld.api.InstalldInterface;
import io.github.libinstalld.api.annotations.AInvocation;
import io.github.libinstalld.api.annotations.BInvocation;
import io.github.libinstalld.api.annotations.InstalldHooker;
import io.github.libinstalld.api.errors.InstalldFailedError;

public class LSPosedBridge {

    private static final String TAG = "LSPosed-Bridge";

    private static final String castException = "Return value's type from hook callback does not match the hooked method";

    private static final Method getCause;

    static {
        Method tmp;
        try {
            tmp = InvocationTargetException.class.getMethod("getCause");
        } catch (Throwable e) {
            tmp = null;
        }
        getCause = tmp;
    }

    public static class HookerCallback {
        @NonNull
        final Method beforeInvocation;
        @NonNull
        final Method afterInvocation;

        final int beforeParams;
        final int afterParams;

        public HookerCallback(@NonNull Method beforeInvocation, @NonNull Method afterInvocation) {
            this.beforeInvocation = beforeInvocation;
            this.afterInvocation = afterInvocation;
            this.beforeParams = beforeInvocation.getParameterCount();
            this.afterParams = afterInvocation.getParameterCount();
        }
    }

    public static void log(String text) {
    }

    public static void log(Throwable t) {

    }

    public static class NativeHooker<T extends Executable> {
        private final Object params;

        private NativeHooker(Executable method) {
            var isStatic = Modifier.isStatic(method.getModifiers());
            Object returnType;
            if (method instanceof Method) {
                returnType = ((Method) method).getReturnType();
            } else {
                returnType = null;
            }
            params = new Object[]{
                    method,
                    returnType,
                    isStatic,
            };
        }

        // This method is quite critical. We should try not to use system methods to avoid
        // endless recursive
        public Object callInstall(Object[] args) throws Throwable {
            LSPosedHookCallback<T> callback = new LSPosedHookCallback<>();

            var array = ((Object[]) params);

            var method = (T) array[0];
            var returnType = (Class<?>) array[1];
            var isStatic = (Boolean) array[2];

            callback.method = method;

            if (isStatic) {
                callback.thisObject = null;
                callback.args = args;
            } else {
                callback.thisObject = args[0];
                callback.args = new Object[args.length - 1];
                //noinspection ManualArrayCopy
                for (int i = 0; i < args.length - 1; ++i) {
                    callback.args[i] = args[i + 1];
                }
            }

            Object[][] callbacksSnapshot = InstalldBridge.callbackSnapshot(HookerCallback.class, method);
            Object[] modernSnapshot = callbacksSnapshot[0];
            Object[] legacySnapshot = callbacksSnapshot[1];

            if (modernSnapshot.length == 0 && legacySnapshot.length == 0) {
                try {
                    return InstalldBridge.ioMethod(method, callback.thisObject, callback.args);
                } catch (InvocationTargetException ite) {
                    throw (Throwable) InstalldBridge.ioMethod(getCause, ite);
                }
            }

            Object[] ctxArray = new Object[modernSnapshot.length];
            XposedBridge.LegacyApiSupport<T> legacy = null;

            // call "before method" callbacks
            int beforeIdx;
            for (beforeIdx = 0; beforeIdx < modernSnapshot.length; beforeIdx++) {
                try {
                    var hooker = (HookerCallback) modernSnapshot[beforeIdx];
                    if (hooker.beforeParams == 0) {
                        ctxArray[beforeIdx] = hooker.beforeInvocation.invoke(null);
                    } else {
                        ctxArray[beforeIdx] = hooker.beforeInvocation.invoke(null, callback);
                    }
                } catch (Throwable t) {
                    LSPosedBridge.log(t);

                    // reset result (ignoring what the unexpectedly exiting callback did)
                    callback.setResult(null);
                    callback.isSkipped = false;
                    continue;
                }

                if (callback.isSkipped) {
                    // skip remaining "before" callbacks and corresponding "after" callbacks
                    beforeIdx++;
                    break;
                }
            }

            if (!callback.isSkipped && legacySnapshot.length != 0) {
                // TODO: Separate classloader
                legacy = new XposedBridge.LegacyApiSupport<>(callback, legacySnapshot);
                legacy.handleBefore();
            }

            // call original method if not requested otherwise
            if (!callback.isSkipped) {
                try {
                    var result = InstalldBridge.ioMethod(method, callback.thisObject, callback.args);
                    callback.setResult(result);
                } catch (InvocationTargetException e) {
                    var throwable = (Throwable) InstalldBridge.ioMethod(getCause, e);
                    callback.setThrowable(throwable);
                }
            }

            // call "after method" callbacks
            for (int afterIdx = beforeIdx - 1; afterIdx >= 0; afterIdx--) {
                Object lastResult = callback.getResult();
                Throwable lastThrowable = callback.getThrowable();
                var hooker = (HookerCallback) modernSnapshot[afterIdx];
                try {
                    if (hooker.afterParams == 0) {
                        hooker.afterInvocation.invoke(null);
                    } else if (hooker.afterParams == 1) {
                        hooker.afterInvocation.invoke(null, callback);
                    } else {
                        hooker.afterInvocation.invoke(null, callback, ctxArray[afterIdx]);
                    }
                } catch (Throwable t) {
                    LSPosedBridge.log(t);

                    // reset to last result (ignoring what the unexpectedly exiting callback did)
                    if (lastThrowable == null) {
                        callback.setResult(lastResult);
                    } else {
                        callback.setThrowable(lastThrowable);
                    }
                }
            }

            if (legacy != null) {
                legacy.handleAfter();
            }

            // return
            var t = callback.getThrowable();
            if (t != null) {
                throw t;
            } else {
                var result = callback.getResult();
                if (returnType != null && !returnType.isPrimitive() && !InstalldBridge.instanceOf(result, returnType)) {
                    throw new ClassCastException(castException);
                }
                return result;
            }
        }
    }

    public static void dummyCallback() {
    }

    public static <T extends Executable> InstalldInterface.MethodUnhooker<T>
    doHook(T hookMethod, int priority, Class<? extends InstalldInterface.Hooker> hooker) {
        if (Modifier.isAbstract(hookMethod.getModifiers())) {
            throw new IllegalArgumentException("Cannot hook abstract methods: " + hookMethod);
        } else if (hookMethod.getDeclaringClass().getClassLoader() == LSPosedContext.class.getClassLoader()) {
            throw new IllegalArgumentException("Do not allow hooking inner methods");
        } else if (hookMethod.getDeclaringClass() == Method.class && hookMethod.getName().equals("invoke")) {
            throw new IllegalArgumentException("Cannot hook Method.invoke");
        } else if (hooker == null) {
            throw new IllegalArgumentException("hooker should not be null!");
        } else if (hooker.getAnnotation(InstalldHooker.class) == null) {
            throw new IllegalArgumentException("Hooker should be annotated with @InstalldHooker");
        }

        Method beforeInvocation = null, afterInvocation = null;
        var modifiers = Modifier.PUBLIC | Modifier.STATIC;
        for (var method : hooker.getDeclaredMethods()) {
            if (method.getAnnotation(BInvocation.class) != null) {
                if (beforeInvocation != null) {
                    throw new IllegalArgumentException("More than one method annotated with @BInvocation");
                }
                boolean valid = (method.getModifiers() & modifiers) == modifiers;
                var params = method.getParameterTypes();
                if (params.length == 1) {
                    valid &= params[0].equals(InstalldInterface.BeforeHookCallback.class);
                } else if (params.length != 0) {
                    valid = false;
                }
                if (!valid) {
                    throw new IllegalArgumentException("BInvocation method format is invalid");
                }
                beforeInvocation = method;
            }
            if (method.getAnnotation(AInvocation.class) != null) {
                if (afterInvocation != null) {
                    throw new IllegalArgumentException("More than one method annotated with @AInvocation");
                }
                boolean valid = (method.getModifiers() & modifiers) == modifiers;
                valid &= method.getReturnType().equals(void.class);
                var params = method.getParameterTypes();
                if (params.length == 1 || params.length == 2) {
                    valid &= params[0].equals(InstalldInterface.AfterHookCallback.class);
                } else if (params.length != 0) {
                    valid = false;
                }
                if (!valid) {
                    throw new IllegalArgumentException("AInvocation method format is invalid");
                }
                afterInvocation = method;
            }
        }
        if (beforeInvocation == null && afterInvocation == null) {
            throw new IllegalArgumentException("No method annotated with @BInvocation or @AInvocation");
        }
        try {
            if (beforeInvocation == null) {
                beforeInvocation = LSPosedBridge.class.getMethod("dummyCallback");
            } else if (afterInvocation == null) {
                afterInvocation = LSPosedBridge.class.getMethod("dummyCallback");
            } else {
                var ret = beforeInvocation.getReturnType();
                var params = afterInvocation.getParameterTypes();
                if (ret != void.class && params.length == 2 && !ret.equals(params[1])) {
                    throw new IllegalArgumentException("BInvocation and AInvocation method format is invalid");
                }
            }
        } catch (NoSuchMethodException e) {
            throw new InstalldFailedError(e);
        }

        var callback = new LSPosedBridge.HookerCallback(beforeInvocation, afterInvocation);
        if (InstalldBridge.hookMethod(true, hookMethod, LSPosedBridge.NativeHooker.class, priority, callback)) {
            return new InstalldInterface.MethodUnhooker<>() {
                @NonNull
                @Override
                public T getOrigin() {
                    return hookMethod;
                }

                @Override
                public void unhook() {
                    InstalldBridge.unhookMethod(true, hookMethod, callback);
                }
            };
        }
        throw new InstalldFailedError("Cannot hook " + hookMethod);
    }
}
