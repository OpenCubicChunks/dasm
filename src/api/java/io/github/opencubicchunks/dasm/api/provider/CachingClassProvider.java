package io.github.opencubicchunks.dasm.api.provider;

import java.util.HashMap;
import java.util.Map;


public class CachingClassProvider implements ClassProvider {
    private final ClassProvider classProvider;
    private final Map<String, byte[]> classProviderCache = new HashMap<>();

    public CachingClassProvider(ClassProvider classProvider) {
        this.classProvider = classProvider;
    }

    @Override
    public byte[] classBytes(String className) {
        return this.classProviderCache.computeIfAbsent(className, this.classProvider::classBytes);
    }
}
