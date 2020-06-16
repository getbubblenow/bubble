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

    public static final String AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
    public static final String AWS_SECRET_KEY = "AWS_SECRET_KEY";

    private final CloudService cloud;
    private final CloudCredentials awsCredentials;

    public BubbleAwsCredentialsProvider (CloudService cloud, CloudCredentials credentials) {
        this.cloud = cloud;
        this.awsCredentials = credentials;
    }

    @Getter(lazy=true) private final AWSCredentials credentials = new BubbleAwsCredentials();

    @Override public void refresh() {}

    private class BubbleAwsCredentials implements AWSCredentials {

        @Override public String getAWSAccessKeyId() {
            final String key = awsCredentials.getParam(AWS_ACCESS_KEY_ID);
            return empty(key)
                    ? die("getAWSAccessKeyId: no AWS_ACCESS_KEY_ID defined in credentials for cloud: "+cloud.getUuid())
                    : key;

        }

        @Override public String getAWSSecretKey() {
            final String key = awsCredentials.getParam(AWS_SECRET_KEY);
            return empty(key)
                    ? die("getAWSSecretKey: no AWS_SECRET_KEY defined in credentials for cloud: "+cloud.getUuid())
                    : key;
        }
    }
}
