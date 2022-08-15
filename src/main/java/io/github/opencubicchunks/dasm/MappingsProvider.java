package io.github.opencubicchunks.dasm;

public interface MappingsProvider {
    MappingsProvider IDENTITY = new MappingsProvider() {
        @Override
        public String mapFieldName(String namespace, String owner, String fieldName, String descriptor) {
            return fieldName;
        }

        @Override
        public String mapMethodName(String namespace, String owner, String methodName, String descriptor) {
            return methodName;
        }

        @Override
        public String mapClassName(String namespace, String className) {
            return className;
        }
    };

    String mapFieldName(String namespace, String owner, String fieldName, String descriptor);

    String mapMethodName(String namespace, String owner, String methodName, String descriptor);

    String mapClassName(String namespace,String className);
}
