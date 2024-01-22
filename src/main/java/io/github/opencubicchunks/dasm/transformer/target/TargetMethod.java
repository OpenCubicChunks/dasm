package io.github.opencubicchunks.dasm.transformer.target;

import io.github.opencubicchunks.dasm.transformer.ClassMethod;
import io.github.opencubicchunks.dasm.transformer.redirect.RedirectSet;
import org.objectweb.asm.Type;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class TargetMethod {
    private final Type srcOwner;
    private final ClassMethod method;
    private final String dstMethodName;
    private final boolean shouldClone;
    private final boolean makeSyntheticAccessor;
    private final List<RedirectSet> redirectSets;

    public TargetMethod(ClassMethod method, String dstMethodName, boolean shouldClone, boolean makeSyntheticAccessor, List<RedirectSet> redirectSets) {
        this(method.owner, method, dstMethodName, shouldClone, makeSyntheticAccessor, redirectSets);
    }

    public TargetMethod(Type srcOwner, ClassMethod method, String dstMethodName, boolean shouldClone, boolean makeSyntheticAccessor, List<RedirectSet> redirectSets) {
        this.srcOwner = srcOwner;
        this.method = method;
        this.dstMethodName = dstMethodName;
        this.shouldClone = shouldClone;
        this.makeSyntheticAccessor = makeSyntheticAccessor;
        this.redirectSets = Collections.unmodifiableList(redirectSets);
    }

    public Type srcOwner() {
        return srcOwner;
    }

    public String dstMethodName() {
        return dstMethodName;
    }

    public boolean shouldClone() {
        return shouldClone;
    }

    public boolean makeSyntheticAccessor() {
        return makeSyntheticAccessor;
    }

    public ClassMethod method() {
        return this.method;
    }

    public List<RedirectSet> redirectSets() {
        return this.redirectSets;
    }

    @Override
    public String toString() {
        return "TargetMethod{" +
                "srcOwner=" + srcOwner +
                ", method=" + method +
                ", dstMethodName='" + dstMethodName + '\'' +
                ", shouldClone=" + shouldClone +
                ", makeSyntheticAccessor=" + makeSyntheticAccessor +
                ", redirectSets=" + redirectSets +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TargetMethod that = (TargetMethod) o;
        return shouldClone == that.shouldClone && makeSyntheticAccessor == that.makeSyntheticAccessor && Objects.equals(srcOwner, that.srcOwner) && Objects.equals(method, that.method) && Objects.equals(dstMethodName, that.dstMethodName) && Objects.equals(redirectSets, that.redirectSets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(srcOwner, method, dstMethodName, shouldClone, makeSyntheticAccessor, redirectSets);
    }
}
