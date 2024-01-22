package io.github.opencubicchunks.dasm.transformer.redirect;

import io.github.opencubicchunks.dasm.transformer.ClassMethod;
import org.objectweb.asm.Type;

import java.util.Objects;

import javax.annotation.Nullable;

public final class MethodRedirect {

    private final ClassMethod method;
    private final Type newOwner;
    private final String dstMethodName;
    // used only for other class redirects
    private final boolean isDstInterface;

    public MethodRedirect(ClassMethod method, @Nullable Type newOwner, String dstMethodName, boolean isDstInterface) {
        this.method = method;
        this.newOwner = newOwner;
        this.dstMethodName = dstMethodName;
        this.isDstInterface = isDstInterface;
    }

    public Type newOwner() {
        return newOwner;
    }

    public ClassMethod method() {
        return method;
    }

    public String dstMethodName() {
        return dstMethodName;
    }

    public boolean isDstInterface() {
        return isDstInterface;
    }

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MethodRedirect that = (MethodRedirect) o;
        return isDstInterface == that.isDstInterface && Objects.equals(method, that.method) && Objects.equals(newOwner, that.newOwner)
                && Objects.equals(dstMethodName, that.dstMethodName);
    }

    @Override public int hashCode() {
        return Objects.hash(method, newOwner, dstMethodName, isDstInterface);
    }

    @Override public String toString() {
        return "MethodRedirect{" +
                "method=" + method +
                ", newOwner='" + newOwner + '\'' +
                ", dstMethodName='" + dstMethodName + '\'' +
                ", isDstInterface=" + isDstInterface +
                '}';
    }
}
