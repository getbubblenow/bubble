package bubble.dao.app;

public interface AppDataDAOBase extends RemoteDAO {

    String findValueByAppAndSiteAndKey(String app, String site, String key);

}
