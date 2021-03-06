/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.main;

import bubble.BubbleHandlebars;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.main.BaseMain;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.system.CommandShell.execScript;

public class GenerateAlgoConfMain extends BaseMain<GenerateAlgoConfOptions> {

    public static final String HANDLEBARS_SUFFIX = ".hbs";

    public static void main(String[] args) { main(GenerateAlgoConfMain.class, args); }

    @Override protected void run() throws Exception {
        final GenerateAlgoConfOptions options = getOptions();
        final File configTemplate = options.getAlgoConfig();

        if (!configTemplate.exists()) die("Config template does not exist: "+abs(configTemplate));
        if (!configTemplate.canRead()) die("Config template is not readable: "+abs(configTemplate));

        final String templateName = configTemplate.getName();
        if (!templateName.endsWith(HANDLEBARS_SUFFIX)) die("Config template is not a handlebars template: "+abs(configTemplate));

        final File config = new File(configTemplate.getParentFile(), templateName.substring(0, templateName.length() - HANDLEBARS_SUFFIX.length()));
        if ((config.exists() && !config.canWrite()) || (!config.exists() && !config.getParentFile().canWrite())) {
            die("Config file is not writeable: "+abs(config));
        }

        final String configTemplateData = FileUtil.toStringOrDie(configTemplate);
        final Map<String, Object> ctx = new HashMap<>();
        ctx.put("bubbleUsers", loadDevices());
        final String configData = HandlebarsUtil.apply(BubbleHandlebars.instance.getHandlebars(), configTemplateData, ctx, '<', '>');
        FileUtil.toFileOrDie(config, configData);
        out("wrote config: "+abs(config));
    }

    private List<String> loadDevices() {
        try {
            final String sqlResult = execScript("echo \"select uuid from device where enabled = TRUE and device_type != 'non_vpn'\" | PGPASSWORD=\"$(cat /home/bubble/.BUBBLE_PG_PASSWORD)\" psql -U bubble -h 127.0.0.1 bubble -qt");
            final List<String> deviceUuids = Arrays.stream(sqlResult.split("\n"))
                    .filter(device -> !empty(device))
                    .map(String::trim)
                    .collect(Collectors.toList());
            if (empty(deviceUuids)) return die("loadUsers: no devices found");
            return deviceUuids;

        } catch (Exception e) {
            return die("loadDevices: "+e);
        }
    }

}
