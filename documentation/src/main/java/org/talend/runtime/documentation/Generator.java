/**
 * Copyright (C) 2006-2018 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.talend.runtime.documentation;

import static java.util.Comparator.comparing;
import static java.util.Locale.ENGLISH;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.ziplock.JarLocation.jarLocation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.apache.deltaspike.core.api.config.Configuration;
import org.apache.johnzon.jaxrs.jsonb.jaxrs.JsonbJaxrsProvider;
import org.apache.johnzon.mapper.Mapper;
import org.apache.johnzon.mapper.MapperBuilder;
import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.archive.FileArchive;
import org.apache.xbean.finder.archive.JarArchive;
import org.talend.sdk.component.api.configuration.condition.meta.Condition;
import org.talend.sdk.component.api.configuration.constraint.meta.Validation;
import org.talend.sdk.component.api.configuration.constraint.meta.Validations;
import org.talend.sdk.component.api.configuration.type.meta.ConfigurationType;
import org.talend.sdk.component.api.configuration.ui.layout.AutoLayout;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.configuration.ui.meta.Ui;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.service.ActionType;
import org.talend.sdk.component.api.service.asyncvalidation.ValidationResult;
import org.talend.sdk.component.api.service.completion.Values;
import org.talend.sdk.component.api.service.healthcheck.HealthCheckStatus;
import org.talend.sdk.component.api.service.schema.Schema;
import org.talend.sdk.component.api.service.schema.Type;
import org.talend.sdk.component.junit.environment.BaseEnvironmentProvider;
import org.talend.sdk.component.junit.environment.builtin.beam.BeamEnvironment;
import org.talend.sdk.component.runtime.manager.reflect.parameterenricher.ConditionParameterEnricher;
import org.talend.sdk.component.runtime.manager.reflect.parameterenricher.ConfigurationTypeParameterEnricher;
import org.talend.sdk.component.runtime.manager.reflect.parameterenricher.UiParameterEnricher;
import org.talend.sdk.component.runtime.manager.reflect.parameterenricher.ValidationParameterEnricher;
import org.talend.sdk.component.runtime.reflect.Defaults;
import org.talend.sdk.component.server.configuration.ComponentServerConfiguration;
import org.talend.sdk.component.spi.parameter.ParameterExtensionEnricher;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = PRIVATE)
public class Generator {

    public static void main(final String[] args) throws Exception {
        final File generatedDir = new File(args[0], "generated-adoc");
        generatedDir.mkdirs();
        generatedTypes(generatedDir);
        generatedConstraints(generatedDir);
        generatedConditions(generatedDir);
        generatedActions(generatedDir);
        generatedUi(generatedDir);
        generatedServerConfiguration(generatedDir);
        generatedJUnitEnvironment(generatedDir);

        final boolean offline = "offline=true".equals(args[4]);
        if (offline) {
            System.out.println("System is offline, skipping jira changelog and github contributor generation");
            return;
        }
        generatedContributors(generatedDir, args[5], args[6], Boolean.parseBoolean(args[7]));
        generatedJira(generatedDir, args[1], args[2], args[3]);
    }

    private static void generatedJUnitEnvironment(final File generatedDir)
            throws FileNotFoundException, MalformedURLException {
        final File file = new File(generatedDir, "junit-environments.adoc");
        try (final PrintStream stream = new PrintStream(new FileOutputStream(file))) {
            stream.println("");
            stream.println("NOTE: the configuration is read from system properties, environment variables, ....");
            stream.println("");
            stream.println("[role=\"table-striped table-hover table-ordered\",options=\"header,autowidth\"]");
            stream.println("|====");
            stream.println("|Class|Name|Description");
            final File api = jarLocation(BaseEnvironmentProvider.class);
            final ClassLoader loader = Thread.currentThread().getContextClassLoader();
            final AnnotationFinder finder = new AnnotationFinder(
                    api.isDirectory() ? new FileArchive(loader, api) : new JarArchive(loader, api.toURI().toURL()));
            finder
                    .link()
                    .findSubclasses(BeamEnvironment.class)
                    .stream()
                    .sorted(Comparator.comparing(Class::getName))
                    .forEach(type -> {
                        final BeamEnvironment environment;
                        try {
                            environment = BeamEnvironment.class.cast(type.getConstructor().newInstance());
                        } catch (final InstantiationException | IllegalAccessException | NoSuchMethodException
                                | InvocationTargetException e) {
                            throw new IllegalStateException(e);
                        }
                        stream.println("|" + type.getSimpleName() + "|" + environment.getName() + "|"
                                + environment.getName() + " runner");
                    });
            stream.println("|====");
            stream.println();

        }
        System.out.println("Generated " + file);
    }

    private static void generatedContributors(final File generatedDir, final String user, final String pwd,
            final boolean cache) throws Exception {
        Collection<Contributor> contributors;
        if (user == null || user.trim().isEmpty() || "skip".equals(user)) {
            System.err.println("No Github credentials, will skip contributors generation");
            contributors = new ArrayList<>();
        } else {
            try {
                contributors = new Github(user, pwd).load();
            } catch (final RuntimeException re) {
                log.error(re.getMessage(), re);
                contributors = new ArrayList<>();
            }
        }
        final File file = new File(generatedDir, "contributors.json");
        final File cacheFile =
                new File(System.getProperty("user.home"), "build/Talend/component-runtime/.ci-cache/contributors.json");
        if (cache && contributors.isEmpty() && cacheFile.exists()) { // try to reuse the cache
            log.info("Using cached contributors: {}", cacheFile.getAbsolutePath());
            Files.copy(cacheFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        try (final Jsonb jsonb = JsonbBuilder.create(); final Writer writer = new FileWriter(file)) {
            jsonb.toJson(contributors, writer);
        }
        if (cache) {
            cacheFile.getParentFile().mkdirs();
            Files.copy(file.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void generatedJira(final File generatedDir, final String username, final String password,
            final String version) throws FileNotFoundException {
        if (username == null || username.trim().isEmpty() || "skip".equals(username)) {
            System.err.println("No JIRA credentials, will skip changelog generation");
            return;
        }

        final String project = "TCOMP";
        final String jiraBase = "https://jira.talendforge.org";

        final File file = new File(generatedDir, "changelog.adoc");
        final Client client = ClientBuilder.newClient().register(new JsonbJaxrsProvider<>());
        final String auth = "Basic "
                + Base64.getEncoder().encodeToString((username + ':' + password).getBytes(StandardCharsets.UTF_8));

        try {
            final WebTarget restApi = client.target(jiraBase + "/rest/api/2");
            final List<JiraVersion> versions = restApi
                    .path("project/{project}/versions")
                    .resolveTemplate("project", project)
                    .request(APPLICATION_JSON_TYPE)
                    .header("Authorization", auth)
                    .get(new GenericType<List<JiraVersion>>() {
                    });

            final String currentVersion = version.replace("-SNAPSHOT", "");
            final List<JiraVersion> loggedVersions = versions
                    .stream()
                    .filter(v -> (v.isReleased() || jiraVersionMatches(currentVersion, v.getName())))
                    .sorted((o1, o2) -> { // reversed
                                          // order
                        final String[] parts1 = o1.getName().split("\\.");
                        final String[] parts2 = o2.getName().split("\\.");
                        for (int i = 0; i < Math.max(parts1.length, parts2.length); i++) {
                            try {
                                final int major = (parts2.length > i ? Integer.parseInt(parts2[i]) : 0)
                                        - (parts1.length > i ? Integer.parseInt(parts1[i]) : 0);
                                if (major != 0) {
                                    return major;
                                }
                            } catch (final NumberFormatException nfe) {
                                // no-op
                            }
                        }
                        return o2.getName().compareTo(o1.getId());
                    })
                    .collect(toList());
            if (loggedVersions.isEmpty()) {
                try (final PrintStream stream = new PrintStream(new FileOutputStream(file))) {
                    stream.println("No version found.");
                }
                return;
            }

            final String jql = "project=" + project + " AND labels=\"changelog\""
                    + loggedVersions.stream().map(v -> "fixVersion=" + v.getName()).collect(
                            joining(" OR ", " AND (", ")"));
            final Function<Long, JiraIssues> searchFrom = startAt -> restApi
                    .path("search")
                    .queryParam("jql", jql)
                    .queryParam("startAt", startAt)
                    .request(APPLICATION_JSON_TYPE)
                    .header("Authorization", auth)
                    .get(JiraIssues.class);
            final Function<JiraIssues, Stream<JiraIssues>> paginate = new Function<JiraIssues, Stream<JiraIssues>>() {

                @Override
                public Stream<JiraIssues> apply(final JiraIssues issues) {
                    final long nextStartAt = issues.getStartAt() + issues.getMaxResults();
                    final Stream<JiraIssues> fetched = Stream.of(issues);
                    return issues.getTotal() > nextStartAt
                            ? Stream.concat(fetched, apply(searchFrom.apply(nextStartAt)))
                            : fetched;
                }
            };
            final Set<String> includeStatus =
                    Stream.of("closed", "resolved", "development done", "qa done", "done").collect(toSet());
            final Map<String, TreeMap<String, List<JiraIssue>>> issues = Stream
                    .of(searchFrom.apply(0L))
                    .flatMap(paginate)
                    .flatMap(i -> ofNullable(i.getIssues()).map(Collection::stream).orElseGet(Stream::empty))
                    .filter(issue -> includeStatus
                            .contains(issue.getFields().getStatus().getName().toLowerCase(ENGLISH)))
                    .flatMap(i -> i.getFields().getFixVersions().stream().map(v -> Pair.of(v, i)))
                    .collect(groupingBy(pair -> pair.getKey().getName(), TreeMap::new,
                            groupingBy(pair -> pair.getValue().getFields().getIssuetype().getName(), TreeMap::new,
                                    collectingAndThen(mapping(Pair::getValue, toList()), l -> {
                                        l.sort(comparing(JiraIssue::getKey));
                                        return l;
                                    }))));
            final String changelog = issues
                    .entrySet()
                    .stream()
                    .map(versionnedIssues -> new StringBuilder("\n\n== Version ")
                            .append(versionnedIssues.getKey())
                            .append(versionnedIssues.getValue().entrySet().stream().collect(
                                    (Supplier<StringBuilder>) StringBuilder::new,
                                    (builder, issuesByType) -> builder
                                            .append("\n\n=== ")
                                            .append(issuesByType.getKey())
                                            .append("\n\n")
                                            .append(issuesByType.getValue().stream().collect(
                                                    (Supplier<StringBuilder>) StringBuilder::new,
                                                    // note: for now we don't
                                                    // use the description since
                                                    // it is not that
                                                    // useful
                                                    (a, i) -> a
                                                            .append("- link:")
                                                            .append(jiraBase)
                                                            .append("/browse/")
                                                            .append(i.getKey())
                                                            .append("[")
                                                            .append(i.getKey())
                                                            .append("^]")
                                                            .append(": ")
                                                            .append(i.getFields().getSummary())
                                                            .append("\n"),
                                                    StringBuilder::append)),
                                    StringBuilder::append)))
                    .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                    .toString();

            try (final PrintStream stream = new PrintStream(new FileOutputStream(file))) {
                stream.println(changelog);
            }
        } finally {
            client.close();
        }

        System.out.println("Generated " + file);
    }

    private static boolean jiraVersionMatches(final String ref, final String name) {
        return ref.equals(name) || ref.equals(name + ".0");
    }

    private static void generatedServerConfiguration(final File generatedDir)
            throws FileNotFoundException, MalformedURLException {
        final File file = new File(generatedDir, "server-configuration.adoc");
        try (final PrintStream stream = new PrintStream(new FileOutputStream(file))) {
            stream.println("");
            stream.println("NOTE: the configuration is read from system properties, environment variables, ....");
            stream.println("");
            stream.println("[role=\"table-striped table-hover table-ordered\",options=\"header,autowidth\"]");
            stream.println("|====");
            stream.println("|Key|Description|Default");
            final File api = jarLocation(ComponentServerConfiguration.class);
            final ClassLoader loader = Thread.currentThread().getContextClassLoader();
            final AnnotationFinder finder = new AnnotationFinder(
                    api.isDirectory() ? new FileArchive(loader, api) : new JarArchive(loader, api.toURI().toURL()));
            finder
                    .findAnnotatedClasses(Configuration.class)
                    .stream()
                    .sorted(Comparator.comparing(t -> t.getAnnotation(Configuration.class).prefix()))
                    .flatMap(c -> Stream.of(c.getMethods()))
                    .forEach(method -> {
                        final ConfigProperty configProperty = method.getAnnotation(ConfigProperty.class);
                        final String name = method.getDeclaringClass().getAnnotation(Configuration.class).prefix()
                                + configProperty.name();

                        stream.println("|" + name + "|" + method.getAnnotation(Documentation.class).value() + "|"
                                + (ConfigProperty.NULL.equalsIgnoreCase(configProperty.defaultValue()) ? "-"
                                        : configProperty.defaultValue()));
                    });
            stream.println("|====");
            stream.println();

        }
        System.out.println("Generated " + file);
    }

    private static void generatedActions(final File generatedDir) throws Exception {
        final File file = new File(generatedDir, "actions.adoc");
        try (final PrintStream stream = new PrintStream(new FileOutputStream(file))) {
            stream.println("");
            stream.println("[role=\"table-striped table-hover table-ordered\",options=\"header,autowidth\"]");
            stream.println("|====");
            stream.println("|API|Type|Description|Return type|Sample returned type");
            final File api = jarLocation(ActionType.class);
            final ClassLoader loader = Thread.currentThread().getContextClassLoader();
            final AnnotationFinder finder = new AnnotationFinder(
                    api.isDirectory() ? new FileArchive(loader, api) : new JarArchive(loader, api.toURI().toURL()));
            finder
                    .findAnnotatedClasses(ActionType.class)
                    .stream()
                    .sorted(Comparator
                            .comparing(t -> t.getAnnotation(ActionType.class).value() + "#" + t.getSimpleName()))
                    .forEach(type -> {
                        final ActionType actionType = type.getAnnotation(ActionType.class);
                        final Class<?> returnedType = actionType.expectedReturnedType();
                        stream.println("|@" + type.getName() + "|" + actionType.value() + "|" + extractDoc(type) + "|"
                                + (returnedType == Object.class ? "any" : returnedType.getSimpleName()) + "|"
                                + (returnedType != Object.class ? "`" + sample(returnedType).replace("\n", "") + "`"
                                        : "-"));
                    });
            stream.println("|====");
            stream.println();

        }
        System.out.println("Generated " + file);
    }

    private static void generatedUi(final File generatedDir) throws Exception {
        final File file = new File(generatedDir, "ui.adoc");
        try (final PrintStream stream = new PrintStream(new FileOutputStream(file))) {
            stream.println("");
            stream.println("[role=\"table-striped table-hover table-ordered\",options=\"header,autowidth\"]");
            stream.println("|====");
            stream.println("|API|Description|Generated property metadata");
            final File api = jarLocation(Ui.class);
            final ClassLoader loader = Thread.currentThread().getContextClassLoader();
            final AnnotationFinder finder = new AnnotationFinder(
                    api.isDirectory() ? new FileArchive(loader, api) : new JarArchive(loader, api.toURI().toURL()));
            final ParameterExtensionEnricher enricher = new UiParameterEnricher();
            final Mapper mapper = new MapperBuilder().build();
            finder.findAnnotatedClasses(Ui.class).stream().sorted(Comparator.comparing(Class::getName)).forEach(
                    type -> {
                        final Map<String, String> meta = enricher
                                .onParameterAnnotation("theparameter", Object.class, generateAnnotation(type))
                                .entrySet()
                                .stream()
                                .collect(toMap(e -> e.getKey().replace("tcomp::", ""), Map.Entry::getValue));
                        stream.println("|@" + type.getName() + "|" + extractDoc(type) + "|"
                                + mapper.writeObjectAsString(meta));
                    });
            stream.println("|====");
            stream.println();

        }
        System.out.println("Generated " + file);
    }

    private static String sample(final Class<?> returnedType) {
        if (returnedType == Values.class) {
            final Values list = new Values();
            list.setItems(new ArrayList<>());

            final Values.Item item = new Values.Item();
            item.setId("value");
            item.setLabel("label");
            list.getItems().add(item);

            return new MapperBuilder().setPretty(false).build().writeObjectAsString(list);
        }
        if (returnedType == HealthCheckStatus.class) {
            final HealthCheckStatus status = new HealthCheckStatus();
            status.setStatus(HealthCheckStatus.Status.KO);
            status.setComment("Something went wrong");
            return new MapperBuilder().setPretty(false).build().writeObjectAsString(status);
        }
        if (returnedType == Schema.class) {
            final Schema.Entry entry = new Schema.Entry();
            entry.setName("column1");
            entry.setType(Type.STRING);

            final Schema schema = new Schema();
            schema.setEntries(new ArrayList<>());
            schema.getEntries().add(entry);
            return new MapperBuilder().setPretty(false).build().writeObjectAsString(schema);
        }
        if (returnedType == ValidationResult.class) {
            final ValidationResult status = new ValidationResult();
            status.setStatus(ValidationResult.Status.KO);
            status.setComment("Something went wrong");
            return new MapperBuilder().setPretty(false).build().writeObjectAsString(status);
        }
        return "{\n" + Stream
                .of(returnedType.getDeclaredFields())
                .map(f -> " \"" + f.getName() + "\": " + createSample(f.getType()))
                .collect(joining("\n")) + "\n}";
    }

    private static String createSample(final Class<?> type) {
        if (type.isEnum()) {
            return Stream.of(type.getEnumConstants()).map(e -> Enum.class.cast(e).name()).collect(
                    joining("\"|\"", "\"", "\""));
        }
        return "\"...\"";
    }

    private static void generatedConditions(final File generatedDir) throws Exception {
        final File file = new File(generatedDir, "conditions.adoc");
        try (final PrintStream stream = new PrintStream(new FileOutputStream(file))) {
            stream.println("");
            stream.println("[role=\"table-striped table-hover table-ordered\",options=\"header,autowidth\"]");
            stream.println("|====");
            stream.println("|API|Name|Description|Metadata Sample");
            final File api = jarLocation(Condition.class);
            final ClassLoader loader = Thread.currentThread().getContextClassLoader();
            final ConditionParameterEnricher enricher = new ConditionParameterEnricher();
            final Mapper mapper = new MapperBuilder().build();
            final AnnotationFinder finder = new AnnotationFinder(
                    api.isDirectory() ? new FileArchive(loader, api) : new JarArchive(loader, api.toURI().toURL()));
            finder.findAnnotatedClasses(Condition.class).forEach(type -> stream.println("|@" + type.getName() + "|"
                    + type.getAnnotation(Condition.class).value() + "|" + extractDoc(type) + "|"
                    + mapper
                            .writeObjectAsString(
                                    enricher.onParameterAnnotation("test", String.class, generateAnnotation(type)))
                            .replace("tcomp::", "")));
            stream.println("|====");
            stream.println();

        }
        System.out.println("Generated " + file);
    }

    private static void generatedTypes(final File generatedDir) throws Exception {
        final File file = new File(generatedDir, "configuration-types.adoc");
        try (final PrintStream stream = new PrintStream(new FileOutputStream(file))) {
            stream.println("");
            stream.println("[role=\"table-striped table-hover table-ordered\",options=\"header,autowidth\"]");
            stream.println("|====");
            stream.println("|API|Type|Description|Metadata sample");
            final File api = jarLocation(ConfigurationType.class);
            final ClassLoader loader = Thread.currentThread().getContextClassLoader();
            final ConfigurationTypeParameterEnricher enricher = new ConfigurationTypeParameterEnricher();
            final Mapper mapper = new MapperBuilder().build();
            final AnnotationFinder finder = new AnnotationFinder(
                    api.isDirectory() ? new FileArchive(loader, api) : new JarArchive(loader, api.toURI().toURL()));
            finder
                    .findAnnotatedClasses(ConfigurationType.class)
                    .forEach(type -> stream.println("|" + type.getName() + "|"
                            + type.getAnnotation(ConfigurationType.class).value() + "|" + extractDoc(type) + "|"
                            + mapper.writeObjectAsString(
                                    enricher.onParameterAnnotation("value", String.class, generateAnnotation(type)))));
            stream.println("|====");
            stream.println();

        }
        System.out.println("Generated " + file);
    }

    private static void generatedConstraints(final File generatedDir) throws Exception {
        final File file = new File(generatedDir, "constraints.adoc");
        try (final PrintStream stream = new PrintStream(new FileOutputStream(file))) {
            stream.println("");
            stream.println("[role=\"table-striped table-hover table-ordered\",options=\"header,autowidth\"]");
            stream.println("|====");
            stream.println("|API|Name|Parameter Type|Description|Supported Types|Metadata sample");
            final File api = jarLocation(Validation.class);
            final ClassLoader loader = Thread.currentThread().getContextClassLoader();
            final AnnotationFinder finder = new AnnotationFinder(
                    api.isDirectory() ? new FileArchive(loader, api) : new JarArchive(loader, api.toURI().toURL()));
            final ValidationParameterEnricher enricher = new ValidationParameterEnricher();
            final Mapper mapper = new MapperBuilder().build();
            Stream.concat(finder.findAnnotatedClasses(Validation.class).stream().map(validation -> {
                final Validation val = validation.getAnnotation(Validation.class);
                return createConstraint(validation, val);

            }), finder.findAnnotatedClasses(Validations.class).stream().flatMap(
                    validations -> Stream.of(validations.getAnnotation(Validations.class).value()).map(
                            validation -> createConstraint(validations, validation))))
                    .sorted((o1, o2) -> {
                        final int types = Stream.of(o1.types).map(Class::getName).collect(joining("/")).compareTo(
                                Stream.of(o2.types).map(Class::getName).collect(joining("/")));
                        if (types == 0) {
                            return o1.name.compareTo(o2.name);
                        }
                        return types;
                    })
                    .forEach(constraint -> stream.println("|@" + constraint.marker.getName() + "|" + constraint.name
                            + "|" + sanitizeType(constraint.paramType) + "|" + constraint.description + "|"
                            + Stream.of(constraint.types).map(Class::getName).map(Generator::sanitizeType).collect(
                                    joining(", "))
                            + "|"
                            + mapper
                                    .writeObjectAsString(enricher.onParameterAnnotation("test", constraint.types[0],
                                            generateAnnotation(constraint.marker)))
                                    .replace("tcomp::", "")));
            stream.println("|====");
            stream.println();

        }
        System.out.println("Generated " + file);
    }

    private static String sanitizeType(final String s) {
        return s.replace("java.lang.", "").replace("java.util.", "");
    }

    private static Constraint createConstraint(final Class<?> validation, final Validation val) {
        return new Constraint(val.name(), val.expectedTypes(), getParamType(validation), validation,
                extractDoc(validation));
    }

    private static String extractDoc(final Class<?> validation) {
        return ofNullable(validation.getAnnotation(Documentation.class)).map(Documentation::value).orElse("-");
    }

    private static String getParamType(final Class<?> validation) {
        try {
            final Class<?> returnType = validation.getMethod("value").getReturnType();
            return returnType.getName().toLowerCase(ENGLISH);
        } catch (final NoSuchMethodException e) {
            return "-";
        }
    }

    // generate a "mock" annotation to be able to generate sample metadata - mainly
    // for @Ui
    private static <T extends Annotation> T generateAnnotation(final Class<?> type) {
        return (T) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class<?>[] { type },
                (proxy, method, args) -> {
                    if ("annotationType".equals(method.getName()) && Annotation.class == method.getDeclaringClass()) {
                        return type;
                    }
                    if (method.isDefault()) {
                        return Defaults
                                .of(method.getDeclaringClass())
                                .unreflectSpecial(method, method.getDeclaringClass())
                                .bindTo(proxy)
                                .invokeWithArguments(args);
                    }
                    final Class<?> returnType = method.getReturnType();
                    if (int.class == returnType) {
                        return 1234;
                    }
                    if (double.class == returnType) {
                        return 12.34;
                    }
                    if (String.class == returnType) {
                        return "test";
                    }
                    if (Class.class == returnType) {
                        return AutoLayout.class;
                    }
                    if (String[].class == returnType) {
                        return new String[] { "value1", "value2" };
                    }
                    if (GridLayout.Row[].class == returnType) {
                        return new GridLayout.Row[] { new GridLayout.Row() {

                            @Override
                            public Class<? extends Annotation> annotationType() {
                                return GridLayout.Row.class;
                            }

                            @Override
                            public String[] value() {
                                return new String[] { "first" };
                            }
                        }, new GridLayout.Row() {

                            @Override
                            public Class<? extends Annotation> annotationType() {
                                return GridLayout.Row.class;
                            }

                            @Override
                            public String[] value() {
                                return new String[] { "second", "third" };
                            }
                        } };
                    }
                    if (GridLayout[].class == returnType) {
                        return new GridLayout[] { new GridLayout() {

                            @Override
                            public Row[] value() {
                                return new Row[] { new Row() {

                                    @Override
                                    public Class<? extends Annotation> annotationType() {
                                        return Row.class;
                                    }

                                    @Override
                                    public String[] value() {
                                        return new String[] { "first" };
                                    }
                                }, new Row() {

                                    @Override
                                    public Class<? extends Annotation> annotationType() {
                                        return Row.class;
                                    }

                                    @Override
                                    public String[] value() {
                                        return new String[] { "second", "third" };
                                    }
                                } };
                            }

                            @Override
                            public String[] names() {
                                return new String[] { FormType.MAIN };
                            }

                            @Override
                            public Class<? extends Annotation> annotationType() {
                                return GridLayout.class;
                            }
                        }, new GridLayout() {

                            @Override
                            public Row[] value() {
                                return new Row[] { new Row() {

                                    @Override
                                    public Class<? extends Annotation> annotationType() {
                                        return Row.class;
                                    }

                                    @Override
                                    public String[] value() {
                                        return new String[] { "another" };
                                    }
                                } };
                            }

                            @Override
                            public String[] names() {
                                return new String[] { FormType.ADVANCED };
                            }

                            @Override
                            public Class<? extends Annotation> annotationType() {
                                return GridLayout.class;
                            }
                        } };
                    }
                    return null;
                });
    }

    @RequiredArgsConstructor
    private static final class Constraint {

        private final String name;

        private final Class<?>[] types;

        private final String paramType;

        private final Class<?> marker;

        private final String description;
    }

    @Data
    public static class JiraVersion {

        private String id;

        private String name;

        private boolean released;

        private boolean archived;

        private long projectId;
    }

    @Data
    public static class JiraIssues {

        private long startAt;

        private long maxResults;

        private long total;

        private Collection<JiraIssue> issues;
    }

    @Data
    public static class IssueType {

        private String name;
    }

    @Data
    public static class JiraIssue {

        private String id;

        private String key;

        private Fields fields;
    }

    @Data
    public static class Fields {

        private String summary;

        private String description;

        private IssueType issuetype;

        private Status status;

        private Collection<JiraVersion> fixVersions;
    }

    @Data
    public static class Status {

        private String name;
    }
}
