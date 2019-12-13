package bubble.model.app;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.collection.NameAndValue;

import java.io.Serializable;

public class AppRuleDriverDescriptor implements Serializable {

    @Getter @Setter private String name;
    @Getter @Setter private String driverClass;
    @Getter @Setter private String description;
    @Getter @Setter private String icon;
    @Getter @Setter private NameAndValue[] labels;
    public boolean hasLabels () { return labels != null && labels.length > 0; }

    public void setLabel(NameAndValue label) {
        if (labels == null) {
            labels = new NameAndValue[] { label };
            return;
        }
        boolean found = false;
        for (NameAndValue n : labels) {
            if (n.getName().equals(label.getName())) {
                n.setValue(label.getValue());
                found = true;
            }
        }
        if (!found) labels = ArrayUtil.append(labels, label);
    }

}
