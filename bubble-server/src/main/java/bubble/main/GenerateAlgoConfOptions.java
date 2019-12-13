package bubble.main;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.main.BaseMainOptions;
import org.kohsuke.args4j.Option;

import java.io.File;

public class GenerateAlgoConfOptions extends BaseMainOptions {

    public static final String USAGE_ALGO_CONFIG = "Path to algo config.cfg.hbs file";
    public static final String OPT_ALGO_CONFIG = "-C";
    public static final String LONGOPT_ALGO_CONFIG = "--algo-config";
    @Option(name=OPT_ALGO_CONFIG, aliases=LONGOPT_ALGO_CONFIG, usage=USAGE_ALGO_CONFIG)
    @Getter @Setter private File algoConfig;

}
