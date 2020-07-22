package db;

import lombok.NonNull;
import org.flywaydb.core.api.migration.Context;

public abstract class BubbleValueUpdateMigration extends BubbleMigration {
    @Override public void migrate(@NonNull final Context context) throws Exception {
        final var connection = context.getConnection();
        final var select = connection.prepareStatement("SELECT uuid, " + fieldToUpdate() + " FROM " + tableToUpdate()
                                                       + " WHERE " + condition());
        final var update = connection.prepareStatement("UPDATE " + tableToUpdate()
                                                       + " SET " + fieldToUpdate() + " = ? WHERE uuid = ?");

        final var rows = select.executeQuery();
        while (rows.next()) {
            update.setString(1, updateValues(rows.getString(2)));
            update.setString(2, rows.getString(1));
            update.executeUpdate();
        }
    }

    @NonNull protected abstract String tableToUpdate();
    @NonNull protected abstract String fieldToUpdate();
    @NonNull protected abstract String condition();
    @NonNull protected abstract String updateValues(@NonNull String currentValue);
}
