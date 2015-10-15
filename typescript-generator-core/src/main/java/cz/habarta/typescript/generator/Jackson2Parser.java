
package cz.habarta.typescript.generator;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.ser.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Logger;


public class Jackson2Parser extends ModelParser {

    private final ObjectMapper objectMapper = new ObjectMapper();
    

    public Jackson2Parser(Logger logger, Settings settings) {
        super(logger, settings);
    }

    @Override
    protected BeanModel parseBean(ClassWithUsage classWithUsage) {
        final List<PropertyModel> properties = new ArrayList<>();
        final BeanHelper beanHelper = getBeanHelper(classWithUsage.beanClass);
        if (beanHelper != null) {
            for (BeanPropertyWriter beanPropertyWriter : beanHelper.getProperties()) {
                if (!isParentProperty(beanPropertyWriter.getName(), classWithUsage.beanClass)) {
                    Type propertyType = beanPropertyWriter.getGenericPropertyType();
                    if (propertyType.equals(JsonNode.class)) {
                        propertyType = Object.class;
                    }
                    properties.add(processTypeAndCreateProperty(beanPropertyWriter.getName(), propertyType, classWithUsage.beanClass));
                }
            }
        }

        final JsonSubTypes jsonSubTypes = classWithUsage.beanClass.getAnnotation(JsonSubTypes.class);
        if (jsonSubTypes != null) {
            for (JsonSubTypes.Type type : jsonSubTypes.value()) {
                addBeanToQueue(new ClassWithUsage(type.value(), "<subClass>", classWithUsage.beanClass));
            }
        }
        final Class<?> superclass = classWithUsage.beanClass.getSuperclass().equals(Object.class) ? null : classWithUsage.beanClass.getSuperclass();
        if (superclass != null) {
            addBeanToQueue(new ClassWithUsage(superclass, "<superClass>", classWithUsage.beanClass));
        }
        return new BeanModel(getMappedName(classWithUsage.beanClass), getMappedName(superclass), properties);
    }

    private boolean isParentProperty(String property, Class<?> cls) {
        if (cls.getSuperclass().equals(Object.class)) {
            return false;
        } else {
            final BeanHelper beanHelper = getBeanHelper(cls.getSuperclass());
            if (beanHelper != null) {
                for (BeanPropertyWriter beanPropertyWriter : beanHelper.getProperties()) {
                    if (beanPropertyWriter.getName().equals(property)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private BeanHelper getBeanHelper(Class<?> beanClass) {
        try {
            final DefaultSerializerProvider.Impl serializerProvider1 = (DefaultSerializerProvider.Impl) objectMapper.getSerializerProvider();
            final DefaultSerializerProvider.Impl serializerProvider2 = serializerProvider1.createInstance(objectMapper.getSerializationConfig(), objectMapper.getSerializerFactory());
            final JavaType simpleType = objectMapper.constructType(beanClass);
            final JsonSerializer<?> jsonSerializer = BeanSerializerFactory.instance.createSerializer(serializerProvider2, simpleType);
            if (jsonSerializer == null) {
                return null;
            }
            if (jsonSerializer instanceof BeanSerializer) {
                return new BeanHelper((BeanSerializer) jsonSerializer);
            } else {
                final String jsonSerializerName = jsonSerializer.getClass().getName();
                throw new RuntimeException(String.format("Unknown serializer '%s' for class '%s'", jsonSerializerName, beanClass));
            }
        } catch (JsonMappingException e) {
            throw new RuntimeException(e);
        }
    }

    private static class BeanHelper extends BeanSerializer {
        private static final long serialVersionUID = 1;

        public BeanHelper(BeanSerializer src) {
            super(src);
        }

        public BeanPropertyWriter[] getProperties() {
            return _props;
        }

    }

}
