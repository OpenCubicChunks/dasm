package io.github.opencubicchunks.dasm.transformer.target;

import io.github.opencubicchunks.dasm.transformer.redirect.RedirectSet;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TargetClass {
    private final String className;
    private final List<RedirectSet> redirectSets = new ArrayList<>();
    private boolean debugSelfRedirects = false;
    private final List<TargetMethod> targetMethods = new ArrayList<>();
    private Type wholeClass = null;

    public TargetClass(String className) {
        this.className = className;
    }

    public void addRedirectSet(RedirectSet set) {
        this.redirectSets.add(set);
    }

    public List<RedirectSet> redirectSets() {
        return Collections.unmodifiableList(redirectSets);
    }

    public void addTarget(TargetMethod targetMethod) {
        if (wholeClass != null) {
            throw new IllegalStateException("Cannot add target methods when targeting whole class!");
        }
        this.targetMethods.add(targetMethod);
    }

    public List<TargetMethod> targetMethods() {
        return Collections.unmodifiableList(targetMethods);
    }

    /**
     * Specifies targeting a whole class, without cloning methods. Allows in-place redirects for the whole class
     */
    public void targetWholeClass(Type srcClass) {
        if (!targetMethods.isEmpty()) {
            throw new IllegalStateException("Cannot add target whole class when method targets are specified!");
        }
        wholeClass = srcClass;
    }

    public Type wholeClass() {
        return wholeClass;
    }

    public void setDebugSelfRedirects(boolean debugSelfRedirects) {
        this.debugSelfRedirects = debugSelfRedirects;
    }

    public boolean debugSelfRedirects() {
        return debugSelfRedirects;
    }

    public String getClassName() {
        return className;
    }
}
