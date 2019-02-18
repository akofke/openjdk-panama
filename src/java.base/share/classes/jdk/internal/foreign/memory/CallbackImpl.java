package jdk.internal.foreign.memory;

import java.foreign.memory.Callback;
import java.foreign.memory.Pointer;
import jdk.internal.foreign.LibrariesHelper;

public class CallbackImpl<X> implements Callback<X> {

    private final Pointer<?> addr;
    private final Class<?> funcIntfClass;

    public CallbackImpl(Pointer<?> addr, Class<?> funcIntfClass) {
        this.addr = addr;
        this.funcIntfClass = funcIntfClass;
    }

    private static final CallbackImpl<Object> theNullCallback =
        new CallbackImpl<Object>(Pointer.ofNull(), null) {
            @Override
            public Object asFunction() {
                throw new NullPointerException();
            }
        };

    @SuppressWarnings("unchecked")
    public static <X> Callback<X> ofNull() {
        return (Callback<X>) theNullCallback;
    }

    @Override
    public Pointer<?> entryPoint() {
        return addr;
    }

    @Override
    @SuppressWarnings("unchecked")
    public X asFunction() {
        try {
            ((BoundedPointer<?>) entryPoint()).checkAlive();
            //create a wrapper around a true native function
            Class<?> callbackClass = LibrariesHelper.getCallbackImplClass(funcIntfClass);
            return (X) callbackClass.getConstructor(Pointer.class).newInstance(entryPoint());
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }
}
