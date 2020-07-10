package bubble.notify.upgrade;

import bubble.model.cloud.BubbleVersionInfo;
import bubble.notify.SynchronousNotification;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class JarUpgradeNotification extends SynchronousNotification {

    @Getter @Setter private BubbleVersionInfo versionInfo;

    @Override protected String getCacheKey() { return versionInfo.getSha256(); }

}
