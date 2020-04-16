/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.main.http;

import bubble.main.BubbleApiMain;
import org.apache.commons.io.IOUtils;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.string.Base64;
import org.cobbzilla.wizard.api.ApiException;
import org.cobbzilla.wizard.api.ForbiddenException;
import org.cobbzilla.wizard.api.NotFoundException;
import org.cobbzilla.wizard.api.ValidationException;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.util.RestResponse;

import javax.ws.rs.core.HttpHeaders;

import static org.cobbzilla.util.daemon.ZillaRuntime.errorString;
import static org.cobbzilla.util.http.HttpStatusCodes.OK;
import static org.cobbzilla.util.json.JsonUtil.prettyJson;

public abstract class BubbleHttpMain<OPT extends BubbleHttpOptions> extends BubbleApiMain<OPT> {

    protected abstract RestResponse request(String url) throws Exception;

    protected abstract String getMethod();

    @Override protected ApiClientBase initApiClient() {
        final ApiClientBase api = super.initApiClient();
        final OPT options = getOptions();
        if (options.hasHttpBasicUser() || options.hasHttpBasicPassword()) {
            final String usernameAndPassword = options.getHttpBasicUser() + ":" + options.getHttpBasicPassword();
            api.setHeader(HttpHeaders.AUTHORIZATION, "Basic "+ Base64.encodeBytes(usernameAndPassword.getBytes()));
        }
        return api;
    }

    @Override protected void run() throws Exception {
        final OPT options = getOptions();
        final String url = options.getUrl();
        final String requestUrl = url.startsWith("/") ? url : "/" + url;

        if (options.isRaw()) {
            final HttpRequestBean request;
            final BubbleHttpEntityOptions entityOptions = (options instanceof BubbleHttpEntityOptions) ? (BubbleHttpEntityOptions) options : null;
            if (entityOptions != null) {
                if (entityOptions.hasMultipartFileName()) {
                    request = new HttpRequestBean(getMethod(), requestUrl, entityOptions.getRequestStream(), entityOptions.getMultipartFileName(), NameAndValue.EMPTY_ARRAY);
                } else {
                    request = new HttpRequestBean(getMethod(), requestUrl, entityOptions.getRequestJson());
                }
            } else {
                request = new HttpRequestBean(getMethod(), requestUrl);
            }

            if (options.hasHttpBasicUser()) request.setAuthUsername(options.getHttpBasicUser());
            if (options.hasHttpBasicPassword()) request.setAuthPassword(options.getHttpBasicPassword());
            if (entityOptions != null && entityOptions.hasMultipartFileName()) {
                IOUtils.copyLarge(getApiClient().uploadMultipartStream(request, entityOptions.getMultipartFileName()), System.out);
            } else {
                IOUtils.copyLarge(getApiClient().getStream(request), System.out);
            }
        } else {
            RestResponse response = null;
            try {
                response = request(requestUrl);
                if (response.status == OK) {
                    out(prettyJson(response.json));
                } else {
                    error(response, null, "unexpected status");
                }
            } catch (NotFoundException e) {
                error(response, e, "Not Found");
            } catch (ForbiddenException e) {
                error(response, e, "Forbidden");
            } catch (ValidationException e) {
                error(response, e, "Invalid");
            } catch (Exception e) {
                error(response, e, "Unexpected exception: " + errorString(e));
            }
        }
    }

    protected void error(RestResponse response, Exception e, String message) {
        if (response == null) {
            if (e instanceof ApiException) {
                err("Error: " + e.getMessage() + " - " + message);
                out(prettyJson(((ApiException) e).getResponse().json));
            } else {
                err("Error: " + ((e == null) ? "null" : e.getMessage()) + " - " + message);
            }
        } else {
            err("Error: " + response.status + " - " + message);
            out(response.json);
        }
    }

}
