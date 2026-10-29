package io.jenkins.plugins.parameterfoundry.provider;

import io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode;
import io.jenkins.plugins.parameterfoundry.model.Option;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

final class StructuredOptionParser {

    private static final Pattern JSON_PATH_TOKEN = Pattern.compile("(?:^|\\.)([A-Za-z0-9_-]+)|\\[(\\*|\\d+)]");

    private StructuredOptionParser() {}

    static List<Option> parse(
            byte[] body,
            String format,
            String rootPath,
            String valuePath,
            String labelPath,
            String descriptionPath,
            String groupPath,
            String disabledPath,
            String disabledReasonPath,
            String tagsPath,
            String providerId) {
        try {
            return switch (format) {
                case "json" ->
                    parseJson(
                            body,
                            rootPath,
                            valuePath,
                            labelPath,
                            descriptionPath,
                            groupPath,
                            disabledPath,
                            disabledReasonPath,
                            tagsPath);
                case "xml" ->
                    parseXml(
                            body,
                            rootPath,
                            valuePath,
                            labelPath,
                            descriptionPath,
                            groupPath,
                            disabledPath,
                            disabledReasonPath,
                            tagsPath);
                default -> throw new IllegalArgumentException("Unsupported structured format: " + format);
            };
        } catch (ProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ProviderException(
                    ErrorCode.PROVIDER_UNAVAILABLE, providerId, "Provider response could not be mapped to Options.");
        }
    }

    private static List<Option> parseJson(
            byte[] body,
            String rootPath,
            String valuePath,
            String labelPath,
            String descriptionPath,
            String groupPath,
            String disabledPath,
            String disabledReasonPath,
            String tagsPath) {
        Object root = JSONSerializer.toJSON(new String(body, StandardCharsets.UTF_8));
        List<Object> rows = jsonValues(root, rootPath);
        if (rows.size() == 1 && rows.get(0) instanceof JSONArray array) {
            rows = new ArrayList<>();
            for (Object item : array) {
                rows.add(item);
            }
        }
        List<Option> options = new ArrayList<>();
        for (Object row : rows) {
            String value = jsonString(row, valuePath, true);
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("Mapped Option value is empty.");
            }
            Option option = new Option(value);
            option.setLabel(jsonString(row, labelPath, false));
            option.setDescription(jsonString(row, descriptionPath, false));
            option.setGroup(jsonString(row, groupPath, false));
            option.setDisabled(booleanValue(jsonString(row, disabledPath, false)));
            option.setDisabledReason(jsonString(row, disabledReasonPath, false));
            option.setTags(jsonStrings(row, tagsPath));
            options.add(option);
        }
        return List.copyOf(options);
    }

    private static List<Object> jsonValues(Object root, String path) {
        String normalized = path == null || path.isBlank() ? "$" : path.trim();
        if ("$".equals(normalized)) {
            return List.of(root);
        }
        if (normalized.startsWith("$.")) {
            normalized = normalized.substring(2);
        } else if (normalized.startsWith("$")) {
            normalized = normalized.substring(1);
        }
        Matcher matcher = JSON_PATH_TOKEN.matcher(normalized);
        int offset = 0;
        List<Object> current = List.of(root);
        while (matcher.find()) {
            if (matcher.start() != offset) {
                throw new IllegalArgumentException("Unsupported JSON path.");
            }
            offset = matcher.end();
            List<Object> next = new ArrayList<>();
            String key = matcher.group(1);
            String index = matcher.group(2);
            for (Object value : current) {
                if (key != null && value instanceof JSONObject object && object.containsKey(key)) {
                    next.add(object.get(key));
                } else if ("*".equals(index) && value instanceof JSONArray array) {
                    for (Object item : array) {
                        next.add(item);
                    }
                } else if (index != null && value instanceof JSONArray array) {
                    int itemIndex = Integer.parseInt(index);
                    if (itemIndex >= 0 && itemIndex < array.size()) {
                        next.add(array.get(itemIndex));
                    }
                }
            }
            current = List.copyOf(next);
        }
        if (offset != normalized.length()) {
            throw new IllegalArgumentException("Unsupported JSON path.");
        }
        return current;
    }

    private static String jsonString(Object row, String path, boolean rowWhenBlank) {
        if (path == null || path.isBlank()) {
            return rowWhenBlank ? scalar(row) : null;
        }
        List<Object> values = jsonValues(row, path);
        return values.isEmpty() ? null : scalar(values.get(0));
    }

    private static List<String> jsonStrings(Object row, String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        List<Object> values = jsonValues(row, path);
        if (values.size() == 1 && values.get(0) instanceof JSONArray array) {
            values = new ArrayList<>();
            for (Object item : array) {
                values.add(item);
            }
        }
        return values.stream()
                .map(StructuredOptionParser::scalar)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static String scalar(Object value) {
        if (value == null || value instanceof JSONNull) {
            return null;
        }
        if (value instanceof JSONObject || value instanceof JSONArray) {
            throw new IllegalArgumentException("Expected scalar JSON value.");
        }
        return String.valueOf(value);
    }

    private static List<Option> parseXml(
            byte[] body,
            String rootPath,
            String valuePath,
            String labelPath,
            String descriptionPath,
            String groupPath,
            String disabledPath,
            String disabledReasonPath,
            String tagsPath)
            throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(body));
        XPath xpath = XPathFactory.newInstance().newXPath();
        NodeList rows = (NodeList) xpath.evaluate(
                rootPath == null || rootPath.isBlank() ? "/*" : rootPath, document, XPathConstants.NODESET);
        List<Option> options = new ArrayList<>();
        for (int index = 0; index < rows.getLength(); index++) {
            Node row = rows.item(index);
            String value = xmlString(xpath, row, valuePath, true);
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("Mapped Option value is empty.");
            }
            Option option = new Option(value);
            option.setLabel(xmlString(xpath, row, labelPath, false));
            option.setDescription(xmlString(xpath, row, descriptionPath, false));
            option.setGroup(xmlString(xpath, row, groupPath, false));
            option.setDisabled(booleanValue(xmlString(xpath, row, disabledPath, false)));
            option.setDisabledReason(xmlString(xpath, row, disabledReasonPath, false));
            String tags = xmlString(xpath, row, tagsPath, false);
            option.setTags(
                    tags == null
                            ? List.of()
                            : java.util.Arrays.stream(tags.split(","))
                                    .map(String::trim)
                                    .filter(tag -> !tag.isEmpty())
                                    .toList());
            options.add(option);
        }
        return List.copyOf(options);
    }

    private static String xmlString(XPath xpath, Node row, String path, boolean rowWhenBlank) throws Exception {
        if ((path == null || path.isBlank()) && !rowWhenBlank) {
            return null;
        }
        String expression = path == null || path.isBlank() ? "." : path;
        String value = ((String) xpath.evaluate(expression, row, XPathConstants.STRING)).trim();
        return value.isEmpty() ? null : value;
    }

    private static boolean booleanValue(String value) {
        return value != null
                && java.util.Set.of("true", "1", "yes", "disabled").contains(value.toLowerCase(Locale.ROOT));
    }
}
