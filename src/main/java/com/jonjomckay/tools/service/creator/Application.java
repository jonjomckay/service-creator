package com.jonjomckay.tools.service.creator;

import com.google.common.base.CaseFormat;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.manywho.sdk.api.ContentType;
import com.manywho.sdk.services.actions.Action;
import com.manywho.sdk.services.types.Type;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import io.airlift.airline.Cli;
import io.airlift.airline.Command;
import io.airlift.airline.Help;
import io.airlift.airline.Option;
import io.swagger.codegen.CliOption;
import io.swagger.codegen.ClientOptInput;
import io.swagger.codegen.CodegenConfig;
import io.swagger.codegen.DefaultGenerator;
import io.swagger.codegen.config.CodegenConfigurator;
import org.fusesource.jansi.AnsiConsole;
import org.json.JSONObject;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.fusesource.jansi.Ansi.ansi;

public class Application {
    public static void main(String[] args) {
        AnsiConsole.systemInstall();

        Cli.CliBuilder<Runnable> builder = Cli.<Runnable>builder("manywho-service-creator")
                .withDefaultCommand(Help.class)
                .withCommands(Help.class, Swagger.class);

        Cli<Runnable> cli = builder.build();

        cli.parse(args).run();




    }

    @Command(name = "swagger", description = "Create a service skeleton from a Swagger definition")
    public static class Swagger implements Runnable {

        @Option(name = "--group", description = "The group ID to use for the generated service", required = true)
        private String group;

        @Option(name = "--artifact", description = "The artifact ID to use for the generated service", required = true)
        private String artifact;

        @Option(name = "--url", description = "Swagger definition URL", required = true)
        private String url;

        @Option(name = "--output", description = "Output directory for the generated service")
        private String output = "./example";

        private void createSwaggerClient(String packageName) {
            // Copy the swagger-codegen ignore file to the target folder
            try (InputStream stream = ClassLoader.getSystemResourceAsStream(".swagger-codegen-ignore")) {
                Files.copy(stream, Paths.get(String.format("%s/.swagger-codegen-ignore", output)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            CodegenConfigurator codegenConfigurator = new CodegenConfigurator();
            codegenConfigurator.setInputSpec(url);
            codegenConfigurator.setLang("java");
            codegenConfigurator.setLibrary("retrofit");
            codegenConfigurator.setOutputDir("example");
            codegenConfigurator.setGroupId(group);
            codegenConfigurator.setArtifactId(artifact);
            codegenConfigurator.setApiPackage(String.format("%s.swagger.api", packageName));
            codegenConfigurator.setInvokerPackage(String.format("%s.swagger", packageName));
            codegenConfigurator.setModelPackage(String.format("%s.swagger.model", packageName));

            ClientOptInput input = codegenConfigurator.toClientOptInput();
            CodegenConfig config = input.getConfig();

            for (CliOption cliOption : config.cliOptions()) {
                if (cliOption.getOpt().equals("dateLibrary")) {
                    input.getConfig().additionalProperties().put(cliOption.getOpt(), "java8");
                }
            }

            new DefaultGenerator().opts(input).generate();
        }

        private void createTypes(String packageName, JsonNode response) {
            JSONObject definitions = response.getObject().getJSONObject("definitions");

            for (String name : definitions.keySet()) {
                // Only create a type if this definition is an "object"
                if (definitions.getJSONObject(name).getString("type").equals("object")) {
                    System.out.println("Creating a ManyWho Type for the " + name + " object definition");

                    List<FieldSpec> fields = Lists.newArrayList();

                    JSONObject properties = definitions.getJSONObject(name).getJSONObject("properties");

                    for (String propertyName : properties.keySet()) {
                        // Check if we're creating a scalar property or a typed one
                        if (properties.getJSONObject(propertyName).has("type")) {
                            FieldSpec fieldSpec = null;

                            switch (properties.getJSONObject(propertyName).getString("type")) {
                                case "integer":
                                    fieldSpec = createIntegerPropertyField(propertyName);
                                    break;
                                case "string":
                                    fieldSpec = createStringPropertyField(propertyName);
                                    break;
                            }

                            if (fieldSpec != null) {
                                fields.add(fieldSpec);
                            }
                        } else if (properties.getJSONObject(propertyName).has("$ref")) {
                            // This is an object - cba how to work this out for the prototype yet
                        }
                    }

                    TypeSpec typeSpec = TypeSpec.classBuilder(name)
                            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                            .addFields(fields)
                            .build();

                    JavaFile javaFile = JavaFile.builder(String.format("%s.types", packageName), typeSpec)
                            .build();

                    try {
                        javaFile.writeTo(Paths.get("./example/src/main/java"));
                    } catch (IOException e) {
                        throw new RuntimeException("Unable to save the Type", e);
                    }
                }
            }
        }

        private void createActionsAndDatabases(JsonNode response) {
            JSONObject paths = response.getObject().getJSONObject("paths");

            for (String path : paths.keySet()) {

                JSONObject methods = paths.getJSONObject(path);
                for (String methodName : methods.keySet()) {
                    JSONObject method = methods.getJSONObject(methodName);

                    String name = method.getString("operationId");
                    String summary = method.getString("summary");

                    System.out.print(ansi().a("Processing method "));
                    System.out.print(ansi().bold().fgCyan().a(name).reset());
                    System.out.print(ansi().a(String.format(" (%s)\n", summary)).reset());

                    System.out.print(ansi().bold().fgRed().a(String.format("  %s \t", methodName.toUpperCase())).reset());
                    System.out.print(ansi().a(path));
                    System.out.print(ansi().a("\n"));
                    System.out.print(ansi().a("\n"));

                    String answer = readAnswer();
                    switch (answer) {
                        case "a":
                            createAction(response, method);
                            break;
                        case "c":
                            System.out.println("Creating create");
                            break;
                        case "d":
                            System.out.println("Creating delete");
                            break;
                        case "m":
                            System.out.println("Creating load multiple");
                            break;
                        case "s":
                            System.out.println("Creating load single");
                            break;
                        case "u":
                            System.out.println("Creating update");
                            break;
                    }

                    System.out.println();
                }
            }
        }

        private void createAction(JsonNode response, JSONObject method) {
            System.out.println("Creating action with the name " + method.getString("operationId"));

            AnnotationSpec metadataAnnotation = AnnotationSpec.builder(Action.Metadata.class)
                    .addMember("name", "$S", method.getString("operationId"))
                    .addMember("summary", "$S", method.getString("summary"))
                    .addMember("uri", "$S", method.getString("operationId"))
                    .build();

            List<FieldSpec> inputFields = Lists.newArrayList();
            List<MethodSpec> inputMethods = Lists.newArrayList();
            List<FieldSpec> outputFields = Lists.newArrayList();

            for (Object parameterObject : method.getJSONArray("parameters")) {
                JSONObject parameter = (JSONObject) parameterObject;

                // If we have a "type" property then I think it's a scalar?
                if (parameter.has("type")) {
                    inputFields.add(createStringInputField(parameter.getString("name"), parameter.getBoolean("required")));

                    inputMethods.add(createStringGetter(parameter.getString("name")));
                } else {

                }
            }

            TypeSpec inputType = TypeSpec.classBuilder("Input")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addFields(inputFields)
                    .addMethods(inputMethods)
                    .build();

            TypeSpec actionType = TypeSpec.classBuilder(CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, method.getString("operationId")))
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .addAnnotation(metadataAnnotation)
                    .addType(inputType)
                    .build();

            String type = actionType.toString();

            //System.out.println(type);
        }

        private String readAnswer() {
            System.out.println("Is this method an Action (a), Create (c), Delete (d), Load Multiple (m), Load Single (s), Update (u)? ");

            String input = System.console().readLine();
            if (Strings.isNullOrEmpty(input) || !Arrays.asList("a", "c", "d", "m", "s", "u").contains(input)) {
                System.out.println(ansi().bgRed().a("Please answer with one of the given options").reset());

                return readAnswer();
            }

            return input;
        }

        public void run() {
            String packageName = String.format("%s.%s", group, artifact);

            new File(output).mkdir();

//            createSwaggerClient(packageName);

            JsonNode response;

            try {
                response = Unirest.get(url)
                        .asJson()
                        .getBody();
            }
            catch (UnirestException e) {
                throw new RuntimeException(e);
            }

            createTypes(packageName, response);

            createActionsAndDatabases(response);
        }

        static FieldSpec createIntegerPropertyField(String name) {
            AnnotationSpec annotationSpec = AnnotationSpec.builder(Type.Property.class)
                    .addMember("contentType", "$S", ContentType.Number)
                    .addMember("name", "$S", name)
                    .build();

            return FieldSpec.builder(Double.class, name)
                    .addAnnotation(annotationSpec)
                    .addModifiers(Modifier.PRIVATE)
                    .build();
        }

        static FieldSpec createStringPropertyField(String name) {
            AnnotationSpec annotationSpec = AnnotationSpec.builder(Type.Property.class)
                    .addMember("contentType", "$S", ContentType.String)
                    .addMember("name", "$S", name)
                    .build();

            return FieldSpec.builder(String.class, name)
                    .addAnnotation(annotationSpec)
                    .addModifiers(Modifier.PRIVATE)
                    .build();
        }

        static FieldSpec createStringInputField(String name, boolean required) {
            String readableName = name.replaceAll(
                    String.format("%s|%s|%s",
                            "(?<=[A-Z])(?=[A-Z][a-z])",
                            "(?<=[^A-Z])(?=[A-Z])",
                            "(?<=[A-Za-z])(?=[^A-Za-z])"
                    ),
                    " "
            );

            AnnotationSpec annotationSpec = AnnotationSpec.builder(Action.Input.class)
                    .addMember("contentType", "$S", ContentType.String)
                    .addMember("name", "$S", readableName)
                    .addMember("required", "$S", required)
                    .build();

            return FieldSpec.builder(String.class, name)
                    .addAnnotation(annotationSpec)
                    .addModifiers(Modifier.PRIVATE)
                    .build();
        }

        static MethodSpec createStringGetter(String fieldName) {
            String methodName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);

            return MethodSpec.methodBuilder(methodName)
                    .addModifiers(Modifier.PUBLIC)
                    .addStatement("return $L", fieldName)
                    .returns(String.class)
                    .build();
        }
    }
}
