package io.github.opencubicchunks.dasm.transformer;

import org.objectweb.asm.Type;

import java.util.Objects;

import static org.objectweb.asm.Type.getObjectType;
import static org.objectweb.asm.Type.getType;

public final class ClassField {
    public final Type owner;
    public final String name;
    public final Type desc;

    public ClassField(String owner, String name, String desc) {
        this.owner = getObjectType(owner);
        this.name = name;
        this.desc = getType(desc);
    }

    public ClassField(Type owner, String name, Type desc) {
        this.owner = owner;
        this.name = name;
        this.desc = desc;
    }

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ClassField that = (ClassField) o;
        return owner.equals(that.owner) && name.equals(that.name) && desc.equals(that.desc);
    }

    @Override public int hashCode() {
        return Objects.hash(owner, name, desc);
    }

    @Override public String toString() {
        return "ClassField{" +
                "owner=" + owner +
                ", name='" + name + '\'' +
                ", desc=" + desc +
                '}';
    }
}
