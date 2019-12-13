package bubble.cloud;

import bubble.model.cloud.CloudService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.filters.CustomScrubbage;
import org.cobbzilla.wizard.filters.ScrubbableField;

import javax.persistence.Transient;

import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@NoArgsConstructor @Accessors(chain=true)
public class CloudRegionRelative extends CloudRegion implements CustomScrubbage {

    public CloudRegionRelative(CloudRegion region) { copy(this, region); }

    @Transient @Getter @Setter private CloudService cloud;
    @Transient @Getter @Setter private double distance;

    public void setDistance(double latitude, double longitude) {
        distance = getLocation().distance(latitude, longitude);
    }

    @Override public void scrub(Object entity, ScrubbableField field) {
        switch (field.name) {
            case "cloud":
                if (cloud != null) {
                    cloud.setCredentialsJson(null);
                    cloud.setDriverConfigJson(null);
                }
        }
    }

    public static final ScrubbableField[] SCRUB_FIELDS = {
            new ScrubbableField(CloudRegionRelative.class, "cloud", CloudService.class)
    };

    @Override public ScrubbableField[] fieldsToScrub() { return SCRUB_FIELDS; }
}
