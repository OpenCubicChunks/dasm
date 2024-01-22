package io.github.opencubicchunks.dasm.transformer;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import java.util.Objects;

public final class ClassMethod {
    public final Type owner;
    public final Method method;
    public final Type mappingOwner;

    public ClassMethod(Type owner, Method method) {
        this.owner = owner;
        this.method = method;
        this.mappingOwner = owner;
    }

    // mapping owner because mappings owner may not be the same as in the call site
    public ClassMethod(Type owner, Method method, Type mappingOwner) {
        this.owner = owner;
        this.method = method;
        this.mappingOwner = mappingOwner;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassMethod that = (ClassMethod) o;
        return owner.equals(that.owner) && method.equals(that.method) && mappingOwner.equals(that.mappingOwner);
    }

    @Override public int hashCode() {
        return Objects.hash(owner, method, mappingOwner);
    }

    @Override public String toString() {
        return "ClassMethod{" +
                "owner=" + owner +
                ", method=" + method +
                ", mappingOwner=" + mappingOwner +
                '}';
    }
}
