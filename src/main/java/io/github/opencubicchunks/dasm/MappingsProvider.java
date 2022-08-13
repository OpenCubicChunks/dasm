package io.github.opencubicchunks.dasm;

public interface MappingsProvider {
    String mapFieldName(String namespace, String owner, String fieldName, String descriptor);

    String mapMethodName(String namespace, String owner, String methodName, String descriptor);

    String mapClassName(String namespace,String className);
}
