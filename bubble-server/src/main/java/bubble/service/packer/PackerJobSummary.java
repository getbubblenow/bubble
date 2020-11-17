package bubble.service.packer;

import bubble.model.cloud.AnsibleInstallType;
import bubble.model.cloud.CloudService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.util.time.TimeUtil.formatDuration;

@NoArgsConstructor @Accessors(chain=true)
public class PackerJobSummary {

    @Getter private CloudService cloud;
    @Getter private AnsibleInstallType installType;
    @Getter private long ctime;

    private static final String[] CLOUD_SUMMARY_FIELDS = {"uuid", "name", "account"};

    public PackerJobSummary (PackerJob job) {
        this.cloud = new CloudService();
        copy(this.cloud, job.getCloud(), CLOUD_SUMMARY_FIELDS);

        this.installType = job.getInstallType();
        this.ctime = job.getCtime();
    }

    // derived properties, useful when displaying PackerJobSummary as JSON via `pack_status`
    public String getDuration () { return formatDuration(now() - getCtime()); }
    public PackerJobSummary setDuration (String d) { return this; } // noop


}