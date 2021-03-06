package com.github.ruediste.c3java.properties;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

public class PropertyInfo implements AnnotatedElement {

    private final String name;
    private final TypeToken<?> propertyType;
    private final Method getter;
    private final Method setter;
    private final Field backingField;
    private final PropertyDeclaration declaration;
    private final PropertyInfo parent;
    private final Class<?> bearingType;

    public PropertyInfo(String name, TypeToken<?> propertyType, Method getter, Method setter, Field backingField,
            PropertyDeclaration declaration, PropertyInfo parent, Class<?> bearingType) {
        this.name = name;
        this.propertyType = propertyType;
        this.getter = getter;
        this.setter = setter;
        this.backingField = backingField;
        this.declaration = declaration;
        this.parent = parent;
        this.bearingType = bearingType;
    }

    /**
     * Return the {@link PropertyDeclaration}s for this property, starting with
     * the most specific class and ending with the introducing declaration.
     */
    public Stream<PropertyDeclaration> getDeclarationStream() {
        return StreamSupport.stream(getDeclarations().spliterator(), false);
    }

    /**
     * Return the {@link PropertyDeclaration}s for this property, starting with
     * the most specific class and ending with the introducing declaration.
     */
    public Iterable<PropertyDeclaration> getDeclarations() {
        Iterable<PropertyDeclaration> iterable = new Iterable<PropertyDeclaration>() {

            @Override
            public Iterator<PropertyDeclaration> iterator() {
                return new Iterator<PropertyDeclaration>() {
                    PropertyInfo next = PropertyInfo.this;

                    @Override
                    public PropertyDeclaration next() {
                        PropertyDeclaration result = next.declaration;
                        next = next.getParent();
                        return result;
                    }

                    @Override
                    public boolean hasNext() {
                        return next != null;
                    }
                };
            }
        };
        return iterable;
    }

    public Object getValue(Object target) {
        try {
            if (getter != null)
                return getter.invoke(target);
            if (backingField != null)
                return backingField.get(target);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        throw new UnsupportedOperationException("Cannot read property " + name + " of found on type " + bearingType);
    }

    public void setValue(Object target, Object value) {
        try {
            if (setter != null)
                setter.invoke(target, value);
            else if (backingField != null)
                backingField.set(target, value);
            else
                throw new UnsupportedOperationException(
                        "Cannot write property " + name + " of found on type " + bearingType);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException("Error while setting property " + this + " to value " + value, e);
        }
    }

    public boolean isReadable() {
        return getter != null || backingField != null;
    }

    public boolean isWriteable() {
        return setter != null || backingField != null;
    }

    public String getName() {
        return name;
    }

    public TypeToken<?> getPropertyType() {
        return propertyType;
    }

    public PropertyInfo withGetter(Method getter) {
        return new PropertyInfo(name, propertyType, getter, setter, backingField, declaration, parent, bearingType);
    }

    public PropertyInfo withSetter(Method setter) {
        return new PropertyInfo(name, propertyType, getter, setter, backingField, declaration, parent, bearingType);
    }

    public PropertyInfo withBackingField(Field backingField) {
        return new PropertyInfo(name, propertyType, getter, setter, backingField, declaration, parent, bearingType);
    }

    public PropertyInfo extendedWith(PropertyDeclaration decl) {
        if (!Objects.equals(name, decl.getName()))
            throw new RuntimeException(
                    "names of to be merged properties does not match: " + name + ", " + decl.getName());
        if (!Objects.equals(propertyType, decl.getPropertyType()))
            throw new RuntimeException("types of to be merged properties does not match. name: " + name + " types: "
                    + propertyType + ", " + decl.getPropertyType());

        PropertyInfo result = this;
        if (decl.getGetter() != null)
            result = result.withGetter(decl.getGetter());
        if (decl.getSetter() != null)
            result = result.withSetter(decl.getSetter());
        if (decl.getBackingField() != null)
            result = result.withBackingField(decl.getBackingField());

        return result.withDeclaration(decl).withParent(result);
    }

    public PropertyInfo withParent(PropertyInfo parent) {
        return new PropertyInfo(name, propertyType, getter, setter, backingField, declaration, parent, bearingType);
    }

    public PropertyDeclaration getDeclaration() {
        return declaration;
    }

    public PropertyInfo withDeclaration(PropertyDeclaration declaration) {
        return new PropertyInfo(name, propertyType, getter, setter, backingField, declaration, parent, bearingType);
    }

    public PropertyInfo withIntroducingType(Class<?> declaringType) {
        return new PropertyInfo(name, propertyType, getter, setter, backingField, declaration, parent, bearingType);
    }

    public Method getGetter() {
        return getter;
    }

    public Method getSetter() {
        return setter;
    }

    public Field getBackingField() {
        return backingField;
    }

    public PropertyInfo getParent() {
        return parent;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, bearingType);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PropertyInfo other = (PropertyInfo) obj;
        return Objects.equals(name, other.name) && Objects.equals(bearingType, other.bearingType);

    }

    @Override
    public String toString() {
        return "PropertyInfo [name=" + name + ", bearingType=" + bearingType + "]";
    }

    /**
     * The type first introducing the property
     * 
     * @return
     */
    public Class<?> getIntroducingType() {
        return getIntroducingDeclaration().getDeclaringType();
    }

    public PropertyInfo getIntroduction() {
        PropertyInfo result = this;
        while (result.parent != null)
            result = result.parent;
        return result;
    }

    public PropertyDeclaration getIntroducingDeclaration() {
        return Iterables.getLast(getDeclarations());
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return getDeclarationStream().map(x -> Optional.ofNullable(x.getAnnotation(annotationClass)))
                .filter(x -> x.isPresent()).map(x -> x.get()).findFirst().orElse(null);
    }

    @Override
    public Annotation[] getAnnotations() {
        return getAnnotationsImpl(x -> x.getAnnotations());
    }

    public Annotation[] getAnnotationsImpl(Function<AnnotatedElement, Annotation[]> f) {
        return getDeclarationStream().flatMap(x -> Arrays.stream(f.apply(x))).collect(Collectors.toList())
                .toArray(new Annotation[] {});
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return getAnnotationsImpl(x -> x.getDeclaredAnnotations());
    }

    public Class<?> getBearingType() {
        return bearingType;
    }
}
