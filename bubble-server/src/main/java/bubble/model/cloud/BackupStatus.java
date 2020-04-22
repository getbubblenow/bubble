/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.cloud;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static bubble.ApiConstants.enumFromString;

@NoArgsConstructor @AllArgsConstructor
public enum BackupStatus {

    queued (true),
    backup_in_progress,
    backup_completed (true),
    backup_error (true),
    deleting,
    delete_error (true);

    public static final BackupStatus[] STUCK = {backup_error, deleting, delete_error};

    @Getter private boolean deletable = false;

    @JsonCreator public static BackupStatus fromString (String v) { return enumFromString(BackupStatus.class, v); }

}
