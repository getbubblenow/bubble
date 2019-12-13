package bubble.rule;

import lombok.Getter;
import lombok.Setter;

public class UserConfigField {

    @Getter @Setter private String name;
    @Getter @Setter private String type;
    @Getter @Setter private String description;

    @Getter @Setter private String value;
    public boolean hasValue () { return value != null && value.trim().length() > 0; }

}
