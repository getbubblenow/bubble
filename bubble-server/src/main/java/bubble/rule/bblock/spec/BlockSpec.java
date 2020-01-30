package bubble.rule.bblock.spec;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.http.HttpContentTypes;
import org.cobbzilla.util.string.StringUtil;

import java.util.ArrayList;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.HttpContentTypes.contentType;

@Slf4j
public class BlockSpec {

    public static final String OPT_DOMAIN_PREFIX = "domain=";
    public static final String OPT_SCRIPT = "script";
    public static final String OPT_IMAGE = "image";
    public static final String OPT_STYLESHEET = "stylesheet";

    @Getter private String line;
    @Getter private BlockSpecTarget target;

    @Getter private List<String> domainExclusions;
    @Getter private List<String> typeMatches;
    public boolean hasTypeMatches () { return !empty(typeMatches); }

    @Getter private List<String> typeExclusions;

    public BlockSpec(String line, BlockSpecTarget target, List<String> options, String selector) {
        this.line = line;
        this.target = target;
        this.selector = selector;
        if (options != null) {
            for (String opt : options) {
                if (opt.startsWith(OPT_DOMAIN_PREFIX)) {
                    processDomainOptions(opt.substring(OPT_DOMAIN_PREFIX.length()));

                } else if (opt.startsWith("~")) {
                    final String type = opt.substring(1);
                    if (isTypeOption(type)) {
                        if (typeExclusions == null) typeExclusions = new ArrayList<>();
                        typeExclusions.add(type);
                    } else {
                        log.warn("unsupported option (ignoring): " + opt);
                    }

                } else {
                    if (isTypeOption(opt)) {
                        if (typeMatches == null) typeMatches = new ArrayList<>();
                        typeMatches.add(opt);
                    } else {
                        log.warn("unsupported option (ignoring): "+opt);
                    }
                }
            }
        }
    }

    private void processDomainOptions(String option) {
        final String[] parts = option.split("\\|");
        for (String domainOption : parts) {
            if (domainOption.startsWith("~")) {
                if (domainExclusions == null) domainExclusions = new ArrayList<>();
                domainExclusions.add(domainOption.substring(1));
            } else {
                log.warn("ignoring included domain: "+domainOption);
            }
        }
    }

    public boolean isTypeOption(String type) {
        return type.equals(OPT_SCRIPT) || type.equals(OPT_IMAGE) || type.equals(OPT_STYLESHEET);
    }

    @Getter private String selector;
    public boolean hasSelector() { return !empty(selector); }

    public static List<BlockSpec> parse(String line) {

        line = line.trim();
        int optionStartPos = line.indexOf('$');
        int selectorStartPos = line.indexOf("##");

        // sanity check that selectorStartPos > optionStartPos -- $ may occur AFTER ## if the selector contains a regex
        if (selectorStartPos != -1 && optionStartPos > selectorStartPos) optionStartPos = -1;

        final List<BlockSpecTarget> targets;
        final List<String> options;
        final String selector;
        if (optionStartPos == -1) {
            if (selectorStartPos == -1) {
                // no options, no selector, entire line is the target
                targets = BlockSpecTarget.parseBareLine(line);
                options = null;
                selector = null;
            } else {
                // no options, but selector present. split into target + selector
                targets = BlockSpecTarget.parse(line.substring(0, selectorStartPos));
                options = null;
                selector = line.substring(selectorStartPos+1);
            }
        } else {
            if (selectorStartPos == -1) {
                // no selector, split into target + options
                targets = BlockSpecTarget.parse(line.substring(0, optionStartPos));
                options = StringUtil.splitAndTrim(line.substring(optionStartPos+1), ",");
                selector = null;
            } else {
                // all 3 elements present
                targets = BlockSpecTarget.parse(line.substring(0, optionStartPos));
                options = StringUtil.splitAndTrim(line.substring(optionStartPos + 1, selectorStartPos), ",");
                selector = line.substring(selectorStartPos+1);
            }
        }
        final List<BlockSpec> specs = new ArrayList<>();
        for (BlockSpecTarget target : targets) specs.add(new BlockSpec(line, target, options, selector));
        return specs;
    }

    public boolean matches(String fqdn, String path) {
        if (target.hasDomainRegex() && target.getDomainPattern().matcher(fqdn).find()) {
            return checkDomainExclusionsAndType(fqdn, contentType(path));

        } else if (target.hasRegex()) {
            if (target.getRegexPattern().matcher(path).find()) {
                return checkDomainExclusionsAndType(fqdn, contentType(path));
            }
            final String full = fqdn + path;
            if (target.getRegexPattern().matcher(full).find()) {
                return checkDomainExclusionsAndType(fqdn, contentType(path));
            };
        }
        return false;
    }

    public boolean checkDomainExclusionsAndType(String fqdn, String contentType) {
        if (domainExclusions != null) {
            for (String domain : domainExclusions) {
                if (domain.equals(fqdn)) return false;
            }
        }
        if (typeExclusions != null) {
            for (String type : typeExclusions) {
                switch (type) {
                    case OPT_SCRIPT:
                        if (contentType.equals(HttpContentTypes.APPLICATION_JAVASCRIPT)) return false;
                        break;
                    case OPT_IMAGE:
                        if (contentType.startsWith(HttpContentTypes.IMAGE_PREFIX)) return false;
                        break;
                    case OPT_STYLESHEET:
                        if (contentType.equals(HttpContentTypes.TEXT_CSS)) return false;
                        break;
                }
            }
        }
        if (typeMatches != null) {
            for (String type : typeMatches) {
                switch (type) {
                    case OPT_SCRIPT:
                        if (contentType.equals(HttpContentTypes.APPLICATION_JAVASCRIPT)) return true;
                        break;
                    case OPT_IMAGE:
                        if (contentType.startsWith(HttpContentTypes.IMAGE_PREFIX)) return true;
                        break;
                    case OPT_STYLESHEET:
                        if (contentType.equals(HttpContentTypes.TEXT_CSS)) return true;
                        break;
                }
            }
            return false;
        }
        return true;
    }

}
