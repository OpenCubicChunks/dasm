package io.github.opencubicchunks.dasm.transformer.redirect;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RedirectSet {
    private final String name;
    private final List<TypeRedirect> typeRedirects = new ArrayList<>();
    private final List<FieldRedirect> fieldRedirects = new ArrayList<>();
    private final List<MethodRedirect> methodRedirects = new ArrayList<>();

    public RedirectSet(String name) {
        this.name = name;
    }

    public void addRedirect(TypeRedirect redirect) {
        this.typeRedirects.add(redirect);
    }

    public void addRedirect(FieldRedirect redirect) {
        this.fieldRedirects.add(redirect);
    }

    public void addRedirect(MethodRedirect redirect) {
        this.methodRedirects.add(redirect);
    }

    public String getName() {
        return name;
    }

    @NotNull
    public List<TypeRedirect> getTypeRedirects() {
        return Collections.unmodifiableList(this.typeRedirects);
    }

    @NotNull public List<FieldRedirect> getFieldRedirects() {
        return Collections.unmodifiableList(this.fieldRedirects);
    }

    @NotNull public List<MethodRedirect> getMethodRedirects() {
        return Collections.unmodifiableList(methodRedirects);
    }

    public void mergeIfNotPresent(RedirectSet other) {
        other.typeRedirects.forEach(typeRedirect -> {
            if (!this.typeRedirects.contains(typeRedirect)) {
                this.typeRedirects.add(typeRedirect);
            }
        });
        other.fieldRedirects.forEach(typeRedirect -> {
            if (!this.fieldRedirects.contains(typeRedirect)) {
                this.fieldRedirects.add(typeRedirect);
            }
        });
        other.methodRedirects.forEach(typeRedirect -> {
            if (!this.methodRedirects.contains(typeRedirect)) {
                this.methodRedirects.add(typeRedirect);
            }
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RedirectSet that = (RedirectSet) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
