/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.backup;

import bubble.model.cloud.BubbleBackup;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true)
public class BackupDecision {

    public BackupDecision(BubbleBackup queuedBackup) {
        this.queuedBackup = queuedBackup;
        this.backupNow = true;
    }

    public static final BackupDecision FALSE = new BackupDecision().setBackupNow(false);
    public static final BackupDecision TRUE = new BackupDecision().setBackupNow(true);

    @Getter @Setter private boolean backupNow = false;
    @Getter @Setter private BubbleBackup queuedBackup;
    public boolean hasQueuedBackup() { return queuedBackup != null; }

}
