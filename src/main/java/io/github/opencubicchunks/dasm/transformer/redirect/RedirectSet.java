package io.github.opencubicchunks.dasm.transformer.redirect;

import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RedirectSet {
    private final String name;
    private final Set<TypeRedirect> typeRedirects = new LinkedHashSet<>();
    private final Set<FieldRedirect> fieldRedirects = new LinkedHashSet<>();
    private final Set<MethodRedirect> methodRedirects = new LinkedHashSet<>();

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
    public Set<TypeRedirect> getTypeRedirects() {
        return Collections.unmodifiableSet(this.typeRedirects);
    }

    @NotNull public Set<FieldRedirect> getFieldRedirects() {
        return Collections.unmodifiableSet(this.fieldRedirects);
    }

    @NotNull public Set<MethodRedirect> getMethodRedirects() {
        return Collections.unmodifiableSet(methodRedirects);
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
