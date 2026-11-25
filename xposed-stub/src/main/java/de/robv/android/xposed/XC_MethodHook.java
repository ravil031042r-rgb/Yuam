package de.robv.android.xposed;
import java.lang.reflect.Member;
public abstract class XC_MethodHook {
    public static final class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        public Object result;
        public Throwable throwable;
        public void setResult(Object r) { this.result = r; this.throwable = null; }
        public Object getResult() { return result; }
    }
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
}
