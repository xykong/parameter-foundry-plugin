package io.jenkins.plugins.unifiedparameters.model;

import hudson.model.Item;
import hudson.model.Job;
import java.io.BufferedReader;
import java.io.IOException;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

public final class OptionImportEndpoint {

    private OptionImportEndpoint() {}

    public static void previewForJob(Job<?, ?> job, StaplerRequest2 request, StaplerResponse2 response)
            throws IOException {
        job.checkPermission(Item.READ);
        job.checkPermission(Item.CONFIGURE);
        preview(request, response);
    }

    public static void previewForAdministrator(StaplerRequest2 request, StaplerResponse2 response) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        preview(request, response);
    }

    private static void preview(StaplerRequest2 request, StaplerResponse2 response) throws IOException {
        JSONObject result = new JSONObject();
        try {
            JSONObject body = JSONObject.fromObject(readBody(request));
            var options = OptionImportService.parse(body.optString("content", ""));
            JSONArray encoded = new JSONArray();
            for (Option option : options) {
                JSONObject item = new JSONObject();
                item.put("value", option.getValue());
                item.put("label", option.getLabel());
                item.put("description", option.getDescription());
                item.put("group", option.getGroup());
                item.put("disabled", option.isDisabled());
                item.put("disabledReason", option.getDisabledReason());
                item.put("tags", JSONArray.fromObject(option.getTags()));
                encoded.add(item);
            }
            result.put("ok", true);
            result.put("count", encoded.size());
            result.put("options", encoded);
            write(response, 200, result);
        } catch (IllegalArgumentException exception) {
            result.put("ok", false);
            result.put("message", exception.getMessage());
            write(response, 400, result);
        } catch (RuntimeException exception) {
            result.put("ok", false);
            result.put("message", "Import request is not valid JSON.");
            write(response, 400, result);
        }
    }

    private static String readBody(StaplerRequest2 request) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                content.append(buffer, 0, count);
                if (content.length() > OptionImportService.MAX_CONTENT_LENGTH + 1024) {
                    throw new IllegalArgumentException("Import request exceeds 1 MiB.");
                }
            }
        }
        return content.toString();
    }

    private static void write(StaplerResponse2 response, int status, JSONObject result) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(result.toString());
    }
}
