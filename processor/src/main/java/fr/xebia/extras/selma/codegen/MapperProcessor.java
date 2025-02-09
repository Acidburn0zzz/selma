/*
 * Copyright 2013 Xebia and Séven Le Mesle
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

import fr.xebia.extras.selma.Mapper;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

/**
 *  Process the @Mapper and generate the corresponding implementations
 */
@SupportedAnnotationTypes({"fr.xebia.extras.selma.Mapper"})
public final class MapperProcessor extends AbstractProcessor {


    private final HashMap<String, List<ExecutableElement>> remainingMapperTypes = new HashMap<String, List<ExecutableElement>>();

    static Types types;


    protected static final Set<String> exclusions = new HashSet<String>(Arrays.asList("equals", "getClass", "hashCode", "toString", "notify", "notifyAll", "wait", "clone", "finalize"));

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        types = processingEnv.getTypeUtils();
        populateAllMappers(roundEnv);

        try {
            generateMappingClassses();
        } catch (IOException e) {
            e.printStackTrace();
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            error(writer.toString(), null);
        }

        return false;
    }

    private void generateMappingClassses() throws IOException {

        for (String classe : remainingMapperTypes.keySet()) {
            MapperClassGenerator classGenerator = new MapperClassGenerator(classe, remainingMapperTypes.get(classe), processingEnv);
            classGenerator.build();
        }

    }

    private void populateAllMappers(RoundEnvironment roundEnv) {

        for (Element element : roundEnv.getElementsAnnotatedWith(Mapper.class)) {
            if (!isValidMapperUse(element)) {
                continue;
            } else {
                TypeElement typeElement = (TypeElement)element;
                List<? extends Element> allMembers =  element.getEnclosedElements();
                List<? extends Element> methods = ElementFilter.methodsIn(allMembers);

                for (Element method : methods) {
                    ExecutableElement executableElement = (ExecutableElement) method;

                    if (isValidMapperMethod(executableElement)) {
                        // Here we have a Mapper method to build
                        VariableElement variableElementInType = executableElement.getParameters().get(0);
                        String inType = variableElementInType.asType().toString();
                        String outType = executableElement.getReturnType().toString();
                        info(executableElement, "Found selma method : %s %s (%s);", outType, executableElement.getSimpleName(), inType);
                        putMapper(element, executableElement);
                    }
                }

            }
        }
    }

    private boolean isValidMapperMethod(ExecutableElement executableElement) {

        if(exclusions.contains(executableElement.getSimpleName().toString())){

            return false;
        }

        if (executableElement.getParameters().size() < 1) {
            error(executableElement, "@Mapper method %s can not have less than one parameter", executableElement.getSimpleName());
            return false;
        }
        if (executableElement.getParameters().size() > 1) {
            error(executableElement, "@Mapper method %s can not have more than one parameter", executableElement.getSimpleName());
            return false;
        }

        if (executableElement.getReturnType().getKind() == TypeKind.VOID) {
            error(executableElement, "@Mapper method %s can not return void", executableElement.getSimpleName());
            return false;
        }
        return true;
    }


    private void error(ExecutableElement element, String templateMessage, Object... args) {

        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format(templateMessage, args), element);
    }

    private void putMapper(Element element, ExecutableElement executableElement) {
        String type = element.asType().toString();
        List<ExecutableElement> elementList;
        if (remainingMapperTypes.containsKey(type)) {
            elementList = remainingMapperTypes.get(type);
        } else {
            elementList = new ArrayList<ExecutableElement>();
            remainingMapperTypes.put(type, elementList);
        }
        elementList.add(executableElement);
    }

    private boolean isValidMapperUse(Element element) {

        if (element.getKind() != ElementKind.INTERFACE) {
            error("@Mapper can only be used on interface not on " + element.getKind(), element);
            return false;
        }

        return true;
    }

    private void error(String msg, Element element) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element);
    }

    private void info(Element element, String msgTemplate, Object ... args ) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, String.format(msgTemplate, args), element);
    }

}