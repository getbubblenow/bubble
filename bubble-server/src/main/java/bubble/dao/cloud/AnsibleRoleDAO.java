package bubble.dao.cloud;

import bubble.cloud.CloudServiceType;
import bubble.dao.account.AccountOwnedTemplateDAO;
import bubble.model.account.Account;
import bubble.model.cloud.AnsibleRole;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import bubble.service.cloud.StorageService;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.io.Tarball;
import org.cobbzilla.util.io.TempDir;
import org.cobbzilla.util.string.Base64;
import org.hibernate.criterion.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.*;
import java.util.List;

import static bubble.cloud.storage.StorageServiceDriver.STORAGE_PREFIX;
import static bubble.cloud.storage.local.LocalStorageDriver.LOCAL_STORAGE;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.string.Base64.DONT_GUNZIP;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Repository @Slf4j
public class AnsibleRoleDAO extends AccountOwnedTemplateDAO<AnsibleRole> {

    public static final String ROLE_PATH = "automation/roles/";

    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private StorageService storageService;
    @Autowired private BubbleConfiguration configuration;

    @Override public Order getDefaultSortOrder() { return PRIORITY_ASC; }

    @Override public Object preCreate(AnsibleRole role) {

        // convert [[ .. ]] to {{ }}
        if (role.hasConfig()) role.setConfigJson(role.getConfigJson().replace("[[", "{{").replace("]]", "}}"));

        if (!role.hasTgzB64()) throw invalidEx("err.tgzB64.required");

        // Is it a raw tgz?
        if (role.isTgzB64raw()) {
            // Ensure it is a valid tarball
            try {
                @Cleanup("delete") final TempDir temp = new TempDir();
                final File tarballFile = new File(temp, role.getName() + ".tgz");
                FileUtil.toFile(tarballFile, new ByteArrayInputStream(Base64.decode(role.getTgzB64(), DONT_GUNZIP)));

                Tarball.unroll(tarballFile, temp);
                final File namedRoleDir = new File(temp, role.getRoleName());
                if (!namedRoleDir.exists() || !namedRoleDir.isDirectory()) throw invalidEx("err.tgzB64.invalid.noRolesDir", "no roles/"+ role.getName()+" dir");

                // the role dir and the tarball are the only 2 files here
                final String[] files = temp.list();
                if (files == null || files.length != 2) {
                    throw invalidEx("err.tgzB64.invalid.wrongNumberOfFiles", "multiple entries in tarball base directory");
                }

                final File mainTask = new File(new File(namedRoleDir, "tasks"), "main.yml");
                if (!mainTask.exists() || !mainTask.isFile()) throw invalidEx("err.tgzB64.invalid.missingTasksMainYml", "no roles/"+ role.getName()+"/tasks/main.yml file");

                final String key = ROLE_PATH + tarballFile.getName();
                final List<CloudService> clouds = cloudDAO.findByAccountAndType(role.getAccount(), CloudServiceType.storage);
                String stored = null;
                for (CloudService cloud : clouds) {
                    try {
                        @Cleanup final FileInputStream in = new FileInputStream(tarballFile);
                        cloud.getStorageDriver(configuration).write(configuration.getThisNode().getUuid(), key, in);
                        stored = STORAGE_PREFIX+cloud.getName()+"/"+key;
                        break;
                    } catch (Exception e) {
                        log.warn("preCreate: error storing role archive to "+cloud.getName()+"/"+key+": "+e);
                    }
                }
                if (stored == null) {
                    return die("preCreate: failed to store role archive to any storage service");
                }
                role.setTgzB64(stored);

            } catch (Exception e) {
                throw invalidEx("err.tgzB64.invalid.writingToStorage", "error validating tarball/writing to storage: "+e);
            }

        } else if (role.isTgzB64storage()) {
            // Verify file exists in storage
            try {
                if (!storageService.exists(role.getAccount(), role.getTgzB64())) {
                    throw new IllegalStateException("preCreate: role archive not found in storage: "+role.getTgzB64());
                }
            } catch (Exception e) {
                boolean existsOnClasspath = false;
                final String prefix = STORAGE_PREFIX + LOCAL_STORAGE + "/";
                final String roleTgzPath;
                if (role.getTgzB64().startsWith(prefix + "automation/roles/")) {
                    // check classpath
                    roleTgzPath = role.getTgzB64().substring(prefix.length());
                    try {
                        @Cleanup final InputStream in = getClass().getClassLoader().getResourceAsStream(roleTgzPath);
                        existsOnClasspath = in != null;
                    } catch (Exception ioe) {
                        log.warn("preCreate: role archive not found in storage ("+role.getTgzB64()+") and exception searching classpath ("+roleTgzPath+"): "+shortError(ioe));
                    }
                } else {
                    roleTgzPath = null;
                }
                if (!existsOnClasspath) {
                    throw invalidEx("err.tgzB64.invalid.readingFromStorage", "error reading from " + roleTgzPath + " : " + e);
                }
            }
        }

        return super.preCreate(role);
    }

    public List<AnsibleRole> findByAccountAndNames(Account account, String[] roles) {
        return findByFieldAndFieldIn("account", account.getUuid(), "name", roles, getDefaultSortOrder());
    }

}
