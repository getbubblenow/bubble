package bubble.rule;

import lombok.Getter;
import lombok.Setter;

public class RuleConfigBase implements RuleConfig {

    @Getter @Setter protected String app;
    @Getter @Setter protected String driver;
    @Getter @Setter protected String rule;
    @Getter @Setter protected String matcher;

}
