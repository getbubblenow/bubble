/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.email.sendgrid;

import bubble.cloud.email.SmtpEmailDriver;
import bubble.dao.account.AccountDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.cloud.CloudCredentials;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpResponseBean;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static bubble.cloud.storage.StorageCryptStream.MIN_DISTINCT_LENGTH;
import static bubble.cloud.storage.StorageCryptStream.MIN_KEY_LENGTH;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpMethods.*;
import static org.cobbzilla.util.http.HttpUtil.getResponse;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.security.CryptoUtil.generatePassword;
import static org.cobbzilla.wizard.model.IdentifiableBase.DEFAULT_SHORT_ID_LENGTH;

/**
 * Only to be used with Sendgrid account with Subusers supported!
 */
public class SendgridEmailDriver extends SmtpEmailDriver {

    public static final String SENDGRID_SMTP = "smtp.sendgrid.net";
    public static final String SG_API_BASE = "https://api.sendgrid.com/v3/";
    public static final String HEADER_SENDGRID_ON_BEHALF_OF = "On-Behalf-Of";

    private static final String PARAM_PARENT_SERVICE = "parentService";

    public static final String SUBUSERS_URI = "subusers";
    public static final String IPS_URI = "ips";

    public static final String TRACKING_SETTINGS_URI = "tracking_settings";
    public static final String TRACKING_CLICK = "click";
    public static final String TRACKING_GA = "google_analytics";
    public static final String TRACKING_OPEN = "open";
    public static final String TRACKING_SUBSCRIPTION = "subscription";
    public static final String[] TRACKING_TYPES = {TRACKING_CLICK, TRACKING_GA, TRACKING_OPEN, TRACKING_SUBSCRIPTION};

    @Override protected boolean isServiceCompatible(final String serviceHost) {
        return SENDGRID_SMTP.equals(serviceHost);
    }

    @Override @NonNull public CloudService setupDelegatedCloudService(@NonNull final BubbleConfiguration configuration,
                                                                      @NonNull final CloudService parentService,
                                                                      @NonNull final CloudService delegatedService) {
        final CloudCredentials parentCredentials = parentService.getCredentials();
        if (parentService.delegated() || !parentCredentials.getParam(PARAM_HOST).contains(".sendgrid.net")) {
            return super.setupDelegatedCloudService(configuration, parentService, delegatedService);
        }

        final String sgIpsJson = doRequest(IPS_URI, GET, null, parentService);
        final List<Map<String, Object>> sgIps = json(sgIpsJson, List.class);
        if (empty(sgIps)) die("No IPs set for the specified main Sendgrid user");
        final String sgIp = (String) sgIps.get(0).get("ip");

        // generate a subuser name and password
        final AccountDAO accountDAO = configuration.getBean(AccountDAO.class);
        final Account accountWithDelegate = accountDAO.findByUuid(delegatedService.getAccount());
        final String user = uuid().substring(0, DEFAULT_SHORT_ID_LENGTH);
        final String password = generatePassword(MIN_KEY_LENGTH, MIN_DISTINCT_LENGTH);

        // create subuser
        final CreateSubuserRequest data = new CreateSubuserRequest(user, accountWithDelegate.getEmail(), password, new String[]{sgIp});
        final String responseJson = doRequest(SUBUSERS_URI, POST, json(data, COMPACT_MAPPER), parentService);
        final CreateSubuserResponse subuser = json(responseJson, CreateSubuserResponse.class, COMPACT_MAPPER);

        // disable all tracking for subuser
        for (String trackingType : TRACKING_TYPES) {
            doRequest(TRACKING_SETTINGS_URI+"/"+trackingType, PATCH, DISABLE_TRACKING_JSON, parentService, subuser.getUsername());
        }

        final CloudCredentials credentials = delegatedService.getCredentials();
        credentials.setParam(PARAM_USER, user)
                   .setParam(PARAM_PASSWORD, password)
                   .setParam(PARAM_PARENT_SERVICE, parentService.getUuid());
        delegatedService.setCredentials(credentials).setDelegated(null).setTemplate(false);
        return delegatedService;
    }

    @Override public void postServiceDelete(@NonNull final CloudServiceDAO serviceDAO,
                                            @NonNull final CloudService service) {
        final String parentServiceUuid = service.getCredentials().getParam(PARAM_PARENT_SERVICE);
        if (parentServiceUuid == null) return;

        final CloudService parentService = serviceDAO.findByUuid(parentServiceUuid);
        if (parentService == null) return;

        final String user = service.getCredentials().getParam(PARAM_USER);
        if (empty(user)) return;

        doRequest(SUBUSERS_URI + "/" + user, DELETE, null, parentService);
    }

    private String doRequest(@NonNull final String uri,
                             @NonNull final String method,
                             @Nullable final String json,
                             @NonNull CloudService parentService) {
        return doRequest(uri, method, json, parentService, null);
    }

    private String doRequest(@NonNull final String uri,
                             @NonNull final String method,
                             @Nullable final String json,
                             @NonNull CloudService parentService,
                             String onBehalfOf) {
        final HttpRequestBean request = new HttpRequestBean();
        request.setMethod(method)
               .setUri(SG_API_BASE + uri)
               .setEntity(json)
               .setHeader(CONTENT_TYPE, APPLICATION_JSON)
               .setHeader(AUTHORIZATION, "Bearer " + parentService.getCredentials().getParam(PARAM_PASSWORD));
        if (onBehalfOf != null) {
            request.setHeader(HEADER_SENDGRID_ON_BEHALF_OF, onBehalfOf);
        }

        final HttpResponseBean response;
        try {
            response = getResponse(request);
        } catch (IOException e) {
            return die("doRequest(" + uri + "): " + e.getMessage(), e);
        }
        if (!response.isOk()) {
            return die("doRequest(" + uri + "): HTTP " + response.getStatus() + " : " + response.getEntityString());
        }
        return response.getEntityString();
    }

    @AllArgsConstructor @NoArgsConstructor
    private static class CreateSubuserRequest {
        @Getter @Setter private String username;
        @Getter @Setter private String email;
        @Getter @Setter private String password;
        @Getter @Setter private String[] ips;
    }

    @JsonIgnoreProperties(ignoreUnknown=true)
    private static class CreateSubuserResponse {
        @Getter @Setter private String username;
        @Getter @Setter private Long user_id;
        @Getter @Setter private String email;
    }

    @AllArgsConstructor @NoArgsConstructor
    private static class TrackingSettingsRequest {
        @Getter @Setter private boolean enabled = false;
    }

    public static final TrackingSettingsRequest DISABLE_TRACKING = new TrackingSettingsRequest(false);
    public static final String DISABLE_TRACKING_JSON = json(DISABLE_TRACKING, COMPACT_MAPPER);

}
