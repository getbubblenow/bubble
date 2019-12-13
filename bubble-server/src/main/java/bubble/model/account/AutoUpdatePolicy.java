package bubble.model.account;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.Embeddable;
import java.io.Serializable;

@Embeddable @Accessors(chain=true)
public class AutoUpdatePolicy implements Serializable {

    public static final AutoUpdatePolicy EMPTY_AUTO_UPDATE_POLICY = new AutoUpdatePolicy()
            .setDriverUpdates(null)
            .setAppUpdates(null)
            .setDataUpdates(null)
            .setNewStuff(null);

    @Getter @Setter private Boolean driverUpdates = true;
    public boolean driverUpdates() { return driverUpdates == null || driverUpdates; }

    @Getter @Setter private Boolean appUpdates = true;
    public boolean appUpdates() { return appUpdates == null || appUpdates; }

    @Getter @Setter private Boolean dataUpdates = true;
    public boolean dataUpdates() { return dataUpdates == null || dataUpdates; }

    @Getter @Setter private Boolean newStuff = true;
    public boolean newStuff() { return newStuff == null || newStuff; }

}
