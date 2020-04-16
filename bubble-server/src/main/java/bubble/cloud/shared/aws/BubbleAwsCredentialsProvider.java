/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.shared.aws;

import bubble.model.cloud.CloudCredentials;
import bubble.model.cloud.CloudService;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import lombok.Getter;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class BubbleAwsCredentialsProvider implements AWSCredentialsProvider {

    private final CloudService cloud;
    private final CloudCredentials awsCredentials;
    private String accessKeyParam = "AWS_ACCESS_KEY_ID";
    private String secretKeyParam = "AWS_SECRET_KEY";

    public BubbleAwsCredentialsProvider (CloudService cloud, CloudCredentials credentials) {
        this.cloud = cloud;
        this.awsCredentials = credentials;
    }

    public BubbleAwsCredentialsProvider (CloudService cloud, CloudCredentials credentials, String accessKeyParam, String secretKeyParam) {
        this.cloud = cloud;
        this.awsCredentials = credentials;
        this.accessKeyParam = accessKeyParam;
        this.secretKeyParam = secretKeyParam;
    }

    @Getter(lazy=true) private final AWSCredentials credentials = new BubbleAwsCredentials();

    @Override public void refresh() {}

    private class BubbleAwsCredentials implements AWSCredentials {

        @Override public String getAWSAccessKeyId() {
            final String key = awsCredentials.getParam(accessKeyParam);
            return empty(key)
                    ? die("getAWSAccessKeyId: no accessKeyParam ("+accessKeyParam+") defined in credentials for cloud: "+cloud.getUuid())
                    : key;

        }

        @Override public String getAWSSecretKey() {
            final String key = awsCredentials.getParam(secretKeyParam);
            return empty(key)
                    ? die("getAWSSecretKey: no secretKeyParam ("+secretKeyParam+") defined in credentials for cloud: "+cloud.getUuid())
                    : key;
        }
    }
}
