/*
 * Copyright 2013  Séven Le Mesle
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package fr.xebia.extras.selma.codegen;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

import static fr.xebia.extras.selma.codegen.MappingSourceNode.*;

/**
 *
 */
public abstract class MappingBuilder {

    private static final List<MappingSpecification> mappingSpecificationList = new LinkedList<MappingSpecification>();

    static {  // init specs here

        /**
         * Mapping Primitives
         */
        mappingSpecificationList.add(new MappingSpecification() {
            @Override
            MappingBuilder getBuilder(final MapperGeneratorContext context, final InOutType inOutType) {
                return new MappingBuilder() {
                    @Override
                    public MappingSourceNode buildNodes(final MapperGeneratorContext context, final SourceNodeVars vars) throws IOException {

                        if (context.depth > 0) {
                            root.body(set(vars.outSetterPath(), vars.inGetter()));
                        } else {
                            root.body(assignOutPrime(inOutType.in().toString()));
                        }
                        return root.body;
                    }
                };
            }

            @Override
            boolean match(final MapperGeneratorContext context, final InOutType inOutType) {
                return inOutType.areSamePrimitive();
            }
        });


        /**
         * Enum Mapper
         */
        mappingSpecificationList.add(new MappingSpecification() {
            @Override
            MappingBuilder getBuilder(final MapperGeneratorContext context, final InOutType inOutType) {

                TypeElement typeElement = inOutType.inAsTypeElement();
                final List<String> enumValues = collectEnumValues(typeElement);

                return new MappingBuilder() {
                    @Override
                    MappingSourceNode buildNodes(MapperGeneratorContext context, SourceNodeVars vars) throws IOException {
                        // If we are in a nested context we should call an enum mapping method
                        if (context.depth > 0) {
                            String mappingMethod = context.mappingMethod(inOutType);
                            root.body(vars.setOrAssign(String.format("%s(%%s)", mappingMethod)));
                        } else { // Otherwise we should build the real enum selma code
                            MappingSourceNode node = blank();
                            MappingSourceNode valuesBlock = node;
                            for (String value : enumValues) {
                                node = node.child(mapEnumCase(value));
                                node.body(vars.setOrAssign(String.format("%s.%s", inOutType.out(), value)));
                            }
                            root.body(mapEnumBlock(vars.inGetter())).body(valuesBlock.child);
                        }
                        return root.body;
                    }
                };
            }

            @Override
            boolean match(final MapperGeneratorContext context, final InOutType inOutType) {
                return inOutType.areDeclared() && inOutType.areEnums();
            }
        });

        // Map String or Boxed Primitive
        mappingSpecificationList.add(new SameDeclaredMappingSpecification() {
            @Override
            MappingBuilder getBuilder(final MapperGeneratorContext context, final InOutType inOutType) {
                return new MappingBuilder() {
                    @Override
                    MappingSourceNode buildNodes(MapperGeneratorContext context, SourceNodeVars vars) throws IOException {

                        root.body(vars.setOrAssign(String.format("new %s(%%s)", inOutType.out())));
                        return root.body;
                    }
                };
            }

            @Override
            boolean match(final MapperGeneratorContext context, final InOutType inOutType) {
                if (super.match(context, inOutType)) {
                    DeclaredType declaredType = inOutType.inAsDeclaredType();
                    return (isBoxedPrimitive(declaredType, context) || String.class.getName().equals(declaredType.toString()));
                }
                return false;
            }
        });

        // Map Dates
        mappingSpecificationList.add(new SameDeclaredMappingSpecification() {
            @Override
            MappingBuilder getBuilder(final MapperGeneratorContext context, final InOutType inOutType) {
                return new MappingBuilder() {
                    @Override
                    MappingSourceNode buildNodes(MapperGeneratorContext context, SourceNodeVars vars) throws IOException {
                        root.body(vars.setOrAssign("new java.util.Date(%s.getTime())"));
                        return root.body;
                    }
                };
            }

            @Override
            boolean match(final MapperGeneratorContext context, final InOutType inOutType) {
                if (super.match(context, inOutType)) {
                    return Date.class.getName().equals(inOutType.in().toString());
                }
                return false;
            }
        });

        // Map BigInteger
        mappingSpecificationList.add(new SameDeclaredMappingSpecification() {
            @Override
            MappingBuilder getBuilder(final MapperGeneratorContext context, final InOutType inOutType) {
                return new MappingBuilder() {
                    @Override
                    MappingSourceNode buildNodes(MapperGeneratorContext context, SourceNodeVars vars) throws IOException {
                        root.body(vars.setOrAssign("new java.math.BigInteger(%s.toByteArray())"));
                        return root.body;
                    }
                };
            }

            @Override
            boolean match(final MapperGeneratorContext context, final InOutType inOutType) {
                if (super.match(context, inOutType)) {
                    return BigInteger.class.getName().equals(inOutType.in().toString());
                }
                return false;
            }
        });

        // Map collections
        mappingSpecificationList.add(new MappingSpecification() {
            @Override
            boolean match(final MapperGeneratorContext context, final InOutType inOutType) {

                return inOutType.areDeclared() && isCollection(inOutType.inAsDeclaredType(), context) && isCollection(inOutType.outAsDeclaredType(), context);
            }

            @Override
            MappingBuilder getBuilder(final MapperGeneratorContext context, final InOutType inOutType) {

                // We have a collection
                final TypeMirror genericIn = inOutType.inAsDeclaredType().getTypeArguments().get(0);
                final TypeMirror genericOut = inOutType.outAsDeclaredType().getTypeArguments().get(0);
                // emit writer loop for collection and choose an implementation
                String impl;
                if (inOutType.outAsTypeElement().getKind() == ElementKind.CLASS) {
                    // Use given class for collection
                    impl = inOutType.out().toString();
                } else {
                    impl = CollectionsRegistry.findImplementationForType(inOutType.outAsTypeElement()) + "<" + genericOut.toString() + ">";
                }
                final String implementation = impl;
                return new MappingBuilder() {
                    @Override
                    MappingSourceNode buildNodes(MapperGeneratorContext context, SourceNodeVars vars) throws IOException {

                        final String itemVar = vars.itemVar();
                        final String tmpVar = vars.tmpVar("Collection");
                        MappingSourceNode node = root.body(assign(String.format("%s %s", implementation, tmpVar), String.format("new %s(%s.size())", implementation, vars.inGetter())))
                                .child(vars.setOrAssign(tmpVar))
                                .child(mapCollection(itemVar, genericIn.toString(), vars.inGetter()));

                        context.pushStackForBody(node,
                                new SourceNodeVars().withInOutType(new InOutType(genericIn, genericOut))
                                        .withInField(itemVar)
                                        .withOutField(String.format("%s.add", tmpVar)).withAssign(false));

                        return root.body;
                    }
                };
            }
        });

        // Map map
        mappingSpecificationList.add(new MappingSpecification() {
            @Override
            MappingBuilder getBuilder(final MapperGeneratorContext context, final InOutType inOutType) {
                // We have a Map
                final TypeMirror genericInKey = inOutType.inAsDeclaredType().getTypeArguments().get(0);
                final TypeMirror genericOutKey = inOutType.outAsDeclaredType().getTypeArguments().get(0);
                final TypeMirror genericInValue = inOutType.inAsDeclaredType().getTypeArguments().get(1);
                final TypeMirror genericOutValue = inOutType.outAsDeclaredType().getTypeArguments().get(1);

                // emit writer loop for collection and choose an implementation
                String impl;
                if (inOutType.outAsTypeElement().getKind() == ElementKind.CLASS) {
                    // Use given class for collection
                    impl = inOutType.out().toString();

                } else {
                    impl = CollectionsRegistry.findImplementationForType(inOutType.outAsTypeElement()) + "<" + genericOutKey.toString() + ", " + genericOutValue.toString() + ">";
                }
                final String implementation = impl;

                return new MappingBuilder() {
                    @Override
                    MappingSourceNode buildNodes(MapperGeneratorContext context, SourceNodeVars vars) throws IOException {

                        final String itemVar = vars.itemEntry();
                        final String tmpVar = vars.tmpVar("Map");
                        final String keyVar = vars.tmpVar("Key");
                        MappingSourceNode node = root.body(assign(String.format("%s %s", implementation, tmpVar), String.format("new %s()", implementation)))
                                .child(vars.setOrAssign(tmpVar))
                                .child(mapMap(itemVar, genericInKey.toString(), genericInValue.toString(), vars.inGetter()))
                                .body(assign(String.format("%s %s", genericOutKey, keyVar), "null"));
                        context.pushStackForChild(node, new SourceNodeVars().withInOutType(new InOutType(genericInValue, genericOutValue))
                                .withInField(String.format("%s.getValue()", itemVar)).withInFieldPrefix(String.format("%s,", keyVar))
                                .withOutField(String.format("%s.put", tmpVar))
                                .withAssign(false));
                        context.pushStackForChild(node, new SourceNodeVars().withInOutType(new InOutType(genericInKey, genericOutKey))
                                .withInField(String.format("%s.getKey()", itemVar))
                                .withOutField(keyVar)
                                .withAssign(true));
                        return root.body;
                    }
                };
            }

            @Override
            boolean match(final MapperGeneratorContext context, final InOutType inOutType) {
                return inOutType.areDeclared() && isMap(inOutType.inAsDeclaredType(), context) && isMap(inOutType.outAsDeclaredType(), context);
            }
        });

        // Map array of primitive
        mappingSpecificationList.add(new MappingSpecification() {
            @Override
            MappingBuilder getBuilder(final MapperGeneratorContext context, final InOutType inOutType) {
                return new MappingBuilder() {
                    @Override
                    MappingSourceNode buildNodes(MapperGeneratorContext context, SourceNodeVars vars) throws IOException {

                        root.body(vars.setOrAssign(String.format("new %s[%%s.length]", inOutType.inArrayComponentType())))
                                .child(arrayCopy(vars.inGetter(), vars.outSetterPath()));

                        return root.body;
                    }
                };
            }

            @Override
            boolean match(final MapperGeneratorContext context, final InOutType inOutType) {
                return inOutType.inIsArray() && inOutType.isInArrayComponentPrimitive() && !inOutType.differs();
            }
        });

        // Map array of Declared or array of array
        mappingSpecificationList.add(new MappingSpecification() {
            @Override
            MappingBuilder getBuilder(MapperGeneratorContext context, final InOutType inOutType) {
                return new MappingBuilder() {
                    @Override
                    MappingSourceNode buildNodes(MapperGeneratorContext context, SourceNodeVars vars) throws IOException {
                        MappingSourceNode node;
                        char ptrChar = 'a';
                        final String indexVar = vars.indexVar(ptrChar);
                        final String tmpVar = vars.tmpVar("Array");

                        Map.Entry<TypeMirror, Integer> dims = getArrayDimensionsAndType(inOutType.inAsArrayType());

                        String totalCountVar = vars.totalCountVar();
                        String totalCountVal = String.format("%s.length", vars.inGetter());
                        String newArray = String.format("new %s[%s.length]", dims.getKey(), vars.inGetter());
                        String dimPattern = "[0]";
                        String dim = "";
                        String accessString = String.format("[%s%%%s.length]", indexVar, vars.inGetter());
                        int i = 1;
                        while (i < dims.getValue()) {
                            dim += dimPattern;
                            newArray += String.format("[%s%s.length]", vars.inGetter(), dim);
                            totalCountVal += " * " + String.format("%s%s.length", vars.inGetter(), dim);
                            accessString += String.format("[%s%%%s%s.length]", indexVar, vars.inGetter(), dim);
                            i++;
                        }

                        node = root.body(assign(String.format("%s %s", inOutType.out().toString(), tmpVar), newArray))
                                .child(assign("int " + totalCountVar, totalCountVal))
                                .child(vars.setOrAssign(tmpVar))
                                .child(mapArrayBis(indexVar, totalCountVar));

                        context.pushStackForBody(node,
                                new SourceNodeVars().withInOutType(new InOutType(dims.getKey(), dims.getKey()))
                                        .withInField(String.format("%s%s", vars.inGetter(), accessString))
                                        .withOutField(String.format("%s%s", tmpVar, accessString)).withAssign(true));

                        return root.body;
                    }
                };
            }

            @Override
            boolean match(MapperGeneratorContext context, InOutType inOutType) {
                return !inOutType.differs() && inOutType.inIsArray() && inOutType.isInArrayComponentDeclaredOrArray();
            }
        });

    }

    MappingSourceNode root;

    private MappingBuilder() {
        this.root = blank();
    }

    private static Map.Entry<TypeMirror, Integer> getArrayDimensionsAndType(final ArrayType arrayType) {
        int res = 1;
        ArrayType type = arrayType;
        while (type.getComponentType().getKind() == TypeKind.ARRAY) {
            res++;
            type = (ArrayType) type.getComponentType();
        }

        return new AbstractMap.SimpleEntry<TypeMirror, Integer>(type.getComponentType(), new Integer(res));
    }

    public static MappingBuilder getBuilderFor(final MapperGeneratorContext context, final InOutType inOutType) {

        MappingBuilder res = null;

        for (MappingSpecification specification : mappingSpecificationList) {
            if (specification.match(context, inOutType)) {
                res = specification.getBuilder(context, inOutType);
                break;
            }
        }

        // Found a nested bean ?
        if (res == null && inOutType.areDeclared() && context.depth > 0) {
            res = new MappingBuilder() {
                @Override
                MappingSourceNode buildNodes(MapperGeneratorContext context, SourceNodeVars vars) throws IOException {
                    String mappingMethod = context.mappingMethod(inOutType);
                    root.body(vars.setOrAssign(String.format("%s(%%s)", mappingMethod)));
                    return root.body;
                }
            };
        }

        return res;
    }

    private static List<String> collectEnumValues(TypeElement typeElement) {
        List<String> res = new ArrayList<String>();

        final List<? extends Element> elementInList = typeElement.getEnclosedElements();
        for (Element field : ElementFilter.fieldsIn(elementInList)) {
            if (field.getKind() == ElementKind.ENUM_CONSTANT) {
                res.add(field.getSimpleName().toString());
            }
        }
        return res;
    }

    private static boolean isMap(DeclaredType declaredType, MapperGeneratorContext context) {
        TypeElement typeElement1 = context.elements.getTypeElement("java.util.Map");

        DeclaredType declaredType2 = context.type.getDeclaredType(typeElement1, context.type.getWildcardType(null, null), context.type.getWildcardType(null, null));

        return context.type.isAssignable(declaredType, declaredType2);
    }

    private static boolean isCollection(DeclaredType declaredType, MapperGeneratorContext context) {

        TypeElement typeElement1 = context.elements.getTypeElement("java.util.Collection");

        DeclaredType declaredType2 = context.type.getDeclaredType(typeElement1, context.type.getWildcardType(null, null));

        return context.type.isAssignable(declaredType, declaredType2);
    }

    private static boolean isBoxedPrimitive(DeclaredType declaredType, MapperGeneratorContext context) {
        boolean res = false;
        final PackageElement typePackage = context.elements.getPackageOf(declaredType.asElement());
        final PackageElement javaLang = context.elements.getPackageElement("java.lang");
        if (typePackage.getSimpleName().equals(javaLang.getSimpleName())) {
            try {
                context.type.unboxedType(declaredType);
                res = true;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return res;
    }

    private static MappingSourceNode notNullInField(final SourceNodeVars vars) {
        return controlNull(vars.inGetter());
    }

    public static MappingBuilder newCustomMapper(final InOutType inOutType, final String name) {
        MappingBuilder res = new MappingBuilder() {
            @Override
            MappingSourceNode buildNodes(MapperGeneratorContext context, SourceNodeVars vars) throws IOException {
                context.mappingMethod(inOutType, name);
                root.body(vars.setOrAssign(String.format("%s(%%s)", name)));
                return root.body;
            }
        };
        return res;
    }

    abstract MappingSourceNode buildNodes(final MapperGeneratorContext context, final SourceNodeVars vars) throws IOException;

    public MappingSourceNode build(final MapperGeneratorContext context, final SourceNodeVars vars) throws IOException {
        root = blank();
        MappingSourceNode ptr = buildNodes(context, vars);
        if (context.depth > 0 && !vars.inTypeIsPrime()) {
            // working inside a bean
            root = notNullInField(vars);
            root.body(ptr);
            root.child(controlNullElse()).body(vars.setOrAssign("null"));
            return root;
        } else {
            return buildNodes(context, vars);
        }
    }

    static abstract class MappingSpecification {

        abstract MappingBuilder getBuilder(final MapperGeneratorContext context, final InOutType inOutType);

        abstract boolean match(final MapperGeneratorContext context, final InOutType inOutType);

    }

    static abstract class SameDeclaredMappingSpecification extends MappingSpecification {

        @Override
        boolean match(final MapperGeneratorContext context, final InOutType inOutType) {
            if (!inOutType.differs() && inOutType.areDeclared()) {
                return true;
            }
            return false;
        }
    }
}
