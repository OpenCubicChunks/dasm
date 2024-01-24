package io.github.opencubicchunks.dasm.api.provider;

public interface MappingsProvider {
    MappingsProvider IDENTITY = new MappingsProvider() {
        @Override
        public String mapFieldName(String owner, String fieldName, String descriptor) {
            return fieldName;
        }

        @Override
        public String mapMethodName(String owner, String methodName, String descriptor) {
            return methodName;
        }

        @Override
        public String mapClassName(String className) {
            return className;
        }
    };

    String mapFieldName(String owner, String fieldName, String descriptor);

    String mapMethodName(String owner, String methodName, String descriptor);

    String mapClassName(String className);
}
