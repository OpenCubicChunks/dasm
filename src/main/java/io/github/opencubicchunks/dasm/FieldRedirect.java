package io.github.opencubicchunks.dasm;

import org.objectweb.asm.Type;

import java.util.Objects;

import javax.annotation.Nullable;

public final class FieldRedirect {

    private final Transformer.ClassField field;
    private final Type newOwner;
    private final String dstFieldName;

    public FieldRedirect(Transformer.ClassField field, @Nullable String newOwner, String dstFieldName) {
        this.field = field;
        this.newOwner = newOwner == null ? null : Type.getObjectType(newOwner.replace('.', '/'));
        this.dstFieldName = dstFieldName;
    }

    public Transformer.ClassField field() {
        return field;
    }

    public String dstFieldName() {
        return dstFieldName;
    }

    public Type newOwner() {
        return newOwner;
    }

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FieldRedirect that = (FieldRedirect) o;
        return Objects.equals(field, that.field) && Objects.equals(newOwner, that.newOwner) && Objects.equals(dstFieldName,
                that.dstFieldName);
    }

    @Override public int hashCode() {
        return Objects.hash(field, newOwner, dstFieldName);
    }

    @Override public String toString() {
        return "FieldRedirect{" +
                "field=" + field +
                ", newOwner='" + newOwner + '\'' +
                ", dstFieldName='" + dstFieldName + '\'' +
                '}';
    }
}
