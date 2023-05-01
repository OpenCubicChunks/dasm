package io.github.opencubicchunks.dasm;

import java.util.Objects;

public final class TypeRedirect {

    private String srcClassName;
    private String dstClassName;

    public TypeRedirect(String srcClassName, String dstClassName) {
        this.srcClassName = srcClassName;
        this.dstClassName = dstClassName;
    }

    public String srcClassName() {
        return srcClassName;
    }

    public String dstClassName() {
        return dstClassName;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        TypeRedirect that = (TypeRedirect) obj;
        return Objects.equals(this.srcClassName, that.srcClassName) &&
                Objects.equals(this.dstClassName, that.dstClassName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(srcClassName, dstClassName);
    }

    @Override
    public String toString() {
        return "TypeRedirect[" +
                "srcClassName=" + srcClassName + ", " +
                "dstClassName=" + dstClassName + ']';
    }
}
