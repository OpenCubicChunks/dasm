package io.github.opencubicchunks.dasm.api;

public interface ClassProvider {
    /**
     * @param className The fully qualified name of the class eg: <code>java.lang.String</code>
     * @return The class bytes
     */
    byte[] classBytes(String className);
}
