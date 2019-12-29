package bubble.service.cloud;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.regex.Pattern;

@Accessors(chain=true)
public class NodeProgressMeterTick {

    @Getter @Setter private String account;

    @Getter @Setter private String pattern;
    @JsonIgnore @Getter(lazy=true) private final Pattern _pattern = Pattern.compile(getPattern());

    @Getter @Setter private Boolean exact;
    public boolean exact() { return exact != null && exact; }

    @Getter @Setter private Boolean standard;
    public boolean standard() { return standard != null && standard; }

    @Getter @Setter private Integer percent;

    public NodeProgressMeterTick relativizePercent(int lastStandardPercent) {
        setPercent(Math.round(((float) lastStandardPercent) + (100f - lastStandardPercent) * getPercent() / 100f));
        return this;
    }

    @Getter @Setter private String messageKey;
    @Getter @Setter private String details;

    public boolean matches(String line) {
        return exact()
                ? line.trim().equals(getPattern().trim())
                : get_pattern().matcher(line).matches();
    }

}
