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
import lombok.*;
import org.cobbzilla.util.http.HttpMethods;
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
import static org.cobbzilla.util.http.HttpUtil.getResponse;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.security.CryptoUtil.generatePassword;
import static org.cobbzilla.wizard.model.IdentifiableBase.DEFAULT_SHORT_ID_LENGTH;

/**
 * Only to be used with Sendgrid account with Subusers supported!
 */
public class SendgridSmtpEmailDriver extends SmtpEmailDriver {

    public static final String SENDGRID_SMTP = "smtp.sendgrid.net";
    public static final String SG_API_BASE = "https://api.sendgrid.com/v3/";

    private static final String PARAM_PARENT_SERVICE = "parentService";

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

        final String sgIpsJson = doRequest("ips", HttpMethods.GET, null, parentService);
        final List<Map<String, Object>> sgIps = json(sgIpsJson, List.class);
        if (empty(sgIps)) die("No IPs set for the specified main Sendgrid user");
        final String sgIp = (String) sgIps.get(0).get("ip");

        final Account accountWithDelegate = configuration.getBean(AccountDAO.class)
                                                         .findByUuid(delegatedService.getAccount());
        final String user = uuid().substring(0, DEFAULT_SHORT_ID_LENGTH);
        String password = generatePassword(MIN_KEY_LENGTH, MIN_DISTINCT_LENGTH);
        final CreateSubuserRequest data = new CreateSubuserRequest(user, accountWithDelegate.getEmail(), password,
                                                                   new String[]{ sgIp });
        doRequest("subusers", HttpMethods.POST, json(data), parentService);

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

        doRequest("subusers/" + user, HttpMethods.DELETE, null, parentService);
    }

    private String doRequest(@NonNull final String uri, @NonNull final String method, @Nullable final String json,
                             @NonNull CloudService parentService) {
        final HttpRequestBean request = new HttpRequestBean();
        request.setMethod(method)
               .setUri(SG_API_BASE + uri)
               .setEntity(json)
               .setHeader(CONTENT_TYPE, APPLICATION_JSON)
               .setHeader(AUTHORIZATION, "Bearer " + parentService.getCredentials().getParam(PARAM_PASSWORD));

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
    private class CreateSubuserRequest {
        @Getter @Setter private String username;
        @Getter @Setter private String email;
        @Getter @Setter private String password;
        @Getter @Setter private String[] ips;
    }

}
